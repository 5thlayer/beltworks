// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.gametest;


import java.util.List;
import java.util.Map;


import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import io.github._5thlayer.beltworks.DataComponentsContent;
import io.github._5thlayer.beltworks.ItemContent;
import io.github._5thlayer.beltworks.blocks.Climb;
import io.github._5thlayer.beltworks.blocks.BeltTileBlock;
import io.github._5thlayer.beltworks.model.BeltTier;

/**
 * A slope's edges (PlanetaryFactory #419, ADR 0005, ADR 0017): a slope never turns, so a corner pressed to climb is
 * refused and changes nothing, while a tile across another a
 * block up is a crossing and places level. Each tile is asked for its plan first, and the world and
 * the stack are held to it.
 */
final class BeltSlopeEdgeTests {

    private static final BlockPos FIRST = new BlockPos(3, 1, 3);
    private static final BlockPos ABOVE_AHEAD = FIRST.east().above();

    private BeltSlopeEdgeTests() {
    }

    static void register(BeltGameTests.Registrar tests) {
        tests.test("a_tile_across_a_line_a_block_up_is_placed_level", 40, helper -> {
            ServerPlayer player = BeltWedgeTests.player(helper);
            BeltWedgeTests.byHand(helper, player, FIRST, Direction.EAST, 0);
            BeltWedgeTests.byHand(helper, player, ABOVE_AHEAD, Direction.NORTH, 0);
            level(helper, FIRST);
            level(helper, ABOVE_AHEAD);
            helper.succeed();
        });
        tests.test("a_tile_ending_under_a_line_across_it_is_placed_level", 40, helper -> {
            ServerPlayer player = BeltWedgeTests.player(helper);
            BeltWedgeTests.byHand(helper, player, ABOVE_AHEAD, Direction.NORTH, 0);
            BeltWedgeTests.byHand(helper, player, FIRST, Direction.EAST, 0);
            level(helper, FIRST);
            level(helper, ABOVE_AHEAD);
            helper.succeed();
        });
        tests.test("climb_pressed_on_a_corner_is_refused", 40, BeltSlopeEdgeTests::slopingACorner);
        // Lines a block apart join only where a tile is set to climb, whatever order they are laid in (#107, ADR 0017).
        tests.test("climb_unset_turns_a_corner_laid_by_hand", 40, helper -> {
            ServerPlayer player = BeltWedgeTests.player(helper);
            BeltWedgeTests.byHand(helper, player, FIRST.south(2), Direction.NORTH, 0, 0);
            BeltWedgeTests.byHand(helper, player, FIRST, Direction.EAST, 0);
            helper.succeedWhen(() -> {
                BlockState state = helper.getBlockState(FIRST);
                if (state.getValue(BeltTileBlock.CORNER) == BeltTileBlock.Shape.STRAIGHT) helper.fail("a tile fed from its side stands " + state, FIRST);
            });
        });
        tests.test("climb_unset_leaves_a_tile_level_under_a_line_laid_before_it", 40, helper -> {
            ServerPlayer player = BeltWedgeTests.player(helper);
            BeltWedgeTests.byHand(helper, player, ABOVE_AHEAD, Direction.EAST, 0, 0);
            BeltWedgeTests.byHand(helper, player, FIRST.west(), Direction.EAST, 0, 0);
            helper.succeedWhen(() -> {
                pitched(helper, FIRST, BeltTileBlock.PitchState.LEVEL);
                pitched(helper, ABOVE_AHEAD, BeltTileBlock.PitchState.LEVEL);
            });
        });
        tests.test("climb_unset_leaves_a_line_level_under_one_laid_after_it", 40, helper -> {
            ServerPlayer player = BeltWedgeTests.player(helper);
            BeltWedgeTests.byHand(helper, player, FIRST.west(), Direction.EAST, 0, 0);
            BeltWedgeTests.byHand(helper, player, ABOVE_AHEAD, Direction.EAST, 0, 0);
            helper.succeedWhen(() -> {
                pitched(helper, FIRST, BeltTileBlock.PitchState.LEVEL);
                pitched(helper, ABOVE_AHEAD, BeltTileBlock.PitchState.LEVEL);
            });
        });
        tests.test("climb_unset_keeps_three_stacked_lines_level_laid_top_first", 40, helper -> stacked(helper, 2, 1, 0));
        tests.test("climb_unset_keeps_three_stacked_lines_level_laid_bottom_first", 40, helper -> stacked(helper, 0, 1, 2));
        tests.test("climb_unset_keeps_three_stacked_lines_level_laid_middle_first", 40, helper -> stacked(helper, 1, 2, 0));
        tests.test("climb_set_up_slopes_a_tile_to_a_line_laid_before_it", 40, helper -> {
            ServerPlayer player = BeltWedgeTests.player(helper);
            BeltWedgeTests.byHand(helper, player, ABOVE_AHEAD, Direction.EAST, 0, 0);
            BeltWedgeTests.byHand(helper, player, FIRST.west(), Direction.EAST, 0);
            ItemStack climbing = new ItemStack(ItemContent.tileFor(BeltTier.BELT));
            climbing.set(DataComponentsContent.HELD_CLIMB.get(), BeltTileBlock.ClimbState.UP);
            BeltWedgeTests.use(helper, player, climbing, FIRST);
            helper.succeedWhen(() -> {
                pitched(helper, FIRST, BeltTileBlock.PitchState.FOOT_UP);
                pitched(helper, ABOVE_AHEAD, BeltTileBlock.PitchState.TOP_UP);
            });
        });
        tests.test("climb_pressed_on_a_placed_tile_cycles_it_up_down_and_level", 40, BeltSlopeEdgeTests::pressedInPlace);
        tests.test("climb_pressed_with_a_tile_held_sets_its_next_placement", 40, helper -> {
            ServerPlayer player = BeltWedgeTests.player(helper);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ItemContent.tileFor(BeltTier.BELT), 4));
            BeltTileBlock.ClimbState[] cycle = {BeltTileBlock.ClimbState.UP, BeltTileBlock.ClimbState.DOWN, BeltTileBlock.ClimbState.LEVEL};
            for (int press = 0; press < cycle.length; press++) {
                Climb.press(player, null);
                if (Climb.held(player.getMainHandItem()) != cycle[press]) {
                    helper.fail((press + 1) + " presses from level hold " + Climb.held(player.getMainHandItem()) + ", expected " + cycle[press]);
                }
            }
            // Pressed back to level, it holds no climb, so it stacks with a tile never pressed.
            if (!ItemStack.isSameItemSameComponents(player.getMainHandItem(), new ItemStack(ItemContent.tileFor(BeltTier.BELT)))) {
                helper.fail("a tile pressed back to level still holds a climb");
            }
            helper.succeed();
        });
        // As the gallery's Slopes scene builds them, the lower tile first on the climb.
        tests.test("climb_set_in_a_fill_slopes_up_to_a_tile_filled_after_it", 40, helper -> filled(helper, List.of(
                "fill ~1 ~ ~ ~3 ~ ~ beltworks:belt_tile[facing=east]", "setblock ~4 ~ ~ beltworks:belt_tile[facing=east,climb=up]",
                "fill ~5 ~1 ~ ~9 ~1 ~ beltworks:belt_tile[facing=east]"),
                new BlockPos(4, 1, 3), BeltTileBlock.PitchState.FOOT_UP, BeltTileBlock.PitchState.TOP_UP));
        tests.test("climb_set_in_a_fill_slopes_down_to_a_tile_filled_after_it", 40, helper -> filled(helper, List.of(
                "fill ~1 ~1 ~ ~3 ~1 ~ beltworks:belt_tile[facing=east]", "setblock ~4 ~1 ~ beltworks:belt_tile[facing=east,climb=down]",
                "fill ~5 ~ ~ ~9 ~ ~ beltworks:belt_tile[facing=east]"),
                new BlockPos(4, 2, 3), BeltTileBlock.PitchState.TOP_DOWN, BeltTileBlock.PitchState.FOOT_DOWN));
        tests.test("climb_set_in_a_fill_slopes_over_a_line_as_the_gallery_builds_it", 40, helper -> filled(helper, List.of(
                "setblock ~7 ~ ~-4 beltworks:loader[facing=south]", "fill ~7 ~ ~-3 ~7 ~ ~3 beltworks:belt_tile[facing=south]",
                "setblock ~7 ~ ~4 beltworks:loader[facing=north]", "setblock ~0 ~ ~ beltworks:loader[facing=east]",
                "fill ~1 ~ ~ ~3 ~ ~ beltworks:belt_tile[facing=east]", "setblock ~4 ~ ~ beltworks:belt_tile[facing=east,climb=up]",
                "fill ~5 ~1 ~ ~9 ~1 ~ beltworks:belt_tile[facing=east]",
                "fill ~10 ~ ~ ~14 ~ ~ beltworks:belt_tile[facing=east]", "setblock ~15 ~ ~ beltworks:loader[facing=west]"),
                new BlockPos(4, 1, 3), BeltTileBlock.PitchState.FOOT_UP, BeltTileBlock.PitchState.TOP_UP));
    }

    private static void slopingACorner(GameTestHelper helper) {
        ServerPlayer player = BeltWedgeTests.player(helper);
        BeltWedgeTests.byHand(helper, player, FIRST.north(), Direction.SOUTH, 0);
        BeltWedgeTests.byHand(helper, player, FIRST, Direction.EAST, 0);
        if (helper.getBlockState(FIRST).getValue(BeltTileBlock.CORNER) == BeltTileBlock.Shape.STRAIGHT) {
            helper.fail("a tile fed only from its side is straight, so this proves little", FIRST);
        }
        // Set to climb, a tile is a slope at once, into the open, so a corner is refused it and changes nothing.
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.snapTo(Vec3.atCenterOf(helper.absolutePos(FIRST.west())));
        Map<BlockPos, BlockState> before = BeltWedgeTests.around(helper, FIRST);
        Climb.press(player, helper.absolutePos(FIRST));
        if (!BeltWedgeTests.around(helper, FIRST).equals(before)) helper.fail("a corner pressed to climb changed the world", FIRST);
        helper.succeed();
    }

    /**
     * Runs {@code commands} from the structure's x 0, y 1, z 3, and expects the tile at {@code foot}
     * and the tile it feeds, east of it and a block up or down, to take {@code footPitch} and {@code nextPitch}.
     */
    private static void filled(GameTestHelper helper, List<String> commands, BlockPos foot, BeltTileBlock.PitchState footPitch,
                               BeltTileBlock.PitchState nextPitch) {
        ServerLevel level = helper.getLevel();
        CommandSourceStack source = level.getServer().createCommandSourceStack().withLevel(level)
                .withPosition(Vec3.atLowerCornerOf(helper.absolutePos(new BlockPos(0, 1, 3))));
        for (String command : commands) level.getServer().getCommands().performPrefixedCommand(source, command);
        BlockPos next = foot.east().above(footPitch == BeltTileBlock.PitchState.FOOT_UP ? 1 : -1);
        helper.succeedWhen(() -> {
            pitched(helper, foot, footPitch);
            pitched(helper, next, nextPitch);
        });
    }

    // Three lines a block apart, one over another, laid by hand in the order of their heights given.
    private static void stacked(GameTestHelper helper, int... order) {
        ServerPlayer player = BeltWedgeTests.player(helper);
        for (int height : order) BeltWedgeTests.byHand(helper, player, FIRST.west().above(height), Direction.EAST, 0, 0, 0);
        helper.succeedWhen(() -> {
            for (int height = 0; height < 3; height++) {
                for (int along = -1; along < 2; along++) pitched(helper, FIRST.east(along).above(height), BeltTileBlock.PitchState.LEVEL);
            }
        });
    }

    // Up, down and level again, each press in place reshaping the tile ahead with it.
    private static void pressedInPlace(GameTestHelper helper) {
        ServerPlayer player = BeltWedgeTests.player(helper);
        BeltWedgeTests.byHand(helper, player, ABOVE_AHEAD, Direction.EAST, 0);
        BeltWedgeTests.byHand(helper, player, FIRST, Direction.EAST, 0);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.snapTo(Vec3.atCenterOf(helper.absolutePos(FIRST.west())));
        BlockPos aimed = helper.absolutePos(FIRST);
        Climb.press(player, aimed);
        helper.startSequence()
                .thenExecute(() -> climbs(helper, BeltTileBlock.ClimbState.UP))
                .thenWaitUntil(() -> {
                    pitched(helper, FIRST, BeltTileBlock.PitchState.FOOT_UP);
                    pitched(helper, ABOVE_AHEAD, BeltTileBlock.PitchState.TOP_UP);
                })
                .thenExecute(() -> Climb.press(player, aimed))
                .thenExecute(() -> climbs(helper, BeltTileBlock.ClimbState.DOWN))
                .thenWaitUntil(() -> {
                    pitched(helper, FIRST, BeltTileBlock.PitchState.TOP_DOWN);
                    pitched(helper, ABOVE_AHEAD, BeltTileBlock.PitchState.LEVEL);
                })
                .thenExecute(() -> Climb.press(player, aimed))
                .thenExecute(() -> climbs(helper, BeltTileBlock.ClimbState.LEVEL))
                .thenSucceed();
    }

    private static void climbs(GameTestHelper helper, BeltTileBlock.ClimbState expected) {
        BlockState state = helper.getBlockState(FIRST);
        if (state.getValue(BeltTileBlock.CLIMB) != expected) helper.fail("a pressed tile climbs " + state.getValue(BeltTileBlock.CLIMB) + ", expected " + expected, FIRST);
    }

    private static void pitched(GameTestHelper helper, BlockPos tile, BeltTileBlock.PitchState expected) {
        BlockState state = helper.getBlockState(tile);
        if (!(state.getBlock() instanceof BeltTileBlock) || state.getValue(BeltTileBlock.PITCH) != expected) {
            helper.fail("a built tile stands " + state + ", expected " + expected, tile);
        }
    }

    private static void level(GameTestHelper helper, BlockPos tile) {
        BlockState state = helper.getBlockState(tile);
        if (state.getValue(BeltTileBlock.PITCH) != BeltTileBlock.PitchState.LEVEL
                || state.getValue(BeltTileBlock.CORNER) != BeltTileBlock.Shape.STRAIGHT) {
            helper.fail("a tile crossing another a block up stands " + state + ", expected level and straight", tile);
        }
    }
}
