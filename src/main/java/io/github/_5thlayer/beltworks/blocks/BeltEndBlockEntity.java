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
import io.github._5thlayer.beltworks.model.HeldHand;
import io.github._5thlayer.beltworks.model.Join;
import io.github._5thlayer.beltworks.model.LoaderEnergy;
import io.github._5thlayer.beltworks.model.Splitter;
import io.github._5thlayer.beltworks.model.TransportLine;

import java.util.ArrayList;
import java.util.List;

/** A belt end that is a block: a loader or a splitter half. */
public class BeltEndBlockEntity extends BlockEntity implements BlockEntityTicker<BeltEndBlockEntity> {

    // Optionally works with create and ftb filters.
    private final ItemFilter filter = new ItemFilter();

    private static boolean tearingDownSplitter;

    private final HeldHand<ServerPlayer> heldHand = new HeldHand<>();
    private double handPoint;

    // A loader either loads a line or unloads one, never both, so one limit serves.
    private final FlowLimit flow;
    private final LoaderEnergy energy;
    private final boolean splitter;
    // Run by the left half only, for both halves (#349).
    private final @Nullable Splitter<ItemStack> splitterModel;
    private final Splitter.@Nullable Half<ItemStack> half;
    // The game time a splitter's halves last moved.
    private long halfMovedAt = Long.MIN_VALUE;
    private @Nullable BeltData halfData;
    // Held by the left half for the splitter; the right half's stays unset.
    private SplitterSettings settings = SplitterSettings.NONE;

