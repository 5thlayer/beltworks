// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.gametest;

import io.github._5thlayer.placementpreview.PlacementPlan;
import io.github._5thlayer.placementpreview.Placements;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import io.github._5thlayer.beltworks.ItemContent;
import io.github._5thlayer.beltworks.items.StretchPlan;

import java.util.ArrayList;

/**
 * A splitter's plan is both halves, refused whole (ADR 0006): the click puts down exactly what the
 * plan names, or, refused, changes none of its positions.
 */
final class SplitterPlanTests {

    private static final BlockPos FLOOR = new BlockPos(3, 0, 3);
    private static final BlockPos ABOVE_FLOOR = FLOOR.above();
    private static final int HALVES = 2;

    private SplitterPlanTests() {
    }

    static void register(BeltGameTests.Registrar tests) {
        tests.test("plan_matches_placement_for_a_splitter", 20, helper -> {
            var plan = check(helper, false);
            if (plan.blocks().size() != HALVES) helper.fail("a splitter's plan named " + plan.blocks().size() + " blocks", FLOOR);
            helper.succeed();
        });
        tests.test("plan_refuses_a_splitter_blocked_at_its_second_half", 20, helper -> {
            // A south-facing splitter's right half is west of its left.
            helper.setBlock(ABOVE_FLOOR.west(), Blocks.STONE);
            var plan = check(helper, true);
            if (plan.refusal() != StretchPlan.Reason.BLOCKED) helper.fail("the plan gave " + plan.refusal() + ", expected BLOCKED", ABOVE_FLOOR);
            if (plan.blocks().size() != HALVES) helper.fail("a refused splitter's plan named " + plan.blocks().size() + " blocks", FLOOR);
            helper.succeed();
        });
    }

    /** Asks for the plan of a south-facing click on the floor, clicks, and holds the world to the plan. */
    private static PlacementPlan check(GameTestHelper helper, boolean expectRefused) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setYRot(Direction.SOUTH.toYRot());
        var stack = new ItemStack(ItemContent.SPLITTER.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        var absolute = helper.absolutePos(FLOOR);
        var hit = new BlockHitResult(Vec3.atCenterOf(absolute).relative(Direction.UP, 0.5), Direction.UP, absolute, false);

        var plan = Placements.planFor(helper.getLevel(), player, InteractionHand.MAIN_HAND, stack, hit);
        if (plan == null) throw helper.assertionException(FLOOR, "no plan at all where one was expected");
        if (plan.isRefused() != expectRefused) {
            helper.fail("the plan " + (plan.isRefused() ? "refused" : "accepted") + " where the opposite was expected", FLOOR);
        }
        // Read before as well as after: a refused plan changing nothing is not its positions being empty.
        var before = new ArrayList<BlockState>();
        for (var placed : plan.blocks()) before.add(helper.getLevel().getBlockState(placed.pos()));

        helper.useBlock(FLOOR, player, hit);

        for (var at = 0; at < plan.blocks().size(); at++) {
            var placed = plan.blocks().get(at);
            var now = helper.getLevel().getBlockState(placed.pos());
            var relative = helper.relativePos(placed.pos());
            if (plan.isRefused() && !now.equals(before.get(at))) helper.fail("a refused plan changed the world at one of its positions", relative);
            if (!plan.isRefused() && !now.equals(placed.state())) helper.fail("the plan promised " + placed.state() + " but placing left " + now, relative);
        }
        return plan;
    }
}
