package rearth.belts.client.renderers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.ARGB;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import org.jetbrains.annotations.Nullable;
import rearth.belts.BlockContent;
import rearth.belts.blocks.ChuteBlockEntity;
import rearth.belts.items.BeltItem;
import rearth.belts.items.BeltPlan;
import rearth.belts.model.BeltPath;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The belt item's Placement Preview: the plan its click would execute, drawn translucent, red when
 * the click would be refused (PlanetaryFactory ADR-0069, #372).
 */
public final class BeltPreview {

    private static final int ACCEPTED_TINT = 0x80FFFFFF;
    private static final int REFUSED_TINT = 0x80FF4040;
    private static final int ACCEPTED_LINE = ARGB.colorFromFloat(0.8f, 1f, 1f, 1f);
    private static final int REFUSED_LINE = ARGB.colorFromFloat(0.8f, 1f, 0f, 0f);

    private static @Nullable BeltPlan.Refusal announced;

    private BeltPreview() {
    }

    public record Outline(AABB box, int color) {
    }

    /** The plan of a click with the belt item in the main hand at the block aimed at, or null. */
    public static @Nullable BeltPlan currentPlan() {
        var minecraft = Minecraft.getInstance();
        var player = minecraft.player;
        if (player == null || minecraft.level == null) return null;
        if (!(player.getMainHandItem().getItem() instanceof BeltItem item)) return null;
        if (!(minecraft.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return null;
        return item.plan(minecraft.level, player.getMainHandItem(), player, hit);
    }

    /** The refusal on the action bar, sent only when it changes so it neither spams nor flickers. */
    public static void announce(@Nullable BeltPlan plan) {
        var refusal = plan == null ? null : plan.refusal();
        if (Objects.equals(refusal, announced)) return;
        announced = refusal;
        if (refusal != null) Minecraft.getInstance().gui.setOverlayMessage(refusal.message(), false);
    }

    /** The belt, the loaders and supports the click would place, translucent. */
    public static void submit(BeltPlan plan, PoseStack poseStack, SubmitNodeCollector collector, Vec3 camera) {
        var tint = plan.refused() ? REFUSED_TINT : ACCEPTED_TINT;
        for (var end : plan.ends()) {
            if (end.state() != null) submitBlock(poseStack, collector, camera, end.pos(), end.state(), tint, end.action() == BeltPlan.Action.TURN_SUPPORT);
        }
        if (plan.click() != BeltPlan.Click.START) {
            for (var support : plan.supports()) {
                var state = BlockContent.CONVEYOR_SUPPORT_BLOCK.get().defaultBlockState()
                              .setValue(HorizontalDirectionalBlock.FACING, support.facing());
                submitBlock(poseStack, collector, camera, support.pos(), state, tint, false);
            }
        }
        var path = plan.path();
        if (path != null && path.nodes().size() >= 2) {
            ChuteBeltRenderer.submitPlanned(poseStack, collector, camera, ChuteBlockEntity.BeltData.of(path), plan.ends().getFirst().pos(),
              tangent(path.nodes().getFirst()), tangent(path.nodes().getLast()), plan.tier(), tint);
        }
    }

    /** An outline on each existing block the click would use, and an arrow where a start click fixes a direction. */
    public static List<Outline> outlines(BeltPlan plan, long gameTime) {
        var color = plan.refused() ? REFUSED_LINE : ACCEPTED_LINE;
        var outlines = new ArrayList<Outline>();
        for (var end : plan.ends()) {
            if (end.action() == BeltPlan.Action.USE) outlines.add(new Outline(new AABB(end.pos()).inflate(0.002), color));
            if (end.arrow() != null) {
                var direction = Vec3.atLowerCornerOf(end.arrow().getUnitVec3i());
                var offset = direction.scale(0.1 + (gameTime % 10) / 20f);
                var base = end.pos().getCenter().subtract(direction.scale(0.4));
                outlines.add(new Outline(new AABB(base.subtract(0.1, 0.1, 0.1), base.add(0.1, 0.1, 0.1).add(offset)), color));
            }
        }
        return outlines;
    }

    public static void renderOutlines(List<Outline> outlines, Vec3 camera, PoseStack poseStack, MultiBufferSource buffers) {
        var consumer = buffers.getBuffer(RenderTypes.lines());
        for (var outline : outlines) {
            ShapeRenderer.renderShape(poseStack, consumer, Shapes.create(outline.box()), -camera.x, -camera.y, -camera.z, outline.color(), 2f);
        }
    }

    private static Direction tangent(BeltPath.Node node) {
        return Direction.getApproximateNearest(node.tangentX(), 0, node.tangentZ());
    }

    // Every face is drawn: the block is not there yet, so nothing beside it hides one.
    private static void submitBlock(PoseStack poseStack, SubmitNodeCollector collector, Vec3 camera, BlockPos pos, BlockState state, int tint,
                                    boolean overStanding) {
        var model = Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(state);
        var parts = new ArrayList<BlockStateModelPart>();
        var random = RandomSource.create(state.getSeed(pos));
        model.collectParts(random, parts);
        if (parts.isEmpty()) return;
        var instance = new QuadInstance();
        instance.setColor(tint);
        poseStack.pushPose();
        poseStack.translate(pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z);
        if (overStanding) {
            // Drawn over the block it turns, so it is grown a hair to keep the two from z-fighting.
            poseStack.translate(0.5, 0.5, 0.5);
            poseStack.scale(1.01f, 1.01f, 1.01f);
            poseStack.translate(-0.5, -0.5, -0.5);
        }
        collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(TextureAtlas.LOCATION_BLOCKS), (pose, buffer) -> {
            for (var part : parts) {
                for (var quad : part.getQuads(null)) buffer.putBakedQuad(pose, quad, instance);
                for (var direction : Direction.values()) {
                    for (var quad : part.getQuads(direction)) buffer.putBakedQuad(pose, quad, instance);
                }
            }
        });
        poseStack.popPose();
    }
}
