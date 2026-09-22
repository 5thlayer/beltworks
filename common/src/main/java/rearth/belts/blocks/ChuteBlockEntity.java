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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import rearth.belts.BlockContent;
import rearth.belts.BeltSync;
import rearth.belts.BlockEntitiesContent;
import rearth.belts.ItemContent;
import rearth.belts.api.item.ItemApi;
import rearth.belts.collision.BeltCollisionRegistry;
import rearth.belts.model.BeltContents;
import rearth.belts.model.BeltCost;
import rearth.belts.model.BeltPath;
import rearth.belts.model.BeltTier;
import rearth.belts.model.FlowLimit;
import rearth.belts.model.LoaderEnergy;
import rearth.belts.model.Splitter;
import rearth.belts.util.SplineUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class ChuteBlockEntity extends BlockEntity implements BlockEntityTicker<ChuteBlockEntity> {

    // everything in this section is synced to the client
    private BlockPos target;
    private List<BlockPos> midPoints = new ArrayList<>();
    private BeltTier beltTier = BeltTier.BELT;
    // What placing the belt charged, which is what removing it pays back; a re-path keeps it (#346).
    private int cost;
    // Sent whole only in the update tag; each tick, only the entries gained and lost (#351).
    private final BeltContents<ItemStack> contents = new BeltContents<>();
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

    private static boolean tearingDownSplitter;

    // The client resends a hold every tick the button is down, so a hold it stopped sending lapses.
    private static final int HAND_LAPSE_TICKS = 5;
    private @Nullable HeldHand heldHand;

    // A loader either loads its own belt or unloads another's, never both, so one limit serves.
    private final FlowLimit flow;
    private final LoaderEnergy energy;
    private final boolean splitter;
    // Run by the left half only (#349).
    private final @Nullable Splitter<ItemStack> splitterModel;

    public ChuteBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntitiesContent.CHUTE_BLOCK.get(), pos, state);
        var tier = ((ChuteBlock) state.getBlock()).tier();
        splitter = state.getBlock() instanceof SplitterBlock;
        flow = new FlowLimit(tier.itemsPerTick());
        // Splitters draw no power (ADR-0076).
        energy = new LoaderEnergy(splitter ? BeltTier.BELT : tier);
        splitterModel = splitter ? new Splitter<>(tier.itemsPerTick()) : null;
    }
    
    @Override
    public void tick(Level level, BlockPos pos, BlockState state, ChuteBlockEntity blockEntity) {
        // Before the early returns: an unloading end does not run its own belt, and drains all the same.
        if (!level.isClientSide() && energy.joules() > 0) {
            energy.drain();
            setChanged();
        }

        if (splitter && !level.isClientSide() && state.getValue(SplitterBlock.SIDE) == SplitterBlock.Side.LEFT) {
            tickSplitter(level, pos, state);
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
        
        if (level.isClientSide()) {
            contents.advance(getBeltLength(), beltTier.blocksPerTick());
            return;
        }
        
        if (needsFit) {
            var spawnAt = pos.getCenter();
            for (var spilled : contents.fit(getBeltLength())) {
                level.addFreshEntity(new ItemEntity(level, spawnAt.x, spawnAt.y, spawnAt.z, spilled));
            }
            needsFit = false;
        }
        
        if (contents.tick(getBeltLength(), beltTier.blocksPerTick(), this::extractOne, this::insertIntoTarget, hand(level))) {
            setChanged();
        }
        // After the tick, so a splitter that ran later last tick is sent too.
        var changes = contents.drainChanges();
        if (!changes.isEmpty() && level instanceof ServerLevel serverLevel) BeltSync.send(serverLevel, pos, changes);
        
        // refresh target
        if (level.getGameTime() % 19 == 0)
            assignTargetState(level);
    }

    /** Brings the client's copy of this belt up to what the server's gained and lost. */
    public void applyChanges(BeltContents.Changes<ItemStack> changes) {
        contents.apply(changes);
    }
    
    /**
     * Makes the point at this fraction of the belt's curve the player's end for the belt, until
     * they release it or it lapses. Once their inventory refuses an item the hand stops taking
     * until it is released, and the belt runs on to its end past it (#350).
     */
    public void holdHand(ServerPlayer player, double progress) {
        if (beltData == null || !(progress >= 0 && progress <= 1)) return;
        var reach = player.blockInteractionRange() + 1;
        if (player.getEyePosition().distanceToSqr(SplineUtil.getPositionOnSpline(beltData, progress)) > reach * reach) return;
        var taking = heldHand == null || heldHand.player != player || heldHand.taking;
        heldHand = new HeldHand(player, handPoint(progress), level.getGameTime() + HAND_LAPSE_TICKS, taking);
    }

    /** The entry position a hand at this fraction of the curve holds. */
    public double handPoint(double progress) {
        // Less half an entry, since an entry is drawn at the middle of the belt it occupies.
        return progress * getBeltLength() - BeltContents.SPACING / 2;
    }

    public void releaseHand(Player player) {
        if (heldHand != null && heldHand.player == player) heldHand = null;
    }

    private BeltContents.@Nullable Hand<ItemStack> hand(Level level) {
        if (heldHand == null) return null;
        var held = heldHand;
        var player = held.player;
        if (level.getGameTime() > held.until || player.isRemoved() || !player.isAlive() || player.level() != level) {
            heldHand = null;
            return null;
        }
        if (!held.taking) return null;
        return new BeltContents.Hand<>(held.point, item -> {
            // Asked first: a creative inventory's add answers true when full and voids the item.
            var inventory = player.getInventory();
            if ((inventory.getSlotWithRemainingSpace(item) >= 0 || inventory.getFreeSlot() >= 0)
                  && inventory.add(item.copy())) return true;
            heldHand = new HeldHand(player, held.point, held.until, false);
            return false;
        });
    }

    private record HeldHand(ServerPlayer player, double point, long until, boolean taking) {
    }

    // One model for both halves, so the two inputs and two outputs alternate as one splitter (#349).
    private void tickSplitter(Level level, BlockPos pos, BlockState state) {
        var right = level.getBlockEntity(SplitterBlock.partner(pos, state), BlockEntitiesContent.CHUTE_BLOCK.get());
        if (right.isEmpty() || !right.get().splitter) return;
        var inLeft = incomingBelt();
        var inRight = right.get().incomingBelt();
        if (splitterModel.tick(level.getGameTime(), lane(inLeft), lane(inRight), outgoingLane(), right.get().outgoingLane())) {
            for (var changed : new ChuteBlockEntity[] {inLeft, inRight, this, right.get()}) {
                if (changed != null) changed.setChanged();
            }
        }
    }

    /** The loader or splitter half whose belt ends here, if its belt is laid out. */
    public @Nullable ChuteBlockEntity incomingBelt() {
        if (sourceBeltPos.equals(BlockPos.ZERO)) return null;
        var source = level.getBlockEntity(sourceBeltPos, BlockEntitiesContent.CHUTE_BLOCK.get());
        if (source.isEmpty() || !worldPosition.equals(source.get().target) || source.get().beltData == null) return null;
        return source.get();
    }

    private @Nullable Splitter.Lane<ItemStack> outgoingLane() {
        if (target == null || target.equals(BlockPos.ZERO) || beltData == null) return null;
        return lane(this);
    }

    private static @Nullable Splitter.Lane<ItemStack> lane(@Nullable ChuteBlockEntity belt) {
        return belt == null ? null : new Splitter.Lane<>(belt.contents, belt.getBeltLength(), belt.beltTier.blocksPerTick());
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
        for (var entry : contents.entries()) returned.add(entry.payload());
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
        removeOtherHalf(pos, state);
    }

    // Without drops: the half removed first pays the splitter's one item through its loot table (#349).
    private void removeOtherHalf(BlockPos pos, BlockState state) {
        if (!splitter || level == null || level.isClientSide() || tearingDownSplitter) return;
        var partner = SplitterBlock.partner(pos, state);
        if (!SplitterBlock.isPartner(state, level.getBlockState(partner))) return;
        tearingDownSplitter = true;
        try {
            level.setBlock(partner, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        } finally {
            tearingDownSplitter = false;
        }
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
    
    private boolean insertIntoTarget(ItemStack item) {
        var conveyorEndEntityCandidate = level.getBlockEntity(target, BlockEntitiesContent.CHUTE_BLOCK.get());
        if (conveyorEndEntityCandidate.isEmpty()) return false;
        var conveyorEndEntity = conveyorEndEntityCandidate.get();
        // The splitter pulls from this belt's end, so its two inputs alternate (#349).
        if (conveyorEndEntity.splitter) return false;
        if (!conveyorEndEntity.flow.ready(level.getGameTime()) || !conveyorEndEntity.energy.canMove()) return false;
        var targetInv = ItemApi.BLOCK.find(level, target.relative(conveyorEndEntity.getOwnFacing().getOpposite()), null, null, conveyorEndEntity.getOwnFacing());
        if (targetInv == null) return false;
        
        if (targetInv.insert(item, true) != item.getCount()) return false;
        targetInv.insert(item, false);
        conveyorEndEntity.flow.pass();
        conveyorEndEntity.energy.move();
        conveyorEndEntity.setChanged();
        return true;
    }
    
    // One item per entry, as a Factorio loader puts one item in each belt slot (#344).
    @SuppressWarnings("DataFlowIssue")
    private @Nullable ItemStack extractOne() {
        if (splitter) return null;
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
            return extractingStack;
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
            item.store("stack", ItemStack.OPTIONAL_CODEC, entry.payload());
            item.putInt("id", entry.id());
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
        for (var item : input.childrenListOrEmpty("moving")) {
            var stack = item.read("stack", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
            contents.restore(stack, item.getDoubleOr("position", 0), item.getIntOr("id", 0));
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
    
    public List<BeltContents.Entry<ItemStack>> getBeltEntries() {
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

    public boolean isSplitter() {
        return splitter;
    }

    public BeltTier getBeltTier() {
        return beltTier;
    }

    /** Whether a belt may start here: a loader is one belt's end, a splitter half one belt in and one out. */
    public boolean canStartBelt() {
        return splitter ? target == null || target.equals(BlockPos.ZERO) : !isUsed();
    }

    public boolean canEndBelt() {
        return splitter ? sourceBeltPos.equals(BlockPos.ZERO) : !isUsed();
    }

    /**
     * Where a belt starting here begins, as the position of a loader that would start it. A
     * splitter's belt leaves from its front face, so from where a loader in front of it would stand.
     */
    public BlockPos beltStartPos() {
        return splitter ? worldPosition.relative(getOwnFacing()) : worldPosition;
    }

    /** Where a belt ending here ends, as the position of a loader that would end it: a splitter's back face. */
    public BlockPos beltEndPos() {
        return splitter ? worldPosition.relative(getOwnFacing().getOpposite()) : worldPosition;
    }

    /** The facing a loader ending the belt at {@link #beltEndPos} would have: back towards the belt. */
    public Direction beltEndFacing() {
        return splitter ? getOwnFacing().getOpposite() : getOwnFacing();
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
    
    /**
     * Lays out this end's belt, or changes nothing and returns the bound its shape breaks
     * (PlanetaryFactory ADR-0078).
     */
    public Optional<BeltPath.Refusal> assignFromBeltItem(BlockPos target, List<BlockPos> midpoints, BeltTier beltTier, int cost) {
        var end = level == null ? Optional.<ChuteBlockEntity>empty() : level.getBlockEntity(target, BlockEntitiesContent.CHUTE_BLOCK.get());
        if (end.isPresent()) {
            var refusal = BeltData.path(beltStartPos(), getOwnFacing(), supportsOf(midpoints),
              end.get().beltEndPos(), end.get().beltEndFacing()).refusal();
            if (refusal.isPresent()) return refusal;
        }

        this.target = target;
        this.midPoints = midpoints;
        this.beltTier = beltTier;
        this.cost = cost;
        beltData = BeltData.create(this);
        // At once rather than on the next refresh, so breaking the end straight away finds the belt.
        if (level != null) assignTargetState(level);
        needsFit = true;
        this.setChanged();
        
        if (level instanceof ServerLevel serverWorld)
            serverWorld.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        return Optional.empty();
    }

    @Override
    public void setRemoved() {
        BeltCollisionRegistry.unregister(this);
        super.setRemoved();
    }

    public List<Pair<BlockPos, Direction>> getMidPointsWithTangents() {
        return supportsOf(midPoints);
    }

    private List<Pair<BlockPos, Direction>> supportsOf(List<BlockPos> points) {
        return points.stream()
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
    
    public record BeltData(List<Pair<Vec3, Vec3>> allPoints, double totalLength, double[] segmentLengths) {
        
        public static @Nullable BeltData create(ChuteBlockEntity entity) {
            
            if (entity.getLevel() == null || entity.target == null || entity.target.equals(BlockPos.ZERO))
                return null;
            
            var targetCandidate = entity.getLevel().getBlockEntity(entity.getTarget(), BlockEntitiesContent.CHUTE_BLOCK.get());
            if (targetCandidate.isEmpty()) return null;
            
            var end = targetCandidate.get();
            return of(entity.beltStartPos(), entity.getOwnFacing(), entity.getMidPointsWithTangents(),
              end.beltEndPos(), end.beltEndFacing());
        }
        
        /** The path between two loaders' positions and facings, whether or not they are placed yet. */
        public static BeltData of(BlockPos conveyorStartPoint, Direction startFacing, List<Pair<BlockPos, Direction>> conveyorMidPointsVisual, BlockPos conveyorEndPoint, Direction endFacing) {
            return of(path(conveyorStartPoint, startFacing, conveyorMidPointsVisual, conveyorEndPoint, endFacing));
        }

        public static BeltData of(BeltPath path) {
            var points = path.nodes().stream()
              .map(node -> new Pair<>(new Vec3(node.x(), node.y(), node.z()), new Vec3(node.tangentX(), 0, node.tangentZ())))
              .toList();
            return new BeltData(points, path.length(), path.spanLengths());
        }

        /** As {@link #of}, but with no end the path stops at its last support, as a belt still being laid does. */
        public static BeltPath path(BlockPos start, Direction startFacing, List<Pair<BlockPos, Direction>> supports, @Nullable BlockPos end, @Nullable Direction endFacing) {
            return BeltPath.of(anchor(start, startFacing),
              supports.stream().map(support -> anchor(support.getFirst(), support.getSecond())).toList(),
              end == null || endFacing == null ? null : anchor(end, endFacing));
        }

        private static BeltPath.Anchor anchor(BlockPos pos, Direction facing) {
            return new BeltPath.Anchor(pos.getX(), pos.getY(), pos.getZ(), facing.getStepX(), facing.getStepZ());
        }
        
    }
}
