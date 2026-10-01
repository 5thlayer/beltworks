// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.gametest;

import io.github._5thlayer.groundworks.PlacementPlan;
import io.github._5thlayer.groundworks.Placements;
import io.github._5thlayer.groundworks.Refusal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import io.github._5thlayer.beltworks.BlockContent;
import io.github._5thlayer.beltworks.DataComponentsContent;
import io.github._5thlayer.beltworks.blocks.BeltEndBlockEntity;
import io.github._5thlayer.beltworks.blocks.BeltTileBlock;
import io.github._5thlayer.beltworks.blocks.BeltTileBlockEntity;
import io.github._5thlayer.beltworks.blocks.FeederBlock;
import io.github._5thlayer.beltworks.blocks.FeederBlockEntity;
import io.github._5thlayer.beltworks.blocks.FeederReach;
import io.github._5thlayer.beltworks.blocks.SplitterBlock;
import io.github._5thlayer.beltworks.model.BeltTier;
import io.github._5thlayer.beltworks.model.FeederArms;
import io.github._5thlayer.beltworks.model.Splitter;
import io.github._5thlayer.beltworks.model.TransportLine;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A Fast Replace of the Mod's blocks (#94, Groundworks ADR 0008): a plain click with a tile,
 * splitter, loader or feeder on a placed one of another tier makes a tier swap, for one item charged
 * and one handed back. Every click goes through the game, so through the event Groundworks answers.
 */
final class FastReplaceTests {

    private static final BlockPos AIMED = new BlockPos(4, 1, 3);
    private static final BlockPos LEFT = new BlockPos(4, 1, 2);
    private static final BlockPos RIGHT = LEFT.relative(Direction.EAST.getClockWise());
    private static final BlockPos STANDING = new BlockPos(1, 1, 3);
    private static final BlockPos SLOPE_FIRST = new BlockPos(2, 1, 3);
    private static final int[] SLOPE = {0, 0, 1, 2, 3, 3};
    private static final int UPKEEP_TICKS = 3;
    private static final String NO_ROOM = "message.groundworks.fast_replace_no_room_to_return";

    private FastReplaceTests() {
    }

