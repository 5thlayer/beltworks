// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.client.renderers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.LightCoordsUtil;
import io.github._5thlayer.beltworks.model.FeederMarks;
import io.github._5thlayer.beltworks.model.WorldMark;

import java.util.List;

/**
 * {@link WorldMark}s, a feeder's ends' and a splitter's priorities', flat in a bright yellow on each end's top and drawn over
 * everything, so a tail behind a machine still shows (GLOSSARY.md, Feeder and Output priority).
 */
public final class WorldMarkRenderer {

    private static final int YELLOW = 0xFFE6FF00;
    // How thick the ring's band and the chevron's strokes are, and how long the chevron is along its way.
    private static final double STROKE = 1.5 / 16;
    private static final double CHEVRON_LENGTH = 5 / 16d;
    private static final int RING_SEGMENTS = 24;
    // Just above the top, which nothing hides anyway, so the marks never flicker on it.
    private static final double LIFT = 0.01;

    private WorldMarkRenderer() {
    }

    /** The marks, counted from the block {@code poseStack} stands at. */
    public static void submit(FeederMarks marks, PoseStack poseStack, SubmitNodeCollector collector) {
        submit(List.of(marks.head(), marks.tail()), poseStack, collector);
    }

    /** Any marks, counted from the block {@code poseStack} stands at. */
    public static void submit(List<WorldMark> marks, PoseStack poseStack, SubmitNodeCollector collector) {
        // Vanilla's see-through text background: colour alone, translucent, with no depth test.
        collector.submitCustomGeometry(poseStack, RenderTypes.textBackgroundSeeThrough(), (pose, consumer) -> {
            var quads = new Quads(pose, consumer);
            for (var mark : marks) {
                switch (mark.kind()) {
                    case RING -> ring(mark, quads);
                    case CHEVRON -> chevron(mark, quads);
                    case TRIANGLE -> triangle(mark, quads);
                }
            }
        });
    }

    private static void ring(WorldMark mark, Quads quads) {
        var outer = mark.size() / 2;
        var inner = outer - STROKE;
        for (var i = 0; i < RING_SEGMENTS; i++) {
            var a = 2 * Math.PI * i / RING_SEGMENTS;
            var b = 2 * Math.PI * (i + 1) / RING_SEGMENTS;
            quads.flat(mark,
              outer * Math.cos(a), outer * Math.sin(a), outer * Math.cos(b), outer * Math.sin(b),
              inner * Math.cos(b), inner * Math.sin(b), inner * Math.cos(a), inner * Math.sin(a));
        }
    }

    // Two strokes from the back corners, a mark's size apart, meeting at the tip ahead.
    private static void chevron(WorldMark mark, Quads quads) {
        var ahead = mark.pointing();
        // The way across, a quarter turn from ahead.
        var acrossX = -ahead.z();
        var acrossZ = ahead.x();
        var half = mark.size() / 2;
        var tipX = ahead.x() * CHEVRON_LENGTH / 2;
        var tipZ = ahead.z() * CHEVRON_LENGTH / 2;
        for (var side : new int[] {-1, 1}) {
            var cornerX = -tipX + side * acrossX * half;
            var cornerZ = -tipZ + side * acrossZ * half;
            // Each stroke thickened back along the chevron's way, so its outer edge runs corner to tip.
            var backX = -ahead.x() * STROKE;
            var backZ = -ahead.z() * STROKE;
            quads.flat(mark, cornerX, cornerZ, tipX, tipZ, tipX + backX, tipZ + backZ, cornerX + backX, cornerZ + backZ);
        }
    }

    // Filled, its base a mark's size across and its tip ahead, as long as a chevron; a quad with its tip doubled.
    private static void triangle(WorldMark mark, Quads quads) {
        var ahead = mark.pointing();
        var acrossX = -ahead.z() * mark.size() / 2;
        var acrossZ = ahead.x() * mark.size() / 2;
        var tipX = ahead.x() * CHEVRON_LENGTH / 2;
        var tipZ = ahead.z() * CHEVRON_LENGTH / 2;
        quads.flat(mark, -tipX + acrossX, -tipZ + acrossZ, tipX, tipZ, tipX, tipZ, -tipX - acrossX, -tipZ - acrossZ);
    }

    private record Quads(PoseStack.Pose pose, VertexConsumer consumer) {

        // A flat quad at the mark's top, its corners off the mark's centre, wound both ways so it shows from above and below.
        void flat(WorldMark mark, double... corners) {
            var y = (float) (mark.y() + LIFT);
            for (var i = 0; i < 4; i++) vertex(mark, corners[2 * i], y, corners[2 * i + 1]);
            for (var i = 3; i >= 0; i--) vertex(mark, corners[2 * i], y, corners[2 * i + 1]);
        }

        private void vertex(WorldMark mark, double x, float y, double z) {
            consumer.addVertex(pose.pose(), (float) (mark.x() + x), y, (float) (mark.z() + z))
              .setColor(YELLOW)
              .setLight(LightCoordsUtil.FULL_BRIGHT);
        }
    }
}
