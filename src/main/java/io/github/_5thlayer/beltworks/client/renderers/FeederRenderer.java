// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.client.renderers;

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
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import io.github._5thlayer.beltworks.Beltworks;
import io.github._5thlayer.beltworks.blocks.BeltEndBlock;
import io.github._5thlayer.beltworks.blocks.BeltTileBlock;
import io.github._5thlayer.beltworks.blocks.FeederBlock;
import io.github._5thlayer.beltworks.blocks.FeederBlockEntity;
import io.github._5thlayer.beltworks.model.BeltTier;
import io.github._5thlayer.beltworks.model.FeederArms;
import io.github._5thlayer.beltworks.model.LineScan;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * A feeder's arms in its tier colour, which its block model of a slate base and column leaves out
 * since their reach is its block entity's: each runs from the hub atop the column to the block its
 * end reaches, the head ending in a sweeper's nozzle and the tail in a spout. An item the head
 * takes is drawn rising into the nozzle until it is inside, and not after (CONTEXT.md, Head). The
 * filter is drawn on the column's sides, as a loader's is on its housing.
 */
public class FeederRenderer implements BlockEntityRenderer<FeederBlockEntity, FeederRenderer.RenderState> {

    // Where the arms hang, as the block model's hub has them: above a block's top, so an item on a
    // belt has room to be seen rising into the head's nozzle, whose mouth is at a chest's lid. The
    // tail's spout reaches back down to where it drops.
    private static final float ARM_HALF_WIDTH = 1 / 16f;
    private static final float ARM_BOTTOM = 17.5f / 16;
    private static final float ARM_TOP = 19.5f / 16;
    private static final float NOZZLE_HALF_WIDTH = 2 / 16f;
    private static final float NOZZLE_BOTTOM = 15 / 16f;
    private static final float NOZZLE_TOP = 18 / 16f;
    private static final float MOUTH_HALF_WIDTH = 3 / 16f;
    private static final float MOUTH_BOTTOM = 14 / 16f;
    private static final float SPOUT_HALF_WIDTH = 1.5f / 16;
    private static final float SPOUT_BOTTOM = 10.5f / 16;

    // A taken item rises from where it lay to inside the nozzle, above its mouth, where it is hidden.
    private static final double BELT_SURFACE = 6 / 16d;
    private static final double INSIDE_NOZZLE = 16 / 16d;
    private static final double ITEM_LIFT = 0.1;

    // The filter stands on the column's two sides the arms leave free.
    private static final double COLUMN_HALF_WIDTH = 2 / 16d;
    private static final double FILTER_HEIGHT = 10 / 16d;
    private static final float FILTER_SCALE = 0.22f;

    // Each face samples one texel of its texture: the band's highlight on top, its body on the
    // sides and its shadow beneath, and the slate's likewise.
    private static final float TEXEL_U = 8.5f / 16;
    private static final float[] BAND_V = {0.5f / 16, 8.5f / 16, 15.5f / 16};
    private static final float[] SLATE_V = {1.5f / 16, 8.5f / 16, 0.5f / 16};
    private static final Identifier SLATE = Beltworks.id("block/loader_slate");

    /** A box of an arm in feeder-block units, in its tier's colour or in slate, lit by {@code light}. */
    public record Box(float x0, float y0, float z0, float x1, float y1, float z1, boolean band, int light) {
    }

    record SuckedItem(Vec3 position, float yaw, float scale, int light, ItemStackRenderState itemState) {
    }

    @Override
    public RenderState createRenderState() {
        return new RenderState();
    }

