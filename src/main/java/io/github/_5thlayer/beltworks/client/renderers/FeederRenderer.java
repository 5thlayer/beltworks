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
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import io.github._5thlayer.beltworks.Beltworks;
import io.github._5thlayer.beltworks.blocks.FeederBlock;
import io.github._5thlayer.beltworks.blocks.FeederBlockEntity;
import io.github._5thlayer.beltworks.model.ArmPose;
import io.github._5thlayer.beltworks.model.BeltTier;
import io.github._5thlayer.beltworks.model.FeederArms;
import io.github._5thlayer.beltworks.model.LineScan;

import java.util.List;
import java.util.function.Function;

/**
 * A feeder's arm, which its block model of a slate base leaves out since it swings: a slate plate
 * turning on the base, carrying an arm of three segments as a Factorio inserter's is, the upper arm
 * and forearm in its tier colour, folding or stretching so the slate hand hangs over the spot the
 * arm reaches, and what the hand holds below it (GLOSSARY.md, Arm). The filter is drawn on the
 * base's sides, as a loader's is on its housing.
 */
public class FeederRenderer implements BlockEntityRenderer<FeederBlockEntity, FeederRenderer.RenderState> {

    // The plate turns on the base's top, and the upper arm rises from its centre.
    private static final double BASE_TOP = 6 / 16d;
    private static final double PLATE_TOP = 7.5 / 16;
    private static final double PLATE_HALF_WIDTH = 4.5 / 16;
    // The hand comes down so what it holds sits just above the top of what it is over.
    private static final double HELD_ABOVE_TOP = 0.12;
    private static final double HAND_LENGTH = 4 / 16d;
    private static final double HAND_HALF_WIDTH = 1.5 / 16;
    private static final double HELD_BELOW_HAND = 0.12;
    // Two equal segments reach three blocks out with the wrist over a full block, and fold up nearer in.
    private static final double SEGMENT_LENGTH = 1.6;
    private static final double SEGMENT_HALF_WIDTH = 1 / 16d;
    // The highest the elbow rises, folded over a spot a block away, as the drawn bounds allow.
    private static final double ELBOW_RISE = 2;

    // The filter stands on the base's two sides the facing leaves.
    private static final double BASE_HALF_WIDTH = 7 / 16d;
    private static final double FILTER_HEIGHT = 3 / 16d;
    private static final float FILTER_SCALE = 0.2f;

    // Each face samples one texel of its texture: the band's highlight on top, its body on the
    // sides and its shadow beneath, and the slate's likewise.
    private static final float TEXEL_U = 8.5f / 16;
    private static final float[] BAND_V = {0.5f / 16, 8.5f / 16, 15.5f / 16};
    private static final float[] SLATE_V = {1.5f / 16, 8.5f / 16, 0.5f / 16};
    private static final Identifier SLATE = Beltworks.id("block/loader_slate");

    /**
     * A part of the arm in feeder-block units: a square beam from {@code from} to {@code to}, with
     * two faces along {@code side}, a horizontal unit, in its tier's colour or in slate, lit by {@code light}.
     */
    public record Beam(Vec3 from, Vec3 to, double halfWidth, Vec3 side, boolean band, int light) {
    }

    record HeldItem(Vec3 position, float yaw, float scale, int light, ItemStackRenderState itemState) {
    }

    @Override
    public RenderState createRenderState() {
        return new RenderState();
    }

    @Override
    public void extractRenderState(FeederBlockEntity entity, RenderState state, float partialTicks, Vec3 cameraPosition,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(entity, state, partialTicks, cameraPosition, breakProgress);
        state.beams = List.of();
        state.held = null;
        state.filter = null;

        var level = entity.getLevel();
        var blockState = entity.getBlockState();
        if (level == null || !(blockState.getBlock() instanceof FeederBlock feeder)) return;
        var pos = entity.getBlockPos();
        state.tier = feeder.tier();
        state.facing = blockState.getValue(HorizontalDirectionalBlock.FACING);
        var arms = entity.arms();
        var pose = entity.armPose(partialTicks, top(level, pos, arms, state.facing, FeederArms.Arm.HEAD),
          top(level, pos, arms, state.facing, FeederArms.Arm.TAIL));
        state.beams = beams(pose, at -> LevelRenderer.getLightCoords(level, pos.offset(at)));

        var hand = entity.hand();
        if (!hand.isEmpty()) {
            var itemState = new ItemStackRenderState();
            Minecraft.getInstance().getItemModelResolver().updateForTopItem(itemState, hand, ItemDisplayContext.FIXED, level, null, 0);
            var wrist = wrist(pose);
            state.held = new HeldItem(wrist.add(0, -HAND_LENGTH - HELD_BELOW_HAND, 0), (float) -Math.toDegrees(Math.atan2(pose.x(), pose.z())),
              hand.getItem() instanceof BlockItem ? 0.4f : 0.35f, LevelRenderer.getLightCoords(level, pos.offset(BlockPos.containing(wrist))), itemState);
        }

        if (!entity.filteredItem().isEmpty()) {
            var filterState = new ItemStackRenderState();
            Minecraft.getInstance().getItemModelResolver().updateForTopItem(filterState, entity.filteredItem(), ItemDisplayContext.FIXED,
              level, null, 0);
            state.filter = filterState;
        }
    }

