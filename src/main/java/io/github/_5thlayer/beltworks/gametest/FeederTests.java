// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import io.github._5thlayer.beltworks.BlockContent;
import io.github._5thlayer.beltworks.blocks.BeltEndBlockEntity;
import io.github._5thlayer.beltworks.blocks.BeltTileBlock;
import io.github._5thlayer.beltworks.blocks.BeltTileBlockEntity;
import io.github._5thlayer.beltworks.blocks.FeederBlockEntity;
import io.github._5thlayer.beltworks.blocks.FeederReach;
import io.github._5thlayer.beltworks.blocks.SplitterBlock;
import io.github._5thlayer.beltworks.model.BeltContents;
import io.github._5thlayer.beltworks.model.BeltTier;
import io.github._5thlayer.beltworks.model.FeederArms;

import java.util.List;

/**
 * A feeder of every tier moves one item at a time from the chest its head reaches to the one its
 * tail reaches, at a tenth of its tier's loader, for free while loaders need no power and paying FE
 * for each item at every tier while they do, and takes only what its filter matches. Either
 * end may be a level tile anywhere along a line, straight or corner, or a splitter half, and never a
 * slope or a loader. Tiles are placed downstream first, as in {@link BeltCornerTests}.
 */
final class FeederTests {

    private static final BlockPos HEAD = new BlockPos(2, 1, 2);
    private static final BlockPos FEEDER = HEAD.east();
    private static final BlockPos TAIL = FEEDER.east();

    private static final int WARMUP_TICKS = 20;
    private static final int WINDOW_TICKS = 200;
    private static final long JOULES_PER_FE = 100;

    // A row east from a chest and a loader through five tiles, then a loader and a chest or nothing.
    private static final BlockPos ROW_SOURCE = new BlockPos(1, 1, 2);
    private static final BlockPos ROW_FROM = ROW_SOURCE.east();
    private static final BlockPos ROW_FIRST = ROW_FROM.east();
    private static final int ROW_TILES = 5;
    private static final BlockPos ROW_MIDDLE = ROW_FIRST.east(2);
    private static final BlockPos ROW_TO = ROW_FIRST.east(ROW_TILES);
    private static final BlockPos ROW_TARGET = ROW_TO.east();
    private static final int ROW_SUPPLY = 64;
    // Long enough that the loader has filled the row and it has backed up.
    private static final int ROW_FILL_TICKS = 160;
    private static final int SEEN_WAITING_TICKS = 100;
    private static final int DRAIN_TICKS = 400;
    private static final int DROPPED = 16;

    // A splitter across the row's end, halves facing east.
    private static final BlockPos LEFT = ROW_FIRST.east(3);
    private static final BlockPos RIGHT = LEFT.relative(Direction.EAST.getClockWise());

    // Two east-running tiles turning south through a corner, and two tiles south of it.
    private static final BlockPos CORNER = new BlockPos(5, 1, 2);
    private static final int CORNER_INDEX = 2;
    private static final double TIER_1_BLOCKS_PER_TICK = 0.09375;

    // A foot, a top and a level tile, fed by a loader from a chest, as in BeltTileTests.
    private static final BlockPos SLOPE_SOURCE = new BlockPos(2, 1, 3);
    private static final BlockPos FOOT = SLOPE_SOURCE.east(2);
    private static final int SLOPE_TICKS = 200;

    // A feeder whose arms reach three blocks each, over stone, and one whose tail turns left.
    private static final BlockPos REACHING = new BlockPos(4, 1, 2);
    private static final BlockPos FAR_HEAD = REACHING.west(3);
    private static final BlockPos FAR_TAIL = REACHING.east(3);
    private static final BlockPos TURNED_TAIL = REACHING.north(2);

    private FeederTests() {
    }

