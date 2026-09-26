// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.client.renderers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.data.AtlasIds;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockAndLightGetter;
import io.github._5thlayer.beltworks.Beltworks;
import io.github._5thlayer.beltworks.model.Support;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * A tile's or a splitter's support drawn as boxes in its block's space (ADR 0012), so a placed
 * tile and a planned one draw it with the same code. It is only drawn: it has no collision.
 * A planned one is drawn as the Placement Preview draws its plan: in the plan's tint, see-through
 * and unlit.
 */
public final class SupportRenderer {

    private static final float LEG_WIDTH = 2 / 16f;
    private static final float STRUT_HEIGHT = 2 / 16f;
    // Just outside the tile's edge, so a leg covers its tile's side rather than sharing its faces,
    // and no strut shares a plane with a wall beside it.
    private static final float OUTSET = 1 / 128f;
    // Every face samples one column of the loader's slate, inside its border and clear of its rivets,
    // so a leg looks the same all round wherever it stands; down a face the rows run with height, a
    // dark line and a lit one topping each block of it (#32). A cap takes a plain row.
    private static final float COLUMN_U = 8.5f / 16;
    private static final float ROWS_V = 10 / 16f;
    private static final float CAP_V = 5.5f / 16;

    private SupportRenderer() {
    }

    /**
     * A box of strut in its tile's block space, turned {@code yaw} radians about its own centre from
     * east toward south, and lit as the block it is in.
     */
    public record Box(float x0, float y0, float z0, float x1, float y1, float z1, float yaw, int light) {
    }

    /**
     * The boxes of the support of the tile at {@code pos}: each leg, from where it stands inward, up
     * its tile's side to just under the surface, cut at block boundaries, since an atlas sprite
     * cannot repeat; and, unless it is a slope's, a frame under the tile, a strut from each leg to
     * the next round the block.
     * Each piece is lit as the block it is in.
     */
    public static List<Box> boxes(Support support, BlockAndLightGetter level, BlockPos pos) {
        return boxes(support, pos, at -> LevelRenderer.getLightCoords(level, at));
    }

    /** The boxes of a planned piece's support at {@code pos}, unlit as the plan's own blocks are drawn. */
    public static List<Box> plannedBoxes(Support support, BlockPos pos) {
        return boxes(support, pos, at -> LightCoordsUtil.FULL_BRIGHT);
    }

    private static List<Box> boxes(Support support, BlockPos pos, ToIntFunction<BlockPos> light) {
        var boxes = new ArrayList<Box>();
        var legs = support.legs();
        var centres = new float[legs.size()][];
        for (var at = 0; at < legs.size(); at++) {
            var leg = legs.get(at);
            var x0 = inward((float) leg.x());
            var z0 = inward((float) leg.z());
            centres[at] = new float[] {x0 + LEG_WIDTH / 2, z0 + LEG_WIDTH / 2};
            // Lit as the block each piece is in, since a splitter's legs stand under both its halves.
            var column = pos.offset(Mth.floor(centres[at][0]), 0, Mth.floor(centres[at][1]));
            var drawnTop = leg.top() - OUTSET;
            for (var block = (int) Math.floor(drawnTop); block >= Math.floor(leg.bottom()); block--) {
                var top = Math.min(drawnTop, block + 1);
                var bottom = Math.max(leg.bottom(), block);
                if (top - bottom < 1e-4) continue;
                boxes.add(new Box(x0, (float) bottom, z0, x0 + LEG_WIDTH, (float) top, z0 + LEG_WIDTH, 0,
                  light.applyAsInt(column.above(block))));
            }
        }
        if (!support.framed()) return boxes;
        for (var at = 0; at < centres.length; at++) {
            var from = centres[at];
            var to = centres[(at + 1) % centres.length];
            var length = (float) Math.hypot(to[0] - from[0], to[1] - from[1]) - LEG_WIDTH;
            var midX = (from[0] + to[0]) / 2;
            var midZ = (from[1] + to[1]) / 2;
            var under = light.applyAsInt(pos.offset(Mth.floor(midX), -1, Mth.floor(midZ)));
            boxes.add(new Box(midX - length / 2, -STRUT_HEIGHT, midZ - LEG_WIDTH / 2, midX + length / 2, 0, midZ + LEG_WIDTH / 2,
              (float) Math.atan2(to[1] - from[1], to[0] - from[0]), under));
        }
        return boxes;
    }

    // Where a leg's box starts on one axis: from where it stands, just outside the tile, toward the
    // block's middle.
    private static float inward(float at) {
        return at < 0.5f ? at - OUTSET : at + OUTSET - LEG_WIDTH;
    }

    public static void submit(List<Box> boxes, PoseStack poseStack, SubmitNodeCollector collector) {
        submit(boxes, poseStack, collector, RenderTypes.entityCutout(TextureAtlas.LOCATION_BLOCKS), 0xFFFFFFFF);
    }

    /** A planned piece's support, in the plan's {@code tint} as ARGB. */
    public static void submitPlanned(List<Box> boxes, PoseStack poseStack, SubmitNodeCollector collector, int tint) {
        submit(boxes, poseStack, collector, RenderTypes.entityTranslucent(TextureAtlas.LOCATION_BLOCKS), tint);
    }

    private static void submit(List<Box> boxes, PoseStack poseStack, SubmitNodeCollector collector, RenderType type, int color) {
        if (boxes.isEmpty()) return;
        var sprite = Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS).getSprite(Beltworks.id("block/loader_slate"));
        collector.submitCustomGeometry(poseStack, type, (pose, consumer) -> {
            for (var box : boxes) box(box, sprite, color, pose, consumer);
        });
    }

    // Each face is textured by where it lies in its block, as a block model's faces are, so the
    // pieces of a leg read as one post.
    private static void box(Box box, TextureAtlasSprite sprite, int color, PoseStack.Pose pose, VertexConsumer consumer) {
        float x0 = box.x0, y0 = box.y0, z0 = box.z0, x1 = box.x1, y1 = box.y1, z1 = box.z1;
        var face = new Face(sprite, color, pose, consumer, box, (float) Math.floor(y0), (float) Math.cos(box.yaw), (float) Math.sin(box.yaw));
        face.quad(0, 1, 0, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0);
        face.quad(0, -1, 0, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
        face.quad(0, 0, -1, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0);
        face.quad(0, 0, 1, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
        face.quad(-1, 0, 0, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);
        face.quad(1, 0, 0, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1);
    }

    private record Face(TextureAtlasSprite sprite, int color, PoseStack.Pose pose, VertexConsumer consumer, Box box, float floor, float cos, float sin) {

        // Four corners anticlockwise seen from outside, before the box is turned.
        void quad(int nx, int ny, int nz, float... corners) {
            var pivotX = (box.x0 + box.x1) / 2;
            var pivotZ = (box.z0 + box.z1) / 2;
            for (var at = 0; at < 12; at += 3) {
                float x = corners[at], y = corners[at + 1], z = corners[at + 2];
                var v = ny != 0 ? CAP_V : ROWS_V * Math.clamp(1 - (y - floor), 0, 1);
                var dx = x - pivotX;
                var dz = z - pivotZ;
                consumer.addVertex(pose.pose(), pivotX + dx * cos - dz * sin, y, pivotZ + dx * sin + dz * cos)
                  .setColor(color)
                  .setUv(sprite.getU(COLUMN_U), sprite.getV(v))
                  .setOverlay(OverlayTexture.NO_OVERLAY)
                  .setLight(box.light)
                  .setNormal(pose, nx * cos - nz * sin, ny, nx * sin + nz * cos);
            }
        }
    }
}
