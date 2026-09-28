// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.gametest;

import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import io.github._5thlayer.groundworks.PlacementPlan;
import io.github._5thlayer.groundworks.QuarterTurn;
import io.github._5thlayer.groundworks.Rotate;

import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import io.github._5thlayer.beltworks.Beltworks;
import io.github._5thlayer.beltworks.BlockContent;
import io.github._5thlayer.beltworks.ItemContent;
import io.github._5thlayer.beltworks.blocks.BeltTileBlock;
import io.github._5thlayer.beltworks.blocks.SplitterBlock;
import io.github._5thlayer.beltworks.items.BeltRefusal;
import io.github._5thlayer.beltworks.model.BeltTier;

/**
 * Rotate on the Mod's own pieces, with nothing but Groundworks to press it (the library's ADR 0003).
 * A held tile Rotates the Plan through the look Groundworks turns, and a placed piece Rotates in
 * Place by its own answer: a level tile, a loader and a feeder turn, a splitter half, a slope and a wedge
 * refuse and say why, and a level tile refuses where placing it so would be refused. Every press
 * goes through {@link Rotate#press}, which is what the key's payload calls.
 */
final class RotateTests {

    /** Where the player stands, clear of every piece a test places and within reach of each. */
    private static final BlockPos STAND = new BlockPos(5, 1, 1);
    private static final BlockPos AIMED = new BlockPos(4, 1, 4);
    private static final BlockPos FIRST = new BlockPos(3, 1, 3);
    private static final Direction LOOK = Direction.EAST;

    private RotateTests() {
    }

