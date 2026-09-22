package rearth.belts.blocks;

import com.mojang.serialization.MapCodec;
import rearth.belts.BlockEntitiesContent;
import rearth.belts.util.MathHelpers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A belt end, or a point a belt passes through, placed only by the belt item and free (PlanetaryFactory
 * #366). As an end it faces the way its belts run.
 */
public class ConveyorSupportBlock extends HorizontalDirectionalBlock implements EntityBlock {
    
    private static final Map<Direction, VoxelShape> SHAPES = new HashMap<>();
    
    private VoxelShape createShapeForDirection(Direction direction) {
        return Shapes.or(
          MathHelpers.rotateVoxelShape(Shapes.box(7 / 16f, 0 / 16f, 6 / 16f, 9 / 16f, 4 / 16f, 10 / 16f), direction, AttachFace.FLOOR),
          MathHelpers.rotateVoxelShape(Shapes.box(2 / 16f, 4 / 16f, 6 / 16f, 14 / 16f, 8 / 16f, 10 / 16f), direction, AttachFace.FLOOR)
        ).optimize();
    }
    
    public ConveyorSupportBlock(BlockBehaviour.Properties settings) {
        super(settings);
        registerDefaultState(defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
    }
    
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BlockStateProperties.HORIZONTAL_FACING);
    }
    
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        var dir = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        if (dir == Direction.SOUTH) dir = Direction.NORTH;
        if (dir == Direction.EAST) dir = Direction.WEST;
        return SHAPES.computeIfAbsent(dir, this::createShapeForDirection);
    }
    
    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return Objects.requireNonNull(super.getStateForPlacement(ctx)).setValue(BlockStateProperties.HORIZONTAL_FACING, ctx.getHorizontalDirection().getOpposite());
    }
    
    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return simpleCodec(ConveyorSupportBlock::new);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ChuteBlockEntity(pos, state);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level world, BlockState state, BlockEntityType<T> type) {
        return (world1, pos, state1, blockEntity) -> {
            if (blockEntity instanceof ChuteBlockEntity support) support.tick(world1, pos, state1, support);
        };
    }

    // Breaking a support breaks every belt it holds, refunded as breaking a loader refunds (#366).
    @Override
    public BlockState playerWillDestroy(Level world, BlockPos pos, BlockState state, Player player) {
        if (!world.isClientSide())
            world.getBlockEntity(pos, BlockEntitiesContent.CHUTE_BLOCK.get()).ifPresent(support -> support.releaseBelts(player));
        return super.playerWillDestroy(world, pos, state, player);
    }
}
