// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.blocks;

import io.github._5thlayer.groundworks.PlacementPlan;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.jetbrains.annotations.Nullable;
import io.github._5thlayer.beltworks.model.Support;

/**
 * The supports a Placement Plan's belt pieces show once its click lays them (ADR 0012), for the
 * Placement Preview to draw. They are worked out by a placed piece's own rule, over the world with
 * the plan's blocks counted as placed, so a planned corner, the plan's last tile as its line's end
 * and a planned tile resting on another come out as the click leaves them.
 */
public final class PlannedSupports {

    private PlannedSupports() {
    }

    /** Each planned piece's support, by its position, leaving out those that show none. */
    public static Map<BlockPos, Support> of(BlockGetter level, List<PlacementPlan.Placed> blocks, Support.Setting setting) {
        var world = world(level, blocks);
        var supports = new LinkedHashMap<BlockPos, Support>();
        for (var placed : blocks) {
            var support = Supports.of(world, placed.pos(), placed.state(), setting);
            if (support != null) supports.put(placed.pos(), support);
        }
        return supports;
    }

    /** The world as it stands after the plan's click. */
    public static BlockGetter world(BlockGetter level, List<PlacementPlan.Placed> blocks) {
        var states = new HashMap<BlockPos, BlockState>();
        for (var placed : blocks) states.put(placed.pos(), placed.state());
        return new Planned(level, states);
    }

    /** The world as it stands after the click: the planned blocks in their places, and nothing stored in them yet. */
    private record Planned(BlockGetter level, Map<BlockPos, BlockState> planned) implements BlockGetter {

        @Override
        public @Nullable BlockEntity getBlockEntity(BlockPos pos) {
            return planned.containsKey(pos) ? null : level.getBlockEntity(pos);
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            var state = planned.get(pos);
            return state != null ? state : level.getBlockState(pos);
        }

        @Override
        public FluidState getFluidState(BlockPos pos) {
            var state = planned.get(pos);
            return state != null ? state.getFluidState() : level.getFluidState(pos);
        }

        @Override
        public int getHeight() {
            return level.getHeight();
        }

        @Override
        public int getMinY() {
            return level.getMinY();
        }
    }
}
