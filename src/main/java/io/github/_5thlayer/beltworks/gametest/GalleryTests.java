// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.gametest;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import io.github._5thlayer.beltworks.Beltworks;
import io.github._5thlayer.beltworks.BlockContent;
import io.github._5thlayer.beltworks.model.BeltTier;
import io.github._5thlayer.libworks.gallery.GalleryPack;
import io.github._5thlayer.libworks.testkit.GalleryCheck;
import io.github._5thlayer.libworks.testkit.GalleryCheck.Scene;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.gametest.framework.StructureUtils;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;

/**
 * The Gallery pack builds a scene of each README feature, in README order, through libworks'
 * {@link GalleryCheck} (libworks ADR 0010). Beyond the key blocks, every scene that carries items
 * to a container delivers them there, and a scene's sign builds it again. Each test turns the pack on,
 * which reloads the server's data, so each runs in a batch of its own.
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
            new Scene("loaders", new BlockPos(5, 0, -6), Blocks.BARREL),
            new Scene("feeders", new BlockPos(13, 0, -7), BlockContent.feederFor(BeltTier.BELT)),
            new Scene("dismantle", new BlockPos(10, 0, -7), BlockContent.tileFor(BeltTier.BELT)));

    /** Where each scene's items end up, from where the scene is run, by the scene's name. */
    private static final Map<String, List<BlockPos>> DELIVERIES = Map.of(
            "slopes", List.of(new BlockPos(13, 0, -1), new BlockPos(22, 0, -6)),
            "tiers", List.of(new BlockPos(22, 0, -10), new BlockPos(22, 0, -8), new BlockPos(22, 0, -6), new BlockPos(22, 0, -4)),
            "splitters", List.of(new BlockPos(22, 0, -7), new BlockPos(22, 0, -6)),
            "loaders", List.of(new BlockPos(22, 0, -6)),
            "feeders", List.of(new BlockPos(9, 0, -6), new BlockPos(13, 0, -6), new BlockPos(17, 0, -6), new BlockPos(22, 0, -8)),
            "dismantle", List.of(new BlockPos(22, 0, -7)));

    /** The lines scene's sign, from where the scene is run. */
    private static final BlockPos SIGN = new BlockPos(14, 0, 0);

    private GalleryTests() {
    }

    static void register(BeltGameTests.Registrar tests) {
        tests.testOnItsOwn("gallery_builds_a_scene_of_each_feature", GalleryCheck.TICKS,
                helper -> GalleryCheck.builds(helper, Beltworks.MOD_ID, ORIGIN, SCENES));
        tests.testOnItsOwn("gallery_scenes_carry_items_to_the_ends_of_their_lines", GalleryCheck.TICKS, GalleryTests::deliver);
        tests.testOnItsOwn("gallery_sign_builds_its_scene_again_leaving_nothing_loose", GalleryCheck.TICKS, GalleryTests::replay);
    }

    private static void deliver(GameTestHelper helper) {
        var at = helper.absolutePos(ORIGIN);
        var source = GalleryCheck.operator(helper, at);
        withPack(helper, GalleryCheck.strip(at, SCENES.size()), () -> GalleryCheck.assertRuns(helper, source, GalleryPack.build(Beltworks.MOD_ID)), () -> {
            for (int index = 0; index < SCENES.size(); index++) {
                var name = SCENES.get(index).name();
                for (var end : DELIVERIES.getOrDefault(name, List.of())) {
                    var pos = GalleryCheck.sceneOrigin(at, index).offset(end);
                    var container = helper.getLevel().getBlockEntity(pos) instanceof Container found ? found : null;
                    helper.assertTrue(container != null && !container.isEmpty(),
                            "scene " + name + " has carried nothing to " + pos.toShortString());
                }
            }
        });
    }

    // The scene is broken first, so only the rebuild can put its key block back.
    private static void replay(GameTestHelper helper) {
        var at = helper.absolutePos(ORIGIN);
        var source = GalleryCheck.operator(helper, at);
        var lines = SCENES.getFirst();
        var plot = GalleryCheck.plot(at);
        withPack(helper, plot, () -> {
            GalleryCheck.assertRuns(helper, source, GalleryPack.scene(Beltworks.MOD_ID, lines.name()));
            helper.getLevel().setBlockAndUpdate(at.offset(lines.key()), Blocks.AIR.defaultBlockState());
            var sign = (SignBlockEntity) helper.getLevel().getBlockEntity(at.offset(SIGN));
            helper.assertTrue(sign != null && sign.executeClickCommandsIfPresent(helper.getLevel(), source.getPlayer(), at.offset(SIGN), true),
                    "the lines scene's sign runs no command");
            GalleryCheck.assertKeyBlocks(helper, at, List.of(lines));
            var loose = helper.getLevel().getEntitiesOfClass(ItemEntity.class, AABB.of(plot));
            helper.assertTrue(loose.isEmpty(), "building the scene again left " + loose + " loose");
        }, () -> {
        });
    }

    /**
     * Turns the pack on, runs {@code build} once it is, waits until {@code until} holds, then clears
     * {@code area} and turns the pack off, which a failing test does too.
     */
    private static void withPack(GameTestHelper helper, BoundingBox area, Runnable build, Runnable until) {
        var level = helper.getLevel();
        var server = level.getServer();
        var pack = GalleryPack.id(Beltworks.MOD_ID);
        area.intersectingChunks().forEach(chunk -> level.setChunkForced(chunk.x(), chunk.z(), true));
        var on = GalleryCheck.select(server, pack, true);
        helper.testInfo.addListener(new GameTestListener() {
            @Override
            public void testStructureLoaded(GameTestInfo testInfo) {
            }

            @Override
            public void testPassed(GameTestInfo testInfo, GameTestRunner runner) {
            }

            @Override
            public void testFailed(GameTestInfo testInfo, GameTestRunner runner) {
                StructureUtils.clearSpaceForStructure(area, level);
                on.whenComplete((done, failed) -> server.execute(() -> GalleryCheck.select(server, pack, false)));
            }

            @Override
            public void testAddedForRerun(GameTestInfo original, GameTestInfo copy, GameTestRunner runner) {
            }
        });
        var off = new AtomicReference<CompletableFuture<Void>>();
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(on.isDone(), pack + " is not on yet"))
                .thenWaitUntil(() -> helper.assertTrue(area.intersectingChunks().allMatch(chunk -> level.hasChunk(chunk.x(), chunk.z())),
                        "the gallery's plots are not loaded yet"))
                .thenExecute(build)
                .thenWaitUntil(until)
                .thenExecute(() -> {
                    StructureUtils.clearSpaceForStructure(area, level);
                    off.set(GalleryCheck.select(server, pack, false));
                })
                .thenWaitUntil(() -> helper.assertTrue(off.get() != null && off.get().isDone(), pack + " is not off yet"))
                .thenSucceed();
    }
}
