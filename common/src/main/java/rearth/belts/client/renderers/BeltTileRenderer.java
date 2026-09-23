package rearth.belts.client.renderers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import rearth.belts.blocks.BeltTileBlock;
import rearth.belts.blocks.BeltTileBlockEntity;
import rearth.belts.client.TileLines;

import java.util.ArrayList;
import java.util.List;

/**
 * The items a tile carries, drawn from the client's copy of its line (PlanetaryFactory #395).
 * Each tile draws only the items whose centre is on it, so an item crossing into the next tile is
 * drawn once, and a line whose head is off screen still shows every other tile's items.
 */
public class BeltTileRenderer implements BlockEntityRenderer<BeltTileBlockEntity, BeltTileRenderer.RenderState> {

    // The tile's top face (BeltTileBlock's shape), and how far above it each kind of item's centre
    // sits when it is laid flat.
    private static final double SURFACE = 6 / 16d;
    private static final double BLOCK_LIFT = 0.07;
    private static final double FLAT_LIFT = 0.02;

    @Override
    public RenderState createRenderState() {
        return new RenderState();
    }

    @Override
    public void extractRenderState(BeltTileBlockEntity entity, RenderState state, float partialTicks, Vec3 cameraPosition,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(entity, state, partialTicks, cameraPosition, breakProgress);
        state.items.clear();

        var level = entity.getLevel();
        if (level == null) return;
        var place = TileLines.at(level, entity.getBlockPos());
        if (place == null) return;

        var travel = BeltTileBlock.travel(entity.travel());
        var shape = entity.shape();
        var resolver = Minecraft.getInstance().getItemModelResolver();
        for (var drawn : place.drawn(partialTicks)) {
            var stack = drawn.entry().payload();
            var block = stack.getItem() instanceof BlockItem;
            // Two lanes of four per block by id parity, as a splitter half draws them, so items at
            // a readable size do not overlap. The line itself has one lane (#344).
            var lane = (drawn.entry().id() & 1) == 0 ? 0.125 : -0.125;
            var point = shape.point(drawn.offset(), travel);
            var forward = new Vec3(point.headingX(), 0, point.headingZ());
            var yaw = (float) Math.toDegrees(Math.atan2(-forward.z, forward.x));
            var at = new Vec3(0.5 + point.x(), SURFACE + (block ? BLOCK_LIFT : FLAT_LIFT), 0.5 + point.z())
                       .add(forward.cross(new Vec3(0, 1, 0)).scale(lane));
            var itemState = new ItemStackRenderState();
            resolver.updateForTopItem(itemState, stack, ItemDisplayContext.FIXED, level, null, 0);
            state.items.add(new Item(at, yaw, block ? 0.5f : 0.35f, itemState));
        }
    }

    @Override
    public void submit(RenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState cameraRenderState) {
        for (var item : state.items) {
            poseStack.pushPose();
            poseStack.translate(item.at.x, item.at.y, item.at.z);
            poseStack.mulPose(Axis.YP.rotationDegrees(item.yaw));
            poseStack.mulPose(Axis.XP.rotationDegrees(90));
            poseStack.scale(item.scale, item.scale, item.scale);
            item.itemState.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
            poseStack.popPose();
        }
    }

    public record Item(Vec3 at, float yaw, float scale, ItemStackRenderState itemState) {
    }

    public static class RenderState extends BlockEntityRenderState {
        private final List<Item> items = new ArrayList<>();
    }
}