    static void register(BeltGameTests.Registrar tests) {
        var loadersUnpowered = tests.withLoaderPower(false);
        var loadersPowered = tests.withLoaderPower(true);
        for (var tier : BeltTier.values()) {
            loadersUnpowered.test("unfed_tier_" + tier.number() + "_feeder_moves_at_a_tenth_of_its_loaders_rate_when_loaders_need_no_power",
                    WARMUP_TICKS + WINDOW_TICKS + 20, helper -> movesAtRate(helper, tier, false));
            loadersPowered.test("fed_tier_" + tier.number() + "_feeder_moves_chest_to_chest_at_a_tenth_of_its_loaders_rate",
                    WARMUP_TICKS + WINDOW_TICKS + 20, helper -> movesAtRate(helper, tier, true));
            loadersPowered.test("fed_tier_" + tier.number() + "_feeder_draws_its_own_fe_per_item",
                    WARMUP_TICKS + WINDOW_TICKS + 20, helper -> drawsPerItem(helper, tier));
            loadersPowered.test("unfed_tier_" + tier.number() + "_feeder_moves_nothing_when_loaders_need_power", 80,
                    helper -> stallsUnfed(helper, tier));
        }
        loadersUnpowered.test("a_feeders_filter_limits_what_it_moves", WARMUP_TICKS + WINDOW_TICKS + 20,
                FeederTests::filterLimitsWhatMoves);
        loadersUnpowered.test("feeder_takes_from_a_mid_line_tile_and_the_line_runs_on", DRAIN_TICKS + 20,
                FeederTests::takesMidLine);
        loadersUnpowered.test("feeder_drops_at_a_corner_tiles_midpoint", 120, FeederTests::dropsAtTheMidpoint);
        loadersUnpowered.test("feeder_waits_to_drop_while_the_tile_has_no_gap",
                ROW_FILL_TICKS + SEEN_WAITING_TICKS + DRAIN_TICKS + 20, FeederTests::waitsForAGap);
        loadersUnpowered.test("feeder_drops_onto_a_splitter_half", DRAIN_TICKS + 20, FeederTests::dropsOntoAHalf);
        loadersUnpowered.test("feeder_takes_from_a_splitter_half", DRAIN_TICKS + 20, FeederTests::takesFromAHalf);
        loadersUnpowered.test("feeder_with_a_slope_at_an_end_moves_nothing", SLOPE_TICKS + 20, FeederTests::slopeIsNoEnd);
        loadersUnpowered.test("feeder_with_a_loader_at_an_end_moves_nothing", 100, FeederTests::loaderIsNoEnd);
        loadersUnpowered.test("feeder_head_takes_a_dropped_item_and_delivers_it", 80, FeederTests::takesLoose);
        loadersUnpowered.test("feeder_tail_aimed_at_air_waits_and_spawns_nothing", 80, FeederTests::tailWaitsInAir);
        tests.test("head_reach_and_tail_reach_lengthen_a_placed_feeders_arm_one_to_three_and_back", 20,
                FeederTests::reachesInPlace);
        tests.test("a_held_feeders_reach_is_what_each_placement_takes_until_its_last_item", 20,
                FeederTests::heldReachPlaces);
        tests.test("a_feeders_filter_and_arms_reach_the_client_in_its_update_tag", 20, FeederTests::updateTagCarriesFilterAndArms);
        loadersUnpowered.test("two_feeders_on_one_tile_both_take", WARMUP_TICKS + WINDOW_TICKS + 20,
                FeederTests::twoFeedersShareATile);
        loadersUnpowered.test("feeder_reaching_3_over_stone_moves_at_1_5_items_per_second",
                WARMUP_TICKS + WINDOW_TICKS + 20, FeederTests::reachesThree);
        loadersUnpowered.test("feeder_with_a_left_turned_tail_drops_to_its_left", 120, FeederTests::turnsItsTail);
    }

    // A window passes exactly its rate's items: every tier's rate is a whole number over these ten seconds.
    private static void movesAtRate(GameTestHelper helper, BeltTier tier, boolean fed) {
        int expected = (int) Math.round(tier.feederItemsPerSecond() * WINDOW_TICKS / 20);
        if (fed) LoaderPower.feed(helper);
        place(helper, tier);
        int[] before = new int[1];
        helper.startSequence()
                .thenIdle(WARMUP_TICKS)
                .thenExecute(() -> before[0] = count(chest(helper, TAIL)))
                .thenIdle(WINDOW_TICKS)
                .thenExecute(() -> {
                    int delivered = count(chest(helper, TAIL)) - before[0];
                    if (delivered != expected) {
                        helper.fail("a " + (fed ? "fed" : "unfed") + " tier-" + tier.number() + " feeder delivered " + delivered + " items in "
                                + WINDOW_TICKS + " ticks, expected " + expected, TAIL);
                    }
                })
                .thenSucceed();
    }

    private static void stallsUnfed(GameTestHelper helper, BeltTier tier) {
        place(helper, tier);
        helper.startSequence().thenIdle(60).thenExecute(() -> {
            if (face(helper) == null) helper.fail("a feeder has no energy face", FEEDER);
            if (count(chest(helper, TAIL)) != 0) {
                helper.fail("an unfed feeder moved " + count(chest(helper, TAIL)) + " items", TAIL);
            }
        }).thenSucceed();
    }