    static void register(BeltGameTests.Registrar tests) {
        tests.test("a_tile_of_another_tier_replaces_a_tile_and_keeps_its_items", 20, FastReplaceTests::replacesATile);
        tests.test("a_tile_replace_keeps_a_corners_shape", 20, FastReplaceTests::keepsACorner);
        tests.test("a_tile_replace_keeps_a_slopes_foot_pitch_and_wedges", 40, helper -> keepsASlope(helper, 1));
        tests.test("a_tile_replace_keeps_a_slopes_middle_pitch_and_wedges", 40, helper -> keepsASlope(helper, 2));
        tests.test("a_tile_replace_keeps_a_slopes_top_pitch_and_wedges", 40, helper -> keepsASlope(helper, 4));
        tests.test("a_splitter_of_another_tier_replaces_a_splitter_at_its_left_half", 20, helper -> replacesASplitter(helper, LEFT));
        tests.test("a_splitter_of_another_tier_replaces_a_splitter_at_its_right_half", 20, helper -> replacesASplitter(helper, RIGHT));
        tests.test("a_splitter_replace_with_an_entity_on_the_partner_is_refused", 20, FastReplaceTests::entityOnThePartner);
        tests.test("a_loader_of_another_tier_replaces_a_loader_and_keeps_its_filter_and_facing", 20, FastReplaceTests::replacesALoader);
        tests.test("a_feeder_replace_keeps_the_old_feeders_reach_and_the_held_stack_its_own", 20, FastReplaceTests::keepsAFeedersReach);
        tests.test("a_feeder_replace_keeps_the_old_feeders_tail_turn", 20, FastReplaceTests::keepsAFeedersTailTurn);
        tests.test("a_sneak_click_with_another_tier_on_a_tile_replaces_nothing", 20,
                helper -> sneakReplacesNothing(helper, tile(BeltTier.BELT), BlockContent.tileFor(BeltTier.IMPROVED), false));
        tests.test("a_sneak_click_with_another_tier_on_a_splitter_places_beside", 20,
                helper -> sneakReplacesNothing(helper, half(BeltTier.BELT, SplitterBlock.Side.LEFT), BlockContent.splitterFor(BeltTier.IMPROVED), true));
        tests.test("a_sneak_click_with_another_tier_on_a_loader_places_beside", 20,
                helper -> sneakReplacesNothing(helper, facing(BlockContent.loaderFor(BeltTier.IMPROVED)), BlockContent.loaderFor(BeltTier.EXPRESS), true));
        tests.test("a_sneak_click_with_another_tier_on_a_feeder_places_beside", 20,
                helper -> sneakReplacesNothing(helper, facing(BlockContent.feederFor(BeltTier.BELT)), BlockContent.feederFor(BeltTier.IMPROVED), true));
        tests.test("a_replace_with_no_room_for_the_handed_back_block_is_refused_and_changes_nothing", 20, FastReplaceTests::noRoom);
        tests.test("a_click_on_a_tile_of_another_tier_with_a_stretch_start_stored_lays_the_stretch", 20, FastReplaceTests::stretchWins);
        tests.test("a_splitter_over_a_tile_line_names_the_tiles_it_takes_as_replaced", 20, FastReplaceTests::splitterOverTiles);
        tests.test("a_sneak_click_with_a_splitter_on_a_tile_line_places_beside", 20, FastReplaceTests::splitterBesideTiles);
        tests.test("a_splitter_over_a_tile_line_with_no_room_for_the_tiles_is_refused_and_changes_nothing", 20,
                FastReplaceTests::splitterOverTilesNoRoom);
        tests.test("a_tile_of_the_held_tier_is_not_replaced", 20,
                helper -> sameTierPasses(helper, tile(BeltTier.BELT), BlockContent.tileFor(BeltTier.BELT)));
        tests.test("a_splitter_of_the_held_tier_is_not_replaced", 20,
                helper -> sameTierPasses(helper, half(BeltTier.BELT, SplitterBlock.Side.LEFT), BlockContent.splitterFor(BeltTier.BELT)));
        tests.test("a_feeder_of_the_held_tier_is_not_replaced", 20,
                helper -> sameTierPasses(helper, facing(BlockContent.feederFor(BeltTier.BELT)), BlockContent.feederFor(BeltTier.BELT)));
    }

    private static void replacesATile(GameTestHelper helper) {
        helper.setBlock(AIMED, tile(BeltTier.BELT));
        var shares = List.of(new TransportLine.Share<>(0.2, new ItemStack(Items.IRON_INGOT)),
          new TransportLine.Share<>(0.7, new ItemStack(Items.GOLD_INGOT, 2)));
        helper.getBlockEntity(AIMED, BeltTileBlockEntity.class).carry(shares);
        var player = holding(helper, BlockContent.tileFor(BeltTier.IMPROVED), 2);

        click(helper, player, AIMED, false);

        expect(helper, AIMED, tile(BeltTier.IMPROVED));
        var held = helper.getBlockEntity(AIMED, BeltTileBlockEntity.class).held().stream()
          .map(share -> share.payload().getCount() + " " + share.payload().getItem() + " at " + share.offset()).toList();
        var expected = shares.stream().map(share -> share.payload().getCount() + " " + share.payload().getItem() + " at " + share.offset()).toList();
        if (!held.equals(expected)) helper.fail("the tier-2 tile holds " + held + ", not " + expected, AIMED);
        chargedAndHandedBack(helper, player, BlockContent.tileFor(BeltTier.IMPROVED), BlockContent.tileFor(BeltTier.BELT));
        helper.succeed();
    }

    // A tile east of AIMED's west neighbour turns south at AIMED.
    private static void keepsACorner(GameTestHelper helper) {
        var placer = BeltWedgeTests.player(helper);
        BeltWedgeTests.byHand(helper, placer, AIMED.west(), Direction.EAST, 0);
        BeltWedgeTests.byHand(helper, placer, AIMED, Direction.SOUTH, 0, 0);
        if (helper.getBlockState(AIMED).getValue(BeltTileBlock.CORNER) == BeltTileBlock.Shape.STRAIGHT) {
            helper.fail("the corner was laid straight", AIMED);
        }
        keepsItsState(helper, AIMED);
    }

