package rearth.belts.blocks;

import com.mojang.serialization.MapCodec;
import rearth.belts.util.MathHelpers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
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

public class ConveyorSupportBlock extends HorizontalDirectionalBlock {
    
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
}
