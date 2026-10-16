// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.gametest;

import java.util.HashSet;
import java.util.UUID;

import com.mojang.authlib.GameProfile;


import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import io.github._5thlayer.beltworks.BlockContent;
import io.github._5thlayer.beltworks.ItemContent;
import io.github._5thlayer.beltworks.blocks.BeltTileBlock;
import io.github._5thlayer.beltworks.blocks.BeltTileBlockEntity;
import io.github._5thlayer.beltworks.blocks.BeltEndBlockEntity;
import io.github._5thlayer.beltworks.blocks.SplitterBlock;
import io.github._5thlayer.beltworks.blocks.SplitterMenu;
import io.github._5thlayer.beltworks.items.BeltRefusal;
import io.github._5thlayer.beltworks.items.SplitterItem;
import io.github._5thlayer.beltworks.model.BeltTier;
import io.github._5thlayer.beltworks.model.Splitter;
import io.github._5thlayer.beltworks.blocks.SplitterSettings;

/**
 * A splitter between belt tiles (PlanetaryFactory #394): its split, fallback, cap and break, and a splitter placed
 * across a tile line taking the place of the tile under it.
 *
 * <p>Items flow east. A chest and a loader feed a line of tiles into the splitter's left half, and a
 * line of tiles leaves each half for a loader with a chest behind it or not. The rates are typed.
 */
final class SplitterTileTests {

    private static final BlockPos SOURCE = new BlockPos(1, 1, 2);
    private static final BlockPos FROM = new BlockPos(2, 1, 2);
    private static final BlockPos LEFT = new BlockPos(6, 1, 2);
    private static final BlockPos RIGHT = LEFT.relative(Direction.EAST.getClockWise());
    private static final BlockPos LEFT_END = new BlockPos(10, 1, 2);
    private static final BlockPos RIGHT_END = new BlockPos(10, 1, 3);
    private static final BlockPos SOURCE_RIGHT = new BlockPos(1, 1, 3);
    private static final BlockPos FROM_RIGHT = new BlockPos(2, 1, 3);
    private static final BlockPos STANDING = new BlockPos(6, 1, 0);

    private static final int TIER_1_ITEMS_PER_SECOND = 15;
    // A multiple of four ticks: tier 1 delivers a whole number of items only every four.
    private static final int WINDOW_TICKS = 200;
    private static final int WARMUP_TICKS = 200;
    private static final int SUPPLY = 27 * 64;
    private static final int PRIORITY_SUPPLY = 40;
    private static final int HAND_TICKS = 40;
    // Two blocks of belt, one per half.
    private static final int BACKED_UP = 16;

    // Fewer than the seven-tile line holds, so none waits in the chest, and enough that the tile the
    // splitter replaces and the tiles before it carry some.
    private static final int CROSSED_SUPPLY = 48;
    private static final int CROSSED_FILL_TICKS = 200;
    private static final int CROSSED_DRAIN_TICKS = 300;

    private SplitterTileTests() {
    }

    static void register(BeltGameTests.Registrar tests) {
        tests.test("splitter_between_tiles_splits_one_line_evenly", WARMUP_TICKS + WINDOW_TICKS + 20,
                SplitterTileTests::splitsEvenly);
        tests.test("splitter_between_tiles_sends_everything_to_the_free_side", WARMUP_TICKS + WINDOW_TICKS + 20,
                SplitterTileTests::switchesToTheFreeSide);
        tests.test("tier_1_splitter_caps_a_tier_3_tile_line_at_" + TIER_1_ITEMS_PER_SECOND,
                WARMUP_TICKS + WINDOW_TICKS + 20, SplitterTileTests::capsTheLine);
        tests.test("splitters_back_to_back_split_one_line_evenly", WARMUP_TICKS + WINDOW_TICKS + 20,
                SplitterTileTests::chainedSplitEvenly);
        tests.test("tier_1_splitter_ahead_of_a_tier_3_splitter_caps_it_at_" + TIER_1_ITEMS_PER_SECOND,
                WARMUP_TICKS + WINDOW_TICKS + 20, SplitterTileTests::chainedCapsAtTheSlower);
        tests.test("splitter_unloads_straight_into_a_loader_at_each_half", WARMUP_TICKS + WINDOW_TICKS + 20,
                SplitterTileTests::unloadsIntoLoaders);
        tests.test("loader_loads_straight_into_a_splitter_half", WARMUP_TICKS + WINDOW_TICKS + 20,
                SplitterTileTests::loadedByALoader);
        tests.test("splitter_side_loads_a_tile_line_from_both_halves", WARMUP_TICKS + WINDOW_TICKS + 20,
                SplitterTileTests::sideLoadsALine);
        tests.test("splitter_placed_across_a_tile_line_replaces_the_tile_and_loses_nothing",
                CROSSED_FILL_TICKS + CROSSED_DRAIN_TICKS + 20, SplitterTileTests::placedAcrossALine);
        tests.test("splitter_aimed_across_a_tile_line_facing_north_places_nothing", 20,
                helper -> refusedAcrossALine(helper, Direction.NORTH));
        tests.test("splitter_aimed_across_a_tile_line_facing_south_places_nothing", 20,
                helper -> refusedAcrossALine(helper, Direction.SOUTH));
        tests.test("splitter_between_tiles_broken_at_its_left_half_hands_its_items_to_the_breaker",
                WARMUP_TICKS + 20, helper -> breaks(helper, LEFT));
        tests.test("splitter_between_tiles_broken_at_its_right_half_hands_its_items_to_the_breaker",
                WARMUP_TICKS + 20, helper -> breaks(helper, RIGHT));
        tests.test("a_held_splitter_half_fills_the_inventory", WARMUP_TICKS + HAND_TICKS + 20,
                SplitterTileTests::heldHalfFillsTheInventory);
        tests.test("a_sneaking_player_holding_a_splitter_half_takes_nothing", WARMUP_TICKS + HAND_TICKS + 20,
                SplitterTileTests::sneakingTakesNothing);
        tests.test("splitter_output_priority_fills_its_side_first", WARMUP_TICKS + WINDOW_TICKS + 20,
                SplitterTileTests::priorityFillsItsSide);
        tests.test("splitter_output_priority_overflows_when_its_side_backs_up", WARMUP_TICKS + WINDOW_TICKS + 20,
                SplitterTileTests::priorityOverflows);
        tests.test("splitter_input_priority_empties_its_chest_before_the_other_gives_more_than_its_share",
                WARMUP_TICKS + 20, SplitterTileTests::inputPriorityEmptiesFirst);
        tests.test("splitter_filter_sorts_a_mixed_chest_into_two_chests_with_none_crossing", WARMUP_TICKS + 20,
                SplitterTileTests::filterSorts);
        tests.test("a_filter_set_with_no_output_priority_sets_it_to_the_switch_side", 20,
                SplitterTileTests::filterSetsPriority);
        tests.test("clearing_a_splitters_output_priority_clears_its_filter", 20,
                SplitterTileTests::clearingPriorityClearsFilter);
        tests.test("a_click_on_the_splitter_menus_filter_slot_sets_the_filter_from_the_cursor_and_takes_nothing", 20,
                SplitterTileTests::menuClickSetsFilter);
        tests.test("the_splitter_menus_buttons_set_the_priorities_and_their_sides", 20,
                SplitterTileTests::menuButtonsSetPriorities);
        tests.test("a_splitter_menu_is_valid_only_while_the_splitter_stands_within_reach", 20,
                SplitterTileTests::menuStaysValid);
        tests.test("a_sneak_use_on_a_splitter_half_opens_its_menu_on_the_server", 20,
                SplitterTileTests::sneakOpensMenu);
        tests.test("splitter_settings_survive_a_save_and_load_of_the_halves", 20,
                SplitterTileTests::settingsSurviveSaveAndLoad);
        tests.test("a_broken_and_replaced_splitter_is_plain", 20, SplitterTileTests::replacedIsPlain);
    }

