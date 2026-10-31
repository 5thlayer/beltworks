// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.client.renderers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github._5thlayer.beltworks.blocks.CreativeCrateBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The item a creative crate is set to, drawn flat on each of its four sides between its bands
 * (GLOSSARY.md, Creative Crate). An unset crate shows nothing, and a side another block covers is
 * left out.
 *
 * <p>The item is drawn as its inventory icon, in the GUI display context, pressed flat against the
 * side: a block item is its tilted cube squashed into a picture, not a cube sunk into the crate. The
 * squash turns every face's normal to the side's own, so the icon is lit as the side is.
 */
public class CreativeCrateRenderer implements BlockEntityRenderer<CreativeCrateBlockEntity, CreativeCrateRenderer.RenderState> {

    // Six pixels across, the clear rows between the side's bands, a hair off the face.
    private static final float ITEM_SCALE = 6 / 16f;
    // The icon's depth: little enough to read as flat, enough that its faces keep their order.
    private static final float FLAT = 0.001f;
    private static final double OUT = 0.5 + 0.01;

    /** A side the item shows on, lit by the block in front of it, since the crate's own is dark. */
    record Side(Direction direction, int light) {
    }

    @Override
    public RenderState createRenderState() {
        return new RenderState();
    }

    @Override
    public void extractRenderState(CreativeCrateBlockEntity entity, RenderState state, float partialTicks, Vec3 cameraPosition,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(entity, state, partialTicks, cameraPosition, breakProgress);
        state.item = null;
        state.sides = List.of();
        var level = entity.getLevel();
        var item = entity.item();
        if (level == null || item.isEmpty()) return;

        var sides = new ArrayList<Side>(4);
        for (var direction : Direction.Plane.HORIZONTAL) {
            var front = entity.getBlockPos().relative(direction);
            if (level.getBlockState(front).isSolidRender()) continue;
            sides.add(new Side(direction, LevelRenderer.getLightCoords(level, front)));
        }
        if (sides.isEmpty()) return;
        var itemState = new ItemStackRenderState();
        Minecraft.getInstance().getItemModelResolver().updateForTopItem(itemState, item, ItemDisplayContext.GUI, level, null, 0);
        state.item = itemState;
        state.sides = sides;
    }

    @Override
    public void submit(RenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState cameraRenderState) {
        var item = state.item;
        if (item == null) return;
        for (var side : state.sides) {
            var direction = side.direction();
            poseStack.pushPose();
            poseStack.translate(0.5 + direction.getStepX() * OUT, 0.5, 0.5 + direction.getStepZ() * OUT);
            // The icon's front, its +z as in a GUI, faces out of the side, so it reads unmirrored.
            poseStack.mulPose(Axis.YP.rotationDegrees(-direction.toYRot()));
            poseStack.scale(ITEM_SCALE, ITEM_SCALE, ITEM_SCALE * FLAT);
            item.submit(poseStack, collector, side.light(), OverlayTexture.NO_OVERLAY, 0);
            poseStack.popPose();
        }
    }

    public static class RenderState extends BlockEntityRenderState {
        private @Nullable ItemStackRenderState item;
        private List<Side> sides = List.of();
    }
}