    // The supply is cut and the buffer filled by hand, so the refill's order against the tick can't skew a read.
    private static void drawsPerItem(GameTestHelper helper, BeltTier tier) {
        var power = LoaderPower.feed(helper);
        place(helper, tier);
        long[] before = new long[2];
        helper.startSequence()
                .thenIdle(WARMUP_TICKS)
                .thenExecute(() -> {
                    power.stop();
                    helper.getBlockEntity(FEEDER, FeederBlockEntity.class)
                            .getEnergy().insertFe(Long.MAX_VALUE);
                    before[0] = face(helper).getAmountAsLong();
                    before[1] = count(chest(helper, TAIL));
                })
                .thenIdle(WINDOW_TICKS)
                .thenExecute(() -> {
                    long items = count(chest(helper, TAIL)) - before[1];
                    if (items == 0) helper.fail("the feeder moved nothing in the window, so this proves nothing", FEEDER);
                    long drawn = before[0] - face(helper).getAmountAsLong();
                    long joules = items * tier.feederJoulesPerItem();
                    if (Math.abs(drawn * JOULES_PER_FE - joules) >= JOULES_PER_FE) {
                        helper.fail("the feeder drew " + drawn + " FE for " + items + " items, expected "
                                + joules / (double) JOULES_PER_FE, FEEDER);
                    }
                })
                .thenSucceed();
    }

    // A feeder south of the row's middle tile takes into a chest while the rest runs on to the target.
    private static void takesMidLine(GameTestHelper helper) {
        LoaderPower.feed(helper);
        row(helper, true);
        feeder(helper, ROW_MIDDLE.south(), Direction.SOUTH);
        helper.setBlock(ROW_MIDDLE.south(2), Blocks.CHEST);
        fill(helper, ROW_SOURCE, Items.COBBLESTONE, ROW_SUPPLY);
        helper.startSequence().thenIdle(DRAIN_TICKS).thenExecute(() -> {
            int taken = count(chest(helper, ROW_MIDDLE.south(2)));
            int through = count(chest(helper, ROW_TARGET));
            if (taken == 0 || through == 0 || taken + through != ROW_SUPPLY || dropped(helper) != 0) {
                helper.fail("a feeder on a mid-line tile took " + taken + " and the line ran " + through + " on, of "
                        + ROW_SUPPLY + ", with " + dropped(helper) + " on the ground", ROW_MIDDLE);
            }
        }).thenSucceed();
    }

    // A feeder east of a corner, facing west into it from a chest; seen once its first item is on the line.
    private static void dropsAtTheMidpoint(GameTestHelper helper) {
        LoaderPower.feed(helper);
        helper.setBlock(CORNER.south(2), BeltTileTests.tile(BeltTier.BELT, Direction.SOUTH));
        helper.setBlock(CORNER.south(), BeltTileTests.tile(BeltTier.BELT, Direction.SOUTH));
        helper.setBlock(CORNER, BeltTileTests.tile(BeltTier.BELT, Direction.SOUTH));
        helper.setBlock(CORNER.west(), BeltTileTests.tile(BeltTier.BELT, Direction.EAST));
        helper.setBlock(CORNER.west(2), BeltTileTests.tile(BeltTier.BELT, Direction.EAST));
        feeder(helper, CORNER.east(), Direction.WEST);
        helper.setBlock(CORNER.east(2), Blocks.CHEST);
        fill(helper, CORNER.east(2), Items.IRON_INGOT, 1);
        double[] first = {Double.NaN};
        helper.onEachTick(() -> {
            var line = helper.getBlockEntity(CORNER, BeltTileBlockEntity.class).line();
            if (Double.isNaN(first[0]) && line != null && line.size() > 0) {
                first[0] = line.contents().entries().getFirst().position();
            }
        });
        helper.startSequence().thenIdle(100).thenExecute(() -> {
            if (helper.getBlockState(CORNER).getValue(BeltTileBlock.CORNER) == BeltTileBlock.Shape.STRAIGHT) {
                helper.fail("the tile the feeder drops on is no corner, so this proves nothing", CORNER);
            }
            // Where it was dropped, or moved on by a tick when the line ticked after the feeder.
            double midpoint = CORNER_INDEX + 0.5 - BeltContents.SPACING / 2;
            double slack = BeltContents.SPACING / 2;
            if (!(first[0] >= midpoint - slack && first[0] <= midpoint + slack + TIER_1_BLOCKS_PER_TICK)) {
                helper.fail("a feeder dropped its item at " + first[0] + " along the line, expected the corner's midpoint "
                        + midpoint, CORNER);
            }
        }).thenSucceed();
    }

