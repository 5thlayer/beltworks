package rearth.belts.collision;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import rearth.belts.blocks.ChuteBlockEntity;
import rearth.belts.util.SplineUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.DoubleFunction;

public final class BeltCollisionRegistry {

    private static final double SAMPLE_LENGTH = 0.2;
    private static final double BELT_HALF_WIDTH = 0.33;
    private static final double BELT_THICKNESS = 0.1;
    private static final double BELT_SURFACE_OFFSET = -2 / 16d + 0.08;
    private static final double CONTACT_HEIGHT_BELOW = 0.08;
    private static final double CONTACT_HEIGHT_ABOVE = 0.12;
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final double ITEM_HEIGHT = 0.25;

    private static final Map<Level, LevelCollisionData> LEVEL_DATA = new WeakHashMap<>();
    
    private BeltCollisionRegistry() {
    }

    /** A splitter half's own block of belt (#373). */
    public static void registerHalf(ChuteBlockEntity entity) {
        var beltData = entity.getHalfData();
        if (beltData == null) return;
        register(entity.getLevel(), entity.getBlockPos(), beltData, t -> SplineUtil.getPositionOnSpline(beltData, t), beltData.totalLength(),
          entity.getHalfSpeed() * 20);
    }

    /**
     * A belt tile's block of belt, along its straight or its corner's arc, so what stands on it
     * rides at the tile's own speed and a hand aims at it (PlanetaryFactory #396).
     *
     * @param source compared by equality, so an unchanged tile does not rebuild the index
     */
    public static void registerTile(Level level, BlockPos pos, Object source, DoubleFunction<Vec3> path, double speed) {
        register(level, pos, source, path, 1, speed);
    }

    public static void unregisterTile(Level level, BlockPos pos) {
        unregister(level, pos);
    }

    private static void register(@Nullable Level level, BlockPos pos, Object source, DoubleFunction<Vec3> path, double length, double speed) {
        if (level == null) return;

        var levelData = LEVEL_DATA.computeIfAbsent(level, ignored -> new LevelCollisionData());
        var key = pos.immutable();
        var current = levelData.belts.get(key);
        if (current != null && current.source.equals(source) && current.speed == speed) return;

        levelData.belts.put(key, createCollision(source, path, length, speed));
        levelData.rebuildSectionIndex();
    }

    public static void unregisterHalf(ChuteBlockEntity entity) {
        unregister(entity.getLevel(), entity.getBlockPos());
    }

    private static void unregister(@Nullable Level level, BlockPos pos) {
        if (level == null) return;

        var levelData = LEVEL_DATA.get(level);
        if (levelData == null || levelData.belts.remove(pos) == null) return;

        if (levelData.belts.isEmpty()) {
            LEVEL_DATA.remove(level);
        } else {
            levelData.rebuildSectionIndex();
        }
    }

    public static List<VoxelShape> getCollisionShapes(Level level, AABB queryBounds) {
        var levelData = LEVEL_DATA.get(level);
        if (levelData == null) return List.of();
        return levelData.getCollisionShapes(queryBounds);
    }

    public static void moveServerEntities(Level level) {
        if (level.isClientSide()) return;

        var levelData = LEVEL_DATA.get(level);
        if (levelData == null) return;

        var contacts = new IdentityHashMap<Entity, BeltContact>();
        for (var belt : levelData.belts.values()) {
            for (var entity : level.getEntities((Entity) null, belt.bounds.inflate(0.5, 0.4, 0.5), BeltCollisionRegistry::canBeMoved)) {
                var contact = belt.findContact(entity);
                if (contact == null) continue;

                var previous = contacts.get(entity);
                if (previous == null || contact.distanceSquared < previous.distanceSquared) {
                    contacts.put(entity, contact);
                }
            }
        }

        contacts.forEach(BeltCollisionRegistry::moveEntity);
    }

    public static void moveLocalPlayer(Level level, Player player) {
        if (!level.isClientSide() || player.isSpectator()) return;

        var levelData = LEVEL_DATA.get(level);
        if (levelData == null) return;

        BeltContact closest = null;
        for (var belt : levelData.belts.values()) {
            if (!belt.bounds.inflate(0.5, 0.4, 0.5).intersects(player.getBoundingBox())) continue;

            var contact = belt.findContact(player);
            if (contact != null && (closest == null || contact.distanceSquared < closest.distanceSquared)) {
                closest = contact;
            }
        }

        if (closest != null) applyPlayerVelocity(player, closest);
    }

