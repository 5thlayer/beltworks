package rearth.belts.blocks;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import rearth.belts.model.Wedge;

import java.util.EnumMap;
import java.util.Map;

/**
 * A {@link Wedge}: part of the middle or top standing on it, which places and removes it, and has no
 * item. Its facing is the way it rises. Breaking it breaks the tile, which pays the one drop
 * (PlanetaryFactory #420).
 */
public class BeltWedgeBlock extends HorizontalDirectionalBlock {

    private static final int STEPS = 4;
    private static final Map<Direction, VoxelShape> SHAPES = shapes();

    public BeltWedgeBlock(BlockBehaviour.Properties settings) {
        super(settings);
        registerDefaultState(defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BlockStateProperties.HORIZONTAL_FACING);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(BlockStateProperties.HORIZONTAL_FACING));
    }

    private static Map<Direction, VoxelShape> shapes() {
        var shapes = new EnumMap<Direction, VoxelShape>(Direction.class);
        var size = 16 / STEPS;
        for (var facing : Direction.Plane.HORIZONTAL) {
            var shape = Shapes.empty();
            for (var step = 0; step < STEPS; step++) {
                double back = step * size;
                double front = back + size;
                double height = front;
                var box = switch (facing) {
                    case NORTH -> Block.box(0, 0, 16 - front, 16, height, 16 - back);
                    case SOUTH -> Block.box(0, 0, back, 16, height, front);
                    case EAST -> Block.box(back, 0, 0, front, height, 16);
                    default -> Block.box(16 - front, 0, 0, 16 - back, height, 16);
                };
                shape = Shapes.or(shape, box);
            }
            shapes.put(facing, shape.optimize());
        }
        return shapes;
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        var above = pos.above();
        if (!level.isClientSide() && level.getBlockState(above).getBlock() instanceof BeltTileBlock) level.destroyBlock(above, !player.preventsBlockDrops(), player);
        return super.playerWillDestroy(level, pos, state, player);
    }

    // Its own tile removes it only once the tile no longer needs it, so a wedge gone from under a
    // tile that still does was removed by something else, and takes the tile with it (#420).
    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        var above = level.getBlockState(pos.above());
        if (above.getBlock() instanceof BeltTileBlock && Wedge.needed(above.getValue(BeltTileBlock.PITCH).model())
              && !(level.getBlockState(pos).getBlock() instanceof BeltWedgeBlock)) {
            level.destroyBlock(pos.above(), true);
        }
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return simpleCodec(BeltWedgeBlock::new);
    }
}
