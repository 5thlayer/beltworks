// SPDX-FileCopyrightText: Rearth
// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: CC-BY-4.0 AND MIT

package io.github._5thlayer.beltworks.client.renderers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import io.github._5thlayer.beltworks.Beltworks;
import io.github._5thlayer.beltworks.BlockEntitiesContent;
import io.github._5thlayer.beltworks.blocks.BeltEndBlock;
import io.github._5thlayer.beltworks.blocks.BeltEndBlockEntity;
import io.github._5thlayer.beltworks.client.BeltworksClientConfig;
import io.github._5thlayer.beltworks.model.BeltContents;
import io.github._5thlayer.beltworks.model.BeltTier;
import io.github._5thlayer.beltworks.model.Splitter;
import io.github._5thlayer.beltworks.util.SplineUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class BeltEndRenderer implements BlockEntityRenderer<BeltEndBlockEntity, BeltEndRenderer.RenderState> {

    private static final int BELT_FRAME_COUNT = 16;
    private static final double TEXTURE_REPEAT = 0.75;
    private static final float LINE_WIDTH = 0.33f;
    // Factorio shades each item on a belt at random so a full, fast belt still reads as moving
    // (FFF-393). Minecraft has no tint for an item draw, so the shade is taken off its light.
    private static final int MAX_SHADE_LEVELS = 3;
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final Map<BeltTier, Identifier[]> BELT_FRAME_SPRITES = Arrays.stream(BeltTier.values())
            .collect(Collectors.toMap(tier -> tier, tier -> createFrameSpriteIds(tier.beltTexture())));

    public record Vertex(float x, float y, float z, float u, float v) {
        public static Vertex create(Vec3 pos, float u, float v) {
            return new Vertex((float) pos.x, (float) pos.y, (float) pos.z, u, v);
        }
    }

    public record Quad(Vertex a, Vertex b, Vertex c, Vertex d, int lightA, int lightB, int lightC, int lightD) {
    }

    public record RenderedItem(Vec3 position, float yaw, float pitch, float scale, int lightCoords, ItemStackRenderState itemState) {
    }

    @Override
    public RenderState createRenderState() {
        return new RenderState();
    }

    @Override
    public void extractRenderState(BeltEndBlockEntity entity, RenderState state, float partialTicks, Vec3 cameraPosition,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(entity, state, partialTicks, cameraPosition, breakProgress);

        state.items.clear();
        state.filter = null;
        state.filterOnSplitter = false;
        state.support = List.of();

        var level = entity.getLevel();
        if (level == null) return;
        // Worked out every frame, as a tile's is, so the legs follow the world below.
        var pos = entity.getBlockPos();
        var blockState = entity.getBlockState();
        if (blockState.getBlock() instanceof BeltEndBlock end) {
            var setting = BeltworksClientConfig.supports();
            var support = end.support(level, pos, blockState, setting);
            if (support != null) state.support = SupportRenderer.boxes(support, level, pos, setting);
        }
        if (entity.isSplitter()) addHalfItems(entity, state, partialTicks);

        if (!entity.filteredItem().isEmpty()) {
            var filterState = new ItemStackRenderState();
            Minecraft.getInstance().getItemModelResolver().updateForTopItem(
              filterState, entity.filteredItem(), ItemDisplayContext.FIXED, level, null, 0
            );
            state.filter = filterState;
            state.filterFacing = entity.getOwnFacing();
            state.filterOnSplitter = entity.isSplitter();
        }
    }

    // From where the item draw below puts an item down to where a tile's are, since a half is drawn
    // as a tile (PlanetaryFactory #394): BeltTileRenderer's surface plus its lift for each kind.
    private static final double HALF_BLOCK_DROP = 6 / 16d + 0.07 - (0.8 - 3 / 16d);
    private static final double HALF_FLAT_DROP = 6 / 16d + 0.02 - (0.8 - 3 / 16d - 0.12);

    /** A splitter half's items, each segment's along its own half of the block (#373). */
    private static void addHalfItems(BeltEndBlockEntity entity, RenderState state, float partialTicks) {
        var half = entity.getHalf();
        var halfData = entity.getHalfData();
        if (half == null || halfData == null) return;
        var speed = half.speed();
        for (var segment : List.of(half.entering(), half.leaving())) {
            var start = segment == half.leaving() ? Splitter.MIDLINE : 0;
            var entries = segment.entries();
            var positions = extrapolatedPositions(entries, speed, Splitter.MIDLINE, partialTicks);
            for (int index = 0; index < entries.size(); index++) {
                var progress = Math.clamp(start + positions[index] + BeltContents.SPACING / 2, 0, 1);
                var entry = entries.get(index);
                var item = renderedItem(entity, halfData, progress, speed, entry, shaded(state.lightCoords, entry.id()));
                var drop = entry.payload().getItem() instanceof BlockItem ? HALF_BLOCK_DROP : HALF_FLAT_DROP;
                state.items.add(new RenderedItem(item.position().add(0, drop, 0), item.yaw(), item.pitch(), item.scale(),
                  item.lightCoords(), item.itemState()));
            }
        }
    }

    /** An entry drawn at a fraction of a half's block of belt, facing along it. */
    private static RenderedItem renderedItem(BeltEndBlockEntity entity, BeltEndBlockEntity.BeltData beltData, double progress,
                                             double progressPerTick, BeltContents.Entry<ItemStack> entry, int light) {
        var stack = entry.payload();
        var nextProgress = Math.min(1, progress + progressPerTick);
        var worldPoint = SplineUtil.getPositionOnSpline(beltData, progress);
        var nextWorldPoint = SplineUtil.getPositionOnSpline(beltData, nextProgress);
        var renderPosition = worldPoint.subtract(entity.getBlockPos().getCenter());

        if (!(stack.getItem() instanceof BlockItem)) {
            renderPosition = renderPosition.add(0, -0.12, 0);
        }

        var forward = nextWorldPoint.subtract(worldPoint);
        var flatForward = new Vec3(forward.x, 0, forward.z).normalize();
        // Drawn in two rows of four per block, by id parity, so items at a readable size do not
        // overlap and z-fight. The belt itself has one lane (#344).
        var row = (entry.id() & 1) == 0 ? 0.125 : -0.125;
        renderPosition = renderPosition.add(flatForward.cross(UP).scale(row));
        var scale = stack.getItem() instanceof BlockItem ? 0.5f : 0.35f;
        var yaw = (float) Math.toDegrees(Math.atan2(-flatForward.z, flatForward.x));
        var pitch = (float) Math.toDegrees(Math.atan2(forward.y, Math.sqrt(forward.x * forward.x + forward.z * forward.z)));

        var itemState = new ItemStackRenderState();
        Minecraft.getInstance().getItemModelResolver().updateForTopItem(
          itemState, stack, ItemDisplayContext.FIXED, entity.getLevel(), null, 0
        );
        return new RenderedItem(renderPosition, yaw, pitch, scale, light, itemState);
    }

    @Override
    public void submit(RenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState cameraRenderState) {
        SupportRenderer.submit(state.support, poseStack, collector);
        for (var item : state.items) {
            poseStack.pushPose();
            poseStack.translate(item.position.x + 0.5, item.position.y + 0.8f - 3 / 16f, item.position.z + 0.5);
            poseStack.mulPose(Axis.YP.rotationDegrees(item.yaw));
            poseStack.mulPose(Axis.ZP.rotationDegrees(item.pitch));
            poseStack.mulPose(Axis.XP.rotationDegrees(90));
            poseStack.scale(item.scale, item.scale, item.scale);
            item.itemState.submit(poseStack, collector, item.lightCoords, OverlayTexture.NO_OVERLAY, 0);
            poseStack.popPose();
        }

        // On the slate above the mouth, outside the housing, which is solid (PlanetaryFactory #408).
        // A splitter half has no housing, and its front is a belt: its filter hovers over the midline instead.
        if (state.filter != null) {
            var facing = state.filterFacing;
            poseStack.pushPose();
            if (state.filterOnSplitter) poseStack.translate(0.5, 0.85, 0.5);
            else poseStack.translate(0.5 + facing.getStepX() * 0.52, 14 / 16d, 0.5 + facing.getStepZ() * 0.52);
            poseStack.mulPose(Axis.YP.rotationDegrees(180 - facing.toYRot()));
            poseStack.scale(0.3f, 0.3f, 0.3f);
            state.filter.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
            poseStack.popPose();
        }
    }

    /** A splitter half's belt surface, which its block model leaves out, for a placement preview (PlanetaryFactory #355). */
    public static void submitPlannedSplitter(PoseStack poseStack, SubmitNodeCollector collector, Vec3 camera, BlockPos pos,
                                             Direction facing, BeltTier tier, int argb) {
        var light = LightCoordsUtil.pack(15, 15);
        var quads = createSplitterSurface(facing, TEXTURE_REPEAT, TEXTURE_REPEAT, light);
        poseStack.pushPose();
        // On a tile's top face, where the placed half's block model draws its belt (#394).
        poseStack.translate(0, 6 / 16f + 0.01f - (0.5f - 2 / 16f + 0.08f), 0);
        submitTinted(poseStack, collector, camera, pos, quads, tier, argb, light);
        poseStack.popPose();
    }

    private static void submitTinted(PoseStack poseStack, SubmitNodeCollector collector, Vec3 camera, BlockPos pos,
                                     List<Quad> quads, BeltTier tier, int argb, int light) {
        var sprite = Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS).getSprite(BELT_FRAME_SPRITES.get(tier)[0]);
        poseStack.pushPose();
        poseStack.translate(pos.getX() - camera.x, pos.getY() - camera.y - 2 / 16f + 0.08f, pos.getZ() - camera.z);
        collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(TextureAtlas.LOCATION_BLOCKS), (pose, consumer) -> {
            for (var quad : quads) {
                for (var vertex : List.of(quad.a, quad.b, quad.c, quad.d)) {
                    consumer.addVertex(pose.pose(), vertex.x, vertex.y, vertex.z)
                      .setColor(argb)
                      .setUv(sprite.getU(vertex.u), sprite.getV(vertex.v))
                      .setOverlay(OverlayTexture.NO_OVERLAY)
                      .setLight(light)
                      .setNormal(pose, 0, 1, 0);
                }
            }
        });
        poseStack.popPose();
    }

    /** A splitter half's belt surface, back face to front face, one texture density either side of the midline. */
    private static List<Quad> createSplitterSurface(Direction facing, double inRepeat, double outRepeat, int light) {
        var result = new ArrayList<Quad>();
        var forward = Vec3.atLowerCornerOf(facing.getUnitVec3i());
        var right = forward.cross(UP).scale(LINE_WIDTH);
        var back = new Vec3(0.5, 0.5, 0.5).add(forward.scale(-0.5));
        addStraightStrip(result, back, forward, right, 0.5, 0, inRepeat, light);
        var outStartV = (1 - (0.5 / outRepeat) % 1) % 1;
        addStraightStrip(result, back.add(forward.scale(0.5)), forward, right, 0.5, outStartV, outRepeat, light);
        return result;
    }

    /** A straight run of belt, cut wherever the texture wraps, since an atlas sprite cannot repeat. */
    private static void addStraightStrip(List<Quad> result, Vec3 start, Vec3 forward, Vec3 right, double length,
                                         double startV, double repeat, int light) {
        var travelled = 0d;
        var v = startV;
        while (travelled < length - 1e-6) {
            var step = Math.min(length - travelled, (1 - v) * repeat);
            var nextV = v + step / repeat;
            var from = start.add(forward.scale(travelled));
            var to = start.add(forward.scale(travelled + step));
            addSegmentVertices(to.add(right), from.add(right), to.subtract(right), from.subtract(right), result,
              (float) v, (float) nextV, light, light);
            travelled += step;
            v = nextV >= 1 - 1e-6 ? 0 : nextV;
        }
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

    /** Takes a fixed, id-scattered 0 to {@link #MAX_SHADE_LEVELS} off both light channels. */
    private static int shaded(int lightCoords, int id) {
        var shade = ((id * 0x9E3779B1) >>> 16) % (MAX_SHADE_LEVELS + 1);
        var block = Math.max(0, ((lightCoords >> 4) & 0xF) - shade);
        var sky = Math.max(0, ((lightCoords >> 20) & 0xF) - shade);
        return (block << 4) | (sky << 20);
    }

    /**
     * Where each entry is between two server updates: moved on at the belt's speed and held back
     * behind the entry ahead and at the end, as {@link BeltContents#tick} backs a belt up. Drawing
     * the last update's positions instead moves items in one step per tick.
     */
    private static double[] extrapolatedPositions(List<BeltContents.Entry<ItemStack>> entries, double speed, double length,
                                                  float partialTicks) {
        var advance = speed * partialTicks;
        var positions = new double[entries.size()];
        var limit = length - BeltContents.SPACING;
        for (int index = entries.size() - 1; index >= 0; index--) {
            positions[index] = Math.min(entries.get(index).position() + advance, limit);
            limit = positions[index] - BeltContents.SPACING;
        }
        return positions;
    }

    private static Identifier[] createFrameSpriteIds(String textureName) {
        var result = new Identifier[BELT_FRAME_COUNT];
        for (int frame = 0; frame < BELT_FRAME_COUNT; frame++) {
            var frameName = frame < 10 ? "0" + frame : Integer.toString(frame);
            result[frame] = Beltworks.id("block/" + textureName + "/frame_" + frameName);
        }
        return result;
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
        private final List<RenderedItem> items = new ArrayList<>();
        private ItemStackRenderState filter;
        private Direction filterFacing = Direction.NORTH;
        private boolean filterOnSplitter;
        private List<SupportRenderer.Box> support = List.of();
    }
}
