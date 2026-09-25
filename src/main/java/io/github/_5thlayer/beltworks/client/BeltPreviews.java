// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.client;

import io.github._5thlayer.placementpreview.PlacementPlan;
import io.github._5thlayer.placementpreview.client.Outline;
import io.github._5thlayer.placementpreview.client.PlacementPreviewEvent;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Vector3f;
import io.github._5thlayer.beltworks.ComponentContent;
import io.github._5thlayer.beltworks.blocks.SplitterBlock;
import io.github._5thlayer.beltworks.client.renderers.BeltEndRenderer;
import io.github._5thlayer.beltworks.items.BeltTileItem;
import io.github._5thlayer.beltworks.items.Dismantling;

/**
 * The Mod's own drawing in the placementpreview library's Placement Preview (ADR 0010): a held tile
 * stack's stored start, a Dismantle Plan's span, and a planned splitter's belt surface.
 */
final class BeltPreviews {

    private static final int START_COLOUR = 0xFFFFD040;
    private static final float LINE_WIDTH = 2.5F;
    // A hair above a tile's belt surface.
    private static final double SURFACE = 6.0 / 16.0 + 0.01;
    private static final VoxelShape TILE_OUTLINE = Shapes.create(new AABB(0, 0, 0, 1, SURFACE, 1));

    private BeltPreviews() {
    }

    static void register() {
        NeoForge.EVENT_BUS.addListener(BeltPreviews::stretchStart);
        NeoForge.EVENT_BUS.addListener(BeltPreviews::dismantle);
        NeoForge.EVENT_BUS.addListener(BeltPreviews::splitterBelts);
    }

    /**
     * The start a held tile stack has stored: an outline round the tile there and an arrow the way
     * the stretch will run from it, and an outline round each corner added since. A Marker, so they
     * stay visible whatever the aim while the player looks for the end.
     */
    private static void stretchStart(PlacementPreviewEvent.Marker event) {
        var stack = event.getStack();
        if (!(stack.getItem() instanceof BeltTileItem)) return;
        BlockPos start = stack.get(ComponentContent.BELT_START.get());
        Direction look = stack.get(ComponentContent.BELT_DIR.get());
        if (start == null || look == null) return;

        var geometry = event.getGeometry();
        var camera = geometry.getLevelRenderState().cameraRenderState.pos;
        PoseStack poseStack = geometry.getPoseStack();
        SubmitNodeCollector collector = geometry.getSubmitNodeCollector();
        for (BlockPos corner : stack.getOrDefault(ComponentContent.STRETCH_CORNERS.get(), List.<BlockPos>of())) {
            poseStack.pushPose();
            poseStack.translate(corner.getX() - camera.x(), corner.getY() - camera.y(), corner.getZ() - camera.z());
            collector.submitCustomGeometry(poseStack, RenderTypes.lines(), BeltPreviews::tileOutline);
            poseStack.popPose();
        }
        poseStack.pushPose();
        poseStack.translate(start.getX() - camera.x(), start.getY() - camera.y(), start.getZ() - camera.z());
        collector.submitCustomGeometry(poseStack, RenderTypes.lines(), (pose, buffer) -> {
            tileOutline(pose, buffer);
            var backX = 0.5 - 0.35 * look.getStepX();
            var backZ = 0.5 - 0.35 * look.getStepZ();
            var tipX = 0.5 + 0.35 * look.getStepX();
            var tipZ = 0.5 + 0.35 * look.getStepZ();
            line(buffer, pose, backX, SURFACE, backZ, tipX, SURFACE, tipZ);
            var left = look.getCounterClockWise();
            for (var side : new int[] {1, -1}) {
                line(buffer, pose, tipX, SURFACE, tipZ,
                        tipX - 0.2 * look.getStepX() + side * 0.2 * left.getStepX(), SURFACE,
                        tipZ - 0.2 * look.getStepZ() + side * 0.2 * left.getStepZ());
            }
        });
        poseStack.popPose();
    }

    /**
     * A held dismantling tool with a start stored takes the frame: the tiles and wedges its Dismantle
     * Plan would take up at the aim, or the stored start alone where the plan is refused.
     */
    private static void dismantle(PlacementPreviewEvent.Takeover event) {
        var stack = event.getStack();
        if (!Dismantling.dismantles(stack)) return;
        var level = event.getLevel();
        var start = Dismantling.liveStart(level, stack);
        if (start == null) return;
        var plan = event.getHitResult() instanceof BlockHitResult block && block.getType() == HitResult.Type.BLOCK
                     ? Dismantling.plan(level, stack, block.getBlockPos())
                     : null;
        var positions = new ArrayList<BlockPos>();
        if (plan == null || plan.refused()) {
            positions.add(start);
        } else {
            positions.addAll(plan.tiles());
            positions.addAll(plan.wedges());
        }
        Outline.draw(event.getGeometry(), positions);
        event.setCanceled(true);
    }

    /** A planned splitter half's belt surface, which a block entity renderer draws rather than the block model the preview draws. */
    private static void splitterBelts(PlacementPreviewEvent.Overlay event) {
        var geometry = event.getGeometry();
        var camera = geometry.getLevelRenderState().cameraRenderState.pos;
        for (PlacementPlan.Placed placed : event.getPlan().blocks()) {
            if (placed.state().getBlock() instanceof SplitterBlock splitter) {
                BeltEndRenderer.submitPlannedSplitter(geometry.getPoseStack(), geometry.getSubmitNodeCollector(), camera,
                        placed.pos(), placed.state().getValue(SplitterBlock.FACING), splitter.tier(), event.getTint());
            }
        }
    }

    private static void tileOutline(PoseStack.Pose pose, VertexConsumer buffer) {
        TILE_OUTLINE.forAllEdges((x1, y1, z1, x2, y2, z2) -> line(buffer, pose, x1, y1, z1, x2, y2, z2));
    }

    private static void line(VertexConsumer buffer, PoseStack.Pose pose,
                             double x1, double y1, double z1, double x2, double y2, double z2) {
        var normal = new Vector3f((float) (x2 - x1), (float) (y2 - y1), (float) (z2 - z1)).normalize();
        buffer.addVertex(pose, (float) x1, (float) y1, (float) z1).setColor(START_COLOUR).setNormal(pose, normal).setLineWidth(LINE_WIDTH);
        buffer.addVertex(pose, (float) x2, (float) y2, (float) z2).setColor(START_COLOUR).setNormal(pose, normal).setLineWidth(LINE_WIDTH);
    }
}
