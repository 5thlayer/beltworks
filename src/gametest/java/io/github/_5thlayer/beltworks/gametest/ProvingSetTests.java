// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import io.github._5thlayer.beltworks.BlockContent;
import io.github._5thlayer.beltworks.blocks.BeltTileBlockEntity;
import io.github._5thlayer.beltworks.blocks.FeederBlockEntity;
import io.github._5thlayer.beltworks.model.BeltTier;

/**
 * Vanilla's part of the proving set (ADR 0008): a loader loads from and unloads into a chest, a double
 * chest, a shulker box and a hopper, unloads fuel into a furnace's side, and against a face that refuses, a composter's side, which
 * NeoForge exposes nothing on, or a shulker box offered a shulker box, moves nothing and loses nothing. A target that accepts a simulation and then refuses the real insert loses nothing either,
 * to a loader or a feeder.
 *
 * <p>Items run east: the source, a loader, three tiles, a loader and the target, as in {@link BeltTileTests}.
 */
final class ProvingSetTests {

    private static final BlockPos SOURCE = new BlockPos(2, 1, 3);
    private static final BlockPos FROM = SOURCE.east();
    private static final BlockPos FIRST_TILE = FROM.east();
    private static final int TILES = 3;
    private static final BlockPos TO = FIRST_TILE.east(TILES);
    private static final BlockPos TARGET = TO.east();

    private static final int ITEMS = 16;
    private static final int MOVE_TICKS = 400;
    // Long enough for every item to have reached the line's end and been refused.
    private static final int REFUSED_TICKS = 200;

    // A chest, an east-facing feeder and the stand-in.
    private static final BlockPos HEAD = new BlockPos(2, 1, 2);
    private static final BlockPos FEEDER = HEAD.east();
    private static final BlockPos TAIL = FEEDER.east();

    private ProvingSetTests() {
    }

    static void register(BeltGameTests.Registrar tests) {
        var unpowered = tests.withLoaderPower(false);
        for (var container : new Block[]{Blocks.CHEST, Blocks.SHULKER_BOX, Blocks.HOPPER}) {
            String name = BuiltInRegistries.BLOCK.getKey(container).getPath();
            unpowered.test("a_loader_unloads_into_a_" + name, MOVE_TICKS,
                    helper -> moves(helper, Blocks.CHEST.defaultBlockState(), container.defaultBlockState(), Items.COBBLESTONE));
            unpowered.test("a_loader_loads_from_a_" + name, MOVE_TICKS,
                    helper -> moves(helper, container.defaultBlockState(), Blocks.CHEST.defaultBlockState(), Items.COBBLESTONE));
        }
        // A furnace's side is its fuel slot.
        unpowered.test("a_loader_unloads_fuel_into_a_furnaces_side", MOVE_TICKS,
                helper -> moves(helper, Blocks.CHEST.defaultBlockState(), Blocks.FURNACE.defaultBlockState(), Items.COAL));
        unpowered.test("a_loader_unloads_through_a_full_half_into_a_double_chests_other", MOVE_TICKS,
                ProvingSetTests::unloadsIntoDoubleChest);
        unpowered.test("a_loader_loads_through_an_empty_half_from_a_double_chests_other", MOVE_TICKS,
                ProvingSetTests::loadsFromDoubleChest);
        unpowered.test("a_loader_against_a_ready_composters_side_takes_nothing", REFUSED_TICKS + 20,
                ProvingSetTests::readyComposterSideRefuses);
        unpowered.test("a_loader_against_a_composters_side_backs_up_and_loses_nothing", REFUSED_TICKS + 20,
                ProvingSetTests::composterSideRefuses);
        unpowered.test("a_loader_backs_up_a_shulker_box_a_shulker_box_refuses", REFUSED_TICKS + 20,
                ProvingSetTests::shulkerBoxRefusesShulkerBox);
        unpowered.test("a_loader_loses_nothing_to_a_target_that_refuses_every_other_insert", MOVE_TICKS,
                ProvingSetTests::fickleTargetOfLoader);
        unpowered.test("a_feeder_loses_nothing_to_a_tail_whose_real_insert_refuses_after_a_simulation", REFUSED_TICKS + 20,
                ProvingSetTests::fickleTailOfFeeder);
    }