    // A sneak-use on a half opens its screen, so the hand never takes (#20).
    private static void sneakingTakesNothing(GameTestHelper helper) {
        line(helper, BeltTier.BELT, true, true, BeltTier.BELT);
        ServerPlayer player = player(helper, "beltworks_splitter_sneak");
        player.setShiftKeyDown(true);
        BeltEndBlockEntity held = helper.getBlockEntity(LEFT, BeltEndBlockEntity.class);

        helper.startSequence()
                .thenIdle(WARMUP_TICKS)
                .thenExecuteFor(HAND_TICKS, () -> held.holdHand(player, 0.5))
                .thenExecute(() -> {
                    if (cobblestone(player) != 0) helper.fail("a sneaking player's hand took from a splitter half", LEFT);
                })
                .thenSucceed();
    }

    // Set from the right half, as either half edits the one record (#20).
    private static void priorityFillsItsSide(GameTestHelper helper) {
        line(helper, BeltTier.BELT, true, true, BeltTier.BELT);
        helper.getBlockEntity(RIGHT, BeltEndBlockEntity.class).setOutputPriority(Splitter.Priority.RIGHT);
        int expected = TIER_1_ITEMS_PER_SECOND * WINDOW_TICKS / 20;
        measure(helper, (left, right) -> {
            if (right != expected || left != 0) {
                helper.fail("a splitter with right output priority delivered " + left + " left and " + right
                        + " right in " + WINDOW_TICKS + " ticks, expected all " + expected + " right", LEFT);
            }
        });
    }

    // Nothing behind the left end's loader, so the priority side backs up.
    private static void priorityOverflows(GameTestHelper helper) {
        line(helper, BeltTier.BELT, false, true, BeltTier.BELT);
        helper.getBlockEntity(LEFT, BeltEndBlockEntity.class).setOutputPriority(Splitter.Priority.LEFT);
        int expected = TIER_1_ITEMS_PER_SECOND * WINDOW_TICKS / 20;
        measure(helper, (left, right) -> {
            if (right != expected) {
                helper.fail("with its left priority side backed up a splitter delivered " + right + " right in "
                        + WINDOW_TICKS + " ticks, expected " + expected, LEFT);
            }
        });
    }

    // Two chests feed the halves into one output. Without a priority the left chest would give about half
    // of what has left the two, so its 40 items would not be gone by now.
    private static void inputPriorityEmptiesFirst(GameTestHelper helper) {
        placeSplitter(helper, BeltTier.BELT);
        feed(helper, SOURCE, FROM, BeltTier.BELT);
        feed(helper, SOURCE_RIGHT, FROM_RIGHT, BeltTier.BELT);
        BeltTileTests.chest(helper, SOURCE).clearContent();
        BeltTileTests.fill(helper, SOURCE, PRIORITY_SUPPLY);
        outputs(helper, BeltTier.BELT, LEFT.getX() + 1, true, false);
        helper.getBlockEntity(RIGHT, BeltEndBlockEntity.class).setInputPriority(Splitter.Priority.LEFT);
        int[] rightBefore = new int[1];
        helper.startSequence()
                .thenExecute(() -> rightBefore[0] = BeltTileTests.count(BeltTileTests.chest(helper, SOURCE_RIGHT)))
                .thenIdle(WARMUP_TICKS)
                .thenExecute(() -> {
                    int left = BeltTileTests.count(BeltTileTests.chest(helper, SOURCE));
                    int gave = rightBefore[0] - BeltTileTests.count(BeltTileTests.chest(helper, SOURCE_RIGHT));
                    if (left != 0) helper.fail("the priority chest still holds " + left + " items", SOURCE);
                    if (gave <= 0) helper.fail("the other chest gave nothing once the priority chest was empty", SOURCE_RIGHT);
                })
                .thenSucceed();
    }

    // Cobblestone and dirt alternate slot by slot in one chest; the filter sends cobblestone left.
    private static void filterSorts(GameTestHelper helper) {
        placeSplitter(helper, BeltTier.BELT);
        feed(helper, SOURCE, FROM, BeltTier.BELT);
        var source = BeltTileTests.chest(helper, SOURCE);
        source.clearContent();
        for (int slot = 0; slot < 6; slot++) {
            source.setItem(slot, new ItemStack(slot % 2 == 0 ? Items.COBBLESTONE : Items.DIRT, 16));
        }
        outputs(helper, BeltTier.BELT, LEFT.getX() + 1, true, true);
        helper.getBlockEntity(LEFT, BeltEndBlockEntity.class).setSplitterFilter(new ItemStack(Items.COBBLESTONE), Splitter.Priority.LEFT);
        helper.startSequence()
                .thenIdle(WARMUP_TICKS)
                .thenExecute(() -> {
                    var left = BeltTileTests.chest(helper, LEFT_END.east());
                    var right = BeltTileTests.chest(helper, RIGHT_END.east());
                    if (count(left, Items.COBBLESTONE) != 48 || count(right, Items.DIRT) != 48) {
                        helper.fail("the filter sorted " + count(left, Items.COBBLESTONE) + " cobblestone left and "
                                + count(right, Items.DIRT) + " dirt right, expected 48 each", LEFT);
                    }
                    if (count(left, Items.DIRT) != 0 || count(right, Items.COBBLESTONE) != 0) {
                        helper.fail("the filter let " + count(left, Items.DIRT) + " dirt left and "
                                + count(right, Items.COBBLESTONE) + " cobblestone right cross", LEFT);
                    }
                })
                .thenSucceed();
    }

