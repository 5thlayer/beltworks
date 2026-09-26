// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import io.github._5thlayer.groundworks.PlacementPlan;
import io.github._5thlayer.groundworks.Placements;
import io.github._5thlayer.groundworks.Refusal;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import io.github._5thlayer.beltworks.ItemContent;
import io.github._5thlayer.beltworks.items.BeltTileItem;
import io.github._5thlayer.beltworks.items.SplitterItem;
import io.github._5thlayer.beltworks.items.BeltRefusal;
import io.github._5thlayer.beltworks.model.BeltTier;

/**
 * The plan the Groundworks library draws for a belt piece is the Mod's own plan, translated
 * (ADR 0010): the same blocks, the same replaces and the same refusal. The other plan tests read the
 * library's plan and hold the click to it; these hold the library's plan to the Mod's.
 */
final class PlanTranslationTests {

    private static final BlockPos FLOOR = new BlockPos(3, 0, 3);
    // Two level tiles running east, and a plain click a block above and ahead of the second makes a
    // top over air, which puts a wedge under it.
    private static final BlockPos FIRST = new BlockPos(3, 1, 3);
    private static final BlockPos TOP = FIRST.east(2).above();

    private PlanTranslationTests() {
    }

    static void register(BeltGameTests.Registrar tests) {
        tests.test("the_library_plans_a_splitter_as_the_mod_does", 20, helper -> splitter(helper, Blocks.AIR));
        tests.test("the_library_plans_a_blocked_splitter_as_the_mod_does", 20, helper -> splitter(helper, Blocks.STONE));
        tests.test("the_library_plans_a_plain_tile_as_the_mod_does", 20, helper -> tile(helper, Blocks.AIR.defaultBlockState()));
        // A loader is neither ground nor replaceable, so the wedge cannot stand there.
        tests.test("the_library_plans_a_refused_plain_tile_as_the_mod_does", 20,
                helper -> tile(helper, BeltTileTests.loader(BeltTier.BELT, Direction.NORTH)));
        tests.test("the_library_plans_a_loader_as_vanilla_does", 20, PlanTranslationTests::loader);
    }

    // A south-facing splitter's right half is west of its left.
    private static void splitter(GameTestHelper helper, Block atRightHalf) {
        helper.setBlock(FLOOR.above().west(), atRightHalf);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setYRot(Direction.SOUTH.toYRot());
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ItemContent.SPLITTER.get()));

        var hit = onTop(helper, FLOOR);
        var context = context(player, hit);
        var own = ((SplitterItem) player.getMainHandItem().getItem()).splitterPlan(context);
        if (own == null) throw helper.assertionException(FLOOR, "the Mod plans no splitter here");
        var blocks = own.halves().stream().map(half -> new PlacementPlan.Placed(half.pos(), half.state())).toList();
        agrees(helper, FLOOR, libraryPlan(player, hit), blocks, List.of(), own.blocked() ? BeltRefusal.BLOCKED : null);
    }

    // The tile vanilla would place, and the wedges its reshape puts down with it.
    private static void tile(GameTestHelper helper, BlockState underTop) {
        helper.setBlock(FIRST, BeltTileTests.tile(BeltTier.BELT, Direction.EAST));
        helper.setBlock(FIRST.east(), BeltTileTests.tile(BeltTier.BELT, Direction.EAST));
        helper.setBlock(TOP.below(), underTop);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setYRot(Direction.EAST.toYRot());
        var item = (BeltTileItem) ItemContent.tileFor(BeltTier.BELT);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item));

        // Aimed at the spot itself, which is air, so the tile goes there whatever stands around it.
        var absolute = helper.absolutePos(TOP);
        var hit = new BlockHitResult(Vec3.atCenterOf(absolute), Direction.UP, absolute, false);
        var context = context(player, hit);
        var vanilla = Placements.vanillaPlan(item, context);
        var reshape = item.single(context);
        if (vanilla == null || vanilla.isRefused() || reshape == null) throw helper.assertionException(TOP, "vanilla places no tile here");
        if (reshape.refused() != !underTop.isAir()) helper.fail("the Mod " + (reshape.refused() ? "refuses" : "accepts") + " this tile, so this proves little", TOP);
        if (!reshape.refused() && reshape.wedges().isEmpty()) helper.fail("the tile puts down no wedge, so this proves little", TOP);
        var blocks = new ArrayList<>(vanilla.blocks());
        reshape.wedges().forEach((pos, state) -> blocks.add(new PlacementPlan.Placed(pos, state)));
        agrees(helper, TOP, libraryPlan(player, hit), blocks, List.of(), reshape.refused() ? reshape.refusal() : null);
    }

    // A plain block item of the Mod's, which the library draws only because the Mod opts its blocks in.
    private static void loader(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setYRot(Direction.SOUTH.toYRot());
        var item = (BlockItem) ItemContent.LOADER.get();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item));
        var hit = onTop(helper, FLOOR);
        var vanilla = Placements.vanillaPlan(item, context(player, hit));
        if (vanilla == null) throw helper.assertionException(FLOOR, "vanilla places no loader here");
        agrees(helper, FLOOR, libraryPlan(player, hit), vanilla.blocks(), vanilla.replaces(), vanilla.refusal());
    }

    /** The plan the library draws for this click, as its preview asks for it. */
    private static @Nullable PlacementPlan libraryPlan(Player player, BlockHitResult hit) {
        return Placements.planFor(player.level(), player, InteractionHand.MAIN_HAND, player.getMainHandItem(), hit);
    }

    private static void agrees(GameTestHelper helper, BlockPos at, @Nullable PlacementPlan plan,
                               List<PlacementPlan.Placed> blocks, List<BlockPos> replaces, @Nullable Refusal refusal) {
        if (plan == null) throw helper.assertionException(at, "the library plans nothing where the Mod plans " + blocks.size() + " blocks");
        if (!plan.blocks().equals(blocks)) helper.fail("the library plans " + plan.blocks() + ", the Mod " + blocks, at);
        if (!plan.replaces().equals(replaces)) helper.fail("the library replaces " + plan.replaces() + ", the Mod " + replaces, at);
        if (!Objects.equals(plan.refusal(), refusal)) helper.fail("the library refuses with " + plan.refusal() + ", the Mod " + refusal, at);
        helper.succeed();
    }

    private static BlockPlaceContext context(Player player, BlockHitResult hit) {
        return new BlockPlaceContext(player, InteractionHand.MAIN_HAND, player.getMainHandItem(), hit);
    }

    private static BlockHitResult onTop(GameTestHelper helper, BlockPos ground) {
        var absolute = helper.absolutePos(ground);
        return new BlockHitResult(Vec3.atCenterOf(absolute).relative(Direction.UP, 0.5), Direction.UP, absolute, false);
    }
}
