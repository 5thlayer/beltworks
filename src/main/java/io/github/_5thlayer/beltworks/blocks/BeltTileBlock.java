// SPDX-FileCopyrightText: Rearth
// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: CC-BY-4.0 AND MIT

package io.github._5thlayer.beltworks.blocks;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import io.github._5thlayer.groundworks.TurnsInPlace;
import io.github._5thlayer.beltworks.BlockContent;
import io.github._5thlayer.beltworks.items.BeltRefusal;
import io.github._5thlayer.beltworks.items.SplitterItem;
import io.github._5thlayer.beltworks.model.BeltTier;
import io.github._5thlayer.beltworks.model.LineScan;
import io.github._5thlayer.beltworks.model.Pitch;
import io.github._5thlayer.beltworks.model.Support;
import io.github._5thlayer.beltworks.model.TileShape;
import io.github._5thlayer.beltworks.model.Wedge;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.OptionalDouble;

/**
 * One block of belt (PlanetaryFactory #398). Its facing is its direction of travel, and its shape,
 * straight or a corner, follows from what feeds it (#391), as its pitch follows from the heights of
 * what feeds it and what it feeds (#417).
 */
public class BeltTileBlock extends HorizontalDirectionalBlock implements EntityBlock, TurnsInPlace {

    public static final EnumProperty<Shape> CORNER = EnumProperty.create("shape", Shape.class);
    public static final EnumProperty<PitchState> PITCH = EnumProperty.create("pitch", PitchState.class);

    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 6, 16);
    private static final Map<Direction, Map<PitchState, VoxelShape>> SLOPE_SHAPES = slopeShapes();

    // A tile's pitch asks the tiles a block up or down ahead and behind it, and each of those asks
    // its own (#417).
    private static final int DERIVED_REACH = 2;

    /** Why a slope, or the wedge under one, is not turned in place (ADR 0005). */
    public static final String SLOPE_NOT_TURNED = "message.beltworks.rotate_slope";

    private final BeltTier tier;

    public BeltTileBlock(BlockBehaviour.Properties settings, BeltTier tier) {
        super(settings);
        this.tier = tier;
        registerDefaultState(defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH)
                               .setValue(CORNER, Shape.STRAIGHT).setValue(PITCH, PitchState.LEVEL));
    }

    /** The tier whose speed this tile runs at; a line runs at its slowest tile (ADR 0007). */
    public BeltTier tier() {
        return tier;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        var pitch = state.getValue(PITCH);
        return pitch == PitchState.LEVEL ? SHAPE : SLOPE_SHAPES.get(state.getValue(BlockStateProperties.HORIZONTAL_FACING)).get(pitch);
    }

    // No higher than the surface, so a rider meets the ride's slabs rather than the steps (#417).
    private static Map<Direction, Map<PitchState, VoxelShape>> slopeShapes() {
        var shapes = new EnumMap<Direction, Map<PitchState, VoxelShape>>(Direction.class);
        for (var facing : Direction.Plane.HORIZONTAL) {
            var byPitch = new EnumMap<PitchState, VoxelShape>(PitchState.class);
            for (var pitch : PitchState.values()) {
                var shape = Shapes.empty();
                for (var slice = 0; slice < 16; slice++) {
                    var height = Math.floor(16 * Math.min(pitch.model().surface(slice / 16d), pitch.model().surface((slice + 1) / 16d)));
                    if (height <= 0) continue;
                    double back = slice;
                    double front = slice + 1;
                    var box = switch (facing) {
                        case NORTH -> Block.box(0, 0, 16 - front, 16, height, 16 - back);
                        case SOUTH -> Block.box(0, 0, back, 16, height, front);
                        case EAST -> Block.box(back, 0, 0, front, height, 16);
                        default -> Block.box(16 - front, 0, 0, 16 - back, height, 16);
                    };
                    shape = Shapes.or(shape, box);
                }
                byPitch.put(pitch, shape.optimize());
            }
            shapes.put(facing, byPitch);
        }
        return shapes;
    }

    // A splitter placed across a straight line takes the tile's place (PlanetaryFactory #394).
    @Override
    protected boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
        return context.getItemInHand().getItem() instanceof SplitterItem
                 && SplitterItem.replacesTile(state, context.getHorizontalDirection());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BlockStateProperties.HORIZONTAL_FACING, CORNER, PITCH);
    }

    // The way the player looks, as a Factorio belt is laid; Groundworks' Rotate turns the look itself.
    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext ctx) {
        var state = defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, ctx.getHorizontalDirection());
        return shaped(state, ctx.getLevel(), ctx.getClickedPos());
    }

    // A slope never turns. A level tile takes vanilla's turn, unless a tile placed so would be
    // refused, since the turn reshapes the tiles around it as a placement does (#419, #420).
    @Override
    public TurnsInPlace.Verdict<BlockState> turnInPlace(BlockState state, Level level, BlockPos pos, boolean reverse) {
        if (state.getValue(PITCH) != PitchState.LEVEL) return TurnsInPlace.refused(SLOPE_NOT_TURNED);
        var turned = state.rotate(level, pos, reverse ? Rotation.COUNTERCLOCKWISE_90 : Rotation.CLOCKWISE_90);
        var reshape = reshape(level, Map.of(spot(pos), travel(turned.getValue(BlockStateProperties.HORIZONTAL_FACING))), this);
        return reshape.refused() ? TurnsInPlace.refused(reshape.refusal().messageKey()) : TurnsInPlace.turned(shaped(turned, level, pos));
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
                                     BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        return shaped(state, level, pos);
    }

    // A slope's other end is a block up or down, which no neighbour update reaches, so a placed,
    // turned or broken tile re-derives every tile it could change. Only a change of facing or of
    // block starts one, so the tiles it re-derives start none (#417).
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        keepWedge(level, pos, state);
        if (oldState.is(this) && oldState.getValue(BlockStateProperties.HORIZONTAL_FACING) == state.getValue(BlockStateProperties.HORIZONTAL_FACING)) return;
        reshapeAround(level, pos);
        // Placed in a state it did not derive; its block entity does not exist yet, so it is
        // re-derived on the next tick rather than now.
        if (!shaped(state, level, pos).equals(state)) level.scheduleTick(pos, this, 1);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        var shaped = shaped(state, level, pos);
        if (!shaped.equals(state)) level.setBlock(pos, shaped, Block.UPDATE_ALL);
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        reshapeAround(level, pos);
        var now = level.getBlockState(pos);
        if (now.getBlock() instanceof BeltTileBlock) keepWedge(level, pos, now);
        else if (level.getBlockState(pos.below()).getBlock() instanceof BeltWedgeBlock) level.setBlock(pos.below(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
    }

    // Whatever sets a tile's state, a player, a stretch or a re-derivation, sets its wedge too (#420).
    private static void keepWedge(Level level, BlockPos pos, BlockState state) {
        var below = pos.below();
        var there = level.getBlockState(below);
        if (Wedge.under(state.getValue(PITCH).model(), occupant(level, below)) == Wedge.Verdict.PLACE) {
            var wedge = wedgeFor(state);
            if (!there.equals(wedge)) level.setBlock(below, wedge, Block.UPDATE_ALL);
        } else if (there.getBlock() instanceof BeltWedgeBlock) {
            level.setBlock(below, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    /** What stands at {@code pos}, under a tile, as its wedge reads it. */
    public static Wedge.Below occupant(BlockGetter level, BlockPos pos) {
        var state = level.getBlockState(pos);
        if (state.getBlock() instanceof BeltWedgeBlock) return Wedge.Below.REPLACEABLE;
        // A loader or a machine may be a full cube, and is still no ground for a wedge to stand in for.
        if (state.hasBlockEntity() || !state.getFluidState().isEmpty()) return Wedge.Below.OCCUPIED;
        if (state.canBeReplaced()) return Wedge.Below.REPLACEABLE;
        if (state.isFaceSturdy(level, pos, Direction.UP)) return Wedge.Below.SOLID;
        return Wedge.Below.OCCUPIED;
    }

    private static BlockState wedgeFor(BlockState tile) {
        var uphill = Wedge.uphill(tile.getValue(PITCH).model(), travel(tile.getValue(BlockStateProperties.HORIZONTAL_FACING)));
        return BlockContent.BELT_WEDGE.get().defaultBlockState()
                 .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.getApproximateNearest(uphill.x(), 0, uphill.z()));
    }

    /**
     * The wedges placing {@code planned} tiles adds, the tiles' own and those of the tiles it
     * reshapes, or why it is refused: a wedge that would land on anything but air, a plant or snow
     * (#420), refused where the wedge would go, or a corner the placement would turn into a slope,
     * since a slope never turns (#419). A wedge a reshape removes is not named: its tile's upkeep
     * removes it.
     */
    public static Reshape reshape(Level level, Map<LineScan.Spot, LineScan.Travel> planned, Block tile) {
        var around = around(level, planned);
        var candidates = new LinkedHashMap<BlockPos, BlockState>();
        for (var entry : planned.entrySet()) {
            var pos = pos(entry.getKey());
            var facing = Direction.getApproximateNearest(entry.getValue().x(), 0, entry.getValue().z());
            candidates.put(pos, tile.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, facing));
        }
        for (var spot : planned.keySet()) tilesAround(level, pos(spot)).forEach(candidates::putIfAbsent);

        var placed = new LinkedHashMap<BlockPos, BlockState>();
        for (var candidate : candidates.entrySet()) {
            var pos = candidate.getKey();
            var spot = spot(pos);
            var current = candidate.getValue();
            var formed = formed(current, spot, around);
            var existing = !planned.containsKey(spot);
            if (existing && current.getValue(CORNER) != Shape.STRAIGHT && formed.getValue(PITCH) != PitchState.LEVEL) {
                return new Reshape(Map.of(), BeltRefusal.SLOPE_TURNS, null);
            }
            if (existing && formed.getValue(PITCH) == current.getValue(PITCH)) continue;
            var under = pos.below();
            var below = planned.containsKey(spot(under)) ? Wedge.Below.OCCUPIED : occupant(level, under);
            switch (Wedge.under(formed.getValue(PITCH).model(), below)) {
                case REFUSED -> {
                    return new Reshape(Map.of(), BeltRefusal.WEDGE_BLOCKED, under.immutable());
                }
                case PLACE -> {
                    var wedge = wedgeFor(formed);
                    if (!level.getBlockState(under).equals(wedge)) placed.put(under, wedge);
                }
                case NONE -> {
                }
            }
        }
        return new Reshape(placed, null, null);
    }

    /** The wedges a placement adds, by position, or why it is refused, and where when that is somewhere. */
    public record Reshape(Map<BlockPos, BlockState> wedges, @Nullable BeltRefusal refusal, @Nullable BlockPos at) {

        public boolean refused() {
            return refusal != null;
        }
    }

    private static BlockPos pos(LineScan.Spot spot) {
        return new BlockPos(spot.x(), spot.y(), spot.z());
    }

    private static void reshapeAround(Level level, BlockPos pos) {
        tilesAround(level, pos).forEach((at, there) -> {
            var shaped = shaped(there, level, at);
            if (!shaped.equals(there)) level.setBlock(at, shaped, Block.UPDATE_ALL);
        });
    }

    /** The loaded tiles a change at {@code pos} can reshape, as the world re-derives them and a plan predicts them. */
    private static Map<BlockPos, BlockState> tilesAround(Level level, BlockPos pos) {
        var tiles = new LinkedHashMap<BlockPos, BlockState>();
        for (var at : BlockPos.betweenClosed(pos.offset(-DERIVED_REACH, -DERIVED_REACH, -DERIVED_REACH), pos.offset(DERIVED_REACH, DERIVED_REACH, DERIVED_REACH))) {
            if (at.equals(pos) || Math.abs(at.getX() - pos.getX()) + Math.abs(at.getZ() - pos.getZ()) > DERIVED_REACH || !level.isLoaded(at)) continue;
            var there = level.getBlockState(at);
            if (there.getBlock() instanceof BeltTileBlock) tiles.put(at.immutable(), there);
        }
        return tiles;
    }

    private static BlockState shaped(BlockState state, BlockGetter level, BlockPos pos) {
        return formed(state, spot(pos), around(level, Map.of(spot(pos), travel(state.getValue(BlockStateProperties.HORIZONTAL_FACING)))));
    }

    /** A tile's state with the shape and pitch its neighbours give it. */
    public static BlockState formed(BlockState state, LineScan.Spot spot, TileShape.Around around) {
        var travel = travel(state.getValue(BlockStateProperties.HORIZONTAL_FACING));
        return state.setValue(CORNER, Shape.of(TileShape.at(spot, travel, around)))
                 .setValue(PITCH, PitchState.of(Pitch.at(spot, travel, around)));
    }

    /**
     * The world as a tile's shape and pitch read it, with {@code planned} tiles standing where they
     * are about to be placed. Only loaded blocks are read, since a derivation reaches two blocks off.
     */
    public static TileShape.Around around(BlockGetter level, Map<LineScan.Spot, LineScan.Travel> planned) {
        return new TileShape.Around() {
            @Override
            public LineScan.@Nullable Travel tile(LineScan.Spot spot) {
                var travel = planned.get(spot);
                if (travel != null) return travel;
                var state = stateAt(spot);
                return state.getBlock() instanceof BeltTileBlock ? travel(state.getValue(HorizontalDirectionalBlock.FACING)) : null;
            }

            @Override
            public boolean feeds(LineScan.Spot from, LineScan.Travel travel) {
                var tile = tile(from);
                if (tile != null) return tile.equals(travel);
                return BeltTileBlock.feeds(stateAt(from), travel);
            }

            private BlockState stateAt(LineScan.Spot spot) {
                var pos = new BlockPos(spot.x(), spot.y(), spot.z());
                if (level instanceof LevelReader reader && !reader.hasChunkAt(pos)) return Blocks.AIR.defaultBlockState();
                return level.getBlockState(pos);
            }
        };
    }

    /** The support the tile at {@code pos} travelling {@code travel} shows, or null where it shows none (ADR 0012). */
    public static @Nullable Support support(BlockGetter level, BlockPos pos, LineScan.Travel travel, Support.Setting setting) {
        return Support.at(spot(pos), travel, around(level, Map.of()), ground(level), setting);
    }

    /**
     * The world under and beside a tile, as its support reads it. Unlike a wedge, a leg passes a
     * fluid, and stands on a loader or another tile.
     */
    public static Support.Ground ground(BlockGetter level) {
        return new Support.Ground() {
            @Override
            public Support.Fill fill(LineScan.Spot spot) {
                var pos = pos(spot);
                var state = level.getBlockState(pos);
                var block = state.getBlock();
                if (block instanceof BeltTileBlock || block instanceof BeltEndBlock) return Support.Fill.BELT;
                if (block instanceof BeltWedgeBlock || block instanceof LiquidBlock || state.canBeReplaced()) return Support.Fill.OPEN;
                return state.isFaceSturdy(level, pos, Direction.UP) ? Support.Fill.SOLID : Support.Fill.OPEN;
            }

            // A tile's surface where the leg stands, or nothing outside a turn's curve; a loader's or
            // splitter's top, or a solid block's.
            @Override
            public OptionalDouble top(LineScan.Spot spot, double x, double z) {
                var pos = pos(spot);
                var state = level.getBlockState(pos);
                if (state.getBlock() instanceof BeltTileBlock) {
                    return Support.surface(state.getValue(PITCH).model(), travel(state.getValue(BlockStateProperties.HORIZONTAL_FACING)),
                      state.getValue(CORNER).model(), x, z);
                }
                if (state.getBlock() instanceof BeltEndBlock) return OptionalDouble.of(state.getShape(level, pos).max(Direction.Axis.Y));
                return OptionalDouble.of(1);
            }

            @Override
            public boolean fixes(LineScan.Spot spot, LineScan.Travel toward) {
                var pos = pos(spot);
                return level.getBlockState(pos).isFaceSturdy(level, pos, Direction.getApproximateNearest(toward.x(), 0, toward.z()));
            }
        };
    }

    public static LineScan.Spot spot(BlockPos pos) {
        return new LineScan.Spot(pos.getX(), pos.getY(), pos.getZ());
    }

    // A tile, a loader or a splitter half outputs the way it faces.
    public static boolean feeds(BlockState state, LineScan.Travel travel) {
        if (!(state.getBlock() instanceof BeltTileBlock) && !(state.getBlock() instanceof BeltEndBlock)) return false;
        return travel.equals(travel(state.getValue(HorizontalDirectionalBlock.FACING)));
    }

    public static LineScan.Travel travel(Direction facing) {
        return new LineScan.Travel(facing.getStepX(), facing.getStepZ());
    }

    /** {@link Pitch} as a block state property. */
    public enum PitchState implements StringRepresentable {
        LEVEL(Pitch.LEVEL, "level"),
        FOOT_UP(Pitch.FOOT_UP, "foot_up"),
        MIDDLE_UP(Pitch.MIDDLE_UP, "middle_up"),
        TOP_UP(Pitch.TOP_UP, "top_up"),
        FOOT_DOWN(Pitch.FOOT_DOWN, "foot_down"),
        MIDDLE_DOWN(Pitch.MIDDLE_DOWN, "middle_down"),
        TOP_DOWN(Pitch.TOP_DOWN, "top_down");

        private final Pitch model;
        private final String name;

        PitchState(Pitch model, String name) {
            this.model = model;
            this.name = name;
        }

        public Pitch model() {
            return model;
        }

        public static PitchState of(Pitch model) {
            for (var pitch : values()) if (pitch.model == model) return pitch;
            throw new IllegalArgumentException(model.name());
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    /** {@link TileShape} as a block state property. */
    public enum Shape implements StringRepresentable {
        STRAIGHT(TileShape.STRAIGHT, "straight"),
        FROM_LEFT(TileShape.FROM_LEFT, "from_left"),
        FROM_RIGHT(TileShape.FROM_RIGHT, "from_right");

        private final TileShape model;
        private final String name;

        Shape(TileShape model, String name) {
            this.model = model;
            this.name = name;
        }

        public TileShape model() {
            return model;
        }

        public static Shape of(TileShape model) {
            for (var shape : values()) if (shape.model == model) return shape;
            throw new IllegalArgumentException(model.name());
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return simpleCodec(settings -> new BeltTileBlock(settings, tier));
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BeltTileBlockEntity(pos, state);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level world, BlockState state, BlockEntityType<T> type) {
        if (world.isClientSide()) return (level, pos, blockState, blockEntity) -> {
            if (blockEntity instanceof BeltTileBlockEntity tile) tile.clientTick();
        };
        return (level, pos, blockState, blockEntity) -> {
            if (blockEntity instanceof BeltTileBlockEntity tile) tile.tick();
        };
    }
}
