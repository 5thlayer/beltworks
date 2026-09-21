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
import rearth.belts.model.BeltCost;
import rearth.belts.model.BeltTier;
import rearth.belts.model.FlowLimit;
import rearth.belts.model.LoaderEnergy;
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
    // What placing the belt charged, which is what removing it pays back; a re-path keeps it (#346).
    private int cost;
    private final BeltContents<BeltItem> contents = new BeltContents<>();
    // Sequential rather than random: the renderer keys each item's lerp by id, and a full belt
    // holds 512 (#344).
    private short nextItemId;
    private boolean needsFit = true;
    
    // this is calculated on both the client and server
    private BeltData beltData;
    
    // The loader whose belt ends here. Synced, so the client agrees this end is in use and
    // consumes a filter click rather than going on to the off hand.
    private BlockPos sourceBeltPos = BlockPos.ZERO;
    
    // used for filtering. Optionally works with create and ftb filters.
    public ItemStack filteredItem = ItemStack.EMPTY;
    
    // client only data, used for rendering
    public final Map<Long, Integer> cachedLightCoords = new HashMap<>();
    // When the belt's contents last arrived, in System.nanoTime, for the renderer to extrapolate
    // from. Not the game time: the server resyncs the client's every second, and each jump shows.
    public long contentsReceivedAt;
    
    private boolean networkDirty = false;

    // A loader either loads its own belt or unloads another's, never both, so one limit serves.
    private final FlowLimit flow;
    private final LoaderEnergy energy;

    public ChuteBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntitiesContent.CHUTE_BLOCK.get(), pos, state);
        var tier = ((ChuteBlock) state.getBlock()).tier();
        flow = new FlowLimit(tier.itemsPerTick());
        energy = new LoaderEnergy(tier);
    }
    
    @Override
    public void tick(Level level, BlockPos pos, BlockState state, ChuteBlockEntity blockEntity) {
        // Before the early returns: an unloading end does not run its own belt, and drains all the same.
        if (!level.isClientSide() && energy.joules() > 0) {
            energy.drain();
            setChanged();
        }

        if (target == null || target.equals(BlockPos.ZERO)) {
            BeltCollisionRegistry.unregister(this);
            if (!level.isClientSide() && (!contents.isEmpty() || cost > 0)) {
                releaseBelt(null, pos);
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
    
    /**
     * Removes the belt this loader starts or ends, handing its items and its cost to the player
     * who broke it, or dropping them here when nobody did. The loader at the other end stays.
     */
    public void releaseBelts(@Nullable Player player) {
        if (level == null || level.isClientSide()) return;
        if (target != null && !target.equals(BlockPos.ZERO)) releaseBelt(player, worldPosition);
        var source = level.getBlockEntity(sourceBeltPos, BlockEntitiesContent.CHUTE_BLOCK.get());
        if (source.isPresent() && source.get() != this && worldPosition.equals(source.get().target)) {
            source.get().releaseBelt(player, worldPosition);
        }
        sourceBeltPos = BlockPos.ZERO;
    }

    private void releaseBelt(@Nullable Player player, BlockPos dropAt) {
        BeltCollisionRegistry.unregister(this);

        var returned = new ArrayList<ItemStack>();
        for (var entry : contents.entries()) returned.add(entry.payload().stack());
        var belt = ItemContent.beltFor(beltTier);
        for (var left = cost; left > 0; ) {
            var stack = new ItemStack(belt, Math.min(left, belt.getDefaultMaxStackSize()));
            left -= stack.getCount();
            returned.add(stack);
        }

        if (target != null) {
            level.getBlockEntity(target, BlockEntitiesContent.CHUTE_BLOCK.get())
              .ifPresent(end -> end.linkSource(BlockPos.ZERO));
        }
        contents.clear();
        target = null;
        midPoints = new ArrayList<>();
        beltData = null;
        cost = 0;
        setChanged();
        if (level instanceof ServerLevel serverWorld)
            serverWorld.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);

        var spawnAt = dropAt.getCenter();
        for (var stack : returned) {
            if (player != null) {
                player.getInventory().placeItemBackInInventory(stack);
            } else {
                level.addFreshEntity(new ItemEntity(level, spawnAt.x, spawnAt.y, spawnAt.z, stack));
            }
        }
    }

    // Catches every removal a player's break did not, so an explosion or a command loses nothing.
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        releaseBelts(null);
    }
    
    // notifies the belt end entity that the current entity is the sender to it
    private void assignTargetState(Level level) {
        var beltTargetCandidate = level.getBlockEntity(target, BlockEntitiesContent.CHUTE_BLOCK.get());
        if (beltTargetCandidate.isPresent()) {
            beltTargetCandidate.get().linkSource(worldPosition);
        } else {
            target = null;
            midPoints = new ArrayList<>();
        }
    }
    
    private boolean insertIntoTarget(BeltItem item) {
        var conveyorEndEntityCandidate = level.getBlockEntity(target, BlockEntitiesContent.CHUTE_BLOCK.get());
        if (conveyorEndEntityCandidate.isEmpty()) return false;
        var conveyorEndEntity = conveyorEndEntityCandidate.get();
        if (!conveyorEndEntity.flow.ready(level.getGameTime()) || !conveyorEndEntity.energy.canMove()) return false;
        var targetInv = ItemApi.BLOCK.find(level, target.relative(conveyorEndEntity.getOwnFacing().getOpposite()), null, null, conveyorEndEntity.getOwnFacing());
        if (targetInv == null) return false;
        
        if (targetInv.insert(item.stack(), true) != item.stack().getCount()) return false;
        targetInv.insert(item.stack(), false);
        conveyorEndEntity.flow.pass();
        conveyorEndEntity.energy.move();
        conveyorEndEntity.setChanged();
        return true;
    }
    
    // One item per entry, as a Factorio loader puts one item in each belt slot (#344).
    @SuppressWarnings("DataFlowIssue")
    private @Nullable BeltItem extractOne() {
        if (!flow.ready(level.getGameTime()) || !energy.canMove()) return null;
        var source = ItemApi.BLOCK.find(level, worldPosition.relative(getOwnFacing().getOpposite()), null, null, getOwnFacing());
        if (source == null) return null;

        for (int slot = 0; slot < source.getSlotCount(); slot++) {
            var availableStack = source.getStackInSlot(slot);
            if (availableStack.isEmpty() || !stackMatchesFilter(availableStack)) continue;

            var extractingStack = availableStack.copyWithCount(1);
            if (source.extract(extractingStack, false) <= 0) continue;

            flow.pass();
            energy.move();
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
        output.putInt("cost", cost);
        output.putLong("energy", energy.joules());
        // Saved so breaking the end straight after a load still finds the belt it ends.
        output.store("source", BlockPos.CODEC, sourceBeltPos);

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
        cost = input.getIntOr("cost", 0);
        energy.setJoules(input.getLongOr("energy", 0));
        sourceBeltPos = input.read("source", BlockPos.CODEC).orElse(BlockPos.ZERO);

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

    public double getBeltLength() {
        return BeltCost.slots(beltData.totalLength()) * BeltContents.SPACING;
    }

    /** The belt items removing this belt pays back. */
    public int getCost() {
        return cost;
    }

    /** The FE buffer a tier-2 to tier-4 loader pays each item from; tier 1's is never charged. */
    public LoaderEnergy getEnergy() {
        return energy;
    }

    public BeltTier getBeltTier() {
        return beltTier;
    }

    public boolean isUsed() {
        var usedAsTarget = !sourceBeltPos.equals(BlockPos.ZERO);
        var usedAsSource = target != null && !target.equals(BlockPos.ZERO);
        return usedAsTarget || usedAsSource;
    }

    private void linkSource(BlockPos source) {
        if (sourceBeltPos.equals(source)) return;
        sourceBeltPos = source;
        setChanged();
        if (level instanceof ServerLevel serverWorld)
            serverWorld.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
    }
    
    public void assignFromBeltItem(BlockPos target, List<BlockPos> midpoints, BeltTier beltTier, int cost) {
        this.target = target;
        this.midPoints = midpoints;
        this.beltTier = beltTier;
        this.cost = cost;
        beltData = BeltData.create(this);
        // At once rather than on the next refresh, so breaking the end straight away finds the belt.
        if (level != null) assignTargetState(level);
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
            
            return of(entity.getBlockPos(), entity.getOwnFacing(), entity.getMidPointsWithTangents(),
              entity.getTarget(), targetCandidate.get().getOwnFacing());
        }
        
        /** The path between two loaders' positions and facings, whether or not they are placed yet. */
        public static BeltData of(BlockPos conveyorStartPoint, Direction startFacing, List<Pair<BlockPos, Direction>> conveyorMidPointsVisual, BlockPos conveyorEndPoint, Direction endFacing) {
            var conveyorStartDir = Vec3.atLowerCornerOf(startFacing.getUnitVec3i());
            var conveyorEndDir = Vec3.atLowerCornerOf(endFacing.getOpposite().getUnitVec3i());
            
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