    // A backed-up row with a feeder dropping onto its middle from the south; then the row's end opens.
    private static void waitsForAGap(GameTestHelper helper) {
        LoaderPower.feed(helper);
        row(helper, false);
        fill(helper, ROW_SOURCE, Items.COBBLESTONE, ROW_SUPPLY);
        BlockPos from = ROW_MIDDLE.south(2);
        helper.startSequence()
                .thenIdle(ROW_FILL_TICKS)
                .thenExecute(() -> {
                    helper.setBlock(from, Blocks.CHEST);
                    fill(helper, from, Items.IRON_INGOT, DROPPED);
                    feeder(helper, ROW_MIDDLE.south(), Direction.NORTH);
                })
                .thenIdle(SEEN_WAITING_TICKS)
                .thenExecute(() -> {
                    int waiting = count(chest(helper, from));
                    int held = held(helper, ROW_MIDDLE);
                    if (waiting != DROPPED || held != ROW_TILES * 8) {
                        helper.fail("onto a backed-up line of " + held + " a feeder dropped " + (DROPPED - waiting)
                                + " items, expected it to wait", ROW_MIDDLE);
                    }
                    helper.setBlock(ROW_TARGET, Blocks.CHEST);
                    helper.setBlock(ROW_TO, BeltTileTests.loader(BeltTier.BELT, Direction.WEST));
                })
                .thenIdle(DRAIN_TICKS)
                .thenExecute(() -> {
                    int iron = count(chest(helper, ROW_TARGET), Items.IRON_INGOT);
                    int cobblestone = count(chest(helper, ROW_TARGET), Items.COBBLESTONE);
                    if (iron != DROPPED || cobblestone != ROW_SUPPLY || dropped(helper) != 0) {
                        helper.fail("once the line ran, " + iron + " of " + DROPPED + " dropped items and " + cobblestone
                                + " of " + ROW_SUPPLY + " from the line reached its end, with " + dropped(helper)
                                + " on the ground", ROW_TARGET);
                    }
                })
                .thenSucceed();
    }

    // A feeder north of a splitter's left half drops from a chest; the half hands on to a tile and a chest.
    private static void dropsOntoAHalf(GameTestHelper helper) {
        LoaderPower.feed(helper);
        helper.setBlock(ROW_TARGET, Blocks.CHEST);
        helper.setBlock(ROW_TO, BeltTileTests.loader(BeltTier.BELT, Direction.WEST));
        helper.setBlock(LEFT.east(), BeltTileTests.tile(BeltTier.BELT, Direction.EAST));
        splitter(helper);
        feeder(helper, LEFT.north(), Direction.SOUTH);
        helper.setBlock(LEFT.north(2), Blocks.CHEST);
        fill(helper, LEFT.north(2), Items.IRON_INGOT, DROPPED);
        helper.startSequence().thenIdle(DRAIN_TICKS).thenExecute(() -> {
            int delivered = count(chest(helper, ROW_TARGET));
            int right = helper.getBlockEntity(RIGHT, BeltEndBlockEntity.class).getHalf().size();
            if (delivered != DROPPED || right != 0) {
                helper.fail("a feeder dropping onto a splitter half got " + delivered + " of " + DROPPED
                        + " through its front, and " + right + " onto the other half", ROW_TARGET);
            }
        }).thenSucceed();
    }

    // Three tiles into a splitter with nothing past it, and a feeder north of its left half taking into a chest.
    private static void takesFromAHalf(GameTestHelper helper) {
        LoaderPower.feed(helper);
        splitter(helper);
        for (int tile = 2; tile >= 0; tile--) helper.setBlock(ROW_FIRST.east(tile), BeltTileTests.tile(BeltTier.BELT, Direction.EAST));
        helper.setBlock(ROW_FROM, BeltTileTests.loader(BeltTier.BELT, Direction.EAST));
        helper.setBlock(ROW_SOURCE, Blocks.CHEST);
        feeder(helper, LEFT.north(), Direction.NORTH);
        helper.setBlock(LEFT.north(2), Blocks.CHEST);
        fill(helper, ROW_SOURCE, Items.COBBLESTONE, DROPPED);
        helper.startSequence().thenIdle(DRAIN_TICKS).thenExecute(() -> {
            int taken = count(chest(helper, LEFT.north(2)));
            // The backed-up right half keeps what the splitter sent it; everything else is the feeder's.
            int right = helper.getBlockEntity(RIGHT, BeltEndBlockEntity.class).getHalf().size();
            int left = helper.getBlockEntity(LEFT, BeltEndBlockEntity.class).getHalf().size();
            if (taken == 0 || taken + right != DROPPED || left != 0 || held(helper, ROW_FIRST) != 0) {
                helper.fail("a feeder on a splitter half took " + taken + " of " + DROPPED + ", leaving " + left
                        + " on its half and " + right + " on the other", LEFT);
            }
        }).thenSucceed();
    }

