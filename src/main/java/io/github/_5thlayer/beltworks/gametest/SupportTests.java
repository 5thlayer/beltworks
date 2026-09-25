// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jspecify.annotations.Nullable;
import io.github._5thlayer.beltworks.BlockContent;
import io.github._5thlayer.beltworks.blocks.BeltTileBlock;
import io.github._5thlayer.beltworks.model.Pitch;
import io.github._5thlayer.beltworks.model.Support;

/**
 * A raised tile's support as the world gives it (ADR 0012): what a leg passes and stands on, what
 * holds a tile up or fixes it. The rule itself is {@code SupportTest}'s; these hold the world's
 * blocks to it. Each tile here stands alone, so it is its line's first and last.
 */
final class SupportTests {

    private static final BlockPos RAISED = new BlockPos(3, 5, 3);

    private SupportTests() {
    }

    static void register(BeltGameTests.Registrar tests) {
        tests.test("a_leg_passes_water_and_grass_to_the_ground", 20, helper -> {
            tile(helper, RAISED, Direction.EAST);
            helper.setBlock(RAISED.below(4), Blocks.GRASS_BLOCK);
            helper.setBlock(RAISED.below(3), Blocks.SHORT_GRASS);
            helper.setBlock(RAISED.below(), Blocks.WATER);
            // The grass block's top is three blocks under the tile's floor.
            bottom(helper, RAISED, Direction.EAST, -3);
            helper.succeed();
        });
        tests.test("a_leg_follows_a_block_placed_far_below", 20, helper -> {
            tile(helper, RAISED, Direction.EAST);
            bottom(helper, RAISED, Direction.EAST, -4);
            helper.setBlock(RAISED.below(2), Blocks.STONE);
            bottom(helper, RAISED, Direction.EAST, -1);
            helper.setBlock(RAISED.below(2), Blocks.AIR);
            bottom(helper, RAISED, Direction.EAST, -4);
            helper.succeed();
        });
        tests.test("a_tile_on_stone_or_on_a_tile_shows_no_support", 20, helper -> {
            var onGround = new BlockPos(3, 1, 3);
            tile(helper, onGround, Direction.EAST);
            none(helper, onGround, Direction.EAST);
            tile(helper, onGround.above(), Direction.NORTH);
            none(helper, onGround.above(), Direction.NORTH);
            helper.succeed();
        });
        tests.test("a_tile_beside_a_wall_shows_no_support", 20, helper -> {
            tile(helper, RAISED, Direction.EAST);
            helper.setBlock(RAISED.north(), Blocks.STONE);
            none(helper, RAISED, Direction.EAST);
            // In front of it, the wall is on a side its line uses.
            helper.setBlock(RAISED.north(), Blocks.AIR);
            helper.setBlock(RAISED.east(), Blocks.STONE);
            if (support(helper, RAISED, Direction.EAST) == null) helper.fail("a wall ahead of a line's end fixed it", RAISED);
            helper.succeed();
        });
        tests.test("a_leg_lands_on_a_lower_tiles_surface", 20, helper -> {
            var lower = new BlockPos(3, 1, 3);
            tile(helper, lower, Direction.EAST);
            tile(helper, lower.above(3), Direction.SOUTH);
            bottom(helper, lower.above(3), Direction.SOUTH, Pitch.SURFACE - 3);
            helper.succeed();
        });
    }

    private static void tile(GameTestHelper helper, BlockPos at, Direction facing) {
        helper.setBlock(at, BlockContent.BELT_TILE.get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, facing));
    }

    private static @Nullable Support support(GameTestHelper helper, BlockPos at, Direction facing) {
        return BeltTileBlock.support(helper.getLevel(), helper.absolutePos(at), BeltTileBlock.travel(facing));
    }

    private static void none(GameTestHelper helper, BlockPos at, Direction facing) {
        if (support(helper, at, facing) != null) helper.fail("a tile held up shows a support", at);
    }

    /** Every leg of the tile at {@code at} stands this far under its floor. */
    private static void bottom(GameTestHelper helper, BlockPos at, Direction facing, double expected) {
        var support = support(helper, at, facing);
        if (support == null) {
            helper.fail("a raised tile shows no support", at);
            return;
        }
        for (var leg : support.legs()) {
            if (!leg.standing() || Math.abs(leg.bottom() - expected) > 1e-6) {
                helper.fail("a leg at " + leg.x() + ", " + leg.z() + " stops at " + leg.bottom() + ", expected " + expected, at);
            }
        }
    }
}
