// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.items;

import io.github._5thlayer.groundworks.Leg;
import io.github._5thlayer.groundworks.LegBuilder;
import io.github._5thlayer.groundworks.PlacementPlan;
import io.github._5thlayer.groundworks.Refusal;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jetbrains.annotations.Nullable;
import io.github._5thlayer.beltworks.blocks.BeltTileBlock;
import io.github._5thlayer.beltworks.blocks.BeltWedgeBlock;
import io.github._5thlayer.beltworks.model.BeltLeg;
import io.github._5thlayer.beltworks.model.LineScan;
import io.github._5thlayer.beltworks.model.Wedge;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What a belt's stretch is built of, leg by leg (ADR 0011). Groundworks runs the stretch, from the
 * gesture to charging, and asks the tile item only for each leg's tiles, shaped by {@link BeltLeg}.
 *
 * <p>A tile already on the leg running along it is kept, turned to it if it faces back, and one of
 * another tier is replaced, as Factorio's belts Fast Replace one another (the Pack's ADR-0082). The
 * wedges under the leg's slopes are no blocks of the plan, so they cost nothing: each tile puts its
 * own down as it is placed.
 */
public final class BeltLegs implements LegBuilder {

    @Override
    public boolean claims(Item item) {
        return item instanceof BeltTileItem;
    }

    @Override
    public PlacementPlan build(Level level, Item item, Leg leg) {
        var tile = ((BeltTileItem) item).getBlock();
        var route = leg.route().stream()
                      .map(column -> new BeltLeg.Column(column.x(), column.z(), BeltTileBlock.travel(column.travel())))
                      .toList();
        var shaped = BeltLeg.shape(BeltTileBlock.spot(leg.from()), leg.rise(), route, terrain(level));

        var travels = new LinkedHashMap<LineScan.Spot, LineScan.Travel>();
        for (var step : shaped.tiles()) {
            var there = level.getBlockState(pos(step.spot()));
            // A tile of the held tier already running the leg's way is the leg's as it stands.
            if (there.is(tile) && BeltTileBlock.travel(there.getValue(BlockStateProperties.HORIZONTAL_FACING)).equals(step.travel())) continue;
            travels.put(step.spot(), step.travel());
        }
        var around = BeltTileBlock.around(level, travels);
        var blocks = new ArrayList<PlacementPlan.Placed>();
        var replaces = new ArrayList<BlockPos>();
        travels.forEach((spot, travel) -> {
            var pos = pos(spot);
            var facing = Direction.getApproximateNearest(travel.x(), 0, travel.z());
            blocks.add(new PlacementPlan.Placed(pos, BeltTileBlock.formed(
                    tile.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, facing), spot, around)));
            if (level.getBlockState(pos).getBlock() instanceof BeltTileBlock) replaces.add(pos);
        });
        return new PlacementPlan(blocks, replaces, refusal(level, shaped, travels, tile));
    }

    private static @Nullable Refusal refusal(Level level, BeltLeg.Shaped shaped, Map<LineScan.Spot, LineScan.Travel> travels, Block tile) {
        if (shaped.stop() != null) {
            return switch (shaped.stop()) {
                case SLOPE_TURNS -> BeltRefusal.SLOPE_TURNS;
                case BLOCKED -> new Refusal.At(BeltRefusal.BLOCKED, pos(shaped.at()));
                case CROSSES_A_LINE -> new Refusal.At(BeltRefusal.CROSSES_A_LINE, pos(shaped.at()));
            };
        }
        var reshape = BeltTileBlock.reshape(level, travels, tile);
        if (!reshape.refused()) return null;
        return reshape.at() == null ? reshape.refusal() : new Refusal.At(reshape.refusal(), reshape.at());
    }

    @Override
    public Component message(Refusal refusal) {
        return Component.translatable(((BeltRefusal) refusal).messageKey());
    }

    // A wedge belongs to the slope above it, so a leg neither replaces it nor stands in it (ADR 0005).
    private static BeltLeg.Terrain terrain(Level level) {
        return (spot, travel) -> {
            var pos = pos(spot);
            var state = level.getBlockState(pos);
            if (state.getBlock() instanceof BeltTileBlock) {
                var its = BeltTileBlock.travel(state.getValue(BlockStateProperties.HORIZONTAL_FACING));
                return its.x() * travel.x() + its.z() * travel.z() == 0 ? BeltLeg.Ground.ACROSS : BeltLeg.Ground.ALONG;
            }
            if (state.getBlock() instanceof BeltWedgeBlock || !level.isInWorldBounds(pos)) return BeltLeg.Ground.OBSTACLE;
            return BeltTileBlock.occupant(level, pos) == Wedge.Below.REPLACEABLE ? BeltLeg.Ground.FREE : BeltLeg.Ground.OBSTACLE;
        };
    }

    private static BlockPos pos(LineScan.Spot spot) {
        return new BlockPos(spot.x(), spot.y(), spot.z());
    }
}