    private static void keepsASlope(GameTestHelper helper, int tile) {
        var tiles = BeltWedgeTests.byHand(helper, BeltWedgeTests.player(helper), SLOPE_FIRST, Direction.EAST, SLOPE);
        var aimed = tiles.get(tile);
        if (helper.getBlockState(aimed).getValue(BeltTileBlock.PITCH) == BeltTileBlock.PitchState.LEVEL) {
            helper.fail("slope tile " + tile + " was laid level", aimed);
        }
        keepsItsState(helper, aimed);
    }

    // Replaced, the tile is the held tier's with its old state, and nothing around it, wedges included, changes.
    private static void keepsItsState(GameTestHelper helper, BlockPos aimed) {
        var before = BeltWedgeTests.around(helper, aimed);
        var old = helper.getBlockState(aimed);
        var player = holding(helper, BlockContent.tileFor(BeltTier.IMPROVED), 2);
        click(helper, player, aimed, false);
        helper.runAfterDelay(UPKEEP_TICKS, () -> {
            var after = BeltWedgeTests.around(helper, aimed);
            before.put(aimed, BlockContent.tileFor(BeltTier.IMPROVED).withPropertiesOf(old));
            before.forEach((pos, state) -> {
                if (!after.get(pos).equals(state)) helper.fail("after the replace " + after.get(pos) + " stands where " + state + " was expected", pos);
            });
            chargedAndHandedBack(helper, player, BlockContent.tileFor(BeltTier.IMPROVED), BlockContent.tileFor(BeltTier.BELT));
            helper.succeed();
        });
    }

    private static void replacesASplitter(GameTestHelper helper, BlockPos aimed) {
        placeSplitter(helper);
        var left = helper.getBlockEntity(LEFT, BeltEndBlockEntity.class);
        var right = helper.getBlockEntity(RIGHT, BeltEndBlockEntity.class);
        left.getHalf().entering().restore(new ItemStack(Items.IRON_INGOT), 0.1);
        right.getHalf().leaving().restore(new ItemStack(Items.GOLD_INGOT, 3), 0.3);
        left.setOutputPriority(Splitter.Priority.RIGHT);
        right.setInputPriority(Splitter.Priority.LEFT);
        left.setSplitterFilter(new ItemStack(Items.COBBLESTONE), Splitter.Priority.RIGHT);
        var settings = left.splitterSettings();
        var player = holding(helper, BlockContent.splitterFor(BeltTier.IMPROVED), 2);

        var plan = planOf(helper, player, aimed);
        if (plan == null || plan.isRefused() || plan.replaces().size() != 2) {
            helper.fail("the replace was planned as " + plan + ", not both halves", aimed);
        }
        click(helper, player, aimed, false);

        expect(helper, LEFT, BlockContent.splitterFor(BeltTier.IMPROVED).withPropertiesOf(half(BeltTier.BELT, SplitterBlock.Side.LEFT)));
        expect(helper, RIGHT, BlockContent.splitterFor(BeltTier.IMPROVED).withPropertiesOf(half(BeltTier.BELT, SplitterBlock.Side.RIGHT)));
        left = helper.getBlockEntity(LEFT, BeltEndBlockEntity.class);
        right = helper.getBlockEntity(RIGHT, BeltEndBlockEntity.class);
        if (left.getHalf().entering().size() != 1 || right.getHalf().leaving().size() != 1) {
            helper.fail("the replaced splitter holds " + left.getHalf().payloads() + " and " + right.getHalf().payloads(), aimed);
        }
        if (!left.splitterSettings().inputPriority().equals(settings.inputPriority())
              || !left.splitterSettings().outputPriority().equals(settings.outputPriority())
              || !ItemStack.matches(left.splitterSettings().filter(), settings.filter())) {
            helper.fail("the replaced splitter reads " + left.splitterSettings() + ", not " + settings, aimed);
        }
        chargedAndHandedBack(helper, player, BlockContent.splitterFor(BeltTier.IMPROVED), BlockContent.splitterFor(BeltTier.BELT));
        helper.succeed();
    }

