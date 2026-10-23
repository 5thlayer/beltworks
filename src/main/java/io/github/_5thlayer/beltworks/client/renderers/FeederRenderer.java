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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import io.github._5thlayer.beltworks.Beltworks;
import io.github._5thlayer.beltworks.blocks.BeltTileBlock;
import io.github._5thlayer.beltworks.blocks.FeederBlock;
import io.github._5thlayer.beltworks.blocks.FeederBlockEntity;
import io.github._5thlayer.beltworks.blocks.FeederReach;
import io.github._5thlayer.beltworks.model.ArmParts;
import io.github._5thlayer.beltworks.model.ArmPose;
import io.github._5thlayer.beltworks.model.BeltTier;
import io.github._5thlayer.beltworks.model.FeederArms;
import io.github._5thlayer.beltworks.model.FeederMarks;
import io.github._5thlayer.beltworks.model.LineScan;

import java.util.EnumMap;
import java.util.List;
import java.util.function.Function;

/**
 * A feeder's arm, which its block model of a slate base leaves out since it swings: the parts
 * {@link ArmParts} lays out, each face a block of its texture at a time, and what the hand holds
 * in its pincer (GLOSSARY.md, Arm and Hand). The filter is drawn on the base's sides, as a loader's
 * is on its housing.
 */
public class FeederRenderer implements BlockEntityRenderer<FeederBlockEntity, FeederRenderer.RenderState> {


    // The filter stands on the base's two sides the facing leaves.
    private static final double BASE_HALF_WIDTH = 7 / 16d;
    private static final double FILTER_HEIGHT = 3 / 16d;
    private static final float FILTER_SCALE = 0.2f;

    private static final Identifier PLATE = Beltworks.id("block/feeder_plate");

    /** A part of the arm, lit by {@code light}. */
    public record Beam(ArmParts.Part part, int light) {
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
        state.marks = null;

        var level = entity.getLevel();
        var blockState = entity.getBlockState();
        if (level == null || !(blockState.getBlock() instanceof FeederBlock feeder)) return;
        var pos = entity.getBlockPos();
        state.tier = feeder.tier();
        state.facing = blockState.getValue(HorizontalDirectionalBlock.FACING);
        var arms = entity.arms();
        var headTop = top(level, pos, arms, state.facing, FeederArms.Arm.HEAD);
        var tailTop = top(level, pos, arms, state.facing, FeederArms.Arm.TAIL);
        var pose = entity.armPose(partialTicks, headTop, tailTop);
        if (marked(pos)) state.marks = FeederMarks.of(arms, BeltTileBlock.travel(state.facing), headTop, tailTop);
        var hand = entity.hand();
        var block = hand.getItem() instanceof BlockItem;
        state.beams = beams(pose, hand.isEmpty() ? 0 : ArmParts.heldWidth(block), at -> LevelRenderer.getLightCoords(level, pos.offset(at)));

        if (!hand.isEmpty()) {
            var itemState = new ItemStackRenderState();
            Minecraft.getInstance().getItemModelResolver().updateForTopItem(itemState, hand, ItemDisplayContext.FIXED, level, null, 0);
            state.held = new HeldItem(vec(ArmParts.held(pose)), (float) -Math.toDegrees(Math.atan2(pose.x(), pose.z())),
              (float) (block ? ArmParts.HELD_BLOCK_SCALE : ArmParts.HELD_ITEM_SCALE),
              LevelRenderer.getLightCoords(level, pos.offset(wristBlock(pose))), itemState);
        }

        if (!entity.filteredItem().isEmpty()) {
            var filterState = new ItemStackRenderState();
            Minecraft.getInstance().getItemModelResolver().updateForTopItem(filterState, entity.filteredItem(), ItemDisplayContext.FIXED,
              level, null, 0);
            state.filter = filterState;
        }
    }

    /**
     * A feeder's arm in {@code pose}, its pincer closed on what its hand holds, {@code grip} blocks
     * wide, or open at 0, each part lit by what {@code light} says of the block it is in, counted
     * from the feeder.
     */
    public static List<Beam> beams(ArmPose pose, double grip, Function<BlockPos, Integer> light) {
        var lights = new EnumMap<ArmParts.Place, Integer>(ArmParts.Place.class);
        lights.put(ArmParts.Place.FEEDER, light.apply(BlockPos.ZERO));
        lights.put(ArmParts.Place.ABOVE, light.apply(BlockPos.ZERO.above()));
        lights.put(ArmParts.Place.WRIST, light.apply(wristBlock(pose)));
        return ArmParts.of(pose, grip).stream().map(part -> new Beam(part, lights.get(part.place()))).toList();
    }

    /**
     * How high the block {@code arm} of the feeder at {@code feeder}, facing {@code facing}, reaches
     * stands, as it collides: a belt's surface, a chest's lid, the ground where nothing stands, and
     * no more than a block.
     */
    public static double top(BlockGetter level, BlockPos feeder, FeederArms arms, Direction facing, FeederArms.Arm arm) {
        var spot = new LineScan.Spot(0, 0, 0);
        var end = (arm == FeederArms.Arm.HEAD ? arms.head(spot, BeltTileBlock.travel(facing)) : arms.tail(spot, BeltTileBlock.travel(facing))).spot();
        var at = feeder.offset(end.x(), end.y(), end.z());
        var shape = level.getBlockState(at).getCollisionShape(level, at);
        return shape.isEmpty() ? 0 : Math.clamp(shape.max(Direction.Axis.Y), 0, 1);
    }

