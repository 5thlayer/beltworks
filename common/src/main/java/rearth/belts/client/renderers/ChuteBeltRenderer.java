package rearth.belts.client.renderers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.data.AtlasIds;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.GeometryUtils;
import org.jspecify.annotations.Nullable;
import rearth.belts.blocks.ChuteBlockEntity;
import rearth.belts.util.SplineUtil;

import java.util.ArrayList;
import java.util.List;

public class ChuteBeltRenderer implements BlockEntityRenderer<ChuteBlockEntity, ChuteBeltRenderer.RenderState> {

    public record Vertex(float x, float y, float z, float u, float v) {
        public static Vertex create(Vec3 pos, float u, float v) {
            return new Vertex((float) pos.x, (float) pos.y, (float) pos.z, u, v);
        }
    }

    public record Quad(Vertex a, Vertex b, Vertex c, Vertex d) {
    }

    public record RenderedItem(Vec3 position, float yaw, float pitch, ItemStackRenderState itemState) {
    }

    // overrides NF method to prevent culling issues
    public AABB getRenderBoundingBox(ChuteBlockEntity blockEntity) {
        return new AABB(Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY);
    }

    @Override
    public RenderState createRenderState() {
        return new RenderState();
    }

    @Override
    public void extractRenderState(ChuteBlockEntity entity, RenderState state, float partialTicks, Vec3 cameraPosition,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(entity, state, partialTicks, cameraPosition, breakProgress);

        state.quads = List.of();
        state.items.clear();
        state.filter = null;
        state.beltSprite = null;

        var level = entity.getLevel();
        var targetPos = entity.getTarget();
        var beltData = entity.getBeltData();
        if (level == null || targetPos == null || beltData == null || targetPos.distManhattan(entity.getBlockPos()) < 1) {
            return;
        }

        var targetCandidate = level.getBlockEntity(targetPos, rearth.belts.BlockEntitiesContent.CHUTE_BLOCK.get());
        if (targetCandidate.isEmpty()) return;

        state.quads = createSplineModel(
          beltData,
          entity.getBlockPos(),
          entity.getOwnFacing(),
          targetCandidate.get().getOwnFacing().getOpposite()
        );
        state.beltSprite = Minecraft.getInstance()
                .getAtlasManager()
                .getAtlasOrThrow(AtlasIds.BLOCKS)
                .getSprite(rearth.belts.Belts.id("block/conveyorbelt"));

        var minecraft = Minecraft.getInstance();
        for (var beltItem : entity.getMovingItems()) {
            var progressPerTick = ChuteBlockEntity.BELT_SPEED / beltData.totalLength() / 20f;
            var renderProgress = Mth.lerp(partialTicks, beltItem.previousProgress, beltItem.progress);
            var nextProgress = Math.min(1, renderProgress + progressPerTick);
            var worldPoint = SplineUtil.getPositionOnSpline(beltData, renderProgress);
            var nextWorldPoint = SplineUtil.getPositionOnSpline(beltData, nextProgress);
            var localPoint = worldPoint.subtract(entity.getBlockPos().getCenter());

            var forward = nextWorldPoint.subtract(worldPoint);
            var flatForward = new Vec3(forward.x, 0, forward.z).normalize();
            var yaw = (float) Math.toDegrees(Math.atan2(-flatForward.z, flatForward.x));
            var pitch = (float) Math.toDegrees(Math.atan2(forward.y, Math.sqrt(forward.x * forward.x + forward.z * forward.z)));

            var itemState = new ItemStackRenderState();
            minecraft.getItemModelResolver().updateForTopItem(
              itemState, beltItem.stack, ItemDisplayContext.FIXED, level, null, 0
            );
            state.items.add(new RenderedItem(localPoint, yaw, pitch, itemState));
        }

        if (!entity.filteredItem.isEmpty()) {
            var filterState = new ItemStackRenderState();
            minecraft.getItemModelResolver().updateForTopItem(
              filterState, entity.filteredItem, ItemDisplayContext.FIXED, level, null, 0
            );
            state.filter = filterState;
            state.filterFacing = entity.getOwnFacing();
        }
    }

    @Override
    public void submit(RenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState cameraRenderState) {
        if (!state.quads.isEmpty() && state.beltSprite != null) {
            poseStack.pushPose();
            poseStack.translate(0, -2 / 16f + 0.08f, 0);
            collector.submitCustomGeometry(poseStack, Sheets.cutoutBlockSheet(), (pose, consumer) -> {
                for (var quad : state.quads) {
                    addVertex(consumer, pose, state.beltSprite, quad.a, state.lightCoords);
                    addVertex(consumer, pose, state.beltSprite, quad.b, state.lightCoords);
                    addVertex(consumer, pose, state.beltSprite, quad.c, state.lightCoords);
                    addVertex(consumer, pose, state.beltSprite, quad.d, state.lightCoords);
                }
            });
            poseStack.popPose();
        }

        for (var item : state.items) {
            poseStack.pushPose();
            poseStack.translate(item.position.x + 0.5, item.position.y + 0.8f - 3 / 16f, item.position.z + 0.5);
            poseStack.mulPose(Axis.YP.rotationDegrees(item.yaw));
            poseStack.mulPose(Axis.ZP.rotationDegrees(item.pitch));
            poseStack.mulPose(Axis.XP.rotationDegrees(90));
            poseStack.scale(0.6f, 0.6f, 0.6f);
            item.itemState.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
            poseStack.popPose();
        }

        if (state.filter != null) {
            var direction = Vec3.atLowerCornerOf(state.filterFacing.getUnitVec3i());
            poseStack.pushPose();
            poseStack.translate(0.5 + direction.x * -0.43f, 0.7, 0.5 + direction.z * -0.43f);
            if (state.filterFacing.getAxis() == net.minecraft.core.Direction.Axis.X) {
                poseStack.mulPose(Axis.YP.rotationDegrees(90));
            }
            poseStack.scale(0.4f, 0.4f, 0.4f);
            state.filter.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
            poseStack.popPose();
        }
    }