    // A line over a foot, with a feeder north of the foot taking from it and one south dropping onto it.
    private static void slopeIsNoEnd(GameTestHelper helper) {
        LoaderPower.feed(helper);
        helper.setBlock(SLOPE_SOURCE, Blocks.CHEST);
        helper.setBlock(SLOPE_SOURCE.east(), BeltTileTests.loader(BeltTier.BELT, Direction.EAST));
        BeltTileTests.climb(helper, BeltTier.BELT, FOOT, 3, 1);
        fill(helper, SLOPE_SOURCE, Items.COBBLESTONE, ROW_SUPPLY);
        feeder(helper, FOOT.north(), Direction.NORTH);
        helper.setBlock(FOOT.north(2), Blocks.CHEST);
        feeder(helper, FOOT.south(), Direction.NORTH);
        helper.setBlock(FOOT.south(2), Blocks.CHEST);
        fill(helper, FOOT.south(2), Items.IRON_INGOT, DROPPED);
        helper.startSequence().thenIdle(SLOPE_TICKS).thenExecute(() -> {
            if (BeltTileTests.pitch(helper, FOOT) != BeltTileBlock.PitchState.FOOT_UP) {
                helper.fail("the tile the feeders reach is " + BeltTileTests.pitch(helper, FOOT) + ", not a foot", FOOT);
            }
            int taken = count(chest(helper, FOOT.north(2)));
            int dropped = DROPPED - count(chest(helper, FOOT.south(2)));
            if (taken != 0 || dropped != 0 || held(helper, FOOT) == 0) {
                helper.fail("feeders took " + taken + " from a slope and dropped " + dropped + " onto it", FOOT);
            }
        }).thenSucceed();
    }

    // A feeder taking from a loader, and one dropping into a loader, each with a chest behind the loader.
    private static void loaderIsNoEnd(GameTestHelper helper) {
        LoaderPower.feed(helper);
        BlockPos headLoader = new BlockPos(10, 1, 5);
        helper.setBlock(headLoader.west(), Blocks.CHEST);
        fill(helper, headLoader.west(), Items.COBBLESTONE, DROPPED);
        helper.setBlock(headLoader, BeltTileTests.loader(BeltTier.BELT, Direction.EAST));
        helper.spawnItem(Items.DIRT, headLoader.getX() + 0.5f, headLoader.getY(), headLoader.getZ() + 0.5f);
        feeder(helper, headLoader.east(), Direction.EAST);
        helper.setBlock(headLoader.east(2), Blocks.CHEST);

        BlockPos tailLoader = new BlockPos(17, 1, 5);
        helper.setBlock(tailLoader.west(2), Blocks.CHEST);
        fill(helper, tailLoader.west(2), Items.COBBLESTONE, DROPPED);
        feeder(helper, tailLoader.west(), Direction.EAST);
        helper.setBlock(tailLoader, BeltTileTests.loader(BeltTier.BELT, Direction.WEST));
        helper.setBlock(tailLoader.east(), Blocks.CHEST);
        helper.startSequence().thenIdle(80).thenExecute(() -> {
            int fromLoader = count(chest(helper, headLoader.east(2)));
            if (dropped(helper) == 0) helper.fail("the head took an item lying on a loader", headLoader);
            int intoLoader = count(chest(helper, tailLoader.east()));
            if (fromLoader != 0 || intoLoader != 0) {
                helper.fail("a feeder took " + fromLoader + " through a loader and put " + intoLoader + " into one",
                        headLoader);
            }
        }).thenSucceed();
    }

    private static void takesLoose(GameTestHelper helper) {
        LoaderPower.feed(helper);
        helper.setBlock(TAIL, Blocks.CHEST);
        feeder(helper, FEEDER, Direction.EAST);
        helper.spawnItem(Items.COBBLESTONE, HEAD.getX() + 0.5f, HEAD.getY(), HEAD.getZ() + 0.5f);
        helper.startSequence().thenIdle(60).thenExecute(() -> {
            int arrived = count(chest(helper, TAIL), Items.COBBLESTONE);
            if (arrived != 1) helper.fail("the head delivered " + arrived + " of the 1 item dropped at its target", TAIL);
            if (dropped(helper) != 0) helper.fail("the dropped item is still lying there", HEAD);
        }).thenSucceed();
    }