    private static int count(net.minecraft.world.Container chest, net.minecraft.world.item.Item item) {
        int total = 0;
        for (int slot = 0; slot < chest.getContainerSize(); slot++) {
            if (chest.getItem(slot).is(item)) total += chest.getItem(slot).getCount();
        }
        return total;
    }

    private static void filterSetsPriority(GameTestHelper helper) {
        placeSplitter(helper, BeltTier.BELT);
        // From the left half, with a stack of five: the record holds one, on the side given.
        helper.getBlockEntity(LEFT, BeltEndBlockEntity.class).setSplitterFilter(new ItemStack(Items.DIRT, 5), Splitter.Priority.RIGHT);
        for (BlockPos half : new BlockPos[] {LEFT, RIGHT}) {
            var settings = helper.getBlockEntity(half, BeltEndBlockEntity.class).splitterSettings();
            if (settings.outputPriority() != Splitter.Priority.RIGHT || !settings.filter().is(Items.DIRT) || settings.filter().getCount() != 1) {
                helper.fail("a filter set with no output priority left " + settings + ", expected output RIGHT and one dirt", half);
            }
        }
        // With a priority already set the filter keeps it, whatever side is given.
        helper.getBlockEntity(RIGHT, BeltEndBlockEntity.class).setOutputPriority(Splitter.Priority.LEFT);
        helper.getBlockEntity(RIGHT, BeltEndBlockEntity.class).setSplitterFilter(new ItemStack(Items.STONE), Splitter.Priority.RIGHT);
        var settings = helper.getBlockEntity(LEFT, BeltEndBlockEntity.class).splitterSettings();
        if (settings.outputPriority() != Splitter.Priority.LEFT || !settings.filter().is(Items.STONE)) {
            helper.fail("a filter set beside an output priority left " + settings + ", expected output LEFT and stone", LEFT);
        }
        // A belt or splitter item is a filter like any other.
        helper.getBlockEntity(LEFT, BeltEndBlockEntity.class).setSplitterFilter(new ItemStack(ItemContent.SPLITTER.get()), Splitter.Priority.LEFT);
        if (!helper.getBlockEntity(LEFT, BeltEndBlockEntity.class).splitterSettings().filter().is(ItemContent.SPLITTER.get())) {
            helper.fail("a splitter item was not accepted as a filter", LEFT);
        }
        helper.getBlockEntity(LEFT, BeltEndBlockEntity.class).setSplitterFilter(ItemStack.EMPTY, Splitter.Priority.LEFT);
        settings = helper.getBlockEntity(LEFT, BeltEndBlockEntity.class).splitterSettings();
        if (!settings.filter().isEmpty() || settings.outputPriority() != Splitter.Priority.LEFT) {
            helper.fail("an empty filter left " + settings + ", expected no filter and output priority kept", LEFT);
        }
        helper.succeed();
    }

    private static void clearingPriorityClearsFilter(GameTestHelper helper) {
        placeSplitter(helper, BeltTier.BELT);
        helper.getBlockEntity(LEFT, BeltEndBlockEntity.class).setInputPriority(Splitter.Priority.RIGHT);
        helper.getBlockEntity(LEFT, BeltEndBlockEntity.class).setSplitterFilter(new ItemStack(Items.DIRT), Splitter.Priority.LEFT);
        helper.getBlockEntity(RIGHT, BeltEndBlockEntity.class).setOutputPriority(Splitter.Priority.NONE);
        var settings = helper.getBlockEntity(LEFT, BeltEndBlockEntity.class).splitterSettings();
        if (!settings.filter().isEmpty() || settings.outputPriority() != Splitter.Priority.NONE
              || settings.inputPriority() != Splitter.Priority.RIGHT) {
            helper.fail("clearing output priority left " + settings + ", expected no filter, no output priority and input RIGHT", LEFT);
        }
        helper.succeed();
    }

    private static SplitterMenu menu(GameTestHelper helper, ServerPlayer player) {
        return new SplitterMenu(1, player.getInventory(), helper.absolutePos(LEFT));
    }

