// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.items;

import io.github._5thlayer.groundworks.FastReplace;
import io.github._5thlayer.groundworks.PlacementPlan;
import io.github._5thlayer.groundworks.Placements;
import io.github._5thlayer.groundworks.QuarterTurn;
import io.github._5thlayer.groundworks.Refusal;
import io.github._5thlayer.groundworks.ReplaceBuilder;
import io.github._5thlayer.groundworks.Rotate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jetbrains.annotations.Nullable;
import io.github._5thlayer.beltworks.Beltworks;
import io.github._5thlayer.beltworks.blocks.BeltEndBlock;
import io.github._5thlayer.beltworks.blocks.BeltTileBlock;
import io.github._5thlayer.beltworks.blocks.FeederBlock;
import io.github._5thlayer.beltworks.blocks.SplitterBlock;

import java.util.ArrayList;
import java.util.List;

/**
 * The Mod's four Replace groups (Groundworks ADR 0008): a tile, a splitter, a loader and a feeder
 * each replace one of the same kind and another tier with a plain click, and each replace is a
 * tier swap (GLOSSARY.md), which keeps what the old block held. Only another tier replaces: a block
 * of the held item's own tier is the item's own click.
 */
public final class BeltReplaces {

    private BeltReplaces() {
    }

    /** States the groups, at mod construction on both sides, as the preview asks on the client and the click on the server. */
    public static void register() {
        FastReplace.group(Beltworks.id("tiles"), block -> block instanceof BeltTileBlock, new Tile());
        FastReplace.group(Beltworks.id("splitters"), block -> block instanceof SplitterBlock, new Halves());
        // A loader's facing is its only property, which the library copies.
        FastReplace.group(Beltworks.id("loaders"), block -> block instanceof BeltEndBlock && !(block instanceof SplitterBlock));
        FastReplace.group(Beltworks.id("feeders"), block -> block instanceof FeederBlock, new Feeder());
    }

    /** The held item's block, where it is another tier of the aimed {@code old} block's kind, or null. */
    private static @Nullable Block heldOfAnotherTier(ItemStack held, BlockState old) {
        return held.getItem() instanceof BlockItem item && !old.is(item.getBlock()) ? item.getBlock() : null;
    }

    // The library leaves entities to the builder, as vanilla refuses a placement into one.
    private static @Nullable Refusal obstructed(Level level, List<PlacementPlan.Placed> blocks) {
        var clear = blocks.stream().allMatch(placed -> level.isUnobstructed(placed.state(), placed.pos(), CollisionContext.empty()));
        return clear ? null : BeltRefusal.BLOCKED;
    }

    /** A builder whose refusals are the Mod's own, told by their message keys. */
    private abstract static class TellsBeltRefusals implements ReplaceBuilder {

        @Override
        public Component message(Refusal refusal) {
            return refusal instanceof BeltRefusal belt ? Component.translatable(belt.messageKey())
                     : Component.translatable("message.groundworks.fast_replace_refused");
        }
    }

    /**
     * A block that never reshapes in a tier swap: the new block takes the old one's whole state. A
     * Rotate turn held on the stack goes through the item's own plan, which makes what it does of
     * the turned look, and {@link #turned} keeps what the turn leaves alone.
     */
    private abstract static class KeepsItsState extends TellsBeltRefusals {

        @Override
        public @Nullable PlacementPlan plan(Level level, @Nullable Player player, ItemStack held, BlockPos aimed, BlockState old) {
            var block = heldOfAnotherTier(held, old);
            if (block == null) return null;
            var kept = List.of(new PlacementPlan.Placed(aimed, block.withPropertiesOf(old)));
            if (Rotate.turnOf(held).equals(QuarterTurn.NONE)) return PlacementPlan.replacing(kept, obstructed(level, kept));
            var planned = Placements.planFor(held.getItem(), new Replacing(level, player, held, aimed));
            if (planned == null || planned.blocks().stream().noneMatch(placed -> placed.pos().equals(aimed))) {
                return PlacementPlan.replacing(kept, Refusal.FastReplace.PLANS_ELSEWHERE);
            }
            var blocks = planned.blocks().stream()
              .map(placed -> placed.pos().equals(aimed) ? new PlacementPlan.Placed(aimed, turned(placed.state(), old)) : placed).toList();
            var refusal = planned.refusal() != null ? planned.refusal() : obstructed(level, blocks);
            return new PlacementPlan(blocks, List.of(aimed), refusal);
        }

        /** The turned state the item's plan makes, with what the turn leaves of {@code old} kept. */
        abstract BlockState turned(BlockState planned, BlockState old);
    }

    /** A tile keeps its facing, corner and pitch, so its wedge stands; turned, it is the item's plan's, reshaped. */
    private static final class Tile extends KeepsItsState {

        @Override
        BlockState turned(BlockState planned, BlockState old) {
            return planned;
        }
    }

    /** A feeder keeps its facing and its tail's turn, which a Rotate turn leaves alone. */
    private static final class Feeder extends KeepsItsState {

        @Override
        BlockState turned(BlockState planned, BlockState old) {
            return planned.setValue(FeederBlock.TAIL_TURN, old.getValue(FeederBlock.TAIL_TURN));
        }
    }

    /**
     * Both halves of a splitter, from whichever is aimed, each with its own facing and side, for one
     * splitter charged and one handed back. The aimed half is laid first: its tier swap swaps its
     * partner too, so the partner's own swap finds it done.
     */
    private static final class Halves extends TellsBeltRefusals {

        @Override
        public @Nullable PlacementPlan plan(Level level, @Nullable Player player, ItemStack held, BlockPos aimed, BlockState old) {
            var block = heldOfAnotherTier(held, old);
            if (block == null) return null;
            var halves = new ArrayList<PlacementPlan.Placed>();
            halves.add(new PlacementPlan.Placed(aimed, block.withPropertiesOf(old)));
            var partner = SplitterBlock.partner(aimed, old);
            var partnerState = level.getBlockState(partner);
            if (SplitterBlock.isPartner(old, partnerState)) halves.add(new PlacementPlan.Placed(partner, block.withPropertiesOf(partnerState)));
            return PlacementPlan.replacing(halves, obstructed(level, halves));
        }
    }

    // The item's own plan asked for the aimed block's place, as Groundworks asks one without a builder.
    private static final class Replacing extends BlockPlaceContext {

        Replacing(Level level, @Nullable Player player, ItemStack held, BlockPos aimed) {
            super(level, player, InteractionHand.MAIN_HAND, held,
              new BlockHitResult(Vec3.atCenterOf(aimed).relative(Direction.UP, 0.5), Direction.UP, aimed, false));
            replaceClicked = true;
        }
    }
}
