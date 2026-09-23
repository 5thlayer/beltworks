package rearth.belts.blocks;

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
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import rearth.belts.items.SplitterItem;
import rearth.belts.model.BeltTier;
import rearth.belts.model.LineScan;
import rearth.belts.model.TileShape;

/**
 * One block of belt (PlanetaryFactory #398). Its facing is its direction of travel, and its shape,
 * straight or a corner, follows from what feeds it (#391).
 */
public class BeltTileBlock extends HorizontalDirectionalBlock implements EntityBlock {

    public static final EnumProperty<Shape> CORNER = EnumProperty.create("shape", Shape.class);

    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 6, 16);

    private final BeltTier tier;

    public BeltTileBlock(BlockBehaviour.Properties settings, BeltTier tier) {
        super(settings);
        this.tier = tier;
        registerDefaultState(defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH)
                               .setValue(CORNER, Shape.STRAIGHT));
    }

    /** The tier whose speed this tile runs at; a line runs at its slowest tile (ADR-0076). */
    public BeltTier tier() {
        return tier;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    // A splitter placed across a straight line takes the tile's place (PlanetaryFactory #394).
    @Override
    protected boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
        return context.getItemInHand().getItem() instanceof SplitterItem
                 && SplitterItem.replacesTile(state, context.getHorizontalDirection());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BlockStateProperties.HORIZONTAL_FACING, CORNER);
    }

    // The way the player looks, as a Factorio belt is laid; the pack's Rotate turns the look itself
    // (PlanetaryFactory ADR-0083).
    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext ctx) {
        var state = defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, ctx.getHorizontalDirection());
        return shaped(state, ctx.getLevel(), ctx.getClickedPos());
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
                                     BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        return shaped(state, level, pos);
    }

    private static BlockState shaped(BlockState state, BlockGetter level, BlockPos pos) {
        var facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        var shape = TileShape.at(new LineScan.Spot(pos.getX(), pos.getY(), pos.getZ()), travel(facing),
          (from, travel) -> feeds(level.getBlockState(new BlockPos(from.x(), from.y(), from.z())), travel));
        return state.setValue(CORNER, Shape.of(shape));
    }

    // A tile, a loader or a splitter half outputs the way it faces.
    public static boolean feeds(BlockState state, LineScan.Travel travel) {
        if (!(state.getBlock() instanceof BeltTileBlock) && !(state.getBlock() instanceof ChuteBlock)) return false;
        return travel.equals(travel(state.getValue(HorizontalDirectionalBlock.FACING)));
    }

    public static LineScan.Travel travel(Direction facing) {
        return new LineScan.Travel(facing.getStepX(), facing.getStepZ());
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
