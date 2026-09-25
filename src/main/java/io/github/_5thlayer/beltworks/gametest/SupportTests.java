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
import io.github._5thlayer.beltworks.blocks.BeltEndBlock;
import io.github._5thlayer.beltworks.blocks.BeltTileBlock;
import io.github._5thlayer.beltworks.blocks.SplitterBlock;
import io.github._5thlayer.beltworks.model.BeltTier;
import io.github._5thlayer.beltworks.model.Pitch;
import io.github._5thlayer.beltworks.model.Support;

/**
 * A raised tile's support as the world gives it (ADR 0012): what a leg passes and stands on, what
 * holds a tile up or fixes it, and which belt ends show one. The rule itself is
 * {@code SupportTest}'s; these hold the world's blocks to it. A tile here stands alone, or ends
 * its short line, so it is its line's first or last.
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
        tests.test("a_line_ending_on_a_top_shows_legs_past_its_wedge", 20, helper -> {
            var top = RAISED.east().above();
            tile(helper, RAISED, Direction.EAST);
            tile(helper, top, Direction.EAST);
            helper.setBlock(top.below(), BlockContent.BELT_WEDGE.get().defaultBlockState()
              .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST));
            helper.setBlock(top.below(3), Blocks.STONE);
            // The stone's top is two blocks under the top's floor.
            bottom(helper, top, Direction.EAST, -2);
            helper.succeed();
        });
        tests.test("a_loader_never_shows_a_support", 20, helper -> {
            helper.setBlock(RAISED, BeltTileTests.loader(BeltTier.BELT, Direction.EAST));
            if (endSupport(helper, RAISED) != null) helper.fail("a floating loader shows a support", RAISED);
            helper.succeed();
        });
        tests.test("a_floating_splitter_shows_one_support_from_its_left_half", 20, helper -> {
            var right = RAISED.south();
            splitter(helper, RAISED);
            // Only the right half stands over air.
            helper.setBlock(RAISED.below(), Blocks.STONE);
            var support = endSupport(helper, RAISED);
            if (support == null || support.legs().size() != 4) {
                helper.fail("a splitter with a half over air shows no support", RAISED);
            }
            if (endSupport(helper, right) != null) helper.fail("a splitter's right half shows a second support", right);
            helper.setBlock(right.below(), Blocks.STONE);
            if (endSupport(helper, RAISED) != null) helper.fail("a splitter held up under both halves shows a support", RAISED);
            helper.succeed();
        });
    }

    private static void tile(GameTestHelper helper, BlockPos at, Direction facing) {
        helper.setBlock(at, BlockContent.BELT_TILE.get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, facing));
    }

    /** A splitter facing east, its left half at {@code left} and its right half south of it. */
    private static void splitter(GameTestHelper helper, BlockPos left) {
        for (var side : SplitterBlock.Side.values()) {
            helper.setBlock(side == SplitterBlock.Side.LEFT ? left : left.south(),
              BlockContent.splitterFor(BeltTier.BELT).defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST)
                .setValue(SplitterBlock.SIDE, side));
        }
    }

    private static @Nullable Support endSupport(GameTestHelper helper, BlockPos at) {
        var pos = helper.absolutePos(at);
        var state = helper.getLevel().getBlockState(pos);
        return ((BeltEndBlock) state.getBlock()).support(helper.getLevel(), pos, state);
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