    /** A feeder's arm in {@code pose}, each part lit by what {@code light} says of the block it is in, counted from the feeder. */
    public static List<Beam> beams(ArmPose pose, Function<BlockPos, Integer> light) {
        var side = new Vec3(-pose.z(), 0, pose.x());
        var shoulder = new Vec3(0.5, PLATE_TOP, 0.5);
        var wrist = wrist(pose);
        var elbow = elbow(shoulder, wrist, pose);
        var ownLight = light.apply(BlockPos.ZERO);
        var aboveLight = light.apply(BlockPos.ZERO.above());
        var handLight = light.apply(BlockPos.containing(wrist));
        return List.of(
          new Beam(new Vec3(0.5, BASE_TOP, 0.5), shoulder, PLATE_HALF_WIDTH, side, false, ownLight),
          new Beam(shoulder, elbow, SEGMENT_HALF_WIDTH, side, true, aboveLight),
          new Beam(elbow, wrist, SEGMENT_HALF_WIDTH, side, true, handLight),
          new Beam(wrist.add(0, SEGMENT_HALF_WIDTH, 0), wrist.add(0, -HAND_LENGTH, 0), HAND_HALF_WIDTH, side, false, handLight));
    }

    /**
     * How high the block {@code arm} of the feeder at {@code feeder}, facing {@code facing}, reaches
     * stands, as it collides: a belt's surface, a chest's lid, the ground where nothing stands, and
     * no more than a block.
     */
    public static double top(BlockGetter level, BlockPos feeder, FeederArms arms, Direction facing, FeederArms.Arm arm) {
        var spot = new LineScan.Spot(0, 0, 0);
        var travel = new LineScan.Travel(facing.getStepX(), facing.getStepZ());
        var end = (arm == FeederArms.Arm.HEAD ? arms.head(spot, travel) : arms.tail(spot, travel)).spot();
        var at = feeder.offset(end.x(), end.y(), end.z());
        var shape = level.getBlockState(at).getCollisionShape(level, at);
        return shape.isEmpty() ? 0 : Math.clamp(shape.max(Direction.Axis.Y), 0, 1);
    }

    // Over the spot the arm reaches, the hand above the top there.
    private static Vec3 wrist(ArmPose pose) {
        return new Vec3(0.5 + pose.x() * pose.reach(), pose.top() + HELD_ABOVE_TOP + HELD_BELOW_HAND + HAND_LENGTH,
          0.5 + pose.z() * pose.reach());
    }

    // Where two equal segments from the shoulder to the wrist meet, above the line between them.
    private static Vec3 elbow(Vec3 shoulder, Vec3 wrist, ArmPose pose) {
        var out = pose.reach();
        var up = wrist.y - shoulder.y;
        var apart = Math.sqrt(out * out + up * up);
        var rise = Math.sqrt(Math.max(0, SEGMENT_LENGTH * SEGMENT_LENGTH - apart * apart / 4));
        // Half the way out, then square to the shoulder-to-wrist line, upward.
        var along = out / 2 - up / apart * rise;
        var height = up / 2 + out / apart * rise;
        return shoulder.add(pose.x() * along, height, pose.z() * along);
    }

    @Override
    public void submit(RenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState cameraRenderState) {
        submit(state.beams, state.tier, poseStack, collector, RenderTypes.entityCutout(TextureAtlas.LOCATION_BLOCKS), 0xFFFFFFFF);

        var held = state.held;
        if (held != null) {
            poseStack.pushPose();
            poseStack.translate(held.position.x, held.position.y, held.position.z);
            poseStack.mulPose(Axis.YP.rotationDegrees(held.yaw));
            poseStack.scale(held.scale, held.scale, held.scale);
            held.itemState.submit(poseStack, collector, held.light, OverlayTexture.NO_OVERLAY, 0);
            poseStack.popPose();
        }

        if (state.filter != null) {
            for (var side : List.of(state.facing.getClockWise(), state.facing.getCounterClockWise())) {
                poseStack.pushPose();
                var out = BASE_HALF_WIDTH + 0.01;
                poseStack.translate(0.5 + side.getStepX() * out, FILTER_HEIGHT, 0.5 + side.getStepZ() * out);
                poseStack.mulPose(Axis.YP.rotationDegrees(180 - side.toYRot()));
                poseStack.scale(FILTER_SCALE, FILTER_SCALE, FILTER_SCALE);
                state.filter.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
                poseStack.popPose();
            }
        }
    }