    // The head has a chest to take from, and the tail only air: the feeder must not drop anything loose.
    private static void tailWaitsInAir(GameTestHelper helper) {
        LoaderPower.feed(helper);
        helper.setBlock(HEAD, Blocks.CHEST);
        fill(helper, HEAD, Items.COBBLESTONE, DROPPED);
        feeder(helper, FEEDER, Direction.EAST);
        helper.startSequence().thenIdle(60).thenExecute(() -> {
            if (dropped(helper) != 0) helper.fail("a feeder tail dropped " + dropped(helper) + " items loose", TAIL);
            if (count(chest(helper, HEAD), Items.COBBLESTONE) != DROPPED) {
                helper.fail("a feeder with nothing to drop into took from its head", HEAD);
            }
        }).thenSucceed();
    }

    // A feeder each side of the row's middle tile, each into a chest of its own, while the row runs on.
    private static void twoFeedersShareATile(GameTestHelper helper) {
        LoaderPower.feed(helper);
        row(helper, true);
        feeder(helper, ROW_MIDDLE.south(), Direction.SOUTH);
        helper.setBlock(ROW_MIDDLE.south(2), Blocks.CHEST);
        feeder(helper, ROW_MIDDLE.north(), Direction.NORTH);
        helper.setBlock(ROW_MIDDLE.north(2), Blocks.CHEST);
        fill(helper, ROW_SOURCE, Items.COBBLESTONE, 27 * 64);
        helper.startSequence().thenIdle(WARMUP_TICKS + WINDOW_TICKS).thenExecute(() -> {
            int south = count(chest(helper, ROW_MIDDLE.south(2)));
            int north = count(chest(helper, ROW_MIDDLE.north(2)));
            if (south == 0 || north == 0 || Math.abs(south - north) > 1) {
                helper.fail("two feeders on one tile took " + south + " and " + north + ", expected an even share",
                        ROW_MIDDLE);
            }
        }).thenSucceed();
    }

    // Stone between each arm's end and the feeder, which an arm passes over at the rate of reach 1.
    private static void reachesThree(GameTestHelper helper) {
        LoaderPower.feed(helper);
        helper.setBlock(FAR_HEAD, Blocks.CHEST);
        helper.setBlock(FAR_TAIL, Blocks.CHEST);
        for (int step = 1; step < 3; step++) {
            helper.setBlock(REACHING.west(step), Blocks.STONE);
            helper.setBlock(REACHING.east(step), Blocks.STONE);
        }
        fill(helper, FAR_HEAD, Items.COBBLESTONE, 256);
        feeder(helper, REACHING, Direction.EAST);
        helper.getBlockEntity(REACHING, FeederBlockEntity.class).setArms(new FeederArms(3, 3, FeederArms.Turn.STRAIGHT));
        int[] before = new int[1];
        helper.startSequence()
                .thenIdle(WARMUP_TICKS)
                .thenExecute(() -> before[0] = count(chest(helper, FAR_TAIL)))
                .thenIdle(WINDOW_TICKS)
                .thenExecute(() -> {
                    int expected = (int) Math.round(BeltTier.BELT.feederItemsPerSecond() * WINDOW_TICKS / 20);
                    int delivered = count(chest(helper, FAR_TAIL)) - before[0];
                    if (delivered != expected || dropped(helper) != 0) {
                        helper.fail("a feeder reaching 3 delivered " + delivered + " items in " + WINDOW_TICKS
                                + " ticks, expected " + expected, FAR_TAIL);
                    }
                })
                .thenSucceed();
    }

    // An east-facing feeder whose tail turns left reaches the chest two blocks north, not the one ahead.
    private static void turnsItsTail(GameTestHelper helper) {
        LoaderPower.feed(helper);
        helper.setBlock(REACHING.west(), Blocks.CHEST);
        helper.setBlock(REACHING.east(), Blocks.CHEST);
        helper.setBlock(TURNED_TAIL, Blocks.CHEST);
        fill(helper, REACHING.west(), Items.COBBLESTONE, 64);
        feeder(helper, REACHING, Direction.EAST);
        helper.getBlockEntity(REACHING, FeederBlockEntity.class).setArms(new FeederArms(1, 2, FeederArms.Turn.LEFT));
        helper.startSequence().thenIdle(100).thenExecute(() -> {
            int turned = count(chest(helper, TURNED_TAIL));
            int ahead = count(chest(helper, REACHING.east()));
            if (turned == 0 || ahead != 0) {
                helper.fail("a left-turned tail dropped " + turned + " to its left and " + ahead + " ahead", TURNED_TAIL);
            }
        }).thenSucceed();
    }

