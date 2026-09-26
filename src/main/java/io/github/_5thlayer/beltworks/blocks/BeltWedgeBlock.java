// SPDX-FileCopyrightText: Rearth
// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: CC-BY-4.0 AND MIT

package io.github._5thlayer.beltworks.blocks;

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
import io.github._5thlayer.groundworks.TurnsInPlace;
import io.github._5thlayer.beltworks.model.Wedge;

import java.util.EnumMap;
import java.util.Map;

/**
 * A {@link Wedge}: part of the middle or top standing on it, which places and removes it, and has no
 * item. It fills what its block's floor cuts off the slope's band, at the top of the block below on
 * its downhill edge. Its facing is the way the slope rises. Breaking it breaks the tile, which pays the one drop
 * (PlanetaryFactory #420).
 */
public class BeltWedgeBlock extends HorizontalDirectionalBlock implements TurnsInPlace {

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

    // It holds up only its tile, so it is aimed at and broken but never stops what walks under it (#56).
    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    // The corner of its block under the slope's downhill end, square rather than triangular.
    private static Map<Direction, VoxelShape> shapes() {
        var shapes = new EnumMap<Direction, VoxelShape>(Direction.class);
        shapes.put(Direction.NORTH, Block.box(0, 10, 10, 16, 16, 16));
        shapes.put(Direction.SOUTH, Block.box(0, 10, 0, 16, 16, 6));
        shapes.put(Direction.EAST, Block.box(0, 10, 0, 6, 16, 16));
        shapes.put(Direction.WEST, Block.box(10, 10, 0, 16, 16, 16));
        return shapes;
    }

    // Only its slope's tile keeps a wedge, so a wedge turned alone would never be put right.
    @Override
    public TurnsInPlace.Verdict<BlockState> turnInPlace(BlockState state, Level level, BlockPos pos, boolean reverse) {
        return TurnsInPlace.refused(BeltTileBlock.SLOPE_NOT_TURNED);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        var above = pos.above();
        if (!level.isClientSide() && level.getBlockState(above).getBlock() instanceof BeltTileBlock) level.destroyBlock(above, !player.preventsBlockDrops(), player);
        return super.playerWillDestroy(level, pos, state, player);
    }

    // Its own tile removes it only once the tile no longer needs it, so a wedge gone from under a
    // tile that still does was removed by something else, and takes the tile with it (#420). A
    // block set in its place holds the tile up instead (#51).
    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        var above = level.getBlockState(pos.above());
        if (above.getBlock() instanceof BeltTileBlock && BeltTileBlock.wedge(level, pos.above(), above) != Wedge.Verdict.NONE
              && !(level.getBlockState(pos).getBlock() instanceof BeltWedgeBlock)) {
            level.destroyBlock(pos.above(), true);
        }
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return simpleCodec(BeltWedgeBlock::new);
    }
}
