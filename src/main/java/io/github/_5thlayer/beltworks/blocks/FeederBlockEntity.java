// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.blocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import io.github._5thlayer.beltworks.BeltworksConfig;
import io.github._5thlayer.beltworks.BlockEntitiesContent;
import io.github._5thlayer.beltworks.model.FeederArms;
import io.github._5thlayer.beltworks.model.FlowLimit;
import io.github._5thlayer.beltworks.model.LineScan;
import io.github._5thlayer.beltworks.model.LoaderEnergy;
import io.github._5thlayer.beltworks.neoforge.FeederSuckedPayload;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Moves one item at a time from the inventory, level tile or splitter half its head reaches to the
 * one its tail reaches, at a tenth of its tier's loader: for free while loaders need no power, and
 * for FE of its own while they do (ADR 0014). Only what its filter matches is taken. Each arm reaches one to three blocks on
 * the feeder's level, over whatever stands between, and the tail may turn left or right.
 */
public class FeederBlockEntity extends BlockEntity {

    /** How long a taken item is drawn going into the head, in ticks, after which it is inside and hidden. */
    public static final int SUCK_TICKS = 8;

    private final ItemFilter filter = new ItemFilter();
    private final FlowLimit flow;
    private final LoaderEnergy energy;
    private FeederArms arms = FeederArms.ADJACENT;
    // Whether it took the place of a feeder of another tier and kept what that one held.
    private boolean swappedIn;
    // On a client only: the items its head took lately, still being drawn going in.
    private final List<Sucked> sucked = new ArrayList<>();

    /** An item the head took at a client's {@code gameTime}. */
    public record Sucked(ItemStack item, long gameTime) {
    }

    public FeederBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntitiesContent.FEEDER.get(), pos, state);
        var tier = ((FeederBlock) state.getBlock()).tier();
        flow = new FlowLimit(tier.feederItemsPerTick());
        // Read once, as a loader's is: a changed FE rate reaches a feeder when its chunk next loads.
        energy = LoaderEnergy.feeder(tier, BeltworksConfig.loaderPower());
    }

    void tick(Level level, BlockPos pos, BlockState state) {
        if (!flow.ready(level.getGameTime()) || !energy.canMove()) return;
        var facing = state.getValue(HorizontalDirectionalBlock.FACING);
        var spot = new LineScan.Spot(pos.getX(), pos.getY(), pos.getZ());
        var travel = new LineScan.Travel(facing.getStepX(), facing.getStepZ());
        var arms = arms();
        var head = end(level, arms.head(spot, travel), true);
        var tail = end(level, arms.tail(spot, travel), false);
        if (head == null || tail == null) return;

        var taken = head.take(item -> filter.matches(level, item) && tail.accepts(item));
        if (taken == null) return;
        // Taken first, since a tail may use up the stack it is handed.
        var seen = looks(taken.item());
        // An end whose simulation lied gets its item back, so nothing is lost.
        if (!tail.put(taken.item())) {
            taken.undo().run();
            return;
        }
        flow.pass();
        energy.move();
        setChanged();
        // Its watchers are sent the item as a stack: a block event's parameters are a byte each, too
        // narrow for an item's id.
        if (level instanceof ServerLevel server) {
            PacketDistributor.sendToPlayersTrackingChunk(server, ChunkPos.containing(pos), new FeederSuckedPayload(pos, seen));
        }
    }

    // Only what changes how an item is drawn, so a stack's contents, such as a shulker box's, are
    // neither sent with each move nor shown to its watchers.
    private static ItemStack looks(ItemStack item) {
        var seen = new ItemStack(item.getItem());
        for (var type : List.of(DataComponents.ITEM_MODEL, DataComponents.CUSTOM_MODEL_DATA, DataComponents.DYED_COLOR)) {
            seen.copyFrom(type, item);
        }
        if (item.hasFoil()) seen.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        return seen;
    }

    /** On a client: the head took {@code item}, which is drawn going in from now. */
    public void sucked(ItemStack item) {
        if (level == null || item.isEmpty()) return;
        var now = level.getGameTime();
        sucked.removeIf(entry -> now - entry.gameTime() >= SUCK_TICKS);
        sucked.add(new Sucked(item, now));
    }

    /** The items being drawn going into the head, oldest first; always none on a server. */
    public List<Sucked> sucked() {
        return Collections.unmodifiableList(sucked);
    }

    // An arm reaches its end through the face turned towards the feeder; only a head also takes loose items.
    private static @Nullable FeederEnd end(Level level, FeederArms.Target target, boolean head) {
        var at = new BlockPos(target.spot().x(), target.spot().y(), target.spot().z());
        var face = Direction.getApproximateNearest(-target.pointing().x(), 0, -target.pointing().z());
        return head ? FeederEnd.atHead(level, at, face) : FeederEnd.at(level, at, face);
    }

    /** Its arms: the reach kept here, and the tail's turn, which Rotate keeps on the block state. */
    public FeederArms arms() {
        return arms.withTailTurn(getBlockState().getValue(FeederBlock.TAIL_TURN).turn());
    }

    /** Sets both reaches; the tail's turn stays the block state's. */
    public void setArms(FeederArms arms) {
        this.arms = arms;
        setChanged();
        // Its watchers draw the arms, so they are sent the new reach.
        if (level instanceof ServerLevel server) server.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
    }

    public LoaderEnergy getEnergy() {
        return energy;
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

    // A feeder of another tier put in its place keeps its filter, its reach and what energy its tier
    // holds, whatever its facing (#27).
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null && TierSwap.swaps(state, level.getBlockState(pos))) TierSwap.parkSaved(this, pos, () -> {});
    }

    @Override
    public void setLevel(Level level) {
        super.setLevel(level);
        swappedIn = TierSwap.take(this);
    }

    /** Whether it took a feeder of another tier's place and kept its filter and reach (#27). */
    public boolean swappedIn() {
        return swappedIn;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putLong("energy", energy.joules());
        saveDrawn(output);
    }

    // Its filter and arms, which its watchers draw.
    private void saveDrawn(ValueOutput output) {
        filter.save(output);
        output.putInt("head_reach", arms.headReach());
        output.putInt("tail_reach", arms.tailReach());
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        filter.load(input);
        energy.setJoules(input.getLongOr("energy", 0));
        arms = new FeederArms(reach(input, "head_reach"), reach(input, "tail_reach"), FeederArms.Turn.STRAIGHT);
    }

    // A feeder saved before arms had reach, or with a reach out of range, reaches the blocks beside it.
    private static int reach(ValueInput input, String key) {
        var blocks = input.getIntOr(key, FeederArms.MIN_REACH);
        return FeederArms.reaches(blocks) ? blocks : FeederArms.MIN_REACH;
    }

    // Only what its watchers draw: its energy changes with every move, which sends no update.
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
        saveDrawn(output);
        return output.buildResult();
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