    /** The nearest belt a ray from {@code from} to {@code to} meets, and where along its curve. */
    public static @Nullable BeltHit raycast(Level level, Vec3 from, Vec3 to) {
        var levelData = LEVEL_DATA.get(level);
        if (levelData == null) return null;

        var ray = new AABB(from, to);
        BeltHit nearest = null;
        for (var belt : levelData.belts.entrySet()) {
            if (!belt.getValue().bounds.intersects(ray)) continue;
            for (var segment : belt.getValue().segments) {
                // Raised so aiming at an item riding the belt counts as aiming at the belt.
                var hit = segment.bounds.expandTowards(0, ITEM_HEIGHT, 0).clip(from, to);
                if (hit.isEmpty()) continue;
                var distance = from.distanceTo(hit.get());
                if (nearest == null || distance < nearest.distance) {
                    nearest = new BeltHit(belt.getKey(), segment.progressAt(hit.get()), distance);
                }
            }
        }
        return nearest;
    }

    /** @param progress the fraction of the belt's path from its start */
    public record BeltHit(BlockPos source, double progress, double distance) {
    }

    private static boolean canBeMoved(Entity entity) {
        return entity.isAlive()
                && !entity.isSpectator()
                && !entity.noPhysics
                && !entity.isPassenger()
                && !(entity instanceof Player)
                && entity.getPistonPushReaction() != PushReaction.IGNORE;
    }

    private static void moveEntity(Entity entity, BeltContact contact) {
        entity.move(MoverType.SELF, contact.tangent.scale(contact.speed / 20d));
        entity.resetFallDistance();
    }

    private static void applyPlayerVelocity(Player player, BeltContact contact) {
        var movement = player.getDeltaMovement();
        var targetSpeed = contact.speed / 20d;
        var currentSpeed = movement.dot(contact.tangent);
        if (currentSpeed < targetSpeed) {
            player.setDeltaMovement(movement.add(contact.tangent.scale(targetSpeed - currentSpeed)));
        }
        player.resetFallDistance();
    }

    private static BeltCollision createCollision(Object source, DoubleFunction<Vec3> path, double length, double speed) {
        var segmentCount = Math.max(1, (int) Math.ceil(length / SAMPLE_LENGTH));
        var segments = new ArrayList<PathSegment>(segmentCount);
        var slabs = new ArrayList<CollisionSlab>(segmentCount);
        AABB bounds = null;

        var from = surfacePoint(path.apply(0));
        for (int i = 0; i < segmentCount; i++) {
            var to = surfacePoint(path.apply((i + 1d) / segmentCount));
            var tangent = to.subtract(from);
            if (tangent.lengthSqr() < 1.0E-8) {
                from = to;
                continue;
            }

            var normalizedTangent = tangent.normalize();
            var right = normalizedTangent.cross(UP);
            if (right.lengthSqr() < 1.0E-8) right = new Vec3(1, 0, 0);
            right = right.normalize().scale(BELT_HALF_WIDTH);

            var slabBounds = boundsAroundSegment(from, to, right);
            var slab = new CollisionSlab(slabBounds, Shapes.create(slabBounds));
            slabs.add(slab);
            segments.add(new PathSegment(from, to, normalizedTangent, slabBounds, (double) i / segmentCount, (i + 1d) / segmentCount));
            bounds = bounds == null ? slabBounds : bounds.minmax(slabBounds);
            from = to;
        }

        if (bounds == null) {
            var point = surfacePoint(path.apply(0));
            bounds = new AABB(point, point).inflate(BELT_HALF_WIDTH, BELT_THICKNESS, BELT_HALF_WIDTH);
        }

        return new BeltCollision(source, speed, List.copyOf(segments), List.copyOf(slabs), bounds);
    }

    private static Vec3 surfacePoint(Vec3 point) {
        return point.add(0, BELT_SURFACE_OFFSET, 0);
    }

