// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.client;

import io.github._5thlayer.groundworks.PlacementPlan;
import io.github._5thlayer.groundworks.client.PlacementPreviewEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.Nullable;
import io.github._5thlayer.beltworks.blocks.BeltTileBlock;
import io.github._5thlayer.beltworks.blocks.PlannedSupports;
import io.github._5thlayer.beltworks.blocks.SplitterBlock;
import io.github._5thlayer.beltworks.client.renderers.BeltEndRenderer;
import io.github._5thlayer.beltworks.client.renderers.SupportRenderer;
import io.github._5thlayer.beltworks.model.LineScan;
import io.github._5thlayer.beltworks.model.Support;

/**
 * The Mod's own drawing in the Groundworks library's Placement Preview (ADR 0010): a planned
 * splitter's belt surface, the wedges a stretch's slopes put down, which are no blocks of its
 * plan since they cost nothing, and the supports the plan's pieces will show (ADR 0012).
 * Groundworks draws the Stretch's anchors and the Dismantle's span itself (ADR 0011), and never
 * hears of supports.
 */
final class BeltPreviews {

    // The plan last asked about and its wedges: the preview draws one plan frame after frame.
    private static @Nullable PlacementPlan wedgesOf;
    private static Map<BlockPos, BlockState> wedges = Map.of();
    // And the plan and setting last asked about for its supports, with each one's boxes.
    private static @Nullable PlacementPlan supportsOf;
    private static Support.@Nullable Setting supportsSetting;
    private static Map<BlockPos, List<SupportRenderer.Box>> supports = Map.of();

    private BeltPreviews() {
    }

    static void register() {
        NeoForge.EVENT_BUS.addListener(BeltPreviews::splitterBelts);
        NeoForge.EVENT_BUS.addListener(BeltPreviews::stretchWedges);
        NeoForge.EVENT_BUS.addListener(BeltPreviews::plannedSupports);
    }

    /**
     * The support each planned piece will show, drawn with a placed piece's code in the plan's
     * tint, so a refused plan draws them in the refusal's.
     */
    private static void plannedSupports(PlacementPreviewEvent.Overlay event) {
        var plan = event.getPlan();
        var setting = BeltworksClientConfig.supports();
        if (plan != supportsOf || !setting.equals(supportsSetting)) {
            supportsOf = plan;
            supportsSetting = setting;
            var boxes = new LinkedHashMap<BlockPos, List<SupportRenderer.Box>>();
            PlannedSupports.of(event.getLevel(), plan.blocks(), setting)
              .forEach((pos, support) -> boxes.put(pos, SupportRenderer.plannedBoxes(support, pos)));
            supports = boxes;
        }
        if (supports.isEmpty()) return;
        var geometry = event.getGeometry();
        var camera = geometry.getLevelRenderState().cameraRenderState.pos;
        var poseStack = geometry.getPoseStack();
        supports.forEach((pos, boxes) -> {
            poseStack.pushPose();
            poseStack.translate(pos.getX() - camera.x(), pos.getY() - camera.y(), pos.getZ() - camera.z());
            SupportRenderer.submitPlanned(boxes, poseStack, geometry.getSubmitNodeCollector(), event.getTint());
            poseStack.popPose();
        });
    }

    /**
     * The wedges a plan's tiles put down as they are placed, drawn as the plan is: those under its
     * slopes, and under the slopes it makes of tiles already down. A plan that names its wedges, as
     * a single tile's does, has none drawn here.
     */
    private static void stretchWedges(PlacementPreviewEvent.Overlay event) {
        var plan = event.getPlan();
        if (plan != wedgesOf) {
            wedgesOf = plan;
            wedges = wedges(event.getLevel(), plan);
        }
        if (wedges.isEmpty()) return;
        var geometry = event.getGeometry();
        var camera = geometry.getLevelRenderState().cameraRenderState.pos;
        var poseStack = geometry.getPoseStack();
        var random = RandomSource.create();
        var instance = new QuadInstance();
        instance.setColor(event.getTint());
        wedges.forEach((pos, state) -> {
            var parts = new ArrayList<BlockStateModelPart>();
            random.setSeed(state.getSeed(pos));
            Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(state)
              .collectParts(BlockAndTintGetter.EMPTY, pos, state, random, parts);
            poseStack.pushPose();
            poseStack.translate(pos.getX() - camera.x(), pos.getY() - camera.y(), pos.getZ() - camera.z());
            geometry.getSubmitNodeCollector().submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(TextureAtlas.LOCATION_BLOCKS),
              (pose, buffer) -> {
                  for (var part : parts) {
                      emit(buffer, pose, part.getQuads(null), instance);
                      for (var direction : Direction.values()) emit(buffer, pose, part.getQuads(direction), instance);
                  }
              });
            poseStack.popPose();
        });
    }

    private static Map<BlockPos, BlockState> wedges(Level level, PlacementPlan plan) {
        var travels = new LinkedHashMap<LineScan.Spot, LineScan.Travel>();
        Block tile = null;
        for (var placed : plan.blocks()) {
            if (!(placed.state().getBlock() instanceof BeltTileBlock)) continue;
            tile = placed.state().getBlock();
            travels.put(BeltTileBlock.spot(placed.pos()), BeltTileBlock.travel(placed.state().getValue(BlockStateProperties.HORIZONTAL_FACING)));
        }
        if (tile == null) return Map.of();
        var planned = plan.blocks().stream().map(PlacementPlan.Placed::pos).toList();
        var drawn = new LinkedHashMap<BlockPos, BlockState>();
        BeltTileBlock.reshape(level, travels, tile).wedges().forEach((pos, state) -> {
            if (!planned.contains(pos)) drawn.put(pos, state);
        });
        return drawn;
    }

    private static void emit(VertexConsumer buffer, PoseStack.Pose pose, List<BakedQuad> quads, QuadInstance instance) {
        for (var quad : quads) buffer.putBakedQuad(pose, quad, instance);
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
}