    private static void entityOnThePartner(GameTestHelper helper) {
        placeSplitter(helper);
        helper.spawn(EntityType.ARMOR_STAND, RIGHT);
        var player = holding(helper, BlockContent.splitterFor(BeltTier.IMPROVED), 2);
        refusedClick(helper, player, LEFT, "message.beltworks.stretch_blocked");
        helper.succeed();
    }

    private static void replacesALoader(GameTestHelper helper) {
        helper.setBlock(AIMED, facing(BlockContent.loaderFor(BeltTier.IMPROVED)));
        var player = holding(helper, BlockContent.loaderFor(BeltTier.EXPRESS), 2);
        helper.getBlockEntity(AIMED, BeltEndBlockEntity.class).assignFilterItem(new ItemStack(Items.IRON_INGOT), player);

        click(helper, player, AIMED, false);

        expect(helper, AIMED, facing(BlockContent.loaderFor(BeltTier.EXPRESS)));
        var filter = helper.getBlockEntity(AIMED, BeltEndBlockEntity.class).filteredItem();
        if (!filter.is(Items.IRON_INGOT)) helper.fail("the tier-3 loader filters " + filter, AIMED);
        chargedAndHandedBack(helper, player, BlockContent.loaderFor(BeltTier.EXPRESS), BlockContent.loaderFor(BeltTier.IMPROVED));
        helper.succeed();
    }

    private static void keepsAFeedersReach(GameTestHelper helper) {
        helper.setBlock(AIMED, facing(BlockContent.feederFor(BeltTier.BELT)));
        var kept = new FeederArms(3, 2, FeederArms.Turn.STRAIGHT);
        helper.getBlockEntity(AIMED, FeederBlockEntity.class).setArms(kept);
        var player = holding(helper, BlockContent.feederFor(BeltTier.IMPROVED), 2);
        var heldReach = new FeederArms(2, 3, FeederArms.Turn.STRAIGHT);
        player.getMainHandItem().set(DataComponentsContent.HELD_REACH.get(), heldReach);

        click(helper, player, AIMED, false);

        expect(helper, AIMED, facing(BlockContent.feederFor(BeltTier.IMPROVED)));
        var arms = helper.getBlockEntity(AIMED, FeederBlockEntity.class).arms();
        if (!arms.equals(kept)) helper.fail("the replaced feeder reaches " + arms + ", not its own " + kept, AIMED);
        if (!FeederReach.held(player.getMainHandItem()).equals(heldReach)) {
            helper.fail("the held feeders reach " + FeederReach.held(player.getMainHandItem()) + ", not " + heldReach, AIMED);
        }
        chargedAndHandedBack(helper, player, BlockContent.feederFor(BeltTier.IMPROVED), BlockContent.feederFor(BeltTier.BELT));
        helper.succeed();
    }

    private static void keepsAFeedersTailTurn(GameTestHelper helper) {
        var old = facing(BlockContent.feederFor(BeltTier.BELT)).setValue(FeederBlock.TAIL_TURN, FeederBlock.TailTurn.LEFT);
        helper.setBlock(AIMED, old);
        var player = holding(helper, BlockContent.feederFor(BeltTier.IMPROVED), 2);
        click(helper, player, AIMED, false);
        expect(helper, AIMED, BlockContent.feederFor(BeltTier.IMPROVED).withPropertiesOf(old));
        helper.succeed();
    }

    // A sneak-click places beside, as vanilla's does, except a tile's, which stores a stretch's start.
    private static void sneakReplacesNothing(GameTestHelper helper, BlockState old, Block held, boolean placesAbove) {
        helper.setBlock(AIMED, old);
        var player = holding(helper, held, 2);
        click(helper, player, AIMED, true);
        expect(helper, AIMED, old);
        if (count(player, old.getBlock().asItem()) != 0) helper.fail("a sneak-click handed back " + old.getBlock().asItem(), AIMED);
        if (placesAbove && !helper.getBlockState(AIMED.above()).is(held)) {
            helper.fail("a sneak-click left " + helper.getBlockState(AIMED.above()) + " beside, not " + held, AIMED.above());
        }
        helper.succeed();
    }