    @Override
    public void extractRenderState(FeederBlockEntity entity, RenderState state, float partialTicks, Vec3 cameraPosition,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(entity, state, partialTicks, cameraPosition, breakProgress);
        state.boxes = List.of();
        state.items.clear();
        state.filter = null;

        var level = entity.getLevel();
        var blockState = entity.getBlockState();
        if (level == null || !(blockState.getBlock() instanceof FeederBlock feeder)) return;
        var pos = entity.getBlockPos();
        var facing = blockState.getValue(HorizontalDirectionalBlock.FACING);
        state.tier = feeder.tier();
        state.facing = facing;
        state.boxes = boxes(entity.arms(), facing, at -> LevelRenderer.getLightCoords(level, pos.offset(at)));

        var head = entity.arms().head(new LineScan.Spot(0, 0, 0), travel(facing)).spot();
        var headPos = pos.offset(head.x(), 0, head.z());
        var headLight = LevelRenderer.getLightCoords(level, headPos);
        var headBlock = level.getBlockState(headPos).getBlock();
        // Off a tile or a splitter half it rises from the belt, and otherwise from the ground it lay on.
        var from = (headBlock instanceof BeltTileBlock || headBlock instanceof BeltEndBlock ? BELT_SURFACE : 0) + ITEM_LIFT;
        var now = level.getGameTime() + partialTicks;
        for (var sucked : entity.sucked()) {
            var progress = (now - sucked.gameTime()) / FeederBlockEntity.SUCK_TICKS;
            if (progress < 0 || progress >= 1) continue;
            // Slow as it leaves the belt, fast as it goes in.
            var eased = progress * progress;
            var itemState = new ItemStackRenderState();
            Minecraft.getInstance().getItemModelResolver().updateForTopItem(itemState, sucked.item(), ItemDisplayContext.FIXED,
              level, null, 0);
            var size = sucked.item().getItem() instanceof BlockItem ? 0.5f : 0.35f;
            state.items.add(new SuckedItem(new Vec3(head.x() + 0.5, from + (INSIDE_NOZZLE - from) * eased, head.z() + 0.5),
              (float) (progress * 180), size * (float) (1 - 0.6 * eased), headLight, itemState));
        }

        if (!entity.filteredItem().isEmpty()) {
            var filterState = new ItemStackRenderState();
            Minecraft.getInstance().getItemModelResolver().updateForTopItem(filterState, entity.filteredItem(), ItemDisplayContext.FIXED,
              level, null, 0);
            state.filter = filterState;
        }
    }

    /** A feeder's arm boxes, facing {@code facing}, each lit by what {@code light} says of the block it is over, counted from the feeder. */
    public static List<Box> boxes(FeederArms arms, Direction facing, Function<BlockPos, Integer> light) {
        var feeder = new LineScan.Spot(0, 0, 0);
        var boxes = new ArrayList<Box>();
        var head = arms.head(feeder, travel(facing)).spot();
        var tail = arms.tail(feeder, travel(facing)).spot();
        var headLight = light.apply(new BlockPos(head.x(), 0, head.z()));
        var tailLight = light.apply(new BlockPos(tail.x(), 0, tail.z()));
        var ownLight = light.apply(BlockPos.ZERO);
        arm(boxes, head, ownLight);
        column(boxes, head, NOZZLE_HALF_WIDTH, NOZZLE_BOTTOM, NOZZLE_TOP, true, headLight);
        column(boxes, head, MOUTH_HALF_WIDTH, MOUTH_BOTTOM, NOZZLE_BOTTOM, false, headLight);
        arm(boxes, tail, ownLight);
        column(boxes, tail, SPOUT_HALF_WIDTH, SPOUT_BOTTOM, NOZZLE_TOP, true, tailLight);
        return boxes;
    }

    private static LineScan.Travel travel(Direction facing) {
        return new LineScan.Travel(facing.getStepX(), facing.getStepZ());
    }

    // From the hub at the feeder's centre to the centre of the block at end.
    private static void arm(List<Box> boxes, LineScan.Spot end, int light) {
        float x = end.x() + 0.5f, z = end.z() + 0.5f;
        boxes.add(new Box(Math.min(0.5f, x) - ARM_HALF_WIDTH, ARM_BOTTOM, Math.min(0.5f, z) - ARM_HALF_WIDTH,
          Math.max(0.5f, x) + ARM_HALF_WIDTH, ARM_TOP, Math.max(0.5f, z) + ARM_HALF_WIDTH, true, light));
    }

    // Hanging from an arm's end over the centre of the block it reaches.
    private static void column(List<Box> boxes, LineScan.Spot end, float halfWidth, float bottom, float top, boolean band, int light) {
        float x = end.x() + 0.5f, z = end.z() + 0.5f;
        boxes.add(new Box(x - halfWidth, bottom, z - halfWidth, x + halfWidth, top, z + halfWidth, band, light));
    }

