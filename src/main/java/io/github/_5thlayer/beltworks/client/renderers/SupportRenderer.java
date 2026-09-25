// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.client.renderers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.data.AtlasIds;
import net.minecraft.world.level.BlockAndLightGetter;
import io.github._5thlayer.beltworks.Beltworks;
import io.github._5thlayer.beltworks.model.LineScan;
import io.github._5thlayer.beltworks.model.Support;

import java.util.ArrayList;
import java.util.List;

/**
 * A tile's support drawn as boxes in its block's space (ADR 0012), so a placed tile and a planned
 * one draw it with the same code. It is only drawn: it has no collision.
 */
public final class SupportRenderer {

    private static final float LEG_WIDTH = 2 / 16f;
    private static final float CROSSBAR_HEIGHT = 2 / 16f;

    private SupportRenderer() {
    }

    /** A box of strut, from one corner to the other in its tile's block space, lit as the block it is in. */
    public record Box(float x0, float y0, float z0, float x1, float y1, float z1, int light) {
    }

    /**
     * The boxes of the support of the tile at {@code pos} travelling {@code travel}: under the tile, a
     * crossbar across its travel at each end, tying that end's two legs, and each leg cut at block
     * boundaries, since an atlas sprite cannot repeat, and lit as the block each piece is in.
     */
    public static List<Box> boxes(Support support, LineScan.Travel travel, BlockAndLightGetter level, BlockPos pos) {
        var boxes = new ArrayList<Box>();
        var light = LevelRenderer.getLightCoords(level, pos);
        var under = LevelRenderer.getLightCoords(level, pos.below());
        for (var end : new float[] {0, 1 - LEG_WIDTH}) {
            if (travel.x() != 0) boxes.add(new Box(end, -CROSSBAR_HEIGHT, 0, end + LEG_WIDTH, 0, 1, under));
            else boxes.add(new Box(0, -CROSSBAR_HEIGHT, end, 1, 0, end + LEG_WIDTH, under));
        }
        for (var leg : support.legs()) {
            var x0 = leg.corner().x() * (1 - LEG_WIDTH);
            var z0 = leg.corner().z() * (1 - LEG_WIDTH);
            for (var block = (int) Math.floor(leg.top()); block >= Math.floor(leg.bottom()); block--) {
                var top = Math.min(leg.top(), block + 1);
                var bottom = Math.max(leg.bottom(), block);
                if (top - bottom < 1e-4) continue;
                boxes.add(new Box(x0, (float) bottom, z0, x0 + LEG_WIDTH, (float) top, z0 + LEG_WIDTH,
                  block == 0 ? light : LevelRenderer.getLightCoords(level, pos.above(block))));
            }
        }
        return boxes;
    }

    public static void submit(List<Box> boxes, PoseStack poseStack, SubmitNodeCollector collector) {
        if (boxes.isEmpty()) return;
        var sprite = Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS).getSprite(Beltworks.id("block/conveyor_support"));
        collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(TextureAtlas.LOCATION_BLOCKS), (pose, consumer) -> {
            for (var box : boxes) box(box, sprite, pose, consumer);
        });
    }

    // Each face is textured by where it lies in its block, as a block model's faces are, so the
    // pieces of a leg read as one post.
    private static void box(Box box, TextureAtlasSprite sprite, PoseStack.Pose pose, VertexConsumer consumer) {
        var floor = (float) Math.floor(box.y0);
        float x0 = box.x0, y0 = box.y0, z0 = box.z0, x1 = box.x1, y1 = box.y1, z1 = box.z1;
        var face = new Face(sprite, pose, consumer, box.light, floor);
        face.quad(0, 1, 0, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0);
        face.quad(0, -1, 0, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
        face.quad(0, 0, -1, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0);
        face.quad(0, 0, 1, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
        face.quad(-1, 0, 0, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);
        face.quad(1, 0, 0, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1);
    }

    private record Face(TextureAtlasSprite sprite, PoseStack.Pose pose, VertexConsumer consumer, int light, float floor) {

        // Four corners anticlockwise seen from outside; u runs along x, or z on a face turned east or west.
        void quad(int nx, int ny, int nz, float... corners) {
            for (var at = 0; at < 12; at += 3) {
                float x = corners[at], y = corners[at + 1], z = corners[at + 2];
                var u = nx != 0 ? z : x;
                var v = ny != 0 ? z : 1 - (y - floor);
                consumer.addVertex(pose.pose(), x, y, z)
                  .setColor(0xFFFFFFFF)
                  .setUv(sprite.getU(u), sprite.getV(v))
                  .setOverlay(OverlayTexture.NO_OVERLAY)
                  .setLight(light)
                  .setNormal(pose, nx, ny, nz);
            }
        }
    }
}