    private static void noRoom(GameTestHelper helper) {
        helper.setBlock(AIMED, tile(BeltTier.BELT));
        var player = holding(helper, BlockContent.tileFor(BeltTier.IMPROVED), 2);
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getNonEquipmentItems().size(); slot++) {
            if (slot != inventory.getSelectedSlot()) inventory.setItem(slot, new ItemStack(Items.DIRT, 64));
        }
        refusedClick(helper, player, AIMED, NO_ROOM);
        helper.succeed();
    }

    // The aimed tier-2 tile is the stretch's end, which a tier-1 stretch lays over, never replacing it alone.
    private static void stretchWins(GameTestHelper helper) {
        var start = new BlockPos(2, 1, 3);
        var end = start.east(3);
        helper.setBlock(end, tile(BeltTier.IMPROVED));
        var player = StretchTests.started(helper, Direction.EAST, start);
        player.gameMode.useItemOn(player, helper.getLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND, onTop(helper, end));
        for (int i = 0; i < 3; i++) {
            if (!helper.getBlockState(start.east(i)).is(BlockContent.tileFor(BeltTier.BELT))) {
                helper.fail("the stretch laid " + helper.getBlockState(start.east(i)) + " at its tile " + i, start.east(i));
            }
        }
        helper.succeed();
    }

    private static void sameTierPasses(GameTestHelper helper, BlockState old, Block held) {
        helper.setBlock(AIMED, old);
        var player = holding(helper, held, 2);
        var plan = planOf(helper, player, AIMED);
        if (plan != null && !plan.replaces().isEmpty()) helper.fail("a block of the held tier was planned replaced", AIMED);
        click(helper, player, AIMED, false);
        expect(helper, AIMED, old);
        if (count(player, held.asItem()) > 2) helper.fail("a click on a block of the held tier handed one back", AIMED);
        helper.succeed();
    }

    // A tile under each half, running east as the splitter faces: each is taken and handed back.
    private static void splitterOverTiles(GameTestHelper helper) {
        tilesUnderTheHalves(helper);
        var player = holding(helper, BlockContent.splitterFor(BeltTier.BELT), 2);
        var plan = planOf(helper, player, LEFT);
        var both = List.of(helper.absolutePos(LEFT), helper.absolutePos(RIGHT));
        if (plan == null || plan.isRefused() || !plan.replaces().equals(both)) {
            helper.fail("the splitter over the tiles was planned " + plan + ", not replacing both", LEFT);
        }
        click(helper, player, LEFT, false);
        expect(helper, LEFT, half(BeltTier.BELT, SplitterBlock.Side.LEFT));
        expect(helper, RIGHT, half(BeltTier.BELT, SplitterBlock.Side.RIGHT));
        if (count(player, BlockContent.splitterFor(BeltTier.BELT).asItem()) != 1 || count(player, BlockContent.tileFor(BeltTier.BELT).asItem()) != 2) {
            helper.fail("the player holds " + inventory(player) + ", not one splitter and the two tiles", LEFT);
        }
        helper.succeed();
    }

    private static void splitterBesideTiles(GameTestHelper helper) {
        tilesUnderTheHalves(helper);
        var player = holding(helper, BlockContent.splitterFor(BeltTier.BELT), 2);
        click(helper, player, LEFT, true);
        expect(helper, LEFT, tile(BeltTier.BELT));
        expect(helper, RIGHT, tile(BeltTier.BELT));
        expect(helper, LEFT.above(), half(BeltTier.BELT, SplitterBlock.Side.LEFT));
        if (count(player, BlockContent.tileFor(BeltTier.BELT).asItem()) != 0) helper.fail("a sneak-click handed tiles back", LEFT);
        helper.succeed();
    }

    private static void splitterOverTilesNoRoom(GameTestHelper helper) {
        tilesUnderTheHalves(helper);
        var player = holding(helper, BlockContent.splitterFor(BeltTier.BELT), 2);
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getNonEquipmentItems().size(); slot++) {
            if (slot != inventory.getSelectedSlot()) inventory.setItem(slot, new ItemStack(Items.DIRT, 64));
        }
        var plan = planOf(helper, player, LEFT);
        if (plan == null || plan.refusal() != Refusal.FastReplace.NO_ROOM_TO_RETURN) helper.fail("the splitter was planned " + plan, LEFT);
        refusedClick(helper, player, LEFT, NO_ROOM);
        helper.succeed();
    }

    private static void tilesUnderTheHalves(GameTestHelper helper) {
        helper.setBlock(LEFT, tile(BeltTier.BELT));
        helper.setBlock(RIGHT, tile(BeltTier.BELT));
    }

    /** Clicks, and holds the world, the inventory and the drops to unchanged and the player to being told {@code key}. */
    private static void refusedClick(GameTestHelper helper, ListeningPlayer player, BlockPos aimed, String key) {
        var world = BeltWedgeTests.around(helper, aimed);
        var carried = inventory(player);
        click(helper, player, aimed, false);
        world.forEach((pos, state) -> {
            if (!helper.getBlockState(pos).equals(state)) helper.fail("a refused replace left " + helper.getBlockState(pos) + " for " + state, pos);
        });
        if (!inventory(player).equals(carried)) helper.fail("a refused replace changed the inventory to " + inventory(player), aimed);
        if (!player.heard.contains(key)) helper.fail("the player was told " + player.heard + ", not " + key, aimed);
    }

    private static void chargedAndHandedBack(GameTestHelper helper, ListeningPlayer player, Block charged, Block handedBack) {
        if (count(player, charged.asItem()) != 1 || count(player, handedBack.asItem()) != 1) {
            helper.fail("the player holds " + inventory(player) + ", not one " + charged.asItem() + " and one " + handedBack.asItem(), AIMED);
        }
        var dropped = helper.getEntities(EntityType.ITEM).size();
        if (dropped != 0) helper.fail(dropped + " items lie on the ground after the replace", AIMED);
    }

    private static void expect(GameTestHelper helper, BlockPos pos, BlockState expected) {
        if (!helper.getBlockState(pos).equals(expected)) helper.fail(helper.getBlockState(pos) + " stands where " + expected + " was expected", pos);
    }

    private static @Nullable PlacementPlan planOf(GameTestHelper helper, ListeningPlayer player, BlockPos aimed) {
        return Placements.planFor(helper.getLevel(), player, InteractionHand.MAIN_HAND, player.getMainHandItem(), onTop(helper, aimed));
    }

    private static void click(GameTestHelper helper, ListeningPlayer player, BlockPos aimed, boolean sneaking) {
        player.setShiftKeyDown(sneaking);
        player.gameMode.useItemOn(player, helper.getLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND, onTop(helper, aimed));
        player.setShiftKeyDown(false);
    }

    private static BlockHitResult onTop(GameTestHelper helper, BlockPos pos) {
        var absolute = helper.absolutePos(pos);
        return new BlockHitResult(Vec3.atCenterOf(absolute).relative(Direction.UP, 0.5), Direction.UP, absolute, false);
    }

    private static ListeningPlayer holding(GameTestHelper helper, Block block, int count) {
        var player = new ListeningPlayer(helper, STANDING);
        player.setYRot(Direction.EAST.toYRot());
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(block.asItem(), count));
        return player;
    }

    private static int count(ListeningPlayer player, Item item) {
        return inventory(player).getOrDefault(item, 0);
    }

    private static Map<Item, Integer> inventory(ListeningPlayer player) {
        Map<Item, Integer> counts = new HashMap<>();
        for (ItemStack stack : player.getInventory()) {
            if (!stack.isEmpty()) counts.merge(stack.getItem(), stack.getCount(), Integer::sum);
        }
        return counts;
    }

    private static void placeSplitter(GameTestHelper helper) {
        helper.setBlock(LEFT, half(BeltTier.BELT, SplitterBlock.Side.LEFT));
        helper.setBlock(RIGHT, half(BeltTier.BELT, SplitterBlock.Side.RIGHT));
    }

    private static BlockState half(BeltTier tier, SplitterBlock.Side side) {
        return facing(BlockContent.splitterFor(tier)).setValue(SplitterBlock.SIDE, side);
    }

    private static BlockState tile(BeltTier tier) {
        return facing(BlockContent.tileFor(tier));
    }

    private static BlockState facing(Block block) {
        return block.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.EAST);
    }
}
