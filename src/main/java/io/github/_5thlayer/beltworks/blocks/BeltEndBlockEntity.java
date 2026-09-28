// SPDX-FileCopyrightText: Rearth
// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: CC-BY-4.0 AND MIT

package io.github._5thlayer.beltworks.blocks;

import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
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
import io.github._5thlayer.beltworks.BeltSync;
import io.github._5thlayer.beltworks.BeltworksConfig;
import io.github._5thlayer.beltworks.BlockEntitiesContent;
import io.github._5thlayer.beltworks.api.item.ItemApi;
import io.github._5thlayer.beltworks.collision.BeltCollisionRegistry;
import io.github._5thlayer.beltworks.util.SplineUtil;
import io.github._5thlayer.beltworks.model.BeltContents;
import io.github._5thlayer.beltworks.model.BeltTier;
import io.github._5thlayer.beltworks.model.FlowLimit;
import io.github._5thlayer.beltworks.model.Join;
import io.github._5thlayer.beltworks.model.LoaderEnergy;
import io.github._5thlayer.beltworks.model.Splitter;
import io.github._5thlayer.beltworks.model.TransportLine;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/** A belt end that is a block: a loader or a splitter half. */
public class BeltEndBlockEntity extends BlockEntity implements BlockEntityTicker<BeltEndBlockEntity> {

    // Optionally works with create and ftb filters.
    private final ItemFilter filter = new ItemFilter();

    private static boolean tearingDownSplitter;

    // The client resends a hold every tick the button is down, so a hold it stopped sending lapses.
    static final int HAND_LAPSE_TICKS = 5;
    private @Nullable HeldHand heldHand;

    // A loader either loads a line or unloads one, never both, so one limit serves.
    private final FlowLimit flow;
    private final LoaderEnergy energy;
    private final boolean splitter;
    // Run by the left half only, for both halves (#349).
    private final @Nullable Splitter<ItemStack> splitterModel;
    private final Splitter.@Nullable Half<ItemStack> half;
    private final double halfSpeed;
    // The game time a splitter's halves last moved.
    private long halfMovedAt = Long.MIN_VALUE;
    private @Nullable BeltData halfData;