    private static List<Quad> createSplineModel(ChuteBlockEntity.BeltData beltData, BlockPos origin,
                                                net.minecraft.core.Direction startFacing,
                                                net.minecraft.core.Direction endFacing) {
        var result = new ArrayList<Quad>();
        var segmentCount = Math.max(1, (int) Math.ceil(beltData.totalLength() / 0.75f));
        var lineWidth = 0.33f;
        var startDirection = Vec3.atLowerCornerOf(startFacing.getUnitVec3i());
        var endDirection = Vec3.atLowerCornerOf(endFacing.getUnitVec3i());
        var beginRight = startDirection.cross(new Vec3(0, 1, 0)).normalize();
        var localStart = new Vec3(0.5, 0.5, 0.5).add(startDirection.scale(-0.5));
        var lastRight = localStart.add(beginRight.scale(lineWidth));
        var lastLeft = localStart.add(beginRight.scale(-lineWidth));

        for (int i = 0; i < segmentCount; i++) {
            var last = i == segmentCount - 1;
            var progress = i / (double) segmentCount;
            var nextProgress = (i + 1) / (double) segmentCount;
            var localPoint = SplineUtil.getPositionOnSpline(beltData, progress).subtract(origin.getCenter());
            var localPointNext = SplineUtil.getPositionOnSpline(beltData, nextProgress).subtract(origin.getCenter());

            var cross = localPointNext.subtract(localPoint).cross(new Vec3(0, 1, 0)).normalize();
            if (last) cross = endDirection.cross(new Vec3(0, 1, 0)).normalize();

            var nextRight = localPointNext.add(cross.scale(lineWidth)).add(0.5, 0.5, 0.5);
            var nextLeft = localPointNext.add(cross.scale(-lineWidth)).add(0.5, 0.5, 0.5);

            var curveStrength = 1 - Math.abs(lastLeft.subtract(lastRight).normalize().dot(nextLeft.subtract(nextRight).normalize()));
            if (curveStrength > 0.025) {
                var midProgress = (i + 0.5) / segmentCount;
                var localMid = SplineUtil.getPositionOnSpline(beltData, midProgress).subtract(origin.getCenter());
                var midCross = localMid.subtract(localPoint).cross(new Vec3(0, 1, 0)).normalize();
                var midRight = localMid.add(midCross.scale(lineWidth)).add(0.5, 0.5, 0.5);
                var midLeft = localMid.add(midCross.scale(-lineWidth)).add(0.5, 0.5, 0.5);
                addSegmentVertices(midRight, lastRight, midLeft, lastLeft, result, 0, 0.5f);
                addSegmentVertices(nextRight, midRight, nextLeft, midLeft, result, 0.5f, 1);
            } else {
                addSegmentVertices(nextRight, lastRight, nextLeft, lastLeft, result, 0, 1);
            }

            lastRight = nextRight;
            lastLeft = nextLeft;
        }

        return result;
    }

    private static void addSegmentVertices(Vec3 nextRight, Vec3 lastRight, Vec3 nextLeft, Vec3 lastLeft,
                                           List<Quad> result, float vStart, float vEnd) {
        var skirtHeight = 0.15f;
        result.add(new Quad(
          Vertex.create(nextRight, 0, vEnd), Vertex.create(nextLeft, 1, vEnd),
          Vertex.create(lastLeft, 1, vStart), Vertex.create(lastRight, 0, vStart)
        ));
        result.add(new Quad(
          Vertex.create(nextRight.add(0, -skirtHeight, 0), 2 / 16f, vEnd), Vertex.create(nextRight, 0, vEnd),
          Vertex.create(lastRight, 0, vStart), Vertex.create(lastRight.add(0, -skirtHeight, 0), 2 / 16f, vStart)
        ));
        result.add(new Quad(
          Vertex.create(lastLeft.add(0, -skirtHeight, 0), 2 / 16f, vStart), Vertex.create(lastLeft, 0, vStart),
          Vertex.create(nextLeft, 0, vEnd), Vertex.create(nextLeft.add(0, -skirtHeight, 0), 2 / 16f, vEnd)
        ));
        result.add(new Quad(
          Vertex.create(lastRight.add(0, -skirtHeight, 0), 0, vStart), Vertex.create(lastLeft.add(0, -skirtHeight, 0), 1, vStart),
          Vertex.create(nextLeft.add(0, -skirtHeight, 0), 1, vEnd), Vertex.create(nextRight.add(0, -skirtHeight, 0), 0, vEnd)
        ));
    }

    private static void addVertex(VertexConsumer consumer, PoseStack.Pose pose, TextureAtlasSprite sprite, Vertex vertex, int light) {
        consumer.addVertex(pose.pose(), vertex.x, vertex.y, vertex.z)
          .setColor(255, 255, 255, 255)
          .setUv(sprite.getU(vertex.u), sprite.getV(vertex.v))
          .setOverlay(OverlayTexture.NO_OVERLAY)
          .setLight(light)
          .setNormal(pose, 0, 1, 0);
    }

    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 96;
    }

    public static class RenderState extends BlockEntityRenderState {
        private List<Quad> quads = List.of();
        private final List<RenderedItem> items = new ArrayList<>();
        private TextureAtlasSprite beltSprite;
        private ItemStackRenderState filter;
        private net.minecraft.core.Direction filterFacing = net.minecraft.core.Direction.NORTH;
    }
}