    // The cursor stack is the server's own, and a click reads it without taking it (#22).
    private static void menuClickSetsFilter(GameTestHelper helper) {
        placeSplitter(helper, BeltTier.BELT);
        ServerPlayer player = player(helper, "beltworks_splitter_menu_click");
        var menu = menu(helper, player);
        var settings = helper.getBlockEntity(RIGHT, BeltEndBlockEntity.class);

        menu.setCarried(new ItemStack(Items.DIRT, 5));
        menu.clicked(SplitterMenu.FILTER_SLOT, 0, ContainerInput.PICKUP, player);
        if (!settings.splitterSettings().filter().is(Items.DIRT) || settings.splitterSettings().filter().getCount() != 1) {
            helper.fail("a click with dirt on the cursor left " + settings.splitterSettings() + ", expected a filter of one dirt", RIGHT);
        }
        if (settings.splitterSettings().outputPriority() != Splitter.Priority.LEFT) {
            helper.fail("a filter set with no output priority left " + settings.splitterSettings() + ", expected output LEFT, the side shown", RIGHT);
        }
        if (!menu.getCarried().is(Items.DIRT) || menu.getCarried().getCount() != 5) {
            helper.fail("the click took from the cursor: it holds " + menu.getCarried(), RIGHT);
        }
        if (!menu.filter().is(Items.DIRT)) helper.fail("the menu's slot shows " + menu.filter() + ", expected dirt", RIGHT);

        // A right click sets it too, with any item: a splitter item is a filter like any other.
        menu.setCarried(new ItemStack(ItemContent.SPLITTER.get(), 3));
        menu.clicked(SplitterMenu.FILTER_SLOT, 1, ContainerInput.PICKUP, player);
        if (!settings.splitterSettings().filter().is(ItemContent.SPLITTER.get()) || menu.getCarried().getCount() != 3) {
            helper.fail("a click with a splitter item left " + settings.splitterSettings() + " and cursor " + menu.getCarried(), RIGHT);
        }

        // Nothing else done to the slot changes it, or lands an item in it.
        menu.setCarried(new ItemStack(Items.STONE));
        menu.clicked(SplitterMenu.FILTER_SLOT, 0, ContainerInput.QUICK_MOVE, player);
        menu.clicked(SplitterMenu.FILTER_SLOT, 0, ContainerInput.PICKUP_ALL, player);
        if (!settings.splitterSettings().filter().is(ItemContent.SPLITTER.get()) || !menu.getCarried().is(Items.STONE)) {
            helper.fail("a shift-click or a double-click changed the filter to " + settings.splitterSettings().filter(), RIGHT);
        }
        player.getInventory().setItem(9, new ItemStack(Items.COBBLESTONE, 7));
        if (!menu.quickMoveStack(player, 1 + 9).isEmpty() || !menu.filter().is(ItemContent.SPLITTER.get())
              || player.getInventory().getItem(9).getCount() != 7) {
            helper.fail("a shift-click on an inventory slot moved an item into the ghost slot", RIGHT);
        }

        // An empty cursor clears the filter and leaves the priority.
        menu.setCarried(ItemStack.EMPTY);
        menu.clicked(SplitterMenu.FILTER_SLOT, 0, ContainerInput.PICKUP, player);
        if (!settings.splitterSettings().filter().isEmpty() || settings.splitterSettings().outputPriority() != Splitter.Priority.LEFT
              || !menu.filter().isEmpty()) {
            helper.fail("a click with an empty cursor left " + settings.splitterSettings() + ", expected no filter and output LEFT", RIGHT);
        }

        // Turned off and flipped to the right, the switch shows RIGHT, and a filter set now turns it on there.
        menu.clickMenuButton(player, SplitterMenu.OUTPUT_TOGGLE);
        menu.clickMenuButton(player, SplitterMenu.OUTPUT_FLIP);
        menu.setCarried(new ItemStack(Items.STONE));
        menu.clicked(SplitterMenu.FILTER_SLOT, 0, ContainerInput.PICKUP, player);
        if (settings.splitterSettings().outputPriority() != Splitter.Priority.RIGHT || !settings.splitterSettings().filter().is(Items.STONE)) {
            helper.fail("a filter set beside a switch at RIGHT left " + settings.splitterSettings() + ", expected output RIGHT and stone", RIGHT);
        }
        helper.succeed();
    }

    private static void menuButtonsSetPriorities(GameTestHelper helper) {
        placeSplitter(helper, BeltTier.BELT);
        ServerPlayer player = player(helper, "beltworks_splitter_menu_buttons");
        var menu = menu(helper, player);
        var splitter = helper.getBlockEntity(LEFT, BeltEndBlockEntity.class);

        menu.clickMenuButton(player, SplitterMenu.INPUT_TOGGLE);
        if (splitter.splitterSettings().inputPriority() != Splitter.Priority.LEFT || menu.inputPriority() != Splitter.Priority.LEFT) {
            helper.fail("turning input priority on left " + splitter.splitterSettings() + ", expected LEFT", LEFT);
        }
        menu.clickMenuButton(player, SplitterMenu.INPUT_FLIP);
        if (splitter.splitterSettings().inputPriority() != Splitter.Priority.RIGHT || menu.inputSide() != Splitter.Priority.RIGHT) {
            helper.fail("flipping an input priority that is on left " + splitter.splitterSettings() + ", expected RIGHT", LEFT);
        }
        menu.clickMenuButton(player, SplitterMenu.INPUT_TOGGLE);
        menu.clickMenuButton(player, SplitterMenu.INPUT_FLIP);
        if (splitter.splitterSettings().inputPriority() != Splitter.Priority.NONE || menu.inputSide() != Splitter.Priority.LEFT) {
            helper.fail("flipping an input priority that is off left " + splitter.splitterSettings() + ", expected it off and the switch at LEFT", LEFT);
        }

        menu.clickMenuButton(player, SplitterMenu.OUTPUT_TOGGLE);
        menu.setCarried(new ItemStack(Items.DIRT));
        menu.clicked(SplitterMenu.FILTER_SLOT, 0, ContainerInput.PICKUP, player);
        menu.clickMenuButton(player, SplitterMenu.OUTPUT_TOGGLE);
        if (splitter.splitterSettings().outputPriority() != Splitter.Priority.NONE || !splitter.splitterSettings().filter().isEmpty()) {
            helper.fail("turning output priority off left " + splitter.splitterSettings() + ", expected no output priority and no filter", LEFT);
        }
        if (menu.clickMenuButton(player, 99)) helper.fail("an unknown button was accepted", LEFT);
        helper.succeed();
    }

    private static void menuStaysValid(GameTestHelper helper) {
        placeSplitter(helper, BeltTier.BELT);
        ServerPlayer player = player(helper, "beltworks_splitter_menu_valid");
        var menu = menu(helper, player);
        if (!menu.stillValid(player)) helper.fail("a menu opened beside its splitter was not valid", LEFT);
        var at = player.position();
        var far = helper.absoluteVec(STANDING.getBottomCenter().add(0, 0, -40));
        player.setPos(far.x, far.y, far.z);
        if (menu.stillValid(player)) helper.fail("a menu was valid for a player 40 blocks away", LEFT);
        player.setPos(at.x, at.y, at.z);
        helper.setBlock(LEFT, Blocks.AIR);
        if (menu.stillValid(player)) helper.fail("a menu was valid after its splitter half was broken", LEFT);
        helper.succeed();
    }

