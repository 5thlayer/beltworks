// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jetbrains.annotations.Nullable;
import io.github._5thlayer.beltworks.items.BeltTileItem;
import io.github._5thlayer.beltworks.items.SplitterItem;
import io.github._5thlayer.beltworks.items.StretchPlan;

import java.util.ArrayList;
import java.util.List;

/**
 * What a click with a belt piece would do, read off the Mod's own plans as a preview reads them
 * (ADR 0006): the blocks it would put down, the placed tiles it turns or replaces, and a refusal or
 * none. The tests hold the click to it. Until the placementpreview library's plan stands in for it
 * (#41), this is the Pack's {@code TilePlans} and {@code SplitterPlans} over a plan of the tests' own.
 *
 * @param refusal {@link StretchPlan.Reason#BLOCKED} as well where vanilla would refuse the tile
 */
record PlannedClick(List<Placed> blocks, List<BlockPos> replaces, StretchPlan.@Nullable Reason refusal) {

    record Placed(BlockPos pos, BlockState state) {
    }

    PlannedClick {
        blocks = List.copyOf(blocks);
        replaces = List.copyOf(replaces);
    }

    boolean refused() {
        return refusal != null;
    }

    /** The plan of a main-hand click, or null where the click would put nothing down. */
    static @Nullable PlannedClick of(Level level, Player player, ItemStack stack, BlockHitResult hit) {
        var context = new BlockPlaceContext(level, player, InteractionHand.MAIN_HAND, stack, hit);
        if (stack.getItem() instanceof SplitterItem splitter) return splitter(splitter, context);
        if (stack.getItem() instanceof BeltTileItem tile) return tile(tile, context);
        return null;
    }

    private static @Nullable PlannedClick splitter(SplitterItem item, BlockPlaceContext context) {
        var plan = item.plan(context);
        if (plan == null) return null;
        var blocks = plan.halves().stream().map(half -> new Placed(half.pos(), half.state())).toList();
        return new PlannedClick(blocks, List.of(), plan.blocked() ? StretchPlan.Reason.BLOCKED : null);
    }

    // With a stored start, a click lays the stretch and a sneak-click adds a corner where it would
    // end, so both are the stretch. Without one, a sneak-click stores a start at the tile facing the
    // look, and a click places a tile as vanilla would, with the wedges its reshape puts down.
    private static @Nullable PlannedClick tile(BeltTileItem item, BlockPlaceContext context) {
        var stretch = item.stretch(context);
        if (stretch != null) {
            if (stretch.tiles().isEmpty()) return null;
            var blocks = stretch.tiles().stream().map(tile -> new Placed(tile.pos(), tile.state())).toList();
            var replaces = stretch.tiles().stream()
                             .filter(tile -> tile.action() == StretchPlan.Action.TURN || tile.action() == StretchPlan.Action.REPLACE)
                             .map(StretchPlan.Tile::pos).toList();
            return new PlannedClick(blocks, replaces, stretch.refused() ? stretch.refusal().reason() : null);
        }
        if (context.getPlayer() != null && context.getPlayer().isShiftKeyDown()) {
            var start = item.getBlock().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, context.getHorizontalDirection());
            return new PlannedClick(List.of(new Placed(BeltTileItem.aimedTile(context), start)), List.of(), null);
        }
        var vanilla = vanilla(item, context);
        var reshape = item.single(context);
        if (vanilla == null || vanilla.refused() || reshape == null) return vanilla;
        var blocks = new ArrayList<>(vanilla.blocks());
        reshape.wedges().forEach((pos, state) -> blocks.add(new Placed(pos, state)));
        return new PlannedClick(blocks, List.of(), reshape.refused() ? reshape.refusal() : null);
    }

    // BlockItem#place's own chain: canPlace, updatePlacementContext, getStateForPlacement, then
    // survival and obstruction.
    private static @Nullable PlannedClick vanilla(BeltTileItem item, BlockPlaceContext context) {
        if (!item.getBlock().isEnabled(context.getLevel().enabledFeatures()) || !context.canPlace()) return null;
        var updated = item.updatePlacementContext(context);
        if (updated == null) return null;
        var state = item.getBlock().getStateForPlacement(updated);
        if (state == null) return null;
        var pos = updated.getClickedPos();
        var level = context.getLevel();
        var survives = state.canSurvive(level, pos)
                         && level.isUnobstructed(state, pos, CollisionContext.placementContext(context.getPlayer()));
        return new PlannedClick(List.of(new Placed(pos, state)), List.of(), survives ? null : StretchPlan.Reason.BLOCKED);
    }
}
