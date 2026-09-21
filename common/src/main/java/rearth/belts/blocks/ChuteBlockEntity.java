package rearth.belts.blocks;

import dev.architectury.platform.Platform;
import dev.ftb.mods.ftbfiltersystem.api.FTBFilterSystemAPI;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import rearth.belts.BlockContent;
import rearth.belts.BlockEntitiesContent;
import rearth.belts.ItemContent;
import rearth.belts.api.item.ItemApi;
import rearth.belts.collision.BeltCollisionRegistry;
import rearth.belts.model.BeltContents;
import rearth.belts.model.BeltTier;
import rearth.belts.util.SplineUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class ChuteBlockEntity extends BlockEntity implements BlockEntityTicker<ChuteBlockEntity> {

    // everything in this section is synced to the client
    private BlockPos target;
    private List<BlockPos> midPoints = new ArrayList<>();
    private BeltTier beltTier = BeltTier.BELT;
    private final BeltContents<BeltItem> contents = new BeltContents<>();
    // Sequential rather than random: the renderer keys each item's lerp by id, and a full belt
    // holds 512 (#344).
    private short nextItemId;
    private boolean needsFit = true;
    
    // this is calculated on both the client and server
    private BeltData beltData;
    
    // used to check if a belt is used as target. Periodically updated on belt ends from the belt starts.
    private long lastTargetedTime;
    private BlockPos sourceBeltPos = BlockPos.ZERO;
    
    // used for filtering. Optionally works with create and ftb filters.
    public ItemStack filteredItem = ItemStack.EMPTY;
    
    // client only data, used for rendering
    public final Map<Long, Integer> cachedLightCoords = new HashMap<>();
    // When the belt's contents last arrived, in System.nanoTime, for the renderer to extrapolate
    // from. Not the game time: the server resyncs the client's every second, and each jump shows.
    public long contentsReceivedAt;
    
    private boolean networkDirty = false;
    
    public ChuteBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntitiesContent.CHUTE_BLOCK.get(), pos, state);
    }
    
    @Override
    public void tick(Level level, BlockPos pos, BlockState state, ChuteBlockEntity blockEntity) {
        if (target == null || target.equals(BlockPos.ZERO)) {
            BeltCollisionRegistry.unregister(this);
            if (!level.isClientSide() && !contents.isEmpty()) {
                dropContent(level, pos);
            }
            return;
        }
        
        if (beltData == null) {
            beltData = BeltData.create(this);
            if (level instanceof ServerLevel serverLevel)
                serverLevel.sendBlockUpdated(pos, state, state, Block.UPDATE_ALL);
        }
        
        if (beltData == null) {
            BeltCollisionRegistry.unregister(this);
            target = null;
            midPoints = new ArrayList<>();
            return;
        }

        BeltCollisionRegistry.register(this);
        
        if (level.isClientSide()) return;
        
        if (needsFit) {
            var spawnAt = pos.getCenter();
            for (var spilled : contents.fit(getBeltLength())) {
                level.addFreshEntity(new ItemEntity(level, spawnAt.x, spawnAt.y, spawnAt.z, spilled.stack()));
            }
            needsFit = false;
        }
        
        if (contents.tick(getBeltLength(), beltTier.blocksPerTick(), this::extractOne, this::insertIntoTarget)) {
            networkDirty = true;
            setChanged();
        }
        
        // refresh target
        if (level.getGameTime() % 19 == 0)
            assignTargetState(level);
        
        
        if (networkDirty && level instanceof ServerLevel serverWorld) {
            serverWorld.sendBlockUpdated(pos, state, state, Block.UPDATE_ALL);
            networkDirty = false;
        }
        
    }
    
    public void dropContent(Level level, BlockPos pos) {
        BeltCollisionRegistry.unregister(this);
        
        // notify source to be reset
        if (level.getGameTime() - this.lastTargetedTime < 20 && !this.sourceBeltPos.equals(BlockPos.ZERO) && level instanceof ServerLevel serverWorld) {
            var sourceEntityCandidate = level.getBlockEntity(this.sourceBeltPos, BlockEntitiesContent.CHUTE_BLOCK.get());
            if (sourceEntityCandidate.isPresent() && sourceEntityCandidate.get() != this) {
                var source = sourceEntityCandidate.get();
                source.dropContent(level, pos);
                source.target = null;
                serverWorld.sendBlockUpdated(this.sourceBeltPos, source.getBlockState(), source.getBlockState(), Block.UPDATE_ALL);
                source.networkDirty = true;
                source.setChanged();
            }
        }
        
        var spawnAt = pos.getCenter();
        for (var entry : contents.entries()) {
            level.addFreshEntity(new ItemEntity(level, spawnAt.x, spawnAt.y, spawnAt.z, entry.payload().stack()));
        }
        
        if (!contents.isEmpty() || (target != null && !target.equals(BlockPos.ZERO))) {
            // pretend to drop an actual belt
            var stack = new ItemStack(ItemContent.beltFor(beltTier), 1);
            level.addFreshEntity(new ItemEntity(level, spawnAt.x, spawnAt.y, spawnAt.z, stack));
        }
        
        contents.clear();
    }
    
    // notifies the belt end entity that the current entity is the sender to it
    private void assignTargetState(Level level) {
        var beltTargetCandidate = level.getBlockEntity(target, BlockEntitiesContent.CHUTE_BLOCK.get());
        if (beltTargetCandidate.isPresent()) {
            beltTargetCandidate.get().lastTargetedTime = level.getGameTime();
            beltTargetCandidate.get().sourceBeltPos = worldPosition;
        } else {
            target = null;
            midPoints = new ArrayList<>();
        }
    }
    
    private boolean insertIntoTarget(BeltItem item) {
        var conveyorEndEntityCandidate = level.getBlockEntity(target, BlockEntitiesContent.CHUTE_BLOCK.get());
        if (conveyorEndEntityCandidate.isEmpty()) return false;
        var conveyorEndEntity = conveyorEndEntityCandidate.get();
        var targetInv = ItemApi.BLOCK.find(level, target.relative(conveyorEndEntity.getOwnFacing().getOpposite()), null, null, conveyorEndEntity.getOwnFacing());
        if (targetInv == null) return false;
        
        if (targetInv.insert(item.stack(), true) != item.stack().getCount()) return false;
        targetInv.insert(item.stack(), false);
        return true;
    }
    
    // One item per entry, as a Factorio loader puts one item in each belt slot (#344).
    @SuppressWarnings("DataFlowIssue")
    private @Nullable BeltItem extractOne() {
        var source = ItemApi.BLOCK.find(level, worldPosition.relative(getOwnFacing().getOpposite()), null, null, getOwnFacing());
        if (source == null) return null;

        for (int slot = 0; slot < source.getSlotCount(); slot++) {
            var availableStack = source.getStackInSlot(slot);
            if (availableStack.isEmpty() || !stackMatchesFilter(availableStack)) continue;

            var extractingStack = availableStack.copyWithCount(1);
            if (source.extract(extractingStack, false) <= 0) continue;

            return new BeltItem(nextItemId++, extractingStack);
        }
        return null;
    }
    
    private boolean stackMatchesFilter(ItemStack stack) {
        if (filteredItem.isEmpty()) return true;
        
        if (Platform.isModLoaded("ftbfiltersystem")) {
            var filterAPI = FTBFilterSystemAPI.api();
            if (filterAPI.isFilterItem(filteredItem))
                return filterAPI.doesFilterMatch(filteredItem, stack, level.registryAccess());
        }
        
        return stack.getItem().equals(filteredItem.getItem());
    }
    
    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.storeNullable("target", BlockPos.CODEC, target);
        output.store("midpoints", BlockPos.CODEC.listOf(), midPoints);
        output.store("filter", ItemStack.OPTIONAL_CODEC, filteredItem);
        output.putInt("beltTier", beltTier.number());

        var positions = output.childrenList("moving");
        for (var entry : contents.entries()) {
            var item = positions.addChild();
            item.putDouble("position", entry.position());
            item.store("stack", ItemStack.OPTIONAL_CODEC, entry.payload().stack());
            item.putShort("id", entry.payload().id());
        }
    }
    
    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);

        var loadedTarget = input.read("target", BlockPos.CODEC).orElse(null);
        var loadedMidPoints = new ArrayList<>(input.read("midpoints", BlockPos.CODEC.listOf()).orElse(List.of()));
        var pathChanged = !Objects.equals(target, loadedTarget) || !midPoints.equals(loadedMidPoints);

        target = loadedTarget;
        midPoints = loadedMidPoints;
        filteredItem = input.read("filter", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
        beltTier = BeltTier.of(input.getIntOr("beltTier", BeltTier.BELT.number()));

        contents.clear();
        if (level != null && level.isClientSide()) contentsReceivedAt = System.nanoTime();
        for (var item : input.childrenListOrEmpty("moving")) {
            var id = (short) item.getShortOr("id", (short) 0);
            var stack = item.read("stack", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
            contents.restore(new BeltItem(id, stack), item.getDoubleOr("position", 0));
        }
        
        if (level != null && (pathChanged || beltData == null)) {
            beltData = BeltData.create(this);
        }
        needsFit = true;
        
    }
    
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }
    
    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
    
    public List<BeltContents.Entry<BeltItem>> getBeltEntries() {
        return contents.entries();
    }
    
    public BlockPos getTarget() {
        return target;
    }
    
    public BeltData getBeltData() {
        return beltData;
    }
    
    public Direction getOwnFacing() {
        return getBlockState().getValue(HorizontalDirectionalBlock.FACING);
    }

    /** Blocks per second. */
    public float getBeltSpeed() {
        return (float) beltTier.blocksPerSecond();
    }

    // Rounded to whole slots: a spline's length carries float error, and a straight 64-block belt
    // must hold 512 (#344).
    public double getBeltLength() {
        return Math.round(beltData.totalLength() / BeltContents.SPACING) * BeltContents.SPACING;
    }

    public BeltTier getBeltTier() {
        return beltTier;
    }

    public boolean isUsed() {
        var usedAsTarget = level.getGameTime() - lastTargetedTime < 40;
        var usedAsSource = target != null && !target.equals(BlockPos.ZERO);
        return usedAsTarget || usedAsSource;
    }
    
    public void assignFromBeltItem(BlockPos target, List<BlockPos> midpoints, BeltTier beltTier) {
        this.target = target;
        this.midPoints = midpoints;
        this.beltTier = beltTier;
        beltData = BeltData.create(this);
        needsFit = true;
        networkDirty = true;
        this.setChanged();
        
        if (level instanceof ServerLevel serverWorld)
            serverWorld.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
    }

    @Override
    public void setRemoved() {
        BeltCollisionRegistry.unregister(this);
        super.setRemoved();
    }

    public List<Pair<BlockPos, Direction>> getMidPointsWithTangents() {
        return midPoints.stream()
                 .filter(point -> level.getBlockState(point).getBlock().equals(BlockContent.CONVEYOR_SUPPORT_BLOCK.get()))
                 .map(point -> new Pair<>(point, level.getBlockState(point).getValue(HorizontalDirectionalBlock.FACING)))
                 .toList();
    }
    
    public void assignFilterItem(ItemStack stack, Player player) {
        
        if (stack.isEmpty()) {
            resetFilterItem(player);
            return;
        }
        
        player.sendSystemMessage(Component.translatable("message.belts.filter_set"));
        filteredItem = stack.copy();
        this.setChanged();
        
        if (level instanceof ServerLevel serverWorld)
            serverWorld.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
    }
    
    public void resetFilterItem(Player player) {
        player.sendSystemMessage(Component.translatable("message.belts.filter_reset"));
        filteredItem = ItemStack.EMPTY;
        this.setChanged();
        
        if (level instanceof ServerLevel serverWorld)
            serverWorld.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
    }
    
    public record BeltItem(short id, ItemStack stack) {
    }
    
    public record BeltData(List<Pair<Vec3, Vec3>> allPoints, double totalLength, double[] segmentLengths) {
        
        public static @Nullable BeltData create(ChuteBlockEntity entity) {
            
            if (entity.getLevel() == null || entity.target == null || entity.target.equals(BlockPos.ZERO))
                return null;
            
            var targetCandidate = entity.getLevel().getBlockEntity(entity.getTarget(), BlockEntitiesContent.CHUTE_BLOCK.get());
            if (targetCandidate.isEmpty()) return null;
            
            var conveyorStartPoint = entity.getBlockPos();
            var conveyorEndPoint = entity.getTarget();
            var conveyorStartDir = Vec3.atLowerCornerOf(entity.getOwnFacing().getUnitVec3i());
            var conveyorFacing = targetCandidate.get().getOwnFacing();
            var conveyorEndDir = Vec3.atLowerCornerOf(conveyorFacing.getOpposite().getUnitVec3i());
            
            var conveyorMidPointsVisual = entity.getMidPointsWithTangents();
            var conveyorStartPointVisual = conveyorStartPoint.getCenter().add(conveyorStartDir.scale(-0.5f));
            var conveyorEndPointVisual = conveyorEndPoint.getCenter().add(conveyorEndDir.scale(0.5f));
            
            var transformedMidPoints = conveyorMidPointsVisual.stream().map(elem -> new Pair<>(elem.getFirst().getCenter(), Vec3.atLowerCornerOf(elem.getSecond().getUnitVec3i()))).toList();
            var segmentPoints = SplineUtil.getPointPairs(conveyorStartPointVisual, conveyorStartDir, conveyorEndPointVisual, conveyorEndDir, transformedMidPoints);
            
            var segmentLengths = new double[segmentPoints.size() - 1];
            var totalLength = 0d;
            for (int i = 0; i < segmentPoints.size() - 1; i++) {
                var from = segmentPoints.get(i);
                var to = segmentPoints.get(i + 1);
                var length = SplineUtil.getLineLength(from.getFirst(), from.getSecond(), to.getFirst(), to.getSecond());
                segmentLengths[i] = (length);
                totalLength += length;
            }
            
            return new BeltData(segmentPoints, totalLength, segmentLengths);
        }
        
    }
}