    // The server opens the menu, whose provider names the half used; the client's screen follows from the packet.
    // A fake player has no connection to open one on, so the test reads what it would open.
    private static void sneakOpensMenu(GameTestHelper helper) {
        placeSplitter(helper, BeltTier.BELT);
        ServerPlayer player = player(helper, "beltworks_splitter_open");
        player.setShiftKeyDown(true);
        var result = SplitterBlock.useOn(player, ItemStack.EMPTY, helper.absolutePos(RIGHT));
        if (!result.consumesAction()) helper.fail("a sneak-use on a splitter half was not taken: " + result, RIGHT);
        var provider = SplitterBlock.menuProvider(helper.absolutePos(RIGHT));
        if (!(provider.createMenu(2, player.getInventory(), player) instanceof SplitterMenu menu)
              || !menu.half().equals(helper.absolutePos(RIGHT)) || menu.containerId != 2) {
            helper.fail("the provider did not create a menu on the half used", RIGHT);
        }
        player.setShiftKeyDown(false);
        if (SplitterBlock.useOn(player, ItemStack.EMPTY, helper.absolutePos(RIGHT)).consumesAction()) {
            helper.fail("a plain use on a splitter half opened its menu", RIGHT);
        }
        helper.succeed();
    }

    private static void settingsSurviveSaveAndLoad(GameTestHelper helper) {
        placeSplitter(helper, BeltTier.BELT);
        helper.getBlockEntity(LEFT, BeltEndBlockEntity.class).setOutputPriority(Splitter.Priority.RIGHT);
        helper.getBlockEntity(RIGHT, BeltEndBlockEntity.class).setInputPriority(Splitter.Priority.LEFT);
        helper.getBlockEntity(LEFT, BeltEndBlockEntity.class).setSplitterFilter(new ItemStack(Items.COBBLESTONE), Splitter.Priority.RIGHT);
        var registries = helper.getLevel().registryAccess();
        var saved = new CompoundTag[2];
        var halves = new BlockPos[] {LEFT, RIGHT};
        for (int i = 0; i < 2; i++) saved[i] = helper.getBlockEntity(halves[i], BeltEndBlockEntity.class).saveWithFullMetadata(registries);
        var update = helper.getBlockEntity(LEFT, BeltEndBlockEntity.class).getUpdateTag(registries);
        if (!update.getStringOr("output_priority", "").equals(Splitter.Priority.RIGHT.name())) {
            helper.fail("the splitter's update tag carries no output priority", LEFT);
        }
        if (!update.getStringOr("input_priority", "").equals(Splitter.Priority.LEFT.name())) {
            helper.fail("the splitter's update tag carries no input priority", LEFT);
        }
        if (update.read("splitter_filter", ItemStack.CODEC).filter(stack -> stack.is(Items.COBBLESTONE)).isEmpty()) {
            helper.fail("the splitter's update tag carries no filter", LEFT);
        }

        helper.setBlock(LEFT, Blocks.AIR);
        placeSplitter(helper, BeltTier.BELT);
        for (int i = 0; i < 2; i++) {
            helper.getBlockEntity(halves[i], BeltEndBlockEntity.class)
                    .loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, registries, saved[i]));
        }
        for (BlockPos half : halves) {
            var settings = helper.getBlockEntity(half, BeltEndBlockEntity.class).splitterSettings();
            if (settings.outputPriority() != Splitter.Priority.RIGHT || settings.inputPriority() != Splitter.Priority.LEFT) {
                helper.fail("after a save and load the half reads " + settings + ", expected input LEFT and output RIGHT", half);
            }
            if (!settings.filter().is(Items.COBBLESTONE)) helper.fail("after a save and load the half reads filter " + settings.filter(), half);
        }
        // Drawn on the priority half only.
        if (!helper.getBlockEntity(RIGHT, BeltEndBlockEntity.class).filteredItem().is(Items.COBBLESTONE)
              || !helper.getBlockEntity(LEFT, BeltEndBlockEntity.class).filteredItem().isEmpty()) {
            helper.fail("the filter item is not shown on the output priority half alone", RIGHT);
        }
        helper.succeed();
    }

    private static void replacedIsPlain(GameTestHelper helper) {
        placeSplitter(helper, BeltTier.BELT);
        helper.getBlockEntity(LEFT, BeltEndBlockEntity.class).setOutputPriority(Splitter.Priority.LEFT);
        helper.getBlockEntity(LEFT, BeltEndBlockEntity.class).setInputPriority(Splitter.Priority.RIGHT);
        helper.getBlockEntity(LEFT, BeltEndBlockEntity.class).setSplitterFilter(new ItemStack(Items.DIRT), Splitter.Priority.LEFT);
        ServerPlayer player = player(helper, "beltworks_splitter_replacer");
        player.gameMode.destroyBlock(helper.absolutePos(LEFT));
        var dropped = helper.getLevel().getEntities(EntityType.ITEM, area(helper), e -> true).stream()
                .map(ItemEntity::getItem)
                .filter(stack -> stack.is(ItemContent.SPLITTER.get()))
                .toList();
        if (dropped.size() != 1 || !dropped.getFirst().getComponentsPatch().isEmpty()) {
            helper.fail("the broken splitter dropped " + dropped + ", expected one plain splitter", LEFT);
        }

        placeSplitter(helper, BeltTier.BELT);
        for (BlockPos half : new BlockPos[] {LEFT, RIGHT}) {
            if (!helper.getBlockEntity(half, BeltEndBlockEntity.class).splitterSettings().equals(SplitterSettings.NONE)) {
                helper.fail("a splitter placed where one was broken kept its settings", half);
            }
        }
        helper.succeed();
    }

    // A smoke test: the hold's own rules are HeldHandTest's.
    private static void heldHalfFillsTheInventory(GameTestHelper helper) {
        line(helper, BeltTier.BELT, true, true, BeltTier.BELT);
        ServerPlayer player = player(helper, "beltworks_splitter_hand");
        BeltEndBlockEntity held = helper.getBlockEntity(LEFT, BeltEndBlockEntity.class);

        helper.startSequence()
                .thenIdle(WARMUP_TICKS)
                .thenExecuteFor(HAND_TICKS, () -> held.holdHand(player, 0.5))
                .thenExecute(() -> {
                    if (cobblestone(player) == 0) helper.fail("holding a splitter half took nothing", LEFT);
                })
                .thenSucceed();
    }

    private static void splitsEvenly(GameTestHelper helper) {
        line(helper, BeltTier.BELT, true, true, BeltTier.BELT);
        int expected = TIER_1_ITEMS_PER_SECOND * WINDOW_TICKS / 20;
        measure(helper, (left, right) -> {
            if (left + right != expected || Math.abs(left - right) > 1) {
                helper.fail("a splitter between tier-1 tiles delivered " + left + " left and " + right
                        + " right in " + WINDOW_TICKS + " ticks, expected " + expected / 2 + " each", LEFT);
            }
        });
    }

    // Nothing behind the right end's loader, so that side backs up.
    private static void switchesToTheFreeSide(GameTestHelper helper) {
        line(helper, BeltTier.BELT, true, false, BeltTier.BELT);
        int expected = TIER_1_ITEMS_PER_SECOND * WINDOW_TICKS / 20;
        measure(helper, (left, right) -> {
            if (left != expected) {
                helper.fail("with its right side backed up a splitter between tiles delivered " + left
                        + " left in " + WINDOW_TICKS + " ticks, expected " + expected, LEFT);
            }
        });
    }

    private static void capsTheLine(GameTestHelper helper) {
        line(helper, BeltTier.EXPRESS, true, false, BeltTier.BELT);
        int expected = TIER_1_ITEMS_PER_SECOND * WINDOW_TICKS / 20;
        measure(helper, (left, right) -> {
            if (left != expected) {
                helper.fail("a tier-1 splitter between tier-3 tiles delivered " + left + " in "
                        + WINDOW_TICKS + " ticks, expected " + expected, LEFT);
            }
        });
    }

    // Splitters chain directly, as with a tile between them (#85).
    private static void chainedSplitEvenly(GameTestHelper helper) {
        line(helper, BeltTier.BELT, true, true, BeltTier.BELT, BeltTier.BELT);
        int expected = TIER_1_ITEMS_PER_SECOND * WINDOW_TICKS / 20;
        measure(helper, (left, right) -> {
            if (left + right != expected || Math.abs(left - right) > 1) {
                helper.fail("two splitters back to back delivered " + left + " left and " + right
                        + " right in " + WINDOW_TICKS + " ticks, expected " + expected / 2 + " each", LEFT);
            }
        });
    }

    // Each half caps at its own tier: the tier-3 splitter hands on at the tier-1 one's rate (#85).
    private static void chainedCapsAtTheSlower(GameTestHelper helper) {
        line(helper, BeltTier.EXPRESS, true, false, BeltTier.EXPRESS, BeltTier.BELT);
        int expected = TIER_1_ITEMS_PER_SECOND * WINDOW_TICKS / 20;
        measure(helper, (left, right) -> {
            if (left != expected) {
                helper.fail("a tier-1 splitter ahead of a tier-3 one delivered " + left + " in "
                        + WINDOW_TICKS + " ticks, expected " + expected, LEFT);
            }
        });
    }

    // A half's front hands to a loader facing it, as a line's last tile does (#89).
    private static void unloadsIntoLoaders(GameTestHelper helper) {
        placeSplitter(helper, BeltTier.BELT);
        feed(helper, SOURCE, FROM, BeltTier.BELT);
        for (BlockPos half : new BlockPos[] {LEFT, RIGHT}) {
            helper.setBlock(half.east(), BeltTileTests.loader(BeltTier.BELT, Direction.WEST));
            helper.setBlock(half.east(2), Blocks.CHEST);
        }
        splitEvenly(helper, LEFT.east(), RIGHT.east(), "a splitter unloading into loaders");
    }

    // A loader's mouth at a half's back loads it, as it loads a line's first tile (#89).
    private static void loadedByALoader(GameTestHelper helper) {
        placeSplitter(helper, BeltTier.BELT);
        outputs(helper, BeltTier.BELT, LEFT.getX() + 1, true, true);
        helper.setBlock(LEFT.west(2), Blocks.CHEST);
        BeltTileTests.fill(helper, LEFT.west(2), SUPPLY);
        helper.setBlock(LEFT.west(), BeltTileTests.loader(BeltTier.BELT, Direction.EAST));
        splitEvenly(helper, LEFT_END, RIGHT_END, "a splitter loaded by a loader");
    }

    /**
     * A south-facing splitter loaded from the north, both halves' fronts on the side of one line
     * running east to a chest: the line takes the whole split (#89).
     */
    private static void sideLoadsALine(GameTestHelper helper) {
        BlockPos left = new BlockPos(6, 1, 2);
        BlockPos right = left.relative(Direction.SOUTH.getClockWise());
        helper.setBlock(left, half(BeltTier.BELT, SplitterBlock.Side.LEFT, Direction.SOUTH));
        helper.setBlock(right, half(BeltTier.BELT, SplitterBlock.Side.RIGHT, Direction.SOUTH));
        helper.setBlock(left.north(2), Blocks.CHEST);
        BeltTileTests.fill(helper, left.north(2), SUPPLY);
        helper.setBlock(left.north(), BeltTileTests.loader(BeltTier.BELT, Direction.SOUTH));
        BlockPos end = new BlockPos(10, 1, 3);
        for (BlockPos at = right.south(); at.getX() < end.getX(); at = at.east()) {
            helper.setBlock(at, BeltTileTests.tile(BeltTier.BELT, Direction.EAST));
        }
        helper.setBlock(end, BeltTileTests.loader(BeltTier.BELT, Direction.WEST));
        helper.setBlock(end.east(), Blocks.CHEST);
        int expected = TIER_1_ITEMS_PER_SECOND * WINDOW_TICKS / 20;
        measure(helper, end, end, (delivered, ignored) -> {
            if (delivered != expected) {
                helper.fail("a splitter side-loading a line delivered " + delivered + " in " + WINDOW_TICKS
                        + " ticks, expected " + expected, end);
            }
        });
    }

    private static void splitEvenly(GameTestHelper helper, BlockPos leftEnd, BlockPos rightEnd, String what) {
        int expected = TIER_1_ITEMS_PER_SECOND * WINDOW_TICKS / 20;
        measure(helper, leftEnd, rightEnd, (left, right) -> {
            if (left + right != expected || Math.abs(left - right) > 1) {
                helper.fail(what + " delivered " + left + " left and " + right + " right in " + WINDOW_TICKS
                        + " ticks, expected " + expected / 2 + " each", leftEnd);
            }
        });
    }

    /**
     * A backed-up line of seven tiles, a splitter placed by hand on its middle tile: the tile is
     * refunded, every item stays on the line or the half, and once the end has a chest every
     * item reaches it or the right half, which no line leaves.
     */
    private static void placedAcrossALine(GameTestHelper helper) {
        helper.setBlock(SOURCE, Blocks.CHEST);
        helper.setBlock(FROM, BeltTileTests.loader(BeltTier.BELT, Direction.EAST));
        for (BlockPos at = FROM.east(); at.getX() < LEFT_END.getX(); at = at.east()) {
            helper.setBlock(at, BeltTileTests.tile(BeltTier.BELT, Direction.EAST));
        }
        helper.setBlock(LEFT_END, BeltTileTests.loader(BeltTier.BELT, Direction.WEST));
        BeltTileTests.fill(helper, SOURCE, CROSSED_SUPPLY);
        ServerPlayer player = player(helper, "beltworks_splitter_tile_placer");
        player.setYRot(-90);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ItemContent.SPLITTER.get()));

        helper.startSequence()
                .thenIdle(CROSSED_FILL_TICKS)
                .thenExecute(() -> {
                    if (onLines(helper) != CROSSED_SUPPLY) {
                        helper.fail("the line holds " + onLines(helper) + " of " + CROSSED_SUPPLY
                                + " before the splitter, so this proves little", LEFT);
                    }
                    BlockPos floor = helper.absolutePos(LEFT.below());
                    helper.useBlock(LEFT.below(), player, new BlockHitResult(
                            Vec3.atCenterOf(floor).relative(Direction.UP, 0.5), Direction.UP, floor, false));
                    if (!(helper.getBlockState(LEFT).getBlock() instanceof SplitterBlock)
                            || !(helper.getBlockState(RIGHT).getBlock() instanceof SplitterBlock)) {
                        helper.fail("no splitter was placed across the tile line", LEFT);
                    }
                    int tiles = ContainerHelper.clearOrCountMatchingItems(player.getInventory(),
                            stack -> stack.is(BlockContent.tileFor(BeltTier.BELT).asItem()), 0, true);
                    if (tiles != 1) {
                        helper.fail("placing the splitter refunded " + tiles + " tiles, expected the one it replaced", LEFT);
                    }
                    // Its last splitter spent, the held slot takes the tile handed back (#94).
                    if (ContainerHelper.clearOrCountMatchingItems(player.getInventory(), stack -> stack.is(ItemContent.SPLITTER.get()), 0, true) != 0) {
                        helper.fail("the splitter was not spent", LEFT);
                    }
                })
                .thenIdle(1)
                .thenExecute(() -> {
                    int held = onLines(helper) + half(helper, LEFT) + half(helper, RIGHT);
                    if (held != CROSSED_SUPPLY || cobblestone(player) != 0 || onGround(helper) != 0) {
                        helper.fail("after the splitter the line and halves hold " + held + " of " + CROSSED_SUPPLY
                                + ", the placer " + cobblestone(player) + " and the ground " + onGround(helper), LEFT);
                    }
                    helper.setBlock(LEFT_END.east(), Blocks.CHEST);
                })
                .thenIdle(CROSSED_DRAIN_TICKS)
                .thenExecute(() -> {
                    // The right half has no line leaving it, so what it is split backs up there.
                    int delivered = delivered(helper, LEFT_END);
                    int right = half(helper, RIGHT);
                    if (delivered + right != CROSSED_SUPPLY || delivered == 0) {
                        helper.fail("the chest past the splitter holds " + delivered + " and the right half " + right
                                + " of " + CROSSED_SUPPLY, LEFT_END);
                    }
                })
                .thenSucceed();
    }

    /**
     * A splitter facing across a line of east-running tiles, clicked on a tile's top: whichever
     * side its second half falls on, it is planned refused on the tile, and goes neither on it nor above it.
     */
    private static void refusedAcrossALine(GameTestHelper helper, Direction facing) {
        for (BlockPos at = FROM.east(); at.getX() < LEFT_END.getX(); at = at.east()) {
            helper.setBlock(at, BeltTileTests.tile(BeltTier.BELT, Direction.EAST));
        }
        ServerPlayer player = player(helper, "beltworks_splitter_tile_across_" + facing.getSerializedName());
        player.setYRot(facing.toYRot());
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ItemContent.SPLITTER.get()));
        BlockPos tile = helper.absolutePos(LEFT);
        BlockHitResult hit = new BlockHitResult(Vec3.atBottomCenterOf(tile).add(0, 6 / 16d, 0), Direction.UP, tile, false);
        var plan = ((SplitterItem) player.getMainHandItem().getItem())
                .splitterPlan(new BlockPlaceContext(player, InteractionHand.MAIN_HAND, player.getMainHandItem(), hit));
        if (plan == null || plan.refusal() != BeltRefusal.BLOCKED || !plan.halves().getFirst().pos().equals(tile)) {
            helper.fail("a splitter facing " + facing + " is not planned refused on the tile it was aimed at", LEFT);
        }
        helper.useBlock(LEFT, player, hit);

        for (BlockPos at = FROM.east(); at.getX() < LEFT_END.getX(); at = at.east()) {
            if (!(helper.getBlockState(at).getBlock() instanceof BeltTileBlock)) {
                helper.fail("a splitter facing " + facing + " replaced the tile at " + at, at);
            }
            if (!helper.getBlockState(at.above()).isAir()) {
                helper.fail("a splitter facing " + facing + " went on top of the line at " + at, at.above());
            }
        }
        if (player.getMainHandItem().getCount() != 1) {
            helper.fail("a refused splitter was spent", LEFT);
        }
        helper.succeed();
    }

    /** Both halves fed by tile lines with no line leaving either, then broken at one half. */
    private static void breaks(GameTestHelper helper, BlockPos broken) {
        placeSplitter(helper, BeltTier.BELT);
        feed(helper, SOURCE, FROM, BeltTier.BELT);
        feed(helper, SOURCE_RIGHT, FROM_RIGHT, BeltTier.BELT);
        ServerPlayer player = player(helper, "beltworks_splitter_tile_breaker");
        helper.startSequence()
                .thenIdle(WARMUP_TICKS)
                .thenExecute(() -> {
                    int onSplitter = half(helper, LEFT) + half(helper, RIGHT);
                    if (onSplitter != BACKED_UP) {
                        helper.fail("the splitter holds " + onSplitter + ", expected " + BACKED_UP, LEFT);
                    }
                    player.gameMode.destroyBlock(helper.absolutePos(broken));
                    for (BlockPos pos : new BlockPos[] {LEFT, RIGHT}) {
                        if (helper.getBlockState(pos).getBlock() instanceof SplitterBlock) {
                            helper.fail("breaking one half left the other standing", pos);
                        }
                    }
                    int handed = cobblestone(player);
                    if (handed != onSplitter || onGround(helper) != 0) {
                        helper.fail("breaking the splitter handed the breaker " + handed + " of its " + onSplitter
                                + " items and left " + onGround(helper) + " on the ground", broken);
                    }
                    int splitters = helper.getLevel().getEntities(EntityType.ITEM, area(helper), e -> true).stream()
                            .map(ItemEntity::getItem)
                            .filter(stack -> stack.is(ItemContent.SPLITTER.get()))
                            .mapToInt(ItemStack::getCount)
                            .sum();
                    if (splitters != 1) {
                        helper.fail("breaking the splitter dropped " + splitters + " splitters where exactly one is paid", broken);
                    }
                })
                .thenSucceed();
    }

    /**
     * A tile line into the left half, splitters back to back from it, and a tile line out of each
     * last half to a loader with a chest behind it or not.
     */
    private static void line(GameTestHelper helper, BeltTier belts, boolean leftDrains, boolean rightDrains, BeltTier... splitters) {
        if (belts != BeltTier.BELT) LoaderPower.feed(helper);
        for (int i = 0; i < splitters.length; i++) placeSplitter(helper, splitters[i], LEFT.east(i));
        feed(helper, SOURCE, FROM, belts);
        outputs(helper, belts, LEFT.getX() + splitters.length, leftDrains, rightDrains);
    }

    /** A tile line from {@code fromX} on each half's row to a loader, with a chest behind it or not. */
    private static void outputs(GameTestHelper helper, BeltTier belts, int fromX, boolean leftDrains, boolean rightDrains) {
        for (BlockPos end : new BlockPos[] {LEFT_END, RIGHT_END}) {
            for (BlockPos at = new BlockPos(fromX, 1, end.getZ()); at.getX() < end.getX(); at = at.east()) {
                helper.setBlock(at, BeltTileTests.tile(belts, Direction.EAST));
            }
            helper.setBlock(end, BeltTileTests.loader(belts, Direction.WEST));
        }
        if (leftDrains) helper.setBlock(LEFT_END.east(), Blocks.CHEST);
        if (rightDrains) helper.setBlock(RIGHT_END.east(), Blocks.CHEST);
    }

    /** A chest and an east-facing loader, and tiles from the loader to the splitter half on its row. */
    private static void feed(GameTestHelper helper, BlockPos source, BlockPos from, BeltTier tier) {
        helper.setBlock(source, Blocks.CHEST);
        BeltTileTests.fill(helper, source, SUPPLY);
        helper.setBlock(from, BeltTileTests.loader(tier, Direction.EAST));
        for (BlockPos at = from.east(); at.getX() < LEFT.getX(); at = at.east()) {
            helper.setBlock(at, BeltTileTests.tile(tier, Direction.EAST));
        }
    }

    private static void measure(GameTestHelper helper, Check check) {
        measure(helper, LEFT_END, RIGHT_END, check);
    }

    /** What reaches the chest past each end in a window after the warmup. */
    private static void measure(GameTestHelper helper, BlockPos leftEnd, BlockPos rightEnd, Check check) {
        int[] before = new int[2];
        helper.startSequence()
                .thenIdle(WARMUP_TICKS)
                .thenExecute(() -> {
                    before[0] = delivered(helper, leftEnd);
                    before[1] = delivered(helper, rightEnd);
                })
                .thenIdle(WINDOW_TICKS)
                .thenExecute(() -> check.accept(delivered(helper, leftEnd) - before[0],
                        delivered(helper, rightEnd) - before[1]))
                .thenSucceed();
    }

    private static int delivered(GameTestHelper helper, BlockPos end) {
        return helper.getBlockState(end.east()).is(Blocks.CHEST)
                ? BeltTileTests.count(BeltTileTests.chest(helper, end.east())) : 0;
    }

    /** Every item on a tile of the splitter's row, each line counted once. */
    private static int onLines(GameTestHelper helper) {
        var counted = new HashSet<Object>();
        int items = 0;
        for (BlockPos at = FROM.east(); at.getX() < LEFT_END.getX(); at = at.east()) {
            if (!(helper.getLevel().getBlockEntity(helper.absolutePos(at)) instanceof BeltTileBlockEntity tile)) continue;
            var line = tile.line();
            if (line != null && counted.add(line)) items += line.size();
        }
        return items;
    }

    private static int half(GameTestHelper helper, BlockPos pos) {
        return helper.getBlockEntity(pos, BeltEndBlockEntity.class).getHalf().size();
    }

    private static int onGround(GameTestHelper helper) {
        return helper.getLevel().getEntities(EntityType.ITEM, area(helper), e -> true).stream()
                .map(ItemEntity::getItem)
                .filter(stack -> stack.is(Items.COBBLESTONE))
                .mapToInt(ItemStack::getCount)
                .sum();
    }

    private static AABB area(GameTestHelper helper) {
        return new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(16);
    }

    private static void placeSplitter(GameTestHelper helper, BeltTier tier) {
        placeSplitter(helper, tier, LEFT);
    }

    private static void placeSplitter(GameTestHelper helper, BeltTier tier, BlockPos left) {
        helper.setBlock(left, half(tier, SplitterBlock.Side.LEFT));
        helper.setBlock(left.relative(Direction.EAST.getClockWise()), half(tier, SplitterBlock.Side.RIGHT));
    }

    private static BlockState half(BeltTier tier, SplitterBlock.Side side) {
        return half(tier, side, Direction.EAST);
    }

    private static BlockState half(BeltTier tier, SplitterBlock.Side side, Direction facing) {
        return BlockContent.splitterFor(tier).defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, facing)
                .setValue(SplitterBlock.SIDE, side);
    }

    // A player of its own: the shared fake player's inventory is every test's at once.
    private static ServerPlayer player(GameTestHelper helper, String name) {
        ServerPlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
        player.setGameMode(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        var at = helper.absoluteVec(STANDING.getBottomCenter());
        player.setPos(at.x, at.y, at.z);
        return player;
    }

    private static int cobblestone(ServerPlayer player) {
        return ContainerHelper.clearOrCountMatchingItems(player.getInventory(),
                stack -> stack.is(Items.COBBLESTONE), 0, true);
    }

    @FunctionalInterface
    private interface Check {
        void accept(int left, int right);
    }
}
