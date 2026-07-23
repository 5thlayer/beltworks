package rearth.belts.client.renderers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import rearth.belts.Belts;
import rearth.belts.BlockEntitiesContent;
import rearth.belts.blocks.ChuteBlockEntity;
import rearth.belts.util.SplineUtil;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

public class ChuteBeltRenderer implements BlockEntityRenderer<ChuteBlockEntity, ChuteBeltRenderer.RenderState> {

    private static final int BELT_FRAME_COUNT = 16;
    private static final int LIGHT_REFRESH_INTERVAL = 82;
    private static final double ITEM_POSITION_LERP = 0.06;
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final RenderType BELT_RENDER_TYPE =
            RenderTypes.entityCutout(Belts.id("textures/block/conveyorbelt.png"));

    private final Map<ChuteBlockEntity, CachedMesh> meshCache = new WeakHashMap<>();

    public record Vertex(float x, float y, float z, float u, float v) {
        public static Vertex create(Vec3 pos, float u, float v) {
            return new Vertex((float) pos.x, (float) pos.y, (float) pos.z, u, v);
        }
    }

    public record Quad(Vertex a, Vertex b, Vertex c, Vertex d, int lightA, int lightB, int lightC, int lightD) {
    }

    public record RenderedItem(Vec3 position, float yaw, float pitch, int lightCoords, ItemStackRenderState itemState) {
    }

