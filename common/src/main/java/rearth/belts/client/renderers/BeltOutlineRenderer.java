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
import net.minecraft.util.ARGB;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import rearth.belts.BlockContent;
import rearth.belts.BlockEntitiesContent;
import rearth.belts.blocks.ChuteBlockEntity;
import rearth.belts.ComponentContent;
import rearth.belts.items.BeltItem;
import rearth.belts.model.BeltPath;
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

        var supports = new ArrayList<>(BeltItem.getStoredMidpoints(stack, level));

        BeltPath path;
        var hovered = blockHit.getBlockPos();
        var hoveredState = level.getBlockState(hovered);
        if (level.getBlockEntity(hovered, BlockEntitiesContent.CHUTE_BLOCK.get()).isPresent()) {
            path = BeltItem.plannedPath(level, startBlockPos, startFacing, supports, hovered, null);
        } else if (hoveredState.is(BlockContent.CONVEYOR_SUPPORT_BLOCK.get())) {
            if (supports.stream().noneMatch(support -> support.getFirst().equals(hovered)))
                supports.add(Pair.of(hovered, hoveredState.getValue(HorizontalDirectionalBlock.FACING)));
            path = BeltItem.plannedPath(level, startBlockPos, startFacing, supports, null, null);
        } else {
            // As the click places an end loader: on the face hit, or facing the player from the ground.
            var endFacing = blockHit.getDirection().getAxis().isVertical() ? player.getDirection() : blockHit.getDirection();
            path = BeltItem.plannedPath(level, startBlockPos, startFacing, supports, hovered.relative(blockHit.getDirection()), endFacing);
        }

        // Red exactly when the click would be refused for the belt's shape (PlanetaryFactory ADR-0078).
        var color = path.refusal().isPresent()
                ? ARGB.colorFromFloat(0.8f, 1f, 0f, 0f)
                : ARGB.colorFromFloat(0.8f, 1f, 1f, 1f);
        var data = ChuteBlockEntity.BeltData.of(path);
        for (var along = 0d; along < data.totalLength(); along += 0.1) {
            var center = SplineUtil.getPositionOnSpline(data, along / data.totalLength());
            outlines.add(new Outline(new AABB(center, center).inflate(0.05), color));
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
}