    // Each press lengthens only its own arm, from one block to three and back, and tells the new reach.
    private static void reachesInPlace(GameTestHelper helper) {
        feeder(helper, FEEDER, Direction.EAST);
        var placed = helper.getBlockEntity(FEEDER, FeederBlockEntity.class);
        var player = new ListeningPlayer(helper, FEEDER.north(2));
        var expected = FeederArms.ADJACENT;
        for (var arm : FeederArms.Arm.values()) {
            for (int press = 1; press <= 3; press++) {
                FeederReach.press(player, helper.absolutePos(FEEDER), arm);
                expected = expected.lengthened(arm);
                if (!placed.arms().equals(expected)) {
                    helper.fail(arm + " press " + press + " left " + placed.arms() + ", expected " + expected, FEEDER);
                }
            }
        }
        if (!expected.equals(FeederArms.ADJACENT)) helper.fail("three presses of each did not wrap to the start", FEEDER);
        if (helper.getBlockEntity(FEEDER, FeederBlockEntity.class) != placed) helper.fail("a press replaced the feeder", FEEDER);
        if (player.heard.size() != 6) helper.fail("six presses told " + player.heard, FEEDER);
        helper.succeed();
    }

    // A held press sets the stack's reach and leaves the aimed feeder alone; both feeders placed from the stack take it.
    private static void heldReachPlaces(GameTestHelper helper) {
        feeder(helper, FEEDER, Direction.EAST);
        var player = new ListeningPlayer(helper, FEEDER.north(2));
        var stack = new ItemStack(BlockContent.FEEDER_BLOCK.get(), 2);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        FeederReach.press(player, helper.absolutePos(FEEDER), FeederArms.Arm.HEAD);
        FeederReach.press(player, helper.absolutePos(FEEDER), FeederArms.Arm.TAIL);
        FeederReach.press(player, helper.absolutePos(FEEDER), FeederArms.Arm.TAIL);
        var held = new FeederArms(2, 3, FeederArms.Turn.STRAIGHT);
        if (!FeederReach.held(stack).equals(held)) helper.fail("presses left the held reach " + FeederReach.held(stack), FEEDER);
        if (!helper.getBlockEntity(FEEDER, FeederBlockEntity.class).arms().equals(FeederArms.ADJACENT)) {
            helper.fail("a held press changed the aimed feeder", FEEDER);
        }
        for (var ground : List.of(HEAD.south(2), TAIL.south(2))) {
            helper.setBlock(ground, Blocks.STONE);
            player.gameMode.useItemOn(player, helper.getLevel(), stack, InteractionHand.MAIN_HAND, hit(helper, ground));
            var arms = helper.getBlockEntity(ground.above(), FeederBlockEntity.class).arms();
            if (!arms.equals(held)) helper.fail("a feeder placed from the stack reaches " + arms + ", not " + held, ground.above());
        }
        if (!stack.isEmpty()) helper.fail("the stack kept " + stack.getCount() + " feeders after two placements", FEEDER);
        helper.succeed();
    }