    /**
     * Whether a placed feeder at {@code pos} shows its marks: under the crosshair with the HUD shown,
     * unless a feeder is held, whose planned marks the Placement Preview draws instead.
     */
    private static boolean marked(BlockPos pos) {
        var minecraft = Minecraft.getInstance();
        return minecraft.player != null && !minecraft.options.hideGui && !FeederReach.isFeeder(minecraft.player.getMainHandItem())
               && minecraft.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(pos);
    }

    private static BlockPos wristBlock(ArmPose pose) {
        return BlockPos.containing(vec(ArmParts.wrist(pose)));
    }

    @Override
    public void submit(RenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState cameraRenderState) {
        if (state.marks != null) FeederMarksRenderer.submit(state.marks, poseStack, collector);
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
        var arm = atlas.getSprite(Beltworks.id("block/" + tier.feeder() + "_arm"));
        var plate = atlas.getSprite(PLATE);
        collector.submitCustomGeometry(poseStack, type, (pose, consumer) -> {
            for (var beam : beams) {
                beam(beam.part, new Face(beam.part.kind().tierColoured() ? arm : plate, color, pose, consumer, beam.light));
            }
        });
    }

    // Its six faces, each square to its length, its side or the third way across both. Its ends
    // take the middle of the texture; its four long faces run along it, the texture's v with them.
    private static void beam(ArmParts.Part part, Face face) {
        var from = vec(part.from());
        var to = vec(part.to());
        var length = to.subtract(from).normalize();
        var unitSide = vec(part.side());
        var unitAcross = length.cross(unitSide);
        var side = unitSide.scale(part.halfWidth());
        var across = unitAcross.scale(part.halfDepth());
        // Corners of each end: -side -across, +side -across, +side +across, -side +across.
        var start = corners(from, side, across);
        var end = corners(to, side, across);
        var wide = 2 * part.halfWidth();
        var deep = 2 * part.halfDepth();
        face.end(length.reverse(), wide, deep, start, 0, 3, 2, 1);
        face.end(length, wide, deep, end, 0, 1, 2, 3);
        var offsets = corners(Vec3.ZERO, side, across);
        var run = to.subtract(from);
        face.along(unitSide.reverse(), deep, from, run, offsets[0], offsets[3]);
        face.along(unitSide, deep, from, run, offsets[2], offsets[1]);
        face.along(unitAcross.reverse(), wide, from, run, offsets[1], offsets[0]);
        face.along(unitAcross, wide, from, run, offsets[3], offsets[2]);
    }

    private static Vec3 vec(ArmParts.Point point) {
        return new Vec3(point.x(), point.y(), point.z());
    }

    private static Vec3[] corners(Vec3 end, Vec3 side, Vec3 across) {
        return new Vec3[] {end.subtract(side).subtract(across), end.add(side).subtract(across), end.add(side).add(across),
          end.subtract(side).add(across)};
    }

    private record Face(TextureAtlasSprite sprite, int color, PoseStack.Pose pose, VertexConsumer consumer, int light) {

        // An end, its {@code corners} in {@link #corners}' order taken anticlockwise seen from outside
        // as {@code order} says: {@code wide} across u and {@code deep} across v, both from the texture's middle.
        void end(Vec3 normal, double wide, double deep, Vec3[] corners, int... order) {
            var u0 = 0.5 - wide / 2;
            var v0 = 0.5 - deep / 2;
            var u1 = u0 + wide;
            var v1 = v0 + deep;
            double[][] uv = {{u0, v0}, {u1, v0}, {u1, v1}, {u0, v1}};
            for (var i : order) vertex(corners[i], normal, uv[i][0], uv[i][1]);
        }

        // A long face from the corners {@code first} to {@code second} off the beam's axis, anticlockwise
        // seen from outside as first at the start, first at the end, second at the end, second at the
        // start: {@code wide} across u from the texture's middle, a block of v at a time along {@code run}.
        void along(Vec3 normal, double wide, Vec3 from, Vec3 run, Vec3 first, Vec3 second) {
            var length = run.length();
            var u0 = 0.5 - wide / 2;
            var u1 = u0 + wide;
            for (var piece : ArmParts.pieces(length)) {
                var start = from.add(run.scale(piece.start() / length));
                var end = from.add(run.scale(piece.end() / length));
                var v0 = piece.v();
                var v1 = v0 + piece.end() - piece.start();
                vertex(start.add(first), normal, u0, v0);
                vertex(end.add(first), normal, u0, v1);
                vertex(end.add(second), normal, u1, v1);
                vertex(start.add(second), normal, u1, v0);
            }
        }

        private void vertex(Vec3 corner, Vec3 normal, double u, double v) {
            consumer.addVertex(pose.pose(), (float) corner.x, (float) corner.y, (float) corner.z)
              .setColor(color)
              .setUv(sprite.getU((float) u), sprite.getV((float) v))
              .setOverlay(OverlayTexture.NO_OVERLAY)
              .setLight(light)
              .setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
        }
    }

    // Its arm reaches out of its block and above it, so it is drawn while it is in sight.
    @Override
    public AABB getRenderBoundingBox(FeederBlockEntity blockEntity) {
        return new AABB(blockEntity.getBlockPos()).inflate(FeederArms.MAX_REACH, 0, FeederArms.MAX_REACH)
          .expandTowards(0, ArmParts.ELBOW_RISE, 0);
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
        private @Nullable FeederMarks marks;
    }
}
