// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.blocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;
import io.github._5thlayer.beltworks.BeltworksConfig;
import io.github._5thlayer.beltworks.BlockEntitiesContent;
import io.github._5thlayer.beltworks.model.FeederArms;
import io.github._5thlayer.beltworks.model.FlowLimit;
import io.github._5thlayer.beltworks.model.LineScan;
import io.github._5thlayer.beltworks.model.LoaderEnergy;

import java.util.ArrayList;
import java.util.List;

/**
 * Moves one item at a time from the inventory, level tile or splitter half its head reaches to the
 * one its tail reaches, at a tenth of its tier's loader: for free while loaders need no power, and
 * for FE of its own while they do (ADR 0014). Only what its filter matches is taken. Each arm reaches one to three blocks on
 * the feeder's level, over whatever stands between, and the tail may turn left or right.
 */
public class FeederBlockEntity extends BlockEntity {

    /** The block event a move sends its watchers, naming the item the head took, so they draw it sucked in. */
    static final int SUCKED = 1;
    /** How long a taken item is drawn going into the head, in ticks, after which it is inside and hidden. */
    public static final int SUCK_TICKS = 5;

    private final ItemFilter filter = new ItemFilter();
    private final FlowLimit flow;
    private final LoaderEnergy energy;
    private FeederArms arms = FeederArms.ADJACENT;
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
        var head = end(level, arms.head(spot, travel), true);
        var tail = end(level, arms.tail(spot, travel), false);
        if (head == null || tail == null) return;

        var taken = head.take(item -> filter.matches(level, item) && tail.accepts(item));
        if (taken == null) return;
        // An end whose simulation lied gets its item back, so nothing is lost.
        if (!tail.put(taken.item())) {
            taken.undo().run();
            return;
        }
        flow.pass();
        energy.move();
        setChanged();
        level.blockEvent(pos, state.getBlock(), SUCKED, Item.getId(taken.item().getItem()));
    }

    // Sent on to the watchers only when the server answers true.
    boolean receiveEvent(int event, int item) {
        if (event != SUCKED) return false;
        if (level != null && level.isClientSide()) {
            var now = level.getGameTime();
            sucked.removeIf(entry -> now - entry.gameTime() >= SUCK_TICKS);
            var taken = Item.byId(item);
            if (taken != Items.AIR) sucked.add(new Sucked(new ItemStack(taken), now));
        }
        return true;
    }

    /** The items being drawn going into the head, oldest first; always none on a server. */
    public List<Sucked> sucked() {
        return sucked;
    }

    // An arm reaches its end through the face turned towards the feeder; only a head also takes loose items.
    private static @Nullable FeederEnd end(Level level, FeederArms.Target target, boolean head) {
        var at = new BlockPos(target.spot().x(), target.spot().y(), target.spot().z());
        var face = Direction.getApproximateNearest(-target.pointing().x(), 0, -target.pointing().z());
        return head ? FeederEnd.atHead(level, at, face) : FeederEnd.at(level, at, face);
    }

    public FeederArms arms() {
        return arms;
    }

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

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        filter.save(output);
        output.putLong("energy", energy.joules());
        output.putInt("head_reach", arms.headReach());
        output.putInt("tail_reach", arms.tailReach());
        output.putString("tail_turn", arms.tailTurn().name());
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        filter.load(input);
        energy.setJoules(input.getLongOr("energy", 0));
        arms = new FeederArms(reach(input, "head_reach"), reach(input, "tail_reach"), turn(input));
    }

    // A feeder saved before arms had reach, or with a reach out of range, reaches the blocks beside it.
    private static int reach(ValueInput input, String key) {
        var blocks = input.getIntOr(key, FeederArms.MIN_REACH);
        return FeederArms.reaches(blocks) ? blocks : FeederArms.MIN_REACH;
    }

    // Its filter and arms, which its watchers draw.
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    private static FeederArms.Turn turn(ValueInput input) {
        var name = input.getStringOr("tail_turn", FeederArms.Turn.STRAIGHT.name());
        for (var turn : FeederArms.Turn.values()) if (turn.name().equals(name)) return turn;
        return FeederArms.Turn.STRAIGHT;
    }
}