    private record CachedMesh(ChuteBlockEntity.BeltData beltData, Direction startFacing, Direction endFacing,
                              List<Quad> quads, Set<Long> lightPositions, long lightRefreshTick) {
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
        state.beltFrame = 0;

        var level = entity.getLevel();
        var targetPos = entity.getTarget();
        var beltData = entity.getBeltData();
        if (level == null || targetPos == null || beltData == null || targetPos.distManhattan(entity.getBlockPos()) < 1) {
            clearCaches(entity);
            return;
        }

        var targetCandidate = level.getBlockEntity(targetPos, BlockEntitiesContent.CHUTE_BLOCK.get());
        if (targetCandidate.isEmpty()) {
            clearCaches(entity);
            return;
        }

        var startFacing = entity.getOwnFacing();
        var endFacing = targetCandidate.get().getOwnFacing().getOpposite();
        var gameTime = level.getGameTime();
        var cachedMesh = meshCache.get(entity);
        var rebuildMesh = cachedMesh == null
                || cachedMesh.beltData != beltData
                || cachedMesh.startFacing != startFacing
                || cachedMesh.endFacing != endFacing
                || gameTime < cachedMesh.lightRefreshTick
                || gameTime - cachedMesh.lightRefreshTick >= LIGHT_REFRESH_INTERVAL;

        if (rebuildMesh) {
            var meshLightPositions = new HashSet<Long>();
            var quads = createSplineModel(
              beltData,
              entity.getBlockPos(),
              startFacing,
              endFacing,
              pos -> getLightCoords(entity, pos, meshLightPositions, true)
            );
            cachedMesh = new CachedMesh(
              beltData, startFacing, endFacing, quads, Set.copyOf(meshLightPositions), gameTime
            );
            meshCache.put(entity, cachedMesh);
        }

        state.quads = cachedMesh.quads;
        var activeLightPositions = new HashSet<>(cachedMesh.lightPositions);
        var animationTime = (gameTime + partialTicks) * entity.getBeltSpeedMultiplier();
        state.beltFrame = Math.floorMod((int) Math.floor(animationTime), BELT_FRAME_COUNT);

        var minecraft = Minecraft.getInstance();
        var activeItemIds = new HashSet<Short>();
        var originCenter = entity.getBlockPos().getCenter();
        var progressPerTick = entity.getBeltSpeed() / beltData.totalLength() / 20f;
        for (var beltItem : entity.getMovingItems()) {
            var nextProgress = Math.min(1, beltItem.progress + progressPerTick);
            var worldPoint = SplineUtil.getPositionOnSpline(beltData, beltItem.progress);
            var nextWorldPoint = SplineUtil.getPositionOnSpline(beltData, nextProgress);
            var localPoint = worldPoint.subtract(originCenter);
            var renderPosition = entity.lastRenderedPositions
                    .getOrDefault(beltItem.id, localPoint)
                    .lerp(localPoint, ITEM_POSITION_LERP);

            activeItemIds.add(beltItem.id);
            entity.lastRenderedPositions.put(beltItem.id, renderPosition);
            var itemLight = getLightCoords(
              entity,
              BlockPos.containing(renderPosition.add(originCenter)),
              activeLightPositions,
              rebuildMesh
            );

            if (!(beltItem.stack.getItem() instanceof BlockItem)) {
                renderPosition = renderPosition.add(0, -0.12, 0);
            }

            var forward = nextWorldPoint.subtract(worldPoint);
            var flatForward = new Vec3(forward.x, 0, forward.z).normalize();
            var yaw = (float) Math.toDegrees(Math.atan2(-flatForward.z, flatForward.x));
            var pitch = (float) Math.toDegrees(Math.atan2(forward.y, Math.sqrt(forward.x * forward.x + forward.z * forward.z)));

            var itemState = new ItemStackRenderState();
            minecraft.getItemModelResolver().updateForTopItem(
              itemState, beltItem.stack, ItemDisplayContext.FIXED, level, null, 0
            );
            state.items.add(new RenderedItem(renderPosition, yaw, pitch, itemLight, itemState));
        }
        entity.lastRenderedPositions.keySet().removeIf(id -> !activeItemIds.contains(id));
        entity.cachedLightCoords.keySet().removeIf(pos -> !activeLightPositions.contains(pos));

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
        if (!state.quads.isEmpty()) {
            poseStack.pushPose();
            poseStack.translate(0, -2 / 16f + 0.08f, 0);
            collector.submitCustomGeometry(poseStack, BELT_RENDER_TYPE, (pose, consumer) -> {
                for (var quad : state.quads) {
                    addVertex(consumer, pose, quad.a, quad.lightA, state.beltFrame);
                    addVertex(consumer, pose, quad.b, quad.lightB, state.beltFrame);
                    addVertex(consumer, pose, quad.c, quad.lightC, state.beltFrame);
                    addVertex(consumer, pose, quad.d, quad.lightD, state.beltFrame);
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
            item.itemState.submit(poseStack, collector, item.lightCoords, OverlayTexture.NO_OVERLAY, 0);
            poseStack.popPose();
        }

        if (state.filter != null) {
            var direction = Vec3.atLowerCornerOf(state.filterFacing.getUnitVec3i());
            poseStack.pushPose();
            poseStack.translate(0.5 + direction.x * -0.43f, 0.7, 0.5 + direction.z * -0.43f);
            if (state.filterFacing.getAxis() == Direction.Axis.X) {
                poseStack.mulPose(Axis.YP.rotationDegrees(90));
            }
            poseStack.scale(0.4f, 0.4f, 0.4f);
            state.filter.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
            poseStack.popPose();
        }
    }

    private static List<Quad> createSplineModel(ChuteBlockEntity.BeltData beltData, BlockPos origin,
                                                Direction startFacing,
                                                Direction endFacing,
                                                java.util.function.ToIntFunction<BlockPos> lightResolver) {
        var result = new ArrayList<Quad>();
        var segmentCount = Math.max(1, (int) Math.ceil(beltData.totalLength() / 0.75f));
        var lineWidth = 0.33f;
        var startDirection = Vec3.atLowerCornerOf(startFacing.getUnitVec3i());
        var endDirection = Vec3.atLowerCornerOf(endFacing.getUnitVec3i());
        var beginRight = startDirection.cross(UP).normalize();
        var originCenter = origin.getCenter();
        var localStart = new Vec3(0.5, 0.5, 0.5).add(startDirection.scale(-0.5));
        var lastRight = localStart.add(beginRight.scale(lineWidth));
        var lastLeft = localStart.add(beginRight.scale(-lineWidth));

        for (int i = 0; i < segmentCount; i++) {
            var last = i == segmentCount - 1;
            var progress = i / (double) segmentCount;
            var nextProgress = (i + 1) / (double) segmentCount;
            var worldPoint = SplineUtil.getPositionOnSpline(beltData, progress);
            var worldPointNext = SplineUtil.getPositionOnSpline(beltData, nextProgress);
            var localPoint = worldPoint.subtract(originCenter);
            var localPointNext = worldPointNext.subtract(originCenter);
            var startLight = lightResolver.applyAsInt(BlockPos.containing(worldPoint));
            var endLight = lightResolver.applyAsInt(BlockPos.containing(worldPointNext));

            var cross = localPointNext.subtract(localPoint).cross(UP).normalize();
            if (last) cross = endDirection.cross(UP).normalize();

            var nextRight = localPointNext.add(cross.scale(lineWidth)).add(0.5, 0.5, 0.5);
            var nextLeft = localPointNext.add(cross.scale(-lineWidth)).add(0.5, 0.5, 0.5);

            var curveStrength = 1 - Math.abs(lastLeft.subtract(lastRight).normalize().dot(nextLeft.subtract(nextRight).normalize()));
            if (curveStrength > 0.025) {
                var midProgress = (i + 0.5) / segmentCount;
                var worldMid = SplineUtil.getPositionOnSpline(beltData, midProgress);
                var localMid = worldMid.subtract(originCenter);
                var midLight = lightResolver.applyAsInt(BlockPos.containing(worldMid));
                var midCross = localMid.subtract(localPoint).cross(UP).normalize();
                var midRight = localMid.add(midCross.scale(lineWidth)).add(0.5, 0.5, 0.5);
                var midLeft = localMid.add(midCross.scale(-lineWidth)).add(0.5, 0.5, 0.5);
                addSegmentVertices(midRight, lastRight, midLeft, lastLeft, result, 0, 0.5f, startLight, midLight);
                addSegmentVertices(nextRight, midRight, nextLeft, midLeft, result, 0.5f, 1, midLight, endLight);
            } else {
                addSegmentVertices(nextRight, lastRight, nextLeft, lastLeft, result, 0, 1, startLight, endLight);
            }

            lastRight = nextRight;
            lastLeft = nextLeft;
        }

        return result;
    }

    private static void addSegmentVertices(Vec3 nextRight, Vec3 lastRight, Vec3 nextLeft, Vec3 lastLeft,
                                           List<Quad> result, float vStart, float vEnd,
                                           int startLight, int endLight) {
        var skirtHeight = 0.15f;
        result.add(new Quad(
          Vertex.create(nextRight, 0, vEnd), Vertex.create(nextLeft, 1, vEnd),
          Vertex.create(lastLeft, 1, vStart), Vertex.create(lastRight, 0, vStart),
          endLight, endLight, startLight, startLight
        ));
        result.add(new Quad(
          Vertex.create(nextRight.add(0, -skirtHeight, 0), 2 / 16f, vEnd), Vertex.create(nextRight, 0, vEnd),
          Vertex.create(lastRight, 0, vStart), Vertex.create(lastRight.add(0, -skirtHeight, 0), 2 / 16f, vStart),
          endLight, endLight, startLight, startLight
        ));
        result.add(new Quad(
          Vertex.create(lastLeft.add(0, -skirtHeight, 0), 2 / 16f, vStart), Vertex.create(lastLeft, 0, vStart),
          Vertex.create(nextLeft, 0, vEnd), Vertex.create(nextLeft.add(0, -skirtHeight, 0), 2 / 16f, vEnd),
          startLight, startLight, endLight, endLight
        ));
        result.add(new Quad(
          Vertex.create(lastRight.add(0, -skirtHeight, 0), 0, vStart), Vertex.create(lastLeft.add(0, -skirtHeight, 0), 1, vStart),
          Vertex.create(nextLeft.add(0, -skirtHeight, 0), 1, vEnd), Vertex.create(nextRight.add(0, -skirtHeight, 0), 0, vEnd),
          startLight, startLight, endLight, endLight
        ));
    }

    private static int getLightCoords(ChuteBlockEntity entity, BlockPos pos, Set<Long> activePositions, boolean refresh) {
        var key = pos.asLong();
        var firstSample = activePositions.add(key);

        var level = entity.getLevel();
        if (level == null) {
            return 15728880;
        }

        var cachedLight = entity.cachedLightCoords.get(key);
        if (cachedLight == null || (refresh && firstSample)) {
            cachedLight = LevelRenderer.getLightCoords(level, pos);
            entity.cachedLightCoords.put(key, cachedLight);
        }
        return cachedLight;
    }

    private void clearCaches(ChuteBlockEntity entity) {
        meshCache.remove(entity);
        entity.lastRenderedPositions.clear();
        entity.cachedLightCoords.clear();
    }

    private static void addVertex(VertexConsumer consumer, PoseStack.Pose pose, Vertex vertex, int light, int frame) {
        consumer.addVertex(pose.pose(), vertex.x, vertex.y, vertex.z)
          .setColor(255, 255, 255, 255)
          .setUv(vertex.u, (frame + vertex.v) / BELT_FRAME_COUNT)
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
        private int beltFrame;
        private ItemStackRenderState filter;
        private Direction filterFacing = Direction.NORTH;
    }
}
