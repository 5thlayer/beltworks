// SPDX-FileCopyrightText: Rearth
// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: CC-BY-4.0 AND MIT

package io.github._5thlayer.beltworks.items;

import io.github._5thlayer.groundworks.PlacementPlan;
import io.github._5thlayer.groundworks.Placements;
import io.github._5thlayer.groundworks.PlansPlacement;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jetbrains.annotations.Nullable;
import io.github._5thlayer.beltworks.blocks.BeltTileBlock;

import java.util.ArrayList;
import java.util.Map;

/**
 * The tile item (PlanetaryFactory #393). A click places one tile facing the look. It stretches too:
 * Groundworks runs the stretch, from the sneak-clicks to the preview, and {@link BeltLegs} builds its
 * legs (ADR 0011).
 */
public class BeltTileItem extends TooltipBlockItem implements PlansPlacement {

    public BeltTileItem(Block block, Properties settings) {
        super(block, settings);
    }

    @Override
    public InteractionResult place(BlockPlaceContext context) {
        var single = single(context);
        if (single != null && single.refused()) {
            if (!context.getLevel().isClientSide() && context.getPlayer() instanceof ServerPlayer player) {
                player.sendSystemMessage(Component.translatable(single.refusal().messageKey()), true);
            }
            return InteractionResult.FAIL;
        }
        return super.place(context);
    }

    /** What the Groundworks library draws for a click here (ADR 0010): a tile placed as vanilla would, with the wedges its reshape puts down. */
    @Override
    public @Nullable PlacementPlan plan(BlockPlaceContext context) {
        var vanilla = Placements.vanillaPlan(this, context);
        var reshape = single(context);
        if (vanilla == null || vanilla.isRefused() || reshape == null) return vanilla;
        var blocks = new ArrayList<>(vanilla.blocks());
        reshape.wedges().forEach((pos, state) -> blocks.add(new PlacementPlan.Placed(pos, state)));
        return reshape.refused() ? PlacementPlan.refused(blocks, reshape.refusal()) : PlacementPlan.accepted(blocks);
    }

    /**
     * What a plain click does to the belt around it, or null where vanilla would place nothing
     * (#420, #419).
     */
    public BeltTileBlock.@Nullable Reshape single(BlockPlaceContext context) {
        var updated = updatePlacementContext(context);
        if (updated == null || !updated.canPlace()) return null;
        var state = getBlock().getStateForPlacement(updated);
        if (state == null) return null;
        var travel = BeltTileBlock.travel(state.getValue(BlockStateProperties.HORIZONTAL_FACING));
        return BeltTileBlock.reshape(context.getLevel(), Map.of(BeltTileBlock.spot(updated.getClickedPos()), travel), getBlock());
    }
}
