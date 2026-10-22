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
import net.minecraft.world.Containers;
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
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jetbrains.annotations.Nullable;
import io.github._5thlayer.beltworks.BeltworksConfig;
import io.github._5thlayer.beltworks.BlockEntitiesContent;
import io.github._5thlayer.beltworks.FeederArmsSent;
import io.github._5thlayer.beltworks.model.ArmPose;
import io.github._5thlayer.beltworks.model.ArmSwing;
import io.github._5thlayer.beltworks.model.FeederArms;
import io.github._5thlayer.beltworks.model.InserterSwing;
import io.github._5thlayer.beltworks.model.LineScan;
import io.github._5thlayer.beltworks.model.LoaderEnergy;
import io.github._5thlayer.beltworks.neoforge.FeederArmPayload;

import java.util.List;

/**
 * Moves items from the inventory, level tile or splitter half its head reaches to the one its tail
 * reaches, in the hand of an arm that swings between them at its tier's inserter's pace (ADR 0018):
 * the hand grabs at the head up to the inserter's hand of what its filter matches and the tail
 * accepts, and drops it all when the arm reaches the tail. It grabs for free while loaders need no
 * power, and for FE of its own while they do (ADR 0014). Each arm reaches one to three blocks on
 * the feeder's level, over whatever stands between, and the tail may turn left or right. Its
 * watchers are sent the arm only as a half-swing starts or the hand changes, and swing it on their own.
 */
public class FeederBlockEntity extends BlockEntity {

