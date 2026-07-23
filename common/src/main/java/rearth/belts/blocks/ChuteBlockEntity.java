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
import rearth.belts.util.SplineUtil;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class ChuteBlockEntity extends BlockEntity implements BlockEntityTicker<ChuteBlockEntity> {

    // config
    private static final float BASE_BELT_SPEED = 0.9f;
    private static final int EXTRACTION_INTERVAL = 26;
    private static final float ITEM_QUEUE_SPACING = 0.8f;
    public static final int BELT_SPEED_MULTIPLIER = 1;
    
    // everything in this section is synced to the client
    private BlockPos target;
    private List<BlockPos> midPoints = new ArrayList<>();
    // items that are in transit, and not in the queue yet. Key is the progress [0-1] along the current path;
    // first = at begin of transit path, near extraction point. Last = near target.
    private final Deque<BeltItem> movingItems = new ArrayDeque<>();
    
    // number of items actively waiting / blocked by the output side of the belt.
    private int outputQueue = 0;
    
    // this is calculated on both the client and server
    private BeltData beltData;
    
    // used to check if a belt is used as target. Periodically updated on belt ends from the belt starts.
    private long lastTargetedTime;
    private BlockPos sourceBeltPos = BlockPos.ZERO;
    
    // used for filtering. Optionally works with create and ftb filters.
    public ItemStack filteredItem = ItemStack.EMPTY;
    
    // client only data, used for rendering
    public final Map<Short, Vec3> lastRenderedPositions = new HashMap<>();
    public final Map<Long, Integer> cachedLightCoords = new HashMap<>();
    
    private boolean networkDirty = false;
    
    public ChuteBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntitiesContent.CHUTE_BLOCK.get(), pos, state);
    }
    
    @Override
    public void tick(Level world, BlockPos pos, BlockState state, ChuteBlockEntity blockEntity) {
        if (target == null || target.equals(BlockPos.ZERO)) {
            if (!world.isClientSide() && !movingItems.isEmpty()) {
                dropContent(world, pos);
            }
            return;
        }
        
        if (beltData == null) {
            beltData = BeltData.create(this);
            if (world instanceof ServerLevel serverWorld)
                serverWorld.sendBlockUpdated(pos, state, state, Block.UPDATE_ALL);
        }
        
        if (beltData == null) {
            target = null;
            midPoints = new ArrayList<>();
            return;
        }
        
        if (world.isClientSide()) return;
        
        moveItemsOnBelt(world);
        loadItemsOnBelt(world);
        
        // refresh target
        if (world.getGameTime() % 19 == 0)
            assignTargetState(world);
        
        
        if (networkDirty && world instanceof ServerLevel serverWorld) {
            serverWorld.sendBlockUpdated(pos, state, state, Block.UPDATE_ALL);
            networkDirty = false;
        }
        
    }
    
    public void dropContent(Level world, BlockPos pos) {
        
        // notify source to be reset
        if (world.getGameTime() - this.lastTargetedTime < 20 && !this.sourceBeltPos.equals(BlockPos.ZERO) && world instanceof ServerLevel serverWorld) {
            var sourceEntityCandidate = world.getBlockEntity(this.sourceBeltPos, BlockEntitiesContent.CHUTE_BLOCK.get());
            if (sourceEntityCandidate.isPresent() && sourceEntityCandidate.get() != this) {
                var source = sourceEntityCandidate.get();
                source.dropContent(world, pos);
                source.target = null;
                serverWorld.sendBlockUpdated(this.sourceBeltPos, source.getBlockState(), source.getBlockState(), Block.UPDATE_ALL);
                source.networkDirty = true;
                source.setChanged();
            }
        }
        
        var spawnAt = pos.getCenter();
        for (var beltItem : movingItems) {
            world.addFreshEntity(new ItemEntity(world, spawnAt.x, spawnAt.y, spawnAt.z, beltItem.stack));
        }
        
        if (!movingItems.isEmpty() || (target != null && !target.equals(BlockPos.ZERO))) {
            // pretend to drop an actual belt
            var stack = new ItemStack(ItemContent.BELT.get(), 1);
            world.addFreshEntity(new ItemEntity(world, spawnAt.x, spawnAt.y, spawnAt.z, stack));
        }
        
        movingItems.clear();
    }
    
    // notifies the belt end entity that the current entity is the sender to it
    private void assignTargetState(Level world) {
        var beltTargetCandidate = world.getBlockEntity(target, BlockEntitiesContent.CHUTE_BLOCK.get());
        if (beltTargetCandidate.isPresent()) {
            beltTargetCandidate.get().lastTargetedTime = world.getGameTime();
            beltTargetCandidate.get().sourceBeltPos = worldPosition;
        } else {
            target = null;
            midPoints = new ArrayList<>();
        }
    }
    
    private void moveItemsOnBelt(Level world) {
        
        var beltLength = beltData.totalLength();
        var progressDelta = getBeltSpeed() / beltLength / 20f;
        
        boolean unloaded = false;
        outputQueue = 0;
        
        for (var pair : movingItems.reversed()) {
            var itemProgress = pair.progress;
            var newProgress = itemProgress + progressDelta;
            
            var inQueue = newProgress >= getPotentialQueueStart();
            
            // only accept new position if not in queue
            if (inQueue) {
                outputQueue++;
            } else {
                pair.progress = (float) newProgress;
                networkDirty = true;
            }
            
            // try to insert last item (if its in queue). Gets put into queue when the end is reached.
            if (inQueue && outputQueue == 1) {
                var conveyorEndEntityCandidate = world.getBlockEntity(target, BlockEntitiesContent.CHUTE_BLOCK.get());
                if (conveyorEndEntityCandidate.isEmpty()) continue;
                var conveyorEndEntity = conveyorEndEntityCandidate.get();
                var targetInv = ItemApi.BLOCK.find(world, target.relative(conveyorEndEntity.getOwnFacing().getOpposite()), null, null, conveyorEndEntity.getOwnFacing());
                if (targetInv == null) continue;
                
                var insertionStack = pair.stack;
                var insertedAmount = targetInv.insert(insertionStack, true);
                if (insertedAmount == insertionStack.getCount()) {
                    targetInv.insert(insertionStack, false);
                    outputQueue = 0;    // reset queue
                    unloaded = true;
                    networkDirty = true;
                }
            }
        }
        
        if (unloaded)
            movingItems.removeLast();
        
    }
    
    private void loadItemsOnBelt(Level world) {
        var extractionOffset = worldPosition.asLong();
        
        if ((world.getGameTime() + extractionOffset) % EXTRACTION_INTERVAL != 0) return;
        if (getPotentialQueueStart() < 0) return;
        
        var source = ItemApi.BLOCK.find(world, worldPosition.relative(getOwnFacing().getOpposite()), null, null, getOwnFacing());
        if (source == null) return;

        for (int slot = 0; slot < source.getSlotCount(); slot++) {
            var availableStack = source.getStackInSlot(slot);
            if (availableStack.isEmpty() || !stackMatchesFilter(availableStack, world)) continue;

            var extractingStack = availableStack.copyWithCount(Math.min(availableStack.getCount(), 64));
            var extractedAmount = source.extract(extractingStack, false);
            if (extractedAmount <= 0) continue;

            var id = (short) world.getRandom().nextIntBetweenInclusive(Short.MIN_VALUE, Short.MAX_VALUE);
            movingItems.addFirst(new BeltItem(id, extractingStack.copyWithCount(extractedAmount)));
            setChanged();
            networkDirty = true;
            return;
        }
    }
    
    private boolean stackMatchesFilter(ItemStack stack, Level world) {
        if (filteredItem.isEmpty()) return true;
        
        if (Platform.isModLoaded("ftbfiltersystem")) {
            var filterAPI = FTBFilterSystemAPI.api();
            if (filterAPI.isFilterItem(filteredItem))
                return filterAPI.doesFilterMatch(filteredItem, stack, world.registryAccess());
        }
        
        return stack.getItem().equals(filteredItem.getItem());
    }
    
    private float getPotentialQueueStart() {
        return (float) (1 - outputQueue * ITEM_QUEUE_SPACING / beltData.totalLength());
    }
    
    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.storeNullable("target", BlockPos.CODEC, target);
        output.store("midpoints", BlockPos.CODEC.listOf(), midPoints);
        output.store("filter", ItemStack.OPTIONAL_CODEC, filteredItem);

        var positions = output.childrenList("moving");
        for (var pair : movingItems) {
            var item = positions.addChild();
            item.putFloat("progress", pair.progress);
            item.store("stack", ItemStack.OPTIONAL_CODEC, pair.stack);
            item.putShort("id", pair.id);
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

        movingItems.clear();
        movingItems.addAll(input.childrenListOrEmpty("moving").stream().map(item -> {
            var progress = item.getFloatOr("progress", 0);
            var id = (short) item.getShortOr("id", (short) 0);
            var stack = item.read("stack", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
            return new BeltItem(progress, id, stack);
        }).toList());
        
        if (level != null && (pathChanged || beltData == null)) {
            beltData = BeltData.create(this);
        }
        
    }
    
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }
    
    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
    
    public Iterable<BeltItem> getMovingItems() {
        return movingItems;
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

    public int getBeltSpeedMultiplier() {
        return BELT_SPEED_MULTIPLIER;
    }

    public float getBeltSpeed() {
        return BASE_BELT_SPEED * getBeltSpeedMultiplier();
    }
    
    public boolean isUsed() {
        var usedAsTarget = level.getGameTime() - lastTargetedTime < 40;
        var usedAsSource = target != null && !target.equals(BlockPos.ZERO);
        return usedAsTarget || usedAsSource;
    }
    
    public void assignFromBeltItem(BlockPos target, List<BlockPos> midpoints) {
        this.target = target;
        this.midPoints = midpoints;
        beltData = BeltData.create(this);
        networkDirty = true;
        this.setChanged();
        
        if (level instanceof ServerLevel serverWorld)
            serverWorld.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
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
    
    public static class BeltItem {
        public float progress;
        public final short id;
        public final ItemStack stack;
        
        public BeltItem(short id, ItemStack stack) {
            this.id = id;
            this.stack = stack;
        }
        
        public BeltItem(float progress, short id, ItemStack stack) {
            this.id = id;
            this.stack = stack;
            this.progress = progress;
        }
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
