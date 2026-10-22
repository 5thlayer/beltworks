// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.storage.TagValueInput;
import io.github._5thlayer.beltworks.BlockContent;
import io.github._5thlayer.beltworks.blocks.FeederBlockEntity;
import io.github._5thlayer.beltworks.blocks.FeederReach;
import io.github._5thlayer.beltworks.model.ArmSwing;
import io.github._5thlayer.beltworks.model.BeltTier;
import io.github._5thlayer.beltworks.model.FeederArms;

/**
 * What a feeder's hand holds is never lost or dropped loose (ADR 0018): it is saved with the feeder,
 * dropped beside it when it breaks, kept by a tier swap up to the new hand, and held over a tail that
 * stops accepting until one accepts again.
 */
final class FeederHandTests {

    private static final BlockPos HEAD = new BlockPos(2, 1, 2);
    private static final BlockPos FEEDER = HEAD.east();
    private static final BlockPos TAIL = FEEDER.east();

    // Long enough for any arm to reach the tail and, if it would drop, to have dropped.
    private static final int HOLD_TICKS = 60;

    private FeederHandTests() {
    }

    static void register(BeltGameTests.Registrar tests) {
        var unpowered = tests.withLoaderPower(false);
        unpowered.test("feeder_arm_held_stack_survives_save_and_load", 100, FeederHandTests::survivesSaveAndLoad);
        unpowered.test("feeder_arm_saved_before_the_arm_loads_empty_handed_at_rest", 20, FeederHandTests::loadsFromBeforeTheArm);
        unpowered.test("feeder_arm_broken_feeder_drops_its_held_stack_beside_itself", 100, FeederHandTests::breakingDrops);
        unpowered.test("feeder_arm_tier_swap_keeps_the_held_stack", 100, FeederHandTests::swapKeeps);
        unpowered.test("feeder_arm_tier_swap_to_a_smaller_hand_drops_the_excess", 100, FeederHandTests::swapDropsExcess);
        unpowered.test("feeder_arm_holds_over_a_broken_tail_until_one_accepts", 100 + HOLD_TICKS + 100, FeederHandTests::holdsOverABrokenTail);
        unpowered.test("feeder_arm_filter_and_reach_changes_leave_the_hand", 100, FeederHandTests::settingsLeaveTheHand);
    }