    public BeltEndBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntitiesContent.BELT_END.get(), pos, state);
        var tier = ((BeltEndBlock) state.getBlock()).tier();
        splitter = state.getBlock() instanceof SplitterBlock;
        flow = new FlowLimit(tier.itemsPerTick());
        // Splitters draw no power (ADR 0007). The setting is read once, so a changed config reaches a
        // loader when its chunk next loads.
        energy = new LoaderEnergy(splitter ? BeltTier.BELT : tier, BeltworksConfig.loaderPower());
        splitterModel = splitter ? new Splitter<>(tier) : null;
        half = splitter ? new Splitter.Half<>() : null;
        halfSpeed = tier.blocksPerTick();
    }
    
    @Override
    public void tick(Level level, BlockPos pos, BlockState state, BeltEndBlockEntity blockEntity) {
        if (!level.isClientSide() && energy.joules() > 0) {
            energy.drain();
            setChanged();
        }

        if (splitter) tickHalf(level, pos, state);
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
            case HALF_ENTERING -> half == null ? null : half.entering();
            case HALF_LEAVING -> half == null ? null : half.leaving();
        };
    }

    /** A splitter half's own two segments (#373). */
    public enum Track {
        HALF_ENTERING, HALF_LEAVING
    }
    
    /**
     * Makes the point at this fraction of the half's block of belt the player's end for it, until
     * they release it or it lapses. Once their inventory refuses an item the hand stops taking
     * until it is released, and the half runs on past it (#350, #373).
     */
    public void holdHand(ServerPlayer player, double progress) {
        var data = getHalfData();
        if (data == null || !(progress >= 0 && progress <= 1)) return;
        var reach = player.blockInteractionRange() + 1;
        if (player.getEyePosition().distanceToSqr(SplineUtil.getPositionOnSpline(data, progress)) > reach * reach) return;
        var taking = heldHand == null || heldHand.player != player || heldHand.taking;
        heldHand = new HeldHand(player, progress - BeltContents.SPACING / 2, level.getGameTime() + HAND_LAPSE_TICKS, taking);
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
            if (intoInventory(player, item)) return true;
            heldHand = new HeldHand(player, held.point, held.until, false);
            return false;
        });
    }

    /** Whether a hand's holder took the item. */
    static boolean intoInventory(Player player, ItemStack item) {
        // Asked first: a creative inventory's add answers true when full and voids the item.
        var inventory = player.getInventory();
        return (inventory.getSlotWithRemainingSpace(item) >= 0 || inventory.getFreeSlot() >= 0) && inventory.add(item.copy());
    }

    private record HeldHand(ServerPlayer player, double point, long until, boolean taking) {
    }

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
        var right = level.getBlockEntity(SplitterBlock.partner(pos, state), BlockEntitiesContent.BELT_END.get());
        if (right.isEmpty() || !right.get().splitter) return;
        if (splitterModel.tick(level.getGameTime(), side(level), right.get().side(level))) {
            setChanged();
            right.get().setChanged();
            for (var half : List.of(this, right.get())) half.tileAhead().ifPresent(BeltTileBlockEntity::lineChanged);
        }
        halfMovedAt = right.get().halfMovedAt = level.getGameTime();
    }

    private Splitter.Side<ItemStack> side(Level level) {
        return new Splitter.Side<>(half, outgoingHandoff(), hand(level));
    }

    private @Nullable Splitter.Handoff<ItemStack> outgoingHandoff() {
        return tileAhead().map(tile -> tile.entryHandoff(getOwnFacing())).orElse(null);
    }

    // A half feeds the tile line in front of it, where that line starts (#394).
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
        if (!splitter || !Join.offer(item, overshoot, enteringHandoff())) return false;
        setChanged();
        return true;
    }

    private Splitter.Handoff<ItemStack> enteringHandoff() {
        return Splitter.entering(half, halfSpeed, hand(level), halfMovedAt == level.getGameTime() ? 0 : halfSpeed);
    }

    /**
     * Takes the frontmost item on this splitter half that {@code wanted} accepts, for a feeder's
     * head, from past its midline first. Null on a loader, which holds nothing.
     */
    public @Nullable FeederTaken feederTake(Predicate<ItemStack> wanted) {
        if (!splitter) return null;
        var leaving = true;
        var taken = half.leaving().take(0, Splitter.MIDLINE, wanted);
        if (taken == null) {
            leaving = false;
            taken = half.entering().take(0, Splitter.MIDLINE, wanted);
        }
        if (taken == null) return null;
        setChanged();
        return new FeederTaken(taken, leaving);
    }

    /** An item a feeder took from a splitter half, and which of its segments it was on. */
    public record FeederTaken(BeltContents.Entry<ItemStack> entry, boolean leaving) {
    }

    /** Puts an item a feeder took back where it was, when the far end refused it after all. */
    public void feederPutBack(FeederTaken taken) {
        (taken.leaving() ? half.leaving() : half.entering()).place(taken.entry().payload(), taken.entry().position());
        setChanged();
    }

    /** Whether a feeder's tail could drop an item on this splitter half now. */
    public boolean feederCanDrop() {
        return splitter && !Double.isNaN(feederDropPlacement());
    }

    /**
     * Drops an item for a feeder's tail just past this splitter half's midline, where a tile's
     * midpoint is, when there is a gap there; it leaves by the half's own front.
     */
    public boolean feederDrop(ItemStack item) {
        if (!splitter) return false;
        var at = feederDropPlacement();
        if (Double.isNaN(at)) return false;
        half.leaving().place(item, at);
        setChanged();
        return true;
    }

    // As wide as the half moves in a tick, so a one-item gap is caught as it crosses the midline.
    private double feederDropPlacement() {
        return half.leaving().dropPlacement(0, 0, Math.max(BeltContents.SPACING, halfSpeed), Splitter.MIDLINE, false);
    }

    /** Hands a splitter half's items to the player who broke it, or drops them here when nobody did. */
    public void releaseBelts(@Nullable Player player) {
        if (level == null || level.isClientSide() || half == null || half.size() == 0) return;
        var carried = half.payloads();
        half.clear();
        setChanged();
        giveBack(player, carried, worldPosition);
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
    
    /**
     * Unloads one item into the inventory behind this loader's facing, at its own tier's rate and
     * for its own energy. A transport line's last tile offers here (PlanetaryFactory #398).
     */
    public boolean acceptFromLine(ItemStack item) {
        if (splitter) return false;
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
        if (splitter) return null;
        if (!flow.ready(level.getGameTime()) || !energy.canMove()) return null;
        var source = ItemApi.BLOCK.find(level, worldPosition.relative(getOwnFacing().getOpposite()), null, null, getOwnFacing());
        if (source == null) return null;

        for (int slot = 0; slot < source.getSlotCount(); slot++) {
            var availableStack = source.getStackInSlot(slot);
            if (availableStack.isEmpty() || !filter.matches(level, availableStack)) continue;

            var extractingStack = availableStack.copyWithCount(1);
            if (source.extract(extractingStack, false) <= 0) continue;

            flow.pass();
            energy.move();
            return extractingStack;
        }
        return null;
    }
    
    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        filter.save(output);
        output.putLong("energy", energy.joules());

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
        filter.load(input);
        energy.setJoules(input.getLongOr("energy", 0));

        if (half != null) {
            loadEntries(input, "half_entering", half.entering());
            loadEntries(input, "half_leaving", half.leaving());
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
    
    public Direction getOwnFacing() {
        return getBlockState().getValue(HorizontalDirectionalBlock.FACING);
    }

    /** The FE buffer a tier-2 to tier-4 loader pays each item from; tier 1's is never charged. */
    public LoaderEnergy getEnergy() {
        return energy;
    }

    public boolean isSplitter() {
        return splitter;
    }

    @Override
    public void setRemoved() {
        BeltCollisionRegistry.unregisterHalf(this);
        super.setRemoved();
    }

    public ItemStack filteredItem() {
        return filter.item();
    }

    public void assignFilterItem(ItemStack stack, Player player) {
        filter.assign(this, stack, player);
    }

    public void resetFilterItem(Player player) {
        filter.reset(this, player);
    }

    public record BeltData(List<Pair<Vec3, Vec3>> allPoints, double totalLength, double[] segmentLengths) {
    }
}
