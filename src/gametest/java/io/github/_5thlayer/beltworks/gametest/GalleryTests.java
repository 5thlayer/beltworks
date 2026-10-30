// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.gametest;

import java.util.List;

import io.github._5thlayer.beltworks.Beltworks;
import io.github._5thlayer.beltworks.BlockContent;
import io.github._5thlayer.beltworks.model.BeltTier;
import io.github._5thlayer.libworks.gallery.GalleryPack;
import io.github._5thlayer.libworks.testkit.GalleryCheck;
import io.github._5thlayer.libworks.testkit.GalleryCheck.Scene;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.phys.AABB;

/**
 * The Gallery pack builds a scene of each README feature but Dismantle, in README order, through libworks'
 * {@link GalleryCheck} (libworks ADR 0010), with a filtered splitter, loader and feeder after the
 * plain ones, then feeders working smelting columns and two furnaces fed from two belts. These tests
 * check the gallery only: the pack is offered and off, its functions load, its build runs every
 * command and stands each scene's key block in place, and a scene's sign builds it again. The Mod's
 * other game tests check what the scenes' blocks do. Each test turns the pack on, which reloads the
 * server's data, so each runs in a batch of its own.
 */
final class GalleryTests {

    /** Where the gallery is run from: south of the platform, its plots north and east of here. */
    private static final BlockPos ORIGIN = new BlockPos(0, 1, 8);

    private static final List<Scene> SCENES = List.of(
            new Scene("lines", new BlockPos(20, 0, -6), BlockContent.tileFor(BeltTier.BELT)),
            new Scene("stretch", new BlockPos(6, -1, -7), Blocks.GOLD_BLOCK),
            new Scene("slopes", new BlockPos(13, 1, -6), BlockContent.tileFor(BeltTier.BELT)),
            new Scene("tiers", new BlockPos(10, 0, -4), BlockContent.tileFor(BeltTier.TURBO)),
            new Scene("splitters", new BlockPos(12, 0, -7), BlockContent.splitterFor(BeltTier.BELT)),
            new Scene("filtered_splitter", new BlockPos(12, 0, -6), BlockContent.splitterFor(BeltTier.BELT)),
            new Scene("loaders", new BlockPos(5, 0, -6), Blocks.BARREL),
            new Scene("filtered_loader", new BlockPos(6, 0, -6), BlockContent.loaderFor(BeltTier.BELT)),
            new Scene("feeders", new BlockPos(13, 0, -7), BlockContent.feederFor(BeltTier.BELT)),
            new Scene("filtered_feeder", new BlockPos(17, 0, -7), BlockContent.feederFor(BeltTier.BELT)),
            new Scene("smelting", new BlockPos(13, 1, -6), Blocks.FURNACE),
            new Scene("iron_smelting", new BlockPos(13, 1, -6), Blocks.FURNACE));

    /** The lines scene's sign, from where the scene is run. */
    private static final BlockPos SIGN = new BlockPos(14, 0, 0);

    private GalleryTests() {
    }

    static void register(BeltGameTests.Registrar tests) {
        tests.testOnItsOwn("gallery_builds_a_scene_of_each_feature", GalleryCheck.TICKS,
                helper -> GalleryCheck.builds(helper, Beltworks.MOD_ID, ORIGIN, SCENES));
        tests.testOnItsOwn("gallery_sign_builds_its_scene_again_leaving_nothing_loose", GalleryCheck.TICKS, GalleryTests::replay);
        tests.testOnItsOwn("gallery_clear_empties_its_plot", GalleryCheck.TICKS,
                helper -> GalleryCheck.clears(helper, Beltworks.MOD_ID, ORIGIN, GalleryCheck.Layout.ONE_ROW));
    }

    // The scene is broken first, so only the rebuild can put its key block back.
    private static void replay(GameTestHelper helper) {
        var at = helper.absolutePos(ORIGIN);
        var lines = SCENES.getFirst();
        GalleryCheck.withPack(helper, Beltworks.MOD_ID, ORIGIN, GalleryCheck.Layout.ONE_ROW, 1, (source, steps) -> steps.thenExecute(() -> {
            GalleryCheck.assertRuns(helper, source, GalleryPack.scene(Beltworks.MOD_ID, lines.name()));
            helper.getLevel().setBlockAndUpdate(at.offset(lines.key()), Blocks.AIR.defaultBlockState());
            var sign = (SignBlockEntity) helper.getLevel().getBlockEntity(at.offset(SIGN));
            helper.assertTrue(sign != null && sign.executeClickCommandsIfPresent(helper.getLevel(), source.getPlayer(), at.offset(SIGN), true),
                    "the lines scene's sign runs no command");
            GalleryCheck.assertKeyBlocks(helper, at, List.of(lines));
            var loose = helper.getLevel().getEntitiesOfClass(ItemEntity.class, AABB.of(GalleryCheck.plot(at)));
            helper.assertTrue(loose.isEmpty(), "building the scene again left " + loose + " loose");
        })).thenSucceed();
    }
}
