package rearth.belts.client.renderers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.datafixers.util.Pair;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import rearth.belts.BlockContent;
import rearth.belts.BlockEntitiesContent;
import rearth.belts.blocks.ChuteBlockEntity;
import rearth.belts.ComponentContent;
import rearth.belts.items.BeltItem;
import rearth.belts.util.MathHelpers;
import rearth.belts.util.SplineUtil;

import java.util.ArrayList;
import java.util.List;

public final class BeltOutlineRenderer {

    private BeltOutlineRenderer() {
    }

    public record Outline(AABB box, int color) {
    }

    public static List<Outline> extractPlannedBelt(ClientLevel level, BlockHitResult blockHit) {
        var player = Minecraft.getInstance().player;
        if (player == null) return List.of();

        var stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof BeltItem)) return List.of();

        var outlines = new ArrayList<Outline>();
        var hasStart = stack.has(ComponentContent.BELT_START.get()) && stack.has(ComponentContent.BELT_DIR.get());
        if (!hasStart) {
            var potentialStart = blockHit.getBlockPos().relative(blockHit.getDirection());
            var startDir = blockHit.getDirection();
            if (startDir.getAxis() == Direction.Axis.Y) {
                startDir = player.getDirection().getOpposite();
            }

            var startState = level.getBlockState(potentialStart);
            var couldBePlaced = startState.canBeReplaced() || startState.isAir();

            var targetedChute = level.getBlockEntity(blockHit.getBlockPos(), BlockEntitiesContent.CHUTE_BLOCK.get());
            if (targetedChute.isPresent()) {
                startDir = targetedChute.get().getOwnFacing();
                potentialStart = blockHit.getBlockPos();
                couldBePlaced = true;
            }

            var direction = Vec3.atLowerCornerOf(startDir.getUnitVec3i());
            var directionOffset = direction.scale(0.1 + (level.getGameTime() % 10) / 20f);
            var lower = potentialStart.getCenter().subtract(direction.scale(0.4)).subtract(0.1, 0.1, 0.1);
            var upper = potentialStart.getCenter().subtract(direction.scale(0.4)).add(0.1, 0.1, 0.1).add(directionOffset);
            var color = couldBePlaced
                    ? ARGB.colorFromFloat(0.9f, 0.1f, 0.8f, 0.7f)
                    : ARGB.colorFromFloat(0.9f, 1f, 0.1f, 0f);
            outlines.add(new Outline(new AABB(lower, upper), color));
            return List.copyOf(outlines);
        }

        var startBlockPos = stack.get(ComponentContent.BELT_START.get());
        var startFacing = stack.get(ComponentContent.BELT_DIR.get());
        if (startBlockPos == null || startBlockPos.equals(BlockPos.ZERO) || startFacing == null) return List.of();

        var startChute = level.getBlockEntity(startBlockPos, BlockEntitiesContent.CHUTE_BLOCK.get());
        var startPos = startChute.map(ChuteBlockEntity::beltStartPos).orElse(startBlockPos).getCenter();
        var startDir = startFacing.getUnitVec3i();
        var midPoints = BeltItem.getStoredMidpoints(stack, level);

        BlockPos endBlockPos;
        Direction endDir;
        var endChute = level.getBlockEntity(blockHit.getBlockPos(), BlockEntitiesContent.CHUTE_BLOCK.get());
        if (endChute.isPresent()) {
            endBlockPos = endChute.get().beltEndPos();
            endDir = endChute.get().beltEndFacing().getOpposite();
        } else if (level.getBlockState(blockHit.getBlockPos()).is(BlockContent.CONVEYOR_SUPPORT_BLOCK.get())) {
            var conveyorPos = blockHit.getBlockPos();
            var conveyorFacing = level.getBlockState(conveyorPos).getValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING);
            var lastEnd = midPoints.isEmpty() ? startBlockPos : midPoints.getLast().getFirst();
            var distanceForward = conveyorPos.relative(conveyorFacing).distSqr(lastEnd);
            var distanceBackward = conveyorPos.relative(conveyorFacing.getOpposite()).distSqr(lastEnd);
            endDir = distanceBackward < distanceForward ? conveyorFacing : conveyorFacing.getOpposite();
            endBlockPos = conveyorPos;
        } else {
            endBlockPos = blockHit.getBlockPos().relative(blockHit.getDirection());
            endDir = blockHit.getDirection().getOpposite();
            if (endDir.getAxis().isVertical()) {
                endDir = player.getDirection().getOpposite();
            }
        }

        var linePoints = getPositionsAlongLine(
                startPos,
                endBlockPos.getCenter(),
                startDir,
                endDir.getUnitVec3i(),
                midPoints
        );
        var lastForward = Vec3.atLowerCornerOf(startDir).normalize();
        var lastCenter = linePoints.isEmpty() ? Vec3.ZERO : linePoints.getFirst();

        for (var center : linePoints) {
            var newForward = center.subtract(lastCenter).normalize();
            if (center.equals(lastCenter)) newForward = lastForward;

            var curveFactor = newForward.distanceTo(lastForward);
            var color = ARGB.colorFromFloat(0.8f, 1f, 1f, 1f);
            if (curveFactor > 0.25f) color = ARGB.colorFromFloat(0.8f, 1f, 0.6f, 0.2f);
            if (curveFactor > 0.43f) color = ARGB.colorFromFloat(0.8f, 1f, 0f, 0f);

            outlines.add(new Outline(new AABB(center, center).inflate(0.05), color));
            lastCenter = center;
            lastForward = MathHelpers.lerp(lastForward, newForward, 0.3f);
        }

        return List.copyOf(outlines);
    }

    public static void renderPlannedBelt(List<Outline> outlines, Vec3 cameraPos, PoseStack poseStack, MultiBufferSource buffers) {
        if (outlines.isEmpty()) return;

        var consumer = buffers.getBuffer(RenderTypes.lines());
        for (var outline : outlines) {
            ShapeRenderer.renderShape(
                    poseStack,
                    consumer,
                    Shapes.create(outline.box()),
                    -cameraPos.x,
                    -cameraPos.y,
                    -cameraPos.z,
                    outline.color(),
                    2f
            );
        }
    }

    private static List<Vec3> getPositionsAlongLine(
            Vec3 from,
            Vec3 to,
            Vec3i startDir,
            Vec3i endDir,
            List<Pair<BlockPos, Direction>> midpoints
    ) {
        var transformedMidPoints = midpoints.stream()
                .map(point -> Pair.of(point.getFirst().getCenter(), Vec3.atLowerCornerOf(point.getSecond().getUnitVec3i())))
                .toList();
        var segmentPoints = SplineUtil.getPointPairs(
                from,
                Vec3.atLowerCornerOf(startDir),
                to,
                Vec3.atLowerCornerOf(endDir),
                transformedMidPoints
        );
        var distance = SplineUtil.getTotalLength(segmentPoints);
        var result = new ArrayList<Vec3>();

        for (var current = 0f; current < distance; current += 0.1f) {
            result.add(SplineUtil.getPositionOnSpline(from, Vec3.atLowerCornerOf(startDir), to, Vec3.atLowerCornerOf(endDir), midpoints, current / distance));
        }
        return result;
    }
}