    private static void survivesSaveAndLoad(GameTestHelper helper) {
        place(helper, BeltTier.TURBO);
        helper.startSequence().thenWaitUntil(() -> swinging(helper, 2)).thenExecute(() -> {
            var feeder = feeder(helper);
            var registries = helper.getLevel().registryAccess();
            var loaded = new FeederBlockEntity(helper.absolutePos(FEEDER), helper.getBlockState(FEEDER));
            loaded.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, registries, feeder.saveWithoutMetadata(registries)));
            if (!ItemStack.matches(loaded.hand(), feeder.hand())) helper.fail("a save of " + feeder.hand() + " loaded " + loaded.hand(), FEEDER);
            if (loaded.armPhase() != feeder.armPhase()) helper.fail("an arm saved " + feeder.armPhase() + " loaded " + loaded.armPhase(), FEEDER);
        }).thenSucceed();
    }

    private static void loadsFromBeforeTheArm(GameTestHelper helper) {
        place(helper, BeltTier.TURBO);
        var registries = helper.getLevel().registryAccess();
        var tag = feeder(helper).saveWithoutMetadata(registries);
        for (var key : new String[] {"hand", "arm", "arm_elapsed"}) tag.remove(key);
        var loaded = new FeederBlockEntity(helper.absolutePos(FEEDER), helper.getBlockState(FEEDER));
        loaded.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, registries, tag));
        if (!loaded.hand().isEmpty()) helper.fail("a feeder saved before the arm loaded holding " + loaded.hand(), FEEDER);
        if (loaded.armPhase() != ArmSwing.Phase.AT_HEAD) helper.fail("a feeder saved before the arm loaded " + loaded.armPhase(), FEEDER);
        helper.succeed();
    }

    private static void breakingDrops(GameTestHelper helper) {
        place(helper, BeltTier.TURBO);
        helper.startSequence().thenWaitUntil(() -> swinging(helper, 2)).thenExecute(() -> {
            helper.getLevel().destroyBlock(helper.absolutePos(FEEDER), true);
            if (dropped(helper, BlockContent.feederFor(BeltTier.TURBO).asItem()) != 1) helper.fail("the broken feeder did not drop itself", FEEDER);
            int held = dropped(helper, Items.COBBLESTONE);
            if (held != 2) helper.fail("the broken feeder dropped " + held + " of the 2 its hand held", FEEDER);
        }).thenSucceed();
    }

    private static void swapKeeps(GameTestHelper helper) {
        place(helper, BeltTier.EXPRESS);
        helper.startSequence().thenWaitUntil(() -> swinging(helper, 1)).thenExecute(() -> {
            var phase = feeder(helper).armPhase();
            swap(helper, BeltTier.TURBO);
            var hand = feeder(helper).hand();
            if (!hand.is(Items.COBBLESTONE) || hand.getCount() != 1) helper.fail("the tier-4 feeder holds " + hand + ", not the 1 held", FEEDER);
            if (feeder(helper).armPhase() != phase) helper.fail("the swap moved the arm from " + phase + " to " + feeder(helper).armPhase(), FEEDER);
            if (dropped(helper, Items.COBBLESTONE) != 0) helper.fail("the swap dropped items loose", FEEDER);
        }).thenSucceed();
    }

    private static void swapDropsExcess(GameTestHelper helper) {
        place(helper, BeltTier.TURBO);
        helper.startSequence().thenWaitUntil(() -> swinging(helper, 2)).thenExecute(() -> {
            swap(helper, BeltTier.EXPRESS);
            var hand = feeder(helper).hand();
            if (!hand.is(Items.COBBLESTONE) || hand.getCount() != 1) helper.fail("the tier-3 feeder holds " + hand + ", not 1 of the 2", FEEDER);
            int excess = dropped(helper, Items.COBBLESTONE);
            if (excess != 1) helper.fail("the swap dropped " + excess + " of the 1 the smaller hand could not hold", FEEDER);
        }).thenSucceed();
    }

    // The tail chest is still empty when the hand first grabs, so breaking it spills nothing.
    private static void holdsOverABrokenTail(GameTestHelper helper) {
        place(helper, BeltTier.BELT);
        helper.startSequence().thenWaitUntil(() -> swinging(helper, 1)).thenExecute(() -> {
            if (count(chest(helper, TAIL)) != 0) helper.fail("the tail had items before the first swing, so breaking it spills them", TAIL);
            helper.setBlock(TAIL, Blocks.AIR);
        }).thenIdle(HOLD_TICKS).thenExecute(() -> {
            var feeder = feeder(helper);
            if (feeder.hand().getCount() != 1) helper.fail("with its tail broken the hand holds " + feeder.hand(), FEEDER);
            if (feeder.armPhase() != ArmSwing.Phase.AT_TAIL) helper.fail("with its tail broken the arm is " + feeder.armPhase(), FEEDER);
            if (dropped(helper, Items.COBBLESTONE) != 0) helper.fail("the feeder dropped its hand loose", TAIL);
            helper.setBlock(TAIL, Blocks.CHEST);
        }).thenWaitUntil(() -> {
            if (count(chest(helper, TAIL)) == 0) helper.fail("the held item has not reached the new tail", TAIL);
        }).thenExecute(() -> {
            if (dropped(helper, Items.COBBLESTONE) != 0) helper.fail("the feeder dropped items loose", TAIL);
        }).thenSucceed();
    }

    private static void settingsLeaveTheHand(GameTestHelper helper) {
        place(helper, BeltTier.TURBO);
        helper.startSequence().thenWaitUntil(() -> swinging(helper, 2)).thenExecute(() -> {
            var feeder = feeder(helper);
            var held = feeder.hand();
            var player = new ListeningPlayer(helper, FEEDER.north(2));
            feeder.assignFilterItem(new ItemStack(Items.DIRT), player);
            if (!ItemStack.matches(feeder.hand(), held)) helper.fail("setting the filter left the hand " + feeder.hand(), FEEDER);
            feeder.resetFilterItem(player);
            if (!ItemStack.matches(feeder.hand(), held)) helper.fail("clearing the filter left the hand " + feeder.hand(), FEEDER);
            for (var arm : FeederArms.Arm.values()) {
                FeederReach.press(player, helper.absolutePos(FEEDER), arm);
                if (!ItemStack.matches(feeder.hand(), held)) helper.fail("a " + arm + " reach press left the hand " + feeder.hand(), FEEDER);
            }
        }).thenSucceed();
    }

    // The arm on its way to the tail with as many items as given in its hand.
    private static void swinging(GameTestHelper helper, int items) {
        var feeder = feeder(helper);
        if (feeder.armPhase() != ArmSwing.Phase.TO_TAIL || feeder.hand().getCount() != items) {
            helper.fail("the arm is " + feeder.armPhase() + " holding " + feeder.hand(), FEEDER);
        }
    }

    private static void swap(GameTestHelper helper, BeltTier tier) {
        helper.setBlock(FEEDER, BlockContent.feederFor(tier).defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.EAST));
    }

    /** A full chest, an east-facing feeder of the tier, an empty chest. */
    private static void place(GameTestHelper helper, BeltTier tier) {
        helper.setBlock(HEAD, Blocks.CHEST);
        helper.setBlock(TAIL, Blocks.CHEST);
        swap(helper, tier);
        chest(helper, HEAD).setItem(0, new ItemStack(Items.COBBLESTONE, 64));
    }

    private static FeederBlockEntity feeder(GameTestHelper helper) {
        return helper.getBlockEntity(FEEDER, FeederBlockEntity.class);
    }

    private static Container chest(GameTestHelper helper, BlockPos at) {
        return helper.getBlockEntity(at, ChestBlockEntity.class);
    }

    private static int count(Container container) {
        int total = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) total += container.getItem(slot).getCount();
        return total;
    }

    private static int dropped(GameTestHelper helper, Item item) {
        int total = 0;
        for (var entity : helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(2.0))) {
            if (entity.getItem().is(item)) total += entity.getItem().getCount();
        }
        return total;
    }
}