    private static AABB boundsAroundSegment(Vec3 from, Vec3 to, Vec3 right) {
        var fromRight = from.add(right);
        var fromLeft = from.subtract(right);
        var toRight = to.add(right);
        var toLeft = to.subtract(right);

        var minX = Math.min(Math.min(fromRight.x, fromLeft.x), Math.min(toRight.x, toLeft.x));
        var maxX = Math.max(Math.max(fromRight.x, fromLeft.x), Math.max(toRight.x, toLeft.x));
        var minZ = Math.min(Math.min(fromRight.z, fromLeft.z), Math.min(toRight.z, toLeft.z));
        var maxZ = Math.max(Math.max(fromRight.z, fromLeft.z), Math.max(toRight.z, toLeft.z));
        var minY = Math.min(from.y, to.y) - BELT_THICKNESS;
        var maxY = Math.max(from.y, to.y);
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private record CollisionSlab(AABB bounds, VoxelShape shape) {
    }

    private record PathSegment(Vec3 from, Vec3 to, Vec3 tangent, AABB bounds, double fromProgress, double toProgress) {

        private double progressAt(Vec3 point) {
            var segment = to.subtract(from);
            var along = Math.clamp(point.subtract(from).dot(segment) / segment.lengthSqr(), 0, 1);
            return fromProgress + along * (toProgress - fromProgress);
        }

        private BeltContact findContact(Entity entity, double speed) {
            var feet = new Vec3(entity.getX(), entity.getBoundingBox().minY, entity.getZ());
            var segment = to.subtract(from);
            var horizontalLengthSquared = segment.x * segment.x + segment.z * segment.z;
            double progress;
            if (horizontalLengthSquared > 1.0E-8) {
                progress = ((feet.x - from.x) * segment.x + (feet.z - from.z) * segment.z) / horizontalLengthSquared;
            } else {
                progress = (feet.y - from.y) / segment.y;
            }
            progress = Math.clamp(progress, 0, 1);

            var surface = from.lerp(to, progress);
            var heightDifference = feet.y - Math.max(from.y, to.y);
            if (heightDifference < -CONTACT_HEIGHT_BELOW || heightDifference > CONTACT_HEIGHT_ABOVE) return null;

            var dx = feet.x - surface.x;
            var dz = feet.z - surface.z;
            var horizontalDistanceSquared = dx * dx + dz * dz;
            var contactWidth = BELT_HALF_WIDTH + Math.min(entity.getBbWidth() * 0.5, 0.35);
            if (horizontalDistanceSquared > contactWidth * contactWidth) return null;

            var surfaceHeightDifference = feet.y - surface.y;
            return new BeltContact(tangent, speed, horizontalDistanceSquared + surfaceHeightDifference * surfaceHeightDifference);
        }
    }

    private record BeltContact(Vec3 tangent, double speed, double distanceSquared) {
    }

    private static final class BeltCollision {
        private final Object source;
        private final double speed;
        private final List<PathSegment> segments;
        private final List<CollisionSlab> slabs;
        private final AABB bounds;

        private BeltCollision(Object source, double speed, List<PathSegment> segments,
                              List<CollisionSlab> slabs, AABB bounds) {
            this.source = source;
            this.speed = speed;
            this.segments = segments;
            this.slabs = slabs;
            this.bounds = bounds;
        }

        private BeltContact findContact(Entity entity) {
            BeltContact closest = null;
            for (var segment : segments) {
                var contact = segment.findContact(entity, speed);
                if (contact != null && (closest == null || contact.distanceSquared < closest.distanceSquared)) {
                    closest = contact;
                }
            }
            return closest;
        }
    }

    private static final class LevelCollisionData {
        private final Map<BlockPos, BeltCollision> belts = new HashMap<>();
        private final Map<Long, List<CollisionSlab>> sectionIndex = new HashMap<>();

        private void rebuildSectionIndex() {
            sectionIndex.clear();
            for (var belt : belts.values()) {
                for (var slab : belt.slabs) {
                    forEachSection(slab.bounds, section -> sectionIndex
                            .computeIfAbsent(section, ignored -> new ArrayList<>())
                            .add(slab));
                }
            }
        }

        private List<VoxelShape> getCollisionShapes(AABB queryBounds) {
            var expandedQuery = queryBounds.inflate(1.0E-7);
            Set<CollisionSlab> matching = Collections.newSetFromMap(new IdentityHashMap<>());
            forEachSection(expandedQuery, section -> {
                var slabs = sectionIndex.get(section);
                if (slabs == null) return;
                for (var slab : slabs) {
                    if (slab.bounds.intersects(expandedQuery)) matching.add(slab);
                }
            });

            if (matching.isEmpty()) return List.of();
            return matching.stream().map(CollisionSlab::shape).toList();
        }

        private static void forEachSection(AABB bounds, java.util.function.LongConsumer consumer) {
            var minX = SectionPos.blockToSectionCoord((int) Math.floor(bounds.minX));
            var minY = SectionPos.blockToSectionCoord((int) Math.floor(bounds.minY));
            var minZ = SectionPos.blockToSectionCoord((int) Math.floor(bounds.minZ));
            var maxX = SectionPos.blockToSectionCoord((int) Math.floor(bounds.maxX));
            var maxY = SectionPos.blockToSectionCoord((int) Math.floor(bounds.maxY));
            var maxZ = SectionPos.blockToSectionCoord((int) Math.floor(bounds.maxZ));

            for (int x = minX; x <= maxX; x++) {
                for (int y = minY; y <= maxY; y++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        consumer.accept(SectionPos.asLong(x, y, z));
                    }
                }
            }
        }
    }
}
