// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.client.renderers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.LightCoordsUtil;
import io.github._5thlayer.beltworks.model.FeederMarks;

import java.util.List;

/**
 * A feeder's {@link FeederMarks}, flat in a bright yellow on each end's top and drawn over
 * everything, so a tail behind a machine still shows (GLOSSARY.md, Feeder).
 */
public final class FeederMarksRenderer {

    private static final int YELLOW = 0xFFE6FF00;
    // How thick the ring's band and the chevron's strokes are, and how long the chevron is along its way.
    private static final double STROKE = 1.5 / 16;
    private static final double CHEVRON_LENGTH = 5 / 16d;
    private static final int RING_SEGMENTS = 24;
    // Just above the top, which nothing hides anyway, so the marks never flicker on it.
    private static final double LIFT = 0.01;

    private FeederMarksRenderer() {
    }

    /** The marks, counted from the block {@code poseStack} stands at. */
    public static void submit(FeederMarks marks, PoseStack poseStack, SubmitNodeCollector collector) {
        // Vanilla's see-through text background: colour alone, translucent, with no depth test.
        collector.submitCustomGeometry(poseStack, RenderTypes.textBackgroundSeeThrough(), (pose, consumer) -> {
            var quads = new Quads(pose, consumer);
            for (var mark : List.of(marks.head(), marks.tail())) {
                switch (mark.kind()) {
                    case RING -> ring(mark, quads);
                    case CHEVRON -> chevron(mark, quads);
                }
            }
        });
    }

    private static void ring(FeederMarks.Mark mark, Quads quads) {
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
    private static void chevron(FeederMarks.Mark mark, Quads quads) {
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

    private record Quads(PoseStack.Pose pose, VertexConsumer consumer) {

        // A flat quad at the mark's top, its corners off the mark's centre, wound both ways so it shows from above and below.
        void flat(FeederMarks.Mark mark, double... corners) {
            var y = (float) (mark.y() + LIFT);
            for (var i = 0; i < 4; i++) vertex(mark, corners[2 * i], y, corners[2 * i + 1]);
            for (var i = 3; i >= 0; i--) vertex(mark, corners[2 * i], y, corners[2 * i + 1]);
        }

        private void vertex(FeederMarks.Mark mark, double x, float y, double z) {
            consumer.addVertex(pose.pose(), (float) (mark.x() + x), y, (float) (mark.z() + z))
              .setColor(YELLOW)
              .setLight(LightCoordsUtil.FULL_BRIGHT);
        }
    }
}
