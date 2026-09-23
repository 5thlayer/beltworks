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
import rearth.belts.model.BeltCut;
import rearth.belts.model.BeltPath;
import rearth.belts.model.BeltTier;
import rearth.belts.model.FlowLimit;
import rearth.belts.model.Join;
import rearth.belts.model.LoaderEnergy;
import rearth.belts.model.Splitter;
import rearth.belts.model.TransportLine;
import rearth.belts.model.SupportSlots;
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

    // The loader or support whose belt passes through this support (#366).
    private BlockPos passingBeltPos = BlockPos.ZERO;
    
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
    private final boolean support;
    // Run by the left half only, for both halves (#349).
    private final @Nullable Splitter<ItemStack> splitterModel;
    private final Splitter.@Nullable Half<ItemStack> half;
    private final double halfSpeed;
    // The game time this block's belt, and a splitter's halves, last moved.
    private long movedAt = Long.MIN_VALUE;
    private long halfMovedAt = Long.MIN_VALUE;
    private @Nullable BeltData halfData;

    public ChuteBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntitiesContent.CHUTE_BLOCK.get(), pos, state);
        support = state.getBlock() instanceof ConveyorSupportBlock;
        var tier = support ? BeltTier.BELT : ((ChuteBlock) state.getBlock()).tier();
        splitter = state.getBlock() instanceof SplitterBlock;
        flow = new FlowLimit(tier.itemsPerTick());
        // Splitters draw no power (ADR-0076).
        energy = new LoaderEnergy(splitter ? BeltTier.BELT : tier);
        splitterModel = splitter ? new Splitter<>(tier) : null;
        half = splitter ? new Splitter.Half<>() : null;
        halfSpeed = tier.blocksPerTick();
    }
    
    @Override
    public void tick(Level level, BlockPos pos, BlockState state, ChuteBlockEntity blockEntity) {
        // Before the early returns: an unloading end does not run its own belt, and drains all the same.
        if (!level.isClientSide() && energy.joules() > 0) {
            energy.drain();
            setChanged();
        }

        if (splitter) tickHalf(level, pos, state);

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
        
        if (contents.tick(getBeltLength(), beltTier.blocksPerTick(), this::extractOne, this::insertIntoTarget, hand(level, false))) {
            setChanged();
        }
        movedAt = level.getGameTime();
        // After the tick, so a splitter that ran later last tick is sent too.
        send(Track.BELT, contents);
        
        // refresh target
        if (level.getGameTime() % 19 == 0)
            assignTargetState(level);
    }

    private void send(Track track, BeltContents<ItemStack> belt) {
        var changes = belt.drainChanges();
        if (!changes.isEmpty() && level instanceof ServerLevel serverLevel) BeltSync.send(serverLevel, worldPosition, track, changes);
    }

    /** Brings the client's copy of one of this block's belts up to what the server's gained and lost. */
    public void applyChanges(Track track, BeltContents.Changes<ItemStack> changes) {
        var belt = belt(track);
        if (belt != null) belt.apply(changes);
    }

    private @Nullable BeltContents<ItemStack> belt(Track track) {
        return switch (track) {
            case BELT -> contents;
            case HALF_ENTERING -> half == null ? null : half.entering();
            case HALF_LEAVING -> half == null ? null : half.leaving();
        };
    }

    /** The belts a block carries: the one starting at it, and a splitter half's own two segments (#373). */
    public enum Track {
        BELT, HALF_ENTERING, HALF_LEAVING
    }
    
    /**
     * Makes the point at this fraction of the belt's curve the player's end for the belt, until
     * they release it or it lapses. Once their inventory refuses an item the hand stops taking
     * until it is released, and the belt runs on to its end past it (#350). On a splitter's half
     * the belt is the half's own block (#373).
     */
    public void holdHand(ServerPlayer player, boolean onHalf, double progress) {
        var data = onHalf ? getHalfData() : beltData;
        if (data == null || !(progress >= 0 && progress <= 1)) return;
        var reach = player.blockInteractionRange() + 1;
        if (player.getEyePosition().distanceToSqr(SplineUtil.getPositionOnSpline(data, progress)) > reach * reach) return;
        var taking = heldHand == null || heldHand.player != player || heldHand.onHalf != onHalf || heldHand.taking;
        var point = onHalf ? progress - BeltContents.SPACING / 2 : handPoint(progress);
        heldHand = new HeldHand(player, onHalf, point, level.getGameTime() + HAND_LAPSE_TICKS, taking);
    }

    public void holdHand(ServerPlayer player, double progress) {
        holdHand(player, false, progress);
    }

    /** The entry position a hand at this fraction of the curve holds. */
    public double handPoint(double progress) {
        // Less half an entry, since an entry is drawn at the middle of the belt it occupies.
        return progress * getBeltLength() - BeltContents.SPACING / 2;
    }

    public void releaseHand(Player player) {
        if (heldHand != null && heldHand.player == player) heldHand = null;
    }

    private BeltContents.@Nullable Hand<ItemStack> hand(Level level, boolean onHalf) {
        if (heldHand == null) return null;
        var held = heldHand;
        var player = held.player;
        if (level.getGameTime() > held.until || player.isRemoved() || !player.isAlive() || player.level() != level) {
            heldHand = null;
            return null;
        }
        if (!held.taking || held.onHalf != onHalf) return null;
        var point = !onHalf && endInventoryRefuses() ? getBeltLength() : held.point;
        return new BeltContents.Hand<>(point, item -> {
            // Asked first: a creative inventory's add answers true when full and voids the item.
            var inventory = player.getInventory();
            if ((inventory.getSlotWithRemainingSpace(item) >= 0 || inventory.getFreeSlot() >= 0)
                  && inventory.add(item.copy())) return true;
            heldHand = new HeldHand(player, held.onHalf, held.point, held.until, false);
            return false;
        });
    }

    /**
     * Whether the inventory at the belt's far loader refuses the entry nearest it. The entry that
     * stops there sits inside the loader's back plate, where no aim reaches it, so a hand on a belt
     * backed up this way holds its end (PlanetaryFactory #360).
     */
    private boolean endInventoryRefuses() {
        var entries = contents.entries();
        if (entries.isEmpty()) return false;
        var end = level.getBlockEntity(target, BlockEntitiesContent.CHUTE_BLOCK.get()).orElse(null);
        if (end == null || end.splitter || end.support) return false;
        var inventory = ItemApi.BLOCK.find(level, target.relative(end.getOwnFacing().getOpposite()), null, null, end.getOwnFacing());
        var item = entries.getLast().payload();
        return inventory == null || inventory.insert(item, true) != item.getCount();
    }

    private record HeldHand(ServerPlayer player, boolean onHalf, double point, long until, boolean taking) {
    }

    // Before a splitter's early returns: a half carries items with no belt leaving it.
    private void tickHalf(Level level, BlockPos pos, BlockState state) {
        BeltCollisionRegistry.registerHalf(this);
        if (level.isClientSide()) {
            half.entering().advance(Splitter.MIDLINE, halfSpeed);
            half.leaving().advance(Splitter.MIDLINE, halfSpeed);
            return;
        }
        if (state.getValue(SplitterBlock.SIDE) == SplitterBlock.Side.LEFT) tickSplitter(level, pos, state);
        send(Track.HALF_ENTERING, half.entering());
        send(Track.HALF_LEAVING, half.leaving());
    }

    // One model for both halves, so the two inputs and two outputs alternate as one splitter (#349).
    private void tickSplitter(Level level, BlockPos pos, BlockState state) {
        var right = level.getBlockEntity(SplitterBlock.partner(pos, state), BlockEntitiesContent.CHUTE_BLOCK.get());
        if (right.isEmpty() || !right.get().splitter) return;
        if (splitterModel.tick(level.getGameTime(), side(level), right.get().side(level))) {
            setChanged();
            right.get().setChanged();
            for (var half : List.of(this, right.get())) half.tileAhead().ifPresent(BeltTileBlockEntity::lineChanged);
        }
        halfMovedAt = right.get().halfMovedAt = level.getGameTime();
    }

    private Splitter.Side<ItemStack> side(Level level) {
        return new Splitter.Side<>(half, outgoingLane(), hand(level, true));
    }

    /** The loader, splitter half or support whose belt ends here, if its belt is laid out. */
    public @Nullable ChuteBlockEntity incomingBelt() {
        if (sourceBeltPos.equals(BlockPos.ZERO)) return null;
        var source = level.getBlockEntity(sourceBeltPos, BlockEntitiesContent.CHUTE_BLOCK.get());
        if (source.isEmpty() || !worldPosition.equals(source.get().target) || source.get().beltData == null) return null;
        return source.get();
    }

    // Tick order between blocks is the level's, so each join asks whether its receiver has moved (#373).
    private @Nullable Splitter.Lane<ItemStack> outgoingLane() {
        if (target == null || target.equals(BlockPos.ZERO) || beltData == null) {
            if (!splitter) return null;
            return tileAhead().map(tile -> tile.entryLane(getOwnFacing())).orElse(null);
        }
        var speed = beltTier.blocksPerTick();
        return new Splitter.Lane<>(contents, getBeltLength(), speed, hand(level, false), movedAt == level.getGameTime() ? 0 : speed);
    }

    // A half with no belt leaving it feeds the tile line in front of it, where that line starts (#394).
    private Optional<BeltTileBlockEntity> tileAhead() {
        var ahead = worldPosition.relative(getOwnFacing());
        if (!level.isLoaded(ahead)) return Optional.empty();
        return level.getBlockEntity(ahead, BlockEntitiesContent.BELT_TILE.get());
    }

    /**
     * Takes the items a tile replaced by this half carried, each where it sat in the tile, and
     * hands back to the placer what no longer fits (#394).
     */
    public void carry(List<TransportLine.Share<ItemStack>> shares, @Nullable Player player) {
        if (!splitter) return;
        for (var share : shares) {
            if (share.offset() < Splitter.MIDLINE) half.entering().restore(share.payload(), share.offset());
            else half.leaving().restore(share.payload(), share.offset() - Splitter.MIDLINE);
        }
        var spilled = new ArrayList<>(half.entering().fit(Splitter.MIDLINE));
        spilled.addAll(half.leaving().fit(Splitter.MIDLINE));
        setChanged();
        giveBack(player, spilled, worldPosition);
    }

    /**
     * Takes the item at the end of a tile line whose last tile faces into this half's back, at the
     * line's {@link BeltContents#overshoot} (#394).
     */
    public boolean offerFromLine(ItemStack item, double overshoot) {
        if (!splitter || !Join.offer(item, overshoot, enteringLane())) return false;
        setChanged();
        return true;
    }

    private Splitter.Lane<ItemStack> enteringLane() {
        return Splitter.entering(half, halfSpeed, hand(level, true), halfMovedAt == level.getGameTime() ? 0 : halfSpeed);
    }

    /**
     * Removes every belt this end starts, ends or passes, handing its items and its cost to the
     * player who broke it, or dropping them here when nobody did. The belt's other end stays.
     */
    public void releaseBelts(@Nullable Player player) {
        if (level == null || level.isClientSide()) return;
        if (half != null && half.size() > 0) {
            var carried = half.payloads();
            half.clear();
            setChanged();
            giveBack(player, carried, worldPosition);
        }
        if (target != null && !target.equals(BlockPos.ZERO)) releaseBelt(player, worldPosition);
        var source = level.getBlockEntity(sourceBeltPos, BlockEntitiesContent.CHUTE_BLOCK.get());
        if (source.isPresent() && source.get() != this && worldPosition.equals(source.get().target)) {
            source.get().releaseBelt(player, worldPosition);
        }
        sourceBeltPos = BlockPos.ZERO;
        var passing = passingBelt();
        if (passing != null) passing.releaseBelt(player, worldPosition);
        passingBeltPos = BlockPos.ZERO;
    }

    /** The loader or support whose belt passes through this support, if it still does. */
    private @Nullable ChuteBlockEntity passingBelt() {
        if (passingBeltPos.equals(BlockPos.ZERO)) return null;
        var source = level.getBlockEntity(passingBeltPos, BlockEntitiesContent.CHUTE_BLOCK.get());
        if (source.isEmpty() || !source.get().midPoints.contains(worldPosition)) return null;
        return source.get();
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
        linkPassing(midPoints, BlockPos.ZERO);
        contents.clear();
        target = null;
        midPoints = new ArrayList<>();
        beltData = null;
        cost = 0;
        setChanged();
        if (level instanceof ServerLevel serverWorld)
            serverWorld.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);

        giveBack(player, returned, dropAt);
    }

    private void giveBack(@Nullable Player player, List<ItemStack> stacks, BlockPos dropAt) {
        var spawnAt = dropAt.getCenter();
        for (var stack : stacks) {
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
        // Handed on here, before this belt moves, since the overshoot looks a tick ahead (#373). A
        // support with no belt leaving it backs this belt up (#366).
        if (conveyorEndEntity.splitter || conveyorEndEntity.support) {
            var overshoot = contents.overshoot(getBeltLength(), beltTier.blocksPerTick());
            var receiver = conveyorEndEntity.splitter ? conveyorEndEntity.enteringLane() : conveyorEndEntity.outgoingLane();
            if (!Join.offer(item, overshoot, receiver)) return false;
            conveyorEndEntity.setChanged();
            return true;
        }
        return conveyorEndEntity.acceptFromLine(item);
    }

    /**
     * Unloads one item into the inventory behind this loader's facing, at its own tier's rate and
     * for its own energy. A transport line's last tile offers here (PlanetaryFactory #398).
     */
    public boolean acceptFromLine(ItemStack item) {
        if (splitter || support) return false;
        if (!flow.ready(level.getGameTime()) || !energy.canMove()) return false;
        var targetInv = ItemApi.BLOCK.find(level, worldPosition.relative(getOwnFacing().getOpposite()), null, null, getOwnFacing());
        if (targetInv == null) return false;

        if (targetInv.insert(item, true) != item.getCount()) return false;
        targetInv.insert(item, false);
        flow.pass();
        energy.move();
        setChanged();
        return true;
    }

    
    /**
     * Loads one item out of the inventory behind this loader's facing, at its own tier's rate and
     * for its own energy: one item per entry, as a Factorio loader puts one item in each belt slot
     * (#344). A transport line's first tile asks here (PlanetaryFactory #398).
     */
    @SuppressWarnings("DataFlowIssue")
    public @Nullable ItemStack extractOne() {
        if (splitter || support) return null;
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
        output.store("passing", BlockPos.CODEC, passingBeltPos);

        saveEntries(output, "moving", contents);
        if (half != null) {
            saveEntries(output, "half_entering", half.entering());
            saveEntries(output, "half_leaving", half.leaving());
        }
    }

    private static void saveEntries(ValueOutput output, String name, BeltContents<ItemStack> belt) {
        var positions = output.childrenList(name);
        for (var entry : belt.entries()) {
            var item = positions.addChild();
            item.putDouble("position", entry.position());
            item.store("stack", ItemStack.OPTIONAL_CODEC, entry.payload());
            item.putInt("id", entry.id());
        }
    }

    private static void loadEntries(ValueInput input, String name, BeltContents<ItemStack> belt) {
        belt.clear();
        for (var item : input.childrenListOrEmpty(name)) {
            var stack = item.read("stack", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
            belt.restore(stack, item.getDoubleOr("position", 0), item.getIntOr("id", 0));
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
        passingBeltPos = input.read("passing", BlockPos.CODEC).orElse(BlockPos.ZERO);

        loadEntries(input, "moving", contents);
        if (half != null) {
            loadEntries(input, "half_entering", half.entering());
            loadEntries(input, "half_leaving", half.leaving());
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

    /** A splitter half's own belt, or null on any other block. */
    public Splitter.@Nullable Half<ItemStack> getHalf() {
        return half;
    }

    /** Blocks per tick a splitter half's items move: its own tier's belt speed. */
    public double getHalfSpeed() {
        return halfSpeed;
    }

    /** A splitter half's straight block of belt from its back face to its front face, or null on any other block. */
    public @Nullable BeltData getHalfData() {
        if (!splitter) return null;
        if (halfData == null) {
            var facing = getOwnFacing();
            var forward = new Vec3(facing.getStepX(), 0, facing.getStepZ());
            var center = worldPosition.getCenter();
            halfData = new BeltData(List.of(new Pair<>(center.subtract(forward.scale(0.5)), forward),
              new Pair<>(center.add(forward.scale(0.5)), forward)), 1, new double[] {1});
        }
        return halfData;
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

    public boolean isSupport() {
        return support;
    }

    /** Which of this support's slots its belts take (#366). */
    public SupportSlots.Use supportUse() {
        return new SupportSlots.Use(!sourceBeltPos.equals(BlockPos.ZERO), target != null && !target.equals(BlockPos.ZERO),
          passingBelt() != null);
    }

    public BeltTier getBeltTier() {
        return beltTier;
    }

    /** Whether a belt may start here: a loader is one belt's end, a splitter half one belt in and one out. */
    public boolean canStartBelt() {
        if (support) return SupportSlots.refusal(supportUse(), SupportSlots.Click.START).isEmpty();
        return splitter ? target == null || target.equals(BlockPos.ZERO) : !isUsed();
    }

    public boolean canEndBelt() {
        if (support) return SupportSlots.refusal(supportUse(), SupportSlots.Click.END).isEmpty();
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

    /**
     * The facing a loader ending the belt at {@link #beltEndPos} would have: back towards the belt.
     * A support faces the way its belts run.
     */
    public Direction beltEndFacing() {
        return splitter || support ? getOwnFacing().getOpposite() : getOwnFacing();
    }

    public boolean isUsed() {
        var usedAsTarget = !sourceBeltPos.equals(BlockPos.ZERO);
        var usedAsSource = target != null && !target.equals(BlockPos.ZERO);
        return usedAsTarget || usedAsSource || passingBelt() != null;
    }

    private void linkPassing(List<BlockPos> supports, BlockPos belt) {
        for (var point : supports) {
            level.getBlockEntity(point, BlockEntitiesContent.CHUTE_BLOCK.get()).ifPresent(support -> {
                if (!support.support || support.passingBeltPos.equals(belt)) return;
                support.passingBeltPos = belt;
                support.setChanged();
                if (level instanceof ServerLevel serverWorld)
                    serverWorld.sendBlockUpdated(point, support.getBlockState(), support.getBlockState(), Block.UPDATE_ALL);
            });
        }
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
            var refusal = BeltData.path(this, supportsOf(midpoints), end.get()).refusal();
            if (refusal.isPresent()) return refusal;
        }

        this.target = target;
        this.midPoints = midpoints;
        this.beltTier = beltTier;
        this.cost = cost;
        beltData = BeltData.create(this);
        // At once rather than on the next refresh, so breaking the end straight away finds the belt.
        if (level != null) {
            assignTargetState(level);
            linkPassing(midpoints, worldPosition);
        }
        needsFit = true;
        this.setChanged();
        
        if (level instanceof ServerLevel serverWorld)
            serverWorld.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        return Optional.empty();
    }

    /** This end's belt's curve, or null when it has none laid out. */
    public @Nullable BeltPath path() {
        if (level == null || target == null || target.equals(BlockPos.ZERO) || beltData == null) return null;
        return level.getBlockEntity(target, BlockEntitiesContent.CHUTE_BLOCK.get())
                 .map(end -> BeltData.path(this, getMidPointsWithTangents(), end)).orElse(null);
    }

    /**
     * Cuts this end's belt at a splitter half just placed across it (PlanetaryFactory #361). The
     * refund, and any entry that fits nowhere, goes to the placing player or drops at the half.
     */
    public void cut(ChuteBlockEntity splitterHalf, BeltPath.Crossing.Cut cut, @Nullable Player player) {
        var path = path();
        var end = level.getBlockEntity(target, BlockEntitiesContent.CHUTE_BLOCK.get());
        if (path == null || end.isEmpty() || !splitterHalf.splitter) return;

        var halfPos = splitterHalf.worldPosition;
        var facing = splitterHalf.getOwnFacing();
        var halves = path.cut(cut, halfPos.getX(), halfPos.getY(), halfPos.getZ(), facing.getStepX(), facing.getStepZ());
        var supports = getMidPointsWithTangents().stream().map(Pair::getFirst).toList();
        // Along the entries, whose belt is its curve rounded to whole slots.
        var at = cut.at() / path.length() * getBeltLength();
        var costs = BeltCut.Costs.of(cost, halves.upstream().length());

        handOver(splitterHalf, at);
        var returned = new ArrayList<>(BeltCut.cut(contents, at, slotsLength(halves.upstream()), splitterHalf.half,
          splitterHalf.contents, slotsLength(halves.downstream())));

        splitterHalf.target = target;
        splitterHalf.midPoints = new ArrayList<>(supports.subList(cut.span(), supports.size()));
        splitterHalf.beltTier = beltTier;
        splitterHalf.cost = costs.downstream();
        splitterHalf.beltData = BeltData.create(splitterHalf);
        end.get().linkSource(halfPos);
        splitterHalf.linkPassing(splitterHalf.midPoints, halfPos);

        target = halfPos;
        midPoints = new ArrayList<>(supports.subList(0, cut.span()));
        cost = costs.upstream();
        beltData = BeltData.create(this);
        splitterHalf.linkSource(worldPosition);

        for (var changed : List.of(this, splitterHalf)) {
            changed.setChanged();
            if (level instanceof ServerLevel serverWorld)
                serverWorld.sendBlockUpdated(changed.worldPosition, changed.getBlockState(), changed.getBlockState(), Block.UPDATE_ALL);
        }
        if (costs.refund() > 0) returned.add(new ItemStack(ItemContent.beltFor(beltTier), costs.refund()));
        giveBack(player, returned, halfPos);
    }

    private static double slotsLength(BeltPath path) {
        return BeltCost.slots(path.length()) * BeltContents.SPACING;
    }

    // Its hold stays where it was on the belt, now on whichever belt, or the half, holds that point.
    private void handOver(ChuteBlockEntity splitterHalf, double at) {
        if (heldHand == null || heldHand.onHalf) return;
        var place = BeltCut.locate(heldHand.point, at);
        if (place.section() == BeltCut.Section.UPSTREAM) return;
        splitterHalf.heldHand = new HeldHand(heldHand.player, place.section() == BeltCut.Section.SPLITTER, place.position(),
          heldHand.until, heldHand.taking);
        heldHand = null;
    }

    @Override
    public void setRemoved() {
        BeltCollisionRegistry.unregister(this);
        BeltCollisionRegistry.unregisterHalf(this);
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
            
            return of(path(entity, entity.getMidPointsWithTangents(), targetCandidate.get()));
        }

        public static BeltData of(BeltPath path) {
            var points = path.nodes().stream()
              .map(node -> new Pair<>(new Vec3(node.x(), node.y(), node.z()), new Vec3(node.tangentX(), 0, node.tangentZ())))
              .toList();
            return new BeltData(points, path.length(), path.spanLengths());
        }

        /** The path between two placed ends. */
        public static BeltPath path(ChuteBlockEntity start, List<Pair<BlockPos, Direction>> supports, ChuteBlockEntity end) {
            return path(anchor(start.beltStartPos(), start.getOwnFacing(), start.support), supports,
              anchor(end.beltEndPos(), end.beltEndFacing(), end.support));
        }

        /** With no end the path stops at its last support, as a belt still being laid does. */
        public static BeltPath path(BeltPath.Anchor start, List<Pair<BlockPos, Direction>> supports, BeltPath.@Nullable Anchor end) {
            return BeltPath.of(start, supports.stream().map(support -> anchor(support.getFirst(), support.getSecond(), false)).toList(), end);
        }

        /** An end facing the way a loader there would, whether or not it is placed yet. */
        public static BeltPath.Anchor anchor(BlockPos pos, Direction facing, boolean support) {
            return new BeltPath.Anchor(pos.getX(), pos.getY(), pos.getZ(), facing.getStepX(), facing.getStepZ(), support);
        }
        
    }
}