    /** A planned feeder's arm, in the plan's {@code tint} as ARGB. */
    public static void submitPlanned(List<Beam> beams, BeltTier tier, PoseStack poseStack, SubmitNodeCollector collector, int tint) {
        submit(beams, tier, poseStack, collector, RenderTypes.entityTranslucent(TextureAtlas.LOCATION_BLOCKS), tint);
    }

    private static void submit(List<Beam> beams, BeltTier tier, PoseStack poseStack, SubmitNodeCollector collector, RenderType type, int color) {
        if (beams.isEmpty()) return;
        var atlas = Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS);
        var band = atlas.getSprite(Beltworks.id("block/" + tier.loader() + "_band"));
        var slate = atlas.getSprite(SLATE);
        collector.submitCustomGeometry(poseStack, type, (pose, consumer) -> {
            for (var beam : beams) {
                if (beam.band) beam(beam, band, BAND_V, color, pose, consumer);
                else beam(beam, slate, SLATE_V, color, pose, consumer);
            }
        });
    }

    // Its six faces, each square to the beam's length, its side or the third way across both.
    private static void beam(Beam beam, TextureAtlasSprite sprite, float[] v, int color, PoseStack.Pose pose, VertexConsumer consumer) {
        var length = beam.to.subtract(beam.from).normalize();
        var side = beam.side.scale(beam.halfWidth);
        var across = length.cross(beam.side).scale(beam.halfWidth);
        var face = new Face(sprite, v, color, pose, consumer, beam.light);
        Vec3 a = beam.from, b = beam.to;
        // Corners of each end: -side -across, +side -across, +side +across, -side +across.
        var from = corners(a, side, across);
        var to = corners(b, side, across);
        face.quad(length.reverse(), from[0], from[3], from[2], from[1]);
        face.quad(length, to[0], to[1], to[2], to[3]);
        face.quad(beam.side.reverse(), from[0], to[0], to[3], from[3]);
        face.quad(beam.side, from[1], from[2], to[2], to[1]);
        face.quad(across.normalize().reverse(), from[0], from[1], to[1], to[0]);
        face.quad(across.normalize(), from[3], to[3], to[2], from[2]);
    }

    private static Vec3[] corners(Vec3 end, Vec3 side, Vec3 across) {
        return new Vec3[] {end.subtract(side).subtract(across), end.add(side).subtract(across), end.add(side).add(across),
          end.subtract(side).add(across)};
    }

    private record Face(TextureAtlasSprite sprite, float[] v, int color, PoseStack.Pose pose, VertexConsumer consumer, int light) {

        // Four corners anticlockwise seen from outside; a face turned up samples the highlight and one turned down the shadow.
        void quad(Vec3 normal, Vec3... corners) {
            var texel = normal.y > 0.5 ? v[0] : normal.y < -0.5 ? v[2] : v[1];
            for (var corner : corners) {
                consumer.addVertex(pose.pose(), (float) corner.x, (float) corner.y, (float) corner.z)
                  .setColor(color)
                  .setUv(sprite.getU(TEXEL_U), sprite.getV(texel))
                  .setOverlay(OverlayTexture.NO_OVERLAY)
                  .setLight(light)
                  .setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
            }
        }
    }

    // Its arm reaches out of its block and above it, so it is drawn while it is in sight.
    @Override
    public AABB getRenderBoundingBox(FeederBlockEntity blockEntity) {
        return new AABB(blockEntity.getBlockPos()).inflate(FeederArms.MAX_REACH, 0, FeederArms.MAX_REACH)
          .expandTowards(0, ELBOW_RISE, 0);
    }

    @Override
    public int getViewDistance() {
        return 96;
    }

    public static class RenderState extends BlockEntityRenderState {
        private List<Beam> beams = List.of();
        private BeltTier tier = BeltTier.BELT;
        private Direction facing = Direction.NORTH;
        private @Nullable HeldItem held;
        private @Nullable ItemStackRenderState filter;
    }
}