    private static void moves(GameTestHelper helper, BlockState source, BlockState target, Item item) {
        line(helper, source, target);
        fill(helper, SOURCE, item, ITEMS);
        helper.succeedWhen(() -> expectMoved(helper, SOURCE, held(helper, TARGET)));
    }

    private static void unloadsIntoDoubleChest(GameTestHelper helper) {
        BlockPos other = doubleChest(helper, TARGET);
        line(helper, Blocks.CHEST.defaultBlockState(), null);
        fill(helper, SOURCE, Items.COBBLESTONE, ITEMS);
        Container near = container(helper, TARGET);
        for (int slot = 0; slot < near.getContainerSize(); slot++) near.setItem(slot, new ItemStack(Items.STONE, 64));
        helper.succeedWhen(() -> expectMoved(helper, SOURCE, held(helper, other)));
    }

    private static void loadsFromDoubleChest(GameTestHelper helper) {
        BlockPos other = doubleChest(helper, SOURCE);
        line(helper, null, Blocks.CHEST.defaultBlockState());
        fill(helper, other, Items.COBBLESTONE, ITEMS);
        helper.succeedWhen(() -> expectMoved(helper, other, held(helper, TARGET)));
    }

    private static void readyComposterSideRefuses(GameTestHelper helper) {
        line(helper, Blocks.COMPOSTER.defaultBlockState().setValue(ComposterBlock.LEVEL, ComposterBlock.READY),
                Blocks.CHEST.defaultBlockState());
        helper.startSequence().thenIdle(REFUSED_TICKS).thenExecute(() -> {
            int level = helper.getBlockState(SOURCE).getValue(ComposterBlock.LEVEL);
            if (level != ComposterBlock.READY) helper.fail("the ready composter's side gave its bone meal, to level " + level, SOURCE);
            if (onLine(helper) + held(helper, TARGET) != 0) helper.fail("something came out of the composter's side", TARGET);
        }).thenSucceed();
    }

    // A composter takes items only from above and gives its bone meal only below, so its side refuses
    // even what it composts.
    private static void composterSideRefuses(GameTestHelper helper) {
        line(helper, Blocks.CHEST.defaultBlockState(), Blocks.COMPOSTER.defaultBlockState());
        fill(helper, SOURCE, Items.WHEAT_SEEDS, ITEMS);
        helper.startSequence().thenIdle(REFUSED_TICKS).thenExecute(() -> {
            int level = helper.getBlockState(TARGET).getValue(ComposterBlock.LEVEL);
            if (level != 0) helper.fail("the composter's side took seeds, to level " + level, TARGET);
            expectKept(helper, held(helper, SOURCE) + onLine(helper));
        }).thenSucceed();
    }

    private static void shulkerBoxRefusesShulkerBox(GameTestHelper helper) {
        line(helper, Blocks.CHEST.defaultBlockState(), Blocks.SHULKER_BOX.defaultBlockState());
        fill(helper, SOURCE, Items.SHULKER_BOX, 1);
        helper.startSequence().thenIdle(REFUSED_TICKS).thenExecute(() -> {
            if (held(helper, TARGET) != 0) helper.fail("a shulker box took a shulker box", TARGET);
            if (onLine(helper) != 1) helper.fail("the line holds " + onLine(helper) + ", not the refused shulker box", TO);
        }).thenSucceed();
    }

    // A loader no longer simulates, so to it the stand-in is a target refusing every other real insert,
    // the one after each a simulation used to make.
    private static void fickleTargetOfLoader(GameTestHelper helper) {
        line(helper, Blocks.CHEST.defaultBlockState(), null);
        var target = FickleInventory.standAt(helper, TARGET);
        fill(helper, SOURCE, Items.COBBLESTONE, ITEMS);
        helper.succeedWhen(() -> {
            int left = held(helper, SOURCE) + onLine(helper);
            if (left + target.count() < ITEMS) {
                helper.fail((ITEMS - left - target.count()) + " items were lost: the source and line hold " + left
                        + " and the target " + target.count(), TARGET);
            }
            expectMoved(helper, SOURCE, target.count());
        });
    }