    @Override
    public void submit(RenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState cameraRenderState) {
        submit(state.boxes, state.tier, poseStack, collector, RenderTypes.entityCutout(TextureAtlas.LOCATION_BLOCKS), 0xFFFFFFFF);

        for (var item : state.items) {
            poseStack.pushPose();
            poseStack.translate(item.position.x, item.position.y, item.position.z);
            poseStack.mulPose(Axis.YP.rotationDegrees(item.yaw));
            poseStack.scale(item.scale, item.scale, item.scale);
            item.itemState.submit(poseStack, collector, item.light, OverlayTexture.NO_OVERLAY, 0);
            poseStack.popPose();
        }

        if (state.filter != null) {
            for (var side : List.of(state.facing.getClockWise(), state.facing.getCounterClockWise())) {
                poseStack.pushPose();
                var out = COLUMN_HALF_WIDTH + 0.02;
                poseStack.translate(0.5 + side.getStepX() * out, FILTER_HEIGHT, 0.5 + side.getStepZ() * out);
                poseStack.mulPose(Axis.YP.rotationDegrees(180 - side.toYRot()));
                poseStack.scale(FILTER_SCALE, FILTER_SCALE, FILTER_SCALE);
                state.filter.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
                poseStack.popPose();
            }
        }
    }

    /** A planned feeder's arms, in the plan's {@code tint} as ARGB. */
    public static void submitPlanned(List<Box> boxes, BeltTier tier, PoseStack poseStack, SubmitNodeCollector collector, int tint) {
        submit(boxes, tier, poseStack, collector, RenderTypes.entityTranslucent(TextureAtlas.LOCATION_BLOCKS), tint);
    }

    private static void submit(List<Box> boxes, BeltTier tier, PoseStack poseStack, SubmitNodeCollector collector, RenderType type, int color) {
        if (boxes.isEmpty()) return;
        var atlas = Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS);
        var band = atlas.getSprite(Beltworks.id("block/" + tier.loader() + "_band"));
        var slate = atlas.getSprite(SLATE);
        collector.submitCustomGeometry(poseStack, type, (pose, consumer) -> {
            for (var box : boxes) {
                if (box.band) box(box, band, BAND_V, color, pose, consumer);
                else box(box, slate, SLATE_V, color, pose, consumer);
            }
        });
    }

    private static void box(Box box, TextureAtlasSprite sprite, float[] v, int color, PoseStack.Pose pose, VertexConsumer consumer) {
        float x0 = box.x0, y0 = box.y0, z0 = box.z0, x1 = box.x1, y1 = box.y1, z1 = box.z1;
        var face = new Face(sprite, color, pose, consumer, box.light);
        face.quad(0, 1, 0, v[0], x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0);
        face.quad(0, -1, 0, v[2], x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
        face.quad(0, 0, -1, v[1], x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0);
        face.quad(0, 0, 1, v[1], x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
        face.quad(-1, 0, 0, v[1], x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);
        face.quad(1, 0, 0, v[1], x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1);
    }

    private record Face(TextureAtlasSprite sprite, int color, PoseStack.Pose pose, VertexConsumer consumer, int light) {

        // Four corners anticlockwise seen from outside.
        void quad(int nx, int ny, int nz, float v, float... corners) {
            for (var at = 0; at < 12; at += 3) {
                consumer.addVertex(pose.pose(), corners[at], corners[at + 1], corners[at + 2])
                  .setColor(color)
                  .setUv(sprite.getU(TEXEL_U), sprite.getV(v))
                  .setOverlay(OverlayTexture.NO_OVERLAY)
                  .setLight(light)
                  .setNormal(pose, nx, ny, nz);
            }
        }
    }

    // Its arms reach out of its block, so it is drawn while they are in sight.
    @Override
    public AABB getRenderBoundingBox(FeederBlockEntity blockEntity) {
        return new AABB(blockEntity.getBlockPos()).inflate(FeederArms.MAX_REACH, 0, FeederArms.MAX_REACH);
    }

    @Override
    public int getViewDistance() {
        return 96;
    }

    public static class RenderState extends BlockEntityRenderState {
        private List<Box> boxes = List.of();
        private BeltTier tier = BeltTier.BELT;
        private Direction facing = Direction.NORTH;
        private final List<SuckedItem> items = new ArrayList<>();
        private @Nullable ItemStackRenderState filter;
    }
}