    public BeltEndBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntitiesContent.BELT_END.get(), pos, state);
        var tier = ((BeltEndBlock) state.getBlock()).tier();
        splitter = state.getBlock() instanceof SplitterBlock;
        flow = new FlowLimit(tier.itemsPerTick());
        // Splitters draw no power (ADR 0007). The setting is read once, so a changed config reaches a
        // loader when its chunk next loads.
        energy = new LoaderEnergy(splitter ? BeltTier.BELT : tier, BeltworksConfig.loaderPower());
        splitterModel = splitter ? new Splitter<>(tier) : null;
        half = splitter ? new Splitter.Half<>(tier) : null;
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
        // A sneaking player's use opens the splitter's screen instead (#20).
        if (data == null || player.isShiftKeyDown() || !(progress >= 0 && progress <= 1)) return;
        var reach = player.blockInteractionRange() + 1;
        if (player.getEyePosition().distanceToSqr(SplineUtil.getPositionOnSpline(data, progress)) > reach * reach) return;
        heldHand.hold(player, level.getGameTime());
        handPoint = progress - BeltContents.SPACING / 2;
    }

    public void releaseHand(ServerPlayer player) {
        heldHand.release(player);
    }

    private BeltContents.@Nullable Hand<ItemStack> hand(Level level) {
        return heldHand.hand(handPoint, level.getGameTime(), PlayerHand.alive(level), PlayerHand::intoInventory);
    }

    private void tickHalf(Level level, BlockPos pos, BlockState state) {
        BeltCollisionRegistry.registerHalf(this);
        if (level.isClientSide()) {
            half.entering().advance(Splitter.MIDLINE, half.speed());
            half.leaving().advance(Splitter.MIDLINE, half.speed());
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
        }
        halfMovedAt = right.get().halfMovedAt = level.getGameTime();
    }

    private Splitter.Side<ItemStack> side(Level level) {
        return new Splitter.Side<>(half, this::takeFromLoader, outlet(), hand(level));
    }

    // A loader behind a half, its mouth to the half's back, loads it as it loads a line's first tile (#89).
    private @Nullable ItemStack takeFromLoader() {
        var behind = worldPosition.relative(getOwnFacing().getOpposite());
        if (!level.isLoaded(behind)) return null;
        var loader = level.getBlockEntity(behind, BlockEntitiesContent.BELT_END.get()).orElse(null);
        if (loader == null || loader.isSplitter() || loader.getOwnFacing() != getOwnFacing()) return null;
        return loader.extractOne();
    }

    // A half hands on to what stands in front of it, as a line's last tile does (#85, #89).
    private @Nullable BeltOutlet outlet() {
        return BeltOutlet.aheadOfHalf(level, worldPosition.relative(getOwnFacing()), getOwnFacing());
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
     * Takes the item at the end of a tile line or splitter half facing into this half's back, at
     * the sender's {@link BeltContents#overshoot} (#394, #85).
     */
    public boolean offerAtBack(ItemStack item, double overshoot) {
        if (!splitter || !Join.offer(item, overshoot, enteringHandoff())) return false;
        setChanged();
        return true;
    }

    private Splitter.Handoff<ItemStack> enteringHandoff() {
        return Splitter.entering(half, half.speed(), hand(level), halfMovedAt == level.getGameTime() ? 0 : half.speed());
    }

    /** The splitter's one settings record, read from either half, or none where the pair is broken. */
    public SplitterSettings splitterSettings() {
        var left = leftHalf();
        return left == null ? SplitterSettings.NONE : left.settings;
    }

    /** Sets the splitter's output priority from either half: the screen's one way to change it, on the server. */
    public void setOutputPriority(Splitter.Priority priority) {
        var left = leftHalf();
        if (left == null || level == null || level.isClientSide()) return;
        left.applySettings(left.settings.withOutputPriority(priority));
        left.setChanged();
        level.sendBlockUpdated(left.worldPosition, left.getBlockState(), left.getBlockState(), Block.UPDATE_CLIENTS);
    }

    /**
     * Sets the splitter's filter from either half, or clears it with an empty stack. A filter set with
     * no output priority sets it to {@code side}, the side the screen's switch shows: a filter never
     * exists without one.
     */
    public void setSplitterFilter(ItemStack stack, Splitter.Priority side) {
        var left = leftHalf();
        if (left == null || level == null || level.isClientSide()) return;
        left.applySettings(left.settings.withFilter(stack, side));
        left.setChanged();
        level.sendBlockUpdated(left.worldPosition, left.getBlockState(), left.getBlockState(), Block.UPDATE_CLIENTS);
    }

    /** Sets the splitter's input priority from either half, as {@link #setOutputPriority} sets the output's. */
    public void setInputPriority(Splitter.Priority priority) {
        var left = leftHalf();
        if (left == null || level == null || level.isClientSide()) return;
        left.applySettings(left.settings.withInputPriority(priority));
        left.setChanged();
        level.sendBlockUpdated(left.worldPosition, left.getBlockState(), left.getBlockState(), Block.UPDATE_CLIENTS);
    }

    private void applySettings(SplitterSettings settings) {
        this.settings = settings;
        if (splitterModel != null) splitterModel.inputPriority(settings.inputPriority());
        if (splitterModel != null) splitterModel.outputPriority(settings.outputPriority());
        var filterItem = settings.filter();
        if (splitterModel != null) splitterModel.filter(filterItem.isEmpty() ? null : stack -> ItemFilter.matches(level, filterItem, stack));
    }

    private @Nullable BeltEndBlockEntity leftHalf() {
        if (!splitter) return null;
        var state = getBlockState();
        if (state.getValue(SplitterBlock.SIDE) == SplitterBlock.Side.LEFT) return this;
        if (level == null) return null;
        return level.getBlockEntity(SplitterBlock.partner(worldPosition, state), BlockEntitiesContent.BELT_END.get())
                 .filter(partner -> SplitterBlock.isPartner(state, partner.getBlockState())).orElse(null);
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
        if (swapsTier(pos, state)) {
            TierSwap.parkSaved(this, pos, () -> releaseBelts(null));
            swapOtherHalf(pos, state);
            return;
        }
        releaseBelts(null);
        removeOtherHalf(pos, state);
    }

    // A loader keeps what it held whatever its new facing; a splitter half only with its own facing
    // and side, any other being a break and a placement (ADR 0007).
    private boolean swapsTier(BlockPos pos, BlockState state) {
        if (level == null || level.isClientSide()) return false;
        var now = level.getBlockState(pos);
        if (!TierSwap.swaps(state, now)) return false;
        return !splitter || now.getValue(HorizontalDirectionalBlock.FACING) == state.getValue(HorizontalDirectionalBlock.FACING)
                            && now.getValue(SplitterBlock.SIDE) == state.getValue(SplitterBlock.SIDE);
    }

    // The other half takes the new tier too, with its own properties, so a splitter is only ever of
    // one tier. Its removal swaps it as this one's did, and finds this half already swapped.
    private void swapOtherHalf(BlockPos pos, BlockState state) {
        if (!splitter) return;
        var partner = SplitterBlock.partner(pos, state);
        var partnerState = level.getBlockState(partner);
        if (!SplitterBlock.isPartner(state, partnerState)) return;
        level.setBlock(partner, level.getBlockState(pos).getBlock().withPropertiesOf(partnerState), Block.UPDATE_ALL);
    }

    @Override
    public void setLevel(Level level) {
        super.setLevel(level);
        TierSwap.take(this);
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
            if (settings.inputPriority() != Splitter.Priority.NONE) output.putString("input_priority", settings.inputPriority().name());
            if (settings.outputPriority() != Splitter.Priority.NONE) output.putString("output_priority", settings.outputPriority().name());
            if (!settings.filter().isEmpty()) output.store("splitter_filter", ItemStack.CODEC, settings.filter());
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
            applySettings(new SplitterSettings(priority(input.getStringOr("input_priority", "")),
              priority(input.getStringOr("output_priority", "")),
              input.read("splitter_filter", ItemStack.CODEC).orElse(ItemStack.EMPTY)));
        }
    }
    
    private static Splitter.Priority priority(String name) {
        for (var priority : Splitter.Priority.values()) if (priority.name().equals(name)) return priority;
        return Splitter.Priority.NONE;
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
        if (splitter) return shownSplitterFilter();
        return filter.item();
    }

    // Drawn on the half of the output priority side only, where what it matches goes.
    private ItemStack shownSplitterFilter() {
        var settings = splitterSettings();
        if (settings.filter().isEmpty()) return ItemStack.EMPTY;
        var side = getBlockState().getValue(SplitterBlock.SIDE) == SplitterBlock.Side.LEFT ? Splitter.Priority.LEFT : Splitter.Priority.RIGHT;
        return settings.outputPriority() == side ? settings.filter() : ItemStack.EMPTY;
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