    // A client learns a feeder's filter and arms only from the update tag it is sent, as it loads one,
    // so a feeder read from that tag draws them; each change sends it.
    private static void updateTagCarriesFilterAndArms(GameTestHelper helper) {
        feeder(helper, FEEDER, Direction.EAST);
        var placed = helper.getBlockEntity(FEEDER, FeederBlockEntity.class);
        var player = new ListeningPlayer(helper, FEEDER.north(2));
        BlockUpdateWatch.watch(helper.absolutePos(FEEDER));
        placed.assignFilterItem(new ItemStack(Items.DIRT), player);
        FeederReach.press(player, helper.absolutePos(FEEDER), FeederArms.Arm.HEAD);
        FeederReach.press(player, helper.absolutePos(FEEDER), FeederArms.Arm.TAIL);
        FeederReach.press(player, helper.absolutePos(FEEDER), FeederArms.Arm.TAIL);
        placed.setArms(placed.arms().withTailTurn(FeederArms.Turn.LEFT));
        var updates = BlockUpdateWatch.stop(helper.absolutePos(FEEDER));
        if (updates < 5) helper.fail("five changes sent " + updates + " block updates", FEEDER);

        var registries = helper.getLevel().registryAccess();
        var client = new FeederBlockEntity(helper.absolutePos(FEEDER), helper.getBlockState(FEEDER));
        client.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, registries, placed.getUpdateTag(registries)));
        if (!client.filteredItem().is(Items.DIRT)) helper.fail("the update tag carried the filter " + client.filteredItem(), FEEDER);
        var arms = new FeederArms(2, 3, FeederArms.Turn.LEFT);
        if (!client.arms().equals(arms)) helper.fail("the update tag carried the arms " + client.arms() + ", not " + arms, FEEDER);

        placed.resetFilterItem(player);
        client.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, registries, placed.getUpdateTag(registries)));
        if (!client.filteredItem().isEmpty()) helper.fail("the update tag kept a cleared filter " + client.filteredItem(), FEEDER);
        helper.succeed();
    }

    /** The row, placed downstream first, ending in a loader and a chest or in nothing. */
    private static void row(GameTestHelper helper, boolean withEnd) {
        if (withEnd) {
            helper.setBlock(ROW_TARGET, Blocks.CHEST);
            helper.setBlock(ROW_TO, BeltTileTests.loader(BeltTier.BELT, Direction.WEST));
        }
        for (int tile = ROW_TILES - 1; tile >= 0; tile--) {
            helper.setBlock(ROW_FIRST.east(tile), BeltTileTests.tile(BeltTier.BELT, Direction.EAST));
        }
        helper.setBlock(ROW_FROM, BeltTileTests.loader(BeltTier.BELT, Direction.EAST));
        helper.setBlock(ROW_SOURCE, Blocks.CHEST);
    }

    private static void splitter(GameTestHelper helper) {
        var left = BlockContent.splitterFor(BeltTier.BELT).defaultBlockState()
                .setValue(SplitterBlock.FACING, Direction.EAST).setValue(SplitterBlock.SIDE, SplitterBlock.Side.LEFT);
        helper.setBlock(LEFT, left);
        helper.setBlock(RIGHT, left.setValue(SplitterBlock.SIDE, SplitterBlock.Side.RIGHT));
    }

    private static void feeder(GameTestHelper helper, BlockPos at, Direction facing) {
        helper.setBlock(at, BlockContent.FEEDER_BLOCK.get().defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, facing));
    }

    private static void fill(GameTestHelper helper, BlockPos at, Item item, int items) {
        Container container = chest(helper, at);
        for (int slot = 0; items > 0; slot++, items -= 64) container.setItem(slot, new ItemStack(item, Math.min(items, 64)));
    }

    /** What the line through this tile carries. */
    private static int held(GameTestHelper helper, BlockPos tile) {
        var line = helper.getBlockEntity(tile, BeltTileBlockEntity.class).line();
        return line == null ? 0 : line.size();
    }

    private static int dropped(GameTestHelper helper) {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(2.0)).size();
    }

    private static int count(Container container, Item item) {
        int total = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            if (container.getItem(slot).is(item)) total += container.getItem(slot).getCount();
        }
        return total;
    }

    // Cobblestone sits in the first slot, so an unfiltered feeder takes it first.
    private static void filterLimitsWhatMoves(GameTestHelper helper) {
        LoaderPower.feed(helper);
        place(helper, BeltTier.BELT);
        Container head = chest(helper, HEAD);
        head.setItem(0, new ItemStack(Items.COBBLESTONE, 64));
        head.setItem(1, new ItemStack(Items.DIRT, 64));

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIRT));
        helper.useBlock(FEEDER, player, hit(helper, FEEDER));
        helper.getBlockState(FEEDER).useItemOn(ItemStack.EMPTY, helper.getLevel(), player,
                InteractionHand.OFF_HAND, hit(helper, FEEDER));

        helper.startSequence().thenIdle(WARMUP_TICKS + WINDOW_TICKS).thenExecute(() -> {
            int dirt = count(chest(helper, TAIL), Items.DIRT);
            int cobblestone = count(chest(helper, TAIL), Items.COBBLESTONE);
            if (dirt == 0) helper.fail("no dirt arrived, so this proves nothing about the filter", TAIL);
            if (cobblestone != 0) helper.fail(cobblestone + " cobblestone passed a feeder filtered to dirt", HEAD);
        }).thenSucceed();
    }

    private static BlockHitResult hit(GameTestHelper helper, BlockPos pos) {
        BlockPos absolute = helper.absolutePos(pos);
        return new BlockHitResult(Vec3.atCenterOf(absolute).relative(Direction.UP, 0.5), Direction.UP,
                absolute, false);
    }

    /** A full chest, an east-facing feeder of the tier, an empty chest. */
    private static void place(GameTestHelper helper, BeltTier tier) {
        helper.setBlock(HEAD, Blocks.CHEST);
        helper.setBlock(TAIL, Blocks.CHEST);
        helper.setBlock(FEEDER, BlockContent.feederFor(tier).defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.EAST));
        Container head = chest(helper, HEAD);
        for (int slot = 0; slot < 4; slot++) head.setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
    }

    private static EnergyHandler face(GameTestHelper helper) {
        return helper.getLevel().getCapability(Capabilities.Energy.BLOCK, helper.absolutePos(FEEDER), null);
    }

    private static Container chest(GameTestHelper helper, BlockPos at) {
        return helper.getBlockEntity(at, ChestBlockEntity.class);
    }

    private static int count(Container container) {
        int total = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) total += container.getItem(slot).getCount();
        return total;
    }
}