    static void register(BeltGameTests.Registrar tests) {
        for (boolean reverse : List.of(false, true)) {
            String rotate = reverse ? "reverse_rotate" : "rotate";
            tests.test(rotate + "_the_plan_places_a_tile_turned_as_planned", 20, helper -> placesTurned(helper, reverse));
        }
        tests.test("rotate_in_place_turns_a_level_tile_a_quarter_each_press_both_ways", 20,
                helper -> turnsEachPress(helper, BeltTileTests.tile(BeltTier.BELT, LOOK)));
        tests.test("rotate_in_place_turns_a_loader_a_quarter_each_press_both_ways", 20,
                helper -> turnsEachPress(helper, BeltTileTests.loader(BeltTier.BELT, LOOK)));
        tests.test("rotate_in_place_turns_a_feeder_a_quarter_each_press_both_ways", 20,
                helper -> turnsEachPress(helper, BlockContent.FEEDER_BLOCK.get().defaultBlockState()
                        .setValue(HorizontalDirectionalBlock.FACING, LOOK)));
        tests.test("rotate_in_place_refuses_a_splitter_half", 20, RotateTests::splitterRefuses);
        tests.test("rotate_in_place_refuses_a_foot_a_middle_a_top_and_a_wedge", 40, RotateTests::slopeRefuses);
        tests.test("rotate_in_place_refuses_a_level_tile_whose_turn_would_slope_a_corner", 20, RotateTests::wouldSlopeACorner);
        tests.test("rotate_in_place_refuses_a_level_tile_whose_wedge_would_stand_on_a_loader", 20, RotateTests::wedgeOnALoader);
        // The Mod's statement covers only its own blocks. A pack may state every block, and then a
        // press turns the jack o'lantern by the pack's choice, so the press is checked only where no
        // statement covers it: standalone, the library's own tests state some of vanilla's, not this one.
        tests.test("rotate_in_place_leaves_another_mods_block_alone", 20, helper -> {
            if (Beltworks.OURS.test(Blocks.JACK_O_LANTERN)) helper.fail("the Mod's statement covers the jack o'lantern", AIMED);
            if (!Rotate.isTurnedInPlace(Blocks.JACK_O_LANTERN)) {
                helper.setBlock(AIMED, Blocks.JACK_O_LANTERN.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, LOOK));
                refused(helper, emptyHanded(helper), AIMED, AIMED, List.of());
            }
            helper.succeed();
        });
    }

    /** For 0 to 3 presses, a tile places facing the look turned that many quarters, as its plan said. */
    private static void placesTurned(GameTestHelper helper, boolean reverse) {
        var player = new ListeningPlayer(helper, STAND);
        player.setYRot(LOOK.toYRot());
        for (int presses = 0; presses < 4; presses++) {
            // Two apart, so no tile feeds another.
            var at = new BlockPos(1 + 2 * presses, 1, 4);
            var stack = pressed(player, presses, reverse);
            PlacementPlan plan = BeltWedgeTests.planOf(helper, player, stack, at);
            BeltWedgeTests.use(helper, player, stack, at);
            var expected = turned(presses, reverse);
            var placed = helper.getBlockState(at);
            if (!placed.is(BlockContent.BELT_TILE.get()) || placed.getValue(BlockStateProperties.HORIZONTAL_FACING) != expected) {
                helper.fail(presses + " presses placed " + placed + ", expected a tile facing " + expected, at);
            }
            if (plan == null || plan.isRefused() || !plan.blocks().equals(List.of(new PlacementPlan.Placed(helper.absolutePos(at), placed)))) {
                helper.fail(presses + " presses planned " + plan + ", and the click placed " + placed, at);
            }
        }
        helper.succeed();
    }

    /** Each press turns the piece a quarter, clockwise or back, keeping its block entity, and says nothing. */
    private static void turnsEachPress(GameTestHelper helper, BlockState start) {
        helper.setBlock(AIMED, start);
        BlockEntity entity = helper.getBlockEntity(AIMED, BlockEntity.class);
        var player = emptyHanded(helper);
        for (boolean reverse : List.of(false, true)) {
            for (int press = 1; press <= 4; press++) {
                Rotate.press(player, helper.absolutePos(AIMED), reverse);
                var expected = start.setValue(BlockStateProperties.HORIZONTAL_FACING, turned(press, reverse));
                if (!helper.getBlockState(AIMED).equals(expected)) {
                    helper.fail((reverse ? "Reverse Rotate" : "Rotate") + " press " + press + " left "
                            + helper.getBlockState(AIMED) + ", expected " + expected, AIMED);
                }
            }
        }
        if (helper.getBlockEntity(AIMED, BlockEntity.class) != entity) helper.fail("a turn replaced the block entity", AIMED);
        if (!player.heard.isEmpty()) helper.fail("a turn that went through said " + player.heard, AIMED);
        helper.succeed();
    }

    private static void splitterRefuses(GameTestHelper helper) {
        var left = BlockContent.splitterFor(BeltTier.BELT).defaultBlockState()
                .setValue(SplitterBlock.FACING, LOOK).setValue(SplitterBlock.SIDE, SplitterBlock.Side.LEFT);
        var right = SplitterBlock.partner(AIMED, left);
        helper.setBlock(AIMED, left);
        helper.setBlock(right, left.setValue(SplitterBlock.SIDE, SplitterBlock.Side.RIGHT));
        var player = emptyHanded(helper);
        for (var half : List.of(AIMED, right)) refused(helper, player, half, AIMED, List.of(SplitterBlock.NOT_TURNED));
        helper.succeed();
    }

    /** Up three blocks through the air east of {@link #FIRST}: a foot, two middles and a top, a wedge under each but the foot. */
    private static void slopeRefuses(GameTestHelper helper) {
        var player = emptyHanded(helper);
        var tiles = BeltWedgeTests.byHand(helper, player, FIRST, LOOK, 0, 0, 1, 2, 3, 3);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        var foot = tiles.get(1);
        var middle = tiles.get(2);
        var top = tiles.get(4);
        pitched(helper, foot, BeltTileBlock.PitchState.FOOT_UP);
        pitched(helper, middle, BeltTileBlock.PitchState.MIDDLE_UP);
        pitched(helper, top, BeltTileBlock.PitchState.TOP_UP);
        for (var aimed : List.of(foot, middle, top, top.below())) {
            refused(helper, player, aimed, middle, List.of(BeltTileBlock.SLOPE_NOT_TURNED));
        }
        helper.succeed();
    }

    /**
     * A corner fed from its left, and a level tile a block up ahead of it, facing away. Turned to
     * run on from the corner, the tile would make the corner a foot, and a slope never turns.
     */
    private static void wouldSlopeACorner(GameTestHelper helper) {
        var player = emptyHanded(helper);
        var corner = AIMED;
        var tile = corner.east().above();
        helper.setBlock(corner.east(), Blocks.STONE);
        placeTile(helper, player, corner.north(), Direction.SOUTH);
        placeTile(helper, player, corner, LOOK);
        placeTile(helper, player, tile, Direction.NORTH);
        if (helper.getBlockState(corner).getValue(BeltTileBlock.CORNER) == BeltTileBlock.Shape.STRAIGHT) {
            helper.fail("the tile fed from its side is " + helper.getBlockState(corner) + ", expected a corner", corner);
        }
        refused(helper, player, tile, corner, List.of(BeltRefusal.SLOPE_TURNS.messageKey()));
        helper.succeed();
    }

    /** A level tile standing on a loader, facing away from a tile a block down behind it. Turned to run on from that tile, it would be a top with its wedge on the loader. */
    private static void wedgeOnALoader(GameTestHelper helper) {
        var player = emptyHanded(helper);
        var tile = AIMED.east().above();
        helper.setBlock(AIMED.east(), BeltTileTests.loader(BeltTier.BELT, Direction.NORTH));
        placeTile(helper, player, AIMED, LOOK);
        placeTile(helper, player, tile, Direction.NORTH);
        refused(helper, player, tile, AIMED, List.of(BeltRefusal.WEDGE_BLOCKED.messageKey()));
        helper.succeed();
    }

    /** A press on {@code aimed} changes nothing near {@code around} and tells the player {@code told}. */
    private static void refused(GameTestHelper helper, ListeningPlayer player, BlockPos aimed, BlockPos around, List<String> told) {
        Map<BlockPos, BlockState> before = BeltWedgeTests.around(helper, around);
        player.heard.clear();
        Rotate.press(player, helper.absolutePos(aimed), false);
        if (!BeltWedgeTests.around(helper, around).equals(before)) {
            helper.fail("a refused press on " + helper.getBlockState(aimed) + " changed the world", aimed);
        }
        if (!player.heard.equals(told)) helper.fail("a press on " + helper.getBlockState(aimed) + " said " + player.heard + ", not " + told, aimed);
    }

    /** A tile placed by hand at {@code at}, looking {@code look}. */
    private static void placeTile(GameTestHelper helper, ListeningPlayer player, BlockPos at, Direction look) {
        player.setYRot(look.toYRot());
        BeltWedgeTests.use(helper, player, new ItemStack(ItemContent.tileFor(BeltTier.BELT)), at);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    }

    /** A player holding nothing, so a press is Rotate in Place. */
    private static ListeningPlayer emptyHanded(GameTestHelper helper) {
        return new ListeningPlayer(helper, STAND);
    }

    /** Two tiles in the main hand, pressed {@code presses} times. */
    private static ItemStack pressed(ListeningPlayer player, int presses, boolean reverse) {
        var stack = new ItemStack(ItemContent.tileFor(BeltTier.BELT), 2);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        for (int press = 0; press < presses; press++) Rotate.press(player, null, reverse);
        return stack;
    }

    private static Direction turned(int presses, boolean reverse) {
        return QuarterTurn.of(reverse ? -presses : presses).turn(LOOK);
    }

    private static void pitched(GameTestHelper helper, BlockPos pos, BeltTileBlock.PitchState pitch) {
        var state = helper.getBlockState(pos);
        if (!(state.getBlock() instanceof BeltTileBlock) || state.getValue(BeltTileBlock.PITCH) != pitch) {
            helper.fail("the climb has " + state + " here, expected a tile " + pitch, pos);
        }
    }
}