    private final ItemFilter filter = new ItemFilter();
    private final InserterSwing inserter;
    private final LoaderEnergy energy;
    private FeederArms arms = FeederArms.ADJACENT;
    private ArmSwing swing = new ArmSwing();
    // What the hand holds, from the grab at the head to the drop at the tail; on a client, only its looks.
    private ItemStack hand = ItemStack.EMPTY;
    // Whether it took the place of a feeder of another tier and kept what that one held.
    private boolean swappedIn;
    public FeederBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntitiesContent.FEEDER.get(), pos, state);
        var tier = ((FeederBlock) state.getBlock()).tier();
        inserter = tier.feederSwing();
        // Read once, as a loader's is: a changed FE rate reaches a feeder when its chunk next loads.
        energy = LoaderEnergy.feeder(tier, BeltworksConfig.loaderPower());
    }

    void tick(Level level, BlockPos pos, BlockState state) {
        var facing = state.getValue(HorizontalDirectionalBlock.FACING);
        var spot = new LineScan.Spot(pos.getX(), pos.getY(), pos.getZ());
        var travel = new LineScan.Travel(facing.getStepX(), facing.getStepZ());
        var arms = arms();
        // Only a grab or a drop marks it changed: a save mid-swing at worst resumes the swing a little early.
        swing.tick(inserter.halfSwingTicks(arms));
        var held = hand.getCount();
        var started = false;
        if (swing.phase() == ArmSwing.Phase.AT_HEAD && grab(level, pos, arms.head(spot, travel), arms.tail(spot, travel))) {
            swing.swingToTail();
            started = true;
        } else if (swing.phase() == ArmSwing.Phase.AT_TAIL && drop(level, arms.tail(spot, travel))) {
            swing.swingToHead();
            started = true;
        }
        // Its watchers swing the arm on their own between, so many feeders in sight cost no message a tick.
        if (started || hand.getCount() != held) sendArm(level, pos);
    }

    // Its watchers are sent the hand as a stack: a block event's parameters are a byte each, too
    // narrow for an item's id.
    private void sendArm(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel server)) return;
        PacketDistributor.sendToPlayersTrackingChunk(server, ChunkPos.containing(pos),
          new FeederArmPayload(pos, looks(hand), swing.phase(), swing.elapsed()));
        FeederArmsSent.sent(pos);
    }

    /** On a client: the arm swings on from its watchers' last word, which no tick of theirs sends. */
    public void clientTick() {
        swing.tick(inserter.halfSwingTicks(arms()));
    }

    /** On a client: the server's word on the arm, {@code elapsed} Factorio ticks into {@code phase}, holding {@code held}. */
    public void armMoved(ItemStack held, ArmSwing.Phase phase, int elapsed) {
        hand = held;
        swing = ArmSwing.at(phase, elapsed);
    }

    /**
     * Where the hand is {@code partialTicks} into this tick, the arm swinging between ticks, over a
     * head standing {@code headTop} high and a tail {@code tailTop}.
     */
    public ArmPose armPose(float partialTicks, double headTop, double tailTop) {
        var facing = getBlockState().getValue(HorizontalDirectionalBlock.FACING);
        var elapsed = swing.elapsed() + (swing.phase().swinging() ? partialTicks * InserterSwing.FACTORIO_TICKS_PER_TICK : 0);
        return ArmPose.of(arms(), new LineScan.Travel(facing.getStepX(), facing.getStepZ()), inserter, swing.phase(), elapsed, headTop, tailTop);
    }

    // Grabs what the tail accepts now, up to a hand, and only what it can pay for (ADR 0014).
    private boolean grab(Level level, BlockPos pos, FeederArms.Target headTarget, FeederArms.Target tailTarget) {
        var wanted = energy.affordable(inserter.handSize());
        if (wanted == 0) return false;
        var head = end(level, headTarget, true);
        var tail = end(level, tailTarget, false);
        if (head == null || tail == null) return false;

        var grabbed = ItemStack.EMPTY;
        try (var grab = Transaction.openRoot()) {
            while (grabbed.getCount() < wanted) {
                var held = grabbed;
                var taken = head.take(item -> filter.matches(level, item) && (held.isEmpty() || ItemStack.isSameItemSameComponents(held, item))
                  && tail.accepts(plus(held, item), grab), grab);
                if (taken == null) break;
                grabbed = plus(held, taken.item());
            }
            if (grabbed.isEmpty()) return false;
            grab.commit();
        }
        hand = grabbed;
        energy.pay(hand.getCount());
        setChanged();
        return true;
    }

    // A hand holding {@code held} and {@code item} too, which is the same item or the hand's first.
    private static ItemStack plus(ItemStack held, ItemStack item) {
        return held.isEmpty() ? item.copy() : held.copyWithCount(held.getCount() + item.getCount());
    }

    // Drops the whole hand, or, into a tail that takes no more than an item at a time, such as a
    // tile, an item at a time; answers whether the hand is empty. A tail that refuses leaves the
    // hand holding, so nothing is lost or dropped loose (ADR 0008).
    private boolean drop(Level level, FeederArms.Target tailTarget) {
        if (hand.isEmpty()) return true;
        var tail = end(level, tailTarget, false);
        if (tail == null) return false;
        try (var drop = Transaction.openRoot()) {
            // Copies, since a tail may use up the stack it is handed.
            if (tail.put(hand.copy(), drop)) {
                hand = ItemStack.EMPTY;
            } else if (hand.getCount() > 1 && tail.put(hand.copyWithCount(1), drop)) {
                hand = hand.copyWithCount(hand.getCount() - 1);
            } else {
                return false;
            }
            drop.commit();
        }
        setChanged();
        return hand.isEmpty();
    }

    // Only what changes how an item is drawn, so a stack's contents, such as a shulker box's, are
    // neither sent with each move nor shown to its watchers.
    private static ItemStack looks(ItemStack item) {
        var seen = new ItemStack(item.getItem(), item.getCount());
        for (var type : List.of(DataComponents.ITEM_MODEL, DataComponents.CUSTOM_MODEL_DATA, DataComponents.DYED_COLOR)) {
            seen.copyFrom(type, item);
        }
        if (item.hasFoil()) seen.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        return seen;
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

    /** What the hand holds, as a copy. */
    public ItemStack hand() {
        return hand.copy();
    }

    /** Where the arm is in its swing. */
    public ArmSwing.Phase armPhase() {
        return swing.phase();
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

    // A feeder of another tier put in its place keeps its filter, its reach, what energy its tier
    // holds and what its hand holds, whatever its facing (#27); any other removal drops the hand
    // beside the feeder's own drop, so nothing it held is lost (ADR 0018).
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level == null) return;
        // The parcel's spill may run after this removal, so it drops the stack held now.
        var held = hand;
        if (TierSwap.swaps(state, level.getBlockState(pos))) {
            TierSwap.parkSaved(this, pos, () -> dropHand(held));
        } else {
            dropHand(held);
        }
        hand = ItemStack.EMPTY;
    }

    @Override
    public void setLevel(Level level) {
        super.setLevel(level);
        swappedIn = TierSwap.take(this);
        // What a swap brings past the new tier's hand drops beside it.
        if (swappedIn && hand.getCount() > inserter.handSize()) {
            dropHand(hand.split(hand.getCount() - inserter.handSize()));
            setChanged();
        }
    }

    private void dropHand(ItemStack items) {
        if (level == null || level.isClientSide() || items.isEmpty()) return;
        Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), items);
    }

    /** Whether it took a feeder of another tier's place and kept its filter and reach (#27). */
    public boolean swappedIn() {
        return swappedIn;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putLong("energy", energy.joules());
        output.store("hand", ItemStack.OPTIONAL_CODEC, hand);
        saveArm(output);
        saveDrawn(output);
    }

    private void saveArm(ValueOutput output) {
        output.putString("arm", swing.phase().name());
        output.putInt("arm_elapsed", swing.elapsed());
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
        hand = input.read("hand", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
        // A feeder saved before its arm swung rests it over the head, its hand empty.
        swing = ArmSwing.load(input.getStringOr("arm", ""), input.getIntOr("arm_elapsed", 0));
        arms = new FeederArms(reach(input, "head_reach"), reach(input, "tail_reach"), FeederArms.Turn.STRAIGHT);
    }

    // A feeder saved before arms had reach, or with a reach out of range, reaches the blocks beside it.
    private static int reach(ValueInput input, String key) {
        var blocks = input.getIntOr(key, FeederArms.MIN_REACH);
        return FeederArms.reaches(blocks) ? blocks : FeederArms.MIN_REACH;
    }

    // Only what its watchers draw, the hand by its looks: its energy changes with every move, which
    // sends no update.
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
        saveDrawn(output);
        output.store("hand", ItemStack.OPTIONAL_CODEC, looks(hand));
        saveArm(output);
        return output.buildResult();
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
