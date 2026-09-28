// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import io.github._5thlayer.beltworks.BlockContent;
import io.github._5thlayer.beltworks.blocks.FeederBlockEntity;
import io.github._5thlayer.beltworks.model.BeltTier;

/**
 * A feeder moves one item at a time from the chest its head reaches to the one its tail reaches, at
 * a tenth of its tier's loader, paying FE for each item even with loader power switched off.
 */
final class FeederTests {

    private static final BlockPos HEAD = new BlockPos(2, 1, 2);
    private static final BlockPos FEEDER = HEAD.east();
    private static final BlockPos TAIL = FEEDER.east();

    private static final int WARMUP_TICKS = 20;
    private static final int WINDOW_TICKS = 200;
    private static final int TIER_1_EXPECTED = (int) Math.round(BeltTier.BELT.feederItemsPerSecond() * WINDOW_TICKS / 20);
    private static final long JOULES_PER_FE = 100;

    private FeederTests() {
    }

    static void register(BeltGameTests.Registrar tests) {
        var loadersUnpowered = tests.withLoaderPower(false);
        loadersUnpowered.test("fed_tier_1_feeder_moves_chest_to_chest_at_1_5_items_per_second",
                WARMUP_TICKS + WINDOW_TICKS + 20, FeederTests::movesAtRate);
        loadersUnpowered.test("unfed_tier_1_feeder_moves_nothing_when_loaders_need_no_power", 80,
                FeederTests::stallsUnfed);
        loadersUnpowered.test("fed_tier_1_feeder_draws_133_fe_per_item", WARMUP_TICKS + WINDOW_TICKS + 20,
                FeederTests::drawsPerItem);
    }

    private static void movesAtRate(GameTestHelper helper) {
        LoaderPower.feed(helper);
        place(helper);
        int[] before = new int[1];
        helper.startSequence()
                .thenIdle(WARMUP_TICKS)
                .thenExecute(() -> before[0] = count(chest(helper, TAIL)))
                .thenIdle(WINDOW_TICKS)
                .thenExecute(() -> {
                    int delivered = count(chest(helper, TAIL)) - before[0];
                    if (delivered != TIER_1_EXPECTED) {
                        helper.fail("a fed tier-1 feeder delivered " + delivered + " items in " + WINDOW_TICKS
                                + " ticks, expected " + TIER_1_EXPECTED, TAIL);
                    }
                })
                .thenSucceed();
    }

    private static void stallsUnfed(GameTestHelper helper) {
        place(helper);
        helper.startSequence().thenIdle(60).thenExecute(() -> {
            if (face(helper) == null) helper.fail("a feeder has no energy face", FEEDER);
            if (count(chest(helper, TAIL)) != 0) {
                helper.fail("an unfed feeder moved " + count(chest(helper, TAIL)) + " items", TAIL);
            }
        }).thenSucceed();
    }

    // The supply is cut and the buffer filled by hand, so the refill's order against the tick can't skew a read.
    private static void drawsPerItem(GameTestHelper helper) {
        var power = LoaderPower.feed(helper);
        place(helper);
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
                    long joules = items * BeltTier.BELT.feederJoulesPerItem();
                    if (Math.abs(drawn * JOULES_PER_FE - joules) >= JOULES_PER_FE) {
                        helper.fail("the feeder drew " + drawn + " FE for " + items + " items, expected "
                                + joules / (double) JOULES_PER_FE, FEEDER);
                    }
                })
                .thenSucceed();
    }

    /** A full chest, an east-facing feeder, an empty chest. */
    private static void place(GameTestHelper helper) {
        helper.setBlock(HEAD, Blocks.CHEST);
        helper.setBlock(TAIL, Blocks.CHEST);
        helper.setBlock(FEEDER, BlockContent.FEEDER_BLOCK.get().defaultBlockState()
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