    // Each try is a hint the stand-in takes and a real insert it refuses, so the arm waits over it,
    // holding what it grabbed (ADR 0018).
    private static void fickleTailOfFeeder(GameTestHelper helper) {
        helper.setBlock(HEAD, Blocks.CHEST);
        helper.setBlock(FEEDER, BlockContent.feederFor(BeltTier.BELT).defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.EAST));
        var tail = FickleInventory.standAt(helper, TAIL);
        fill(helper, HEAD, Items.COBBLESTONE, ITEMS);
        helper.startSequence().thenIdle(REFUSED_TICKS).thenExecute(() -> {
            if (tail.asked() == 0) helper.fail("the feeder never tried its tail, so this proves nothing", TAIL);
            int hand = helper.getBlockEntity(FEEDER, FeederBlockEntity.class).hand().getCount();
            int total = held(helper, HEAD) + hand + tail.count();
            if (total != ITEMS) helper.fail("the chest, the hand and the tail hold " + total + " of " + ITEMS, TAIL);
        }).thenSucceed();
    }

    /** The source, a loader, three tiles, a loader and the target, each block left out where null. */
    private static void line(GameTestHelper helper, BlockState source, BlockState target) {
        if (source != null) helper.setBlock(SOURCE, source);
        helper.setBlock(FROM, BeltTileTests.loader(BeltTier.BELT, Direction.EAST));
        for (int tile = 0; tile < TILES; tile++) {
            helper.setBlock(FIRST_TILE.east(tile), BeltTileTests.tile(BeltTier.BELT, Direction.EAST));
        }
        helper.setBlock(TO, BeltTileTests.loader(BeltTier.BELT, Direction.WEST));
        if (target != null) helper.setBlock(TARGET, target);
    }

    /** A double chest whose near half is at {@code near} and other half south of it, answering where that is. */
    private static BlockPos doubleChest(GameTestHelper helper, BlockPos near) {
        BlockState left = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.EAST)
                .setValue(ChestBlock.TYPE, ChestType.LEFT);
        BlockPos other = near.relative(ChestBlock.getConnectedDirection(left));
        helper.setBlock(near, left);
        helper.setBlock(other, left.setValue(ChestBlock.TYPE, ChestType.RIGHT));
        return other;
    }

    private static void fill(GameTestHelper helper, BlockPos at, Item item, int items) {
        Container container = container(helper, at);
        int perSlot = Math.min(item.getDefaultMaxStackSize(), 64);
        for (int slot = 0; items > 0; slot++, items -= perSlot) {
            container.setItem(slot, new ItemStack(item, Math.min(items, perSlot)));
        }
    }

    /** Every item has left {@code from} and {@code arrived} of them are at the target. */
    private static void expectMoved(GameTestHelper helper, BlockPos from, int arrived) {
        if (arrived != ITEMS) helper.fail("the target holds " + arrived + " of " + ITEMS, TARGET);
        int left = held(helper, from);
        if (left != 0) helper.fail("the source still holds " + left, from);
    }

    private static void expectKept(GameTestHelper helper, int kept) {
        if (kept != ITEMS) helper.fail("the source and line hold " + kept + " of " + ITEMS, TO);
    }

    private static int onLine(GameTestHelper helper) {
        var line = helper.getBlockEntity(FIRST_TILE, BeltTileBlockEntity.class).line();
        return line == null ? 0 : line.size();
    }

    private static int held(GameTestHelper helper, BlockPos at) {
        return helper.getLevel().getBlockEntity(helper.absolutePos(at)) instanceof Container container
                ? BeltTileTests.count(container) : 0;
    }

    private static Container container(GameTestHelper helper, BlockPos at) {
        if (helper.getLevel().getBlockEntity(helper.absolutePos(at)) instanceof Container container) return container;
        throw helper.assertionException(at, "no container");
    }
}
