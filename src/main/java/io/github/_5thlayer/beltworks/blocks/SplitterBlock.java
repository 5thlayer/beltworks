// SPDX-FileCopyrightText: Rearth
// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: CC-BY-4.0 AND MIT

package io.github._5thlayer.beltworks.blocks;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import io.github._5thlayer.groundworks.TurnsInPlace;
import io.github._5thlayer.beltworks.BlockEntitiesContent;
import io.github._5thlayer.beltworks.model.BeltTier;
import io.github._5thlayer.beltworks.model.Support;

import java.util.Locale;

/**
 * One half of a splitter, two blocks wide across its {@link #FACING}, which is the way items flow.
 * Each half ends one belt at its back and starts one at its front (ADR 0007), and is itself a block
 * of belt of the splitter's tier (PlanetaryFactory #373). The halves share the loader's block
 * entity, with no inventory and no filter.
 */
public class SplitterBlock extends BeltEndBlock implements TurnsInPlace {

    public static final EnumProperty<Side> SIDE = EnumProperty.create("side", Side.class);

    /** Why a splitter half is not turned in place. */
    public static final String NOT_TURNED = "message.beltworks.rotate_splitter";

    // A tile's slab, with the divider on top (PlanetaryFactory #394).
    private static final VoxelShape SHAPE = Shapes.box(0, 0, 0, 1, 8 / 16d, 1);
    // At the belt's surface, a tile's height, so what stands on a half rides its belt.
    private static final VoxelShape COLLISION = Shapes.box(0, 0, 0, 1, 6 / 16d, 1);

    public SplitterBlock(Properties settings, BeltTier tier) {
        super(settings, tier);
        registerDefaultState(defaultBlockState().setValue(SIDE, Side.LEFT));
    }

    /** The other half's position. */
    public static BlockPos partner(BlockPos pos, BlockState state) {
        var facing = state.getValue(FACING);
        return pos.relative(state.getValue(SIDE) == Side.LEFT ? facing.getClockWise() : facing.getCounterClockWise());
    }

    /** Whether {@code candidate} is the other half of the splitter {@code state} is one half of. */
    public static boolean isPartner(BlockState state, BlockState candidate) {
        return candidate.getBlock() == state.getBlock()
                 && candidate.getValue(FACING) == state.getValue(FACING)
                 && candidate.getValue(SIDE) != state.getValue(SIDE);
    }

    // One half turned alone would split the splitter, and a splitter is never turned whole (#28).
    @Override
    public TurnsInPlace.Verdict<BlockState> turnInPlace(BlockState state, Level level, BlockPos pos, boolean reverse) {
        return TurnsInPlace.refused(NOT_TURNED);
    }

    // One support for both halves, which the left half shows.
    @Override
    public @Nullable Support support(BlockGetter level, BlockPos pos, BlockState state, Support.Setting setting) {
        if (state.getValue(SIDE) != Side.LEFT) return null;
        var travel = BeltTileBlock.travel(state.getValue(FACING));
        return Support.splitter(BeltTileBlock.spot(pos), travel, BeltTileBlock.ground(level), setting);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(SIDE);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection()).setValue(SIDE, Side.LEFT);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return COLLISION;
    }

    @Override
    public boolean setsFilter(BeltEndBlockEntity entity, ItemStack stack) {
        return false;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player, BlockHitResult hit) {
        return InteractionResult.PASS;
    }

    // The other half is removed without a player, which would drop its belts' refund on the ground (#346).
    @Override
    public BlockState playerWillDestroy(Level world, BlockPos pos, BlockState state, Player player) {
        if (!world.isClientSide()) {
            world.getBlockEntity(partner(pos, state), BlockEntitiesContent.BELT_END.get())
              .ifPresent(half -> half.releaseBelts(player));
        }
        return super.playerWillDestroy(world, pos, state, player);
    }

    // A mirror turns the left half into the right one.
    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        var mirrored = super.mirror(state, mirror);
        return mirror == Mirror.NONE ? mirrored
                 : mirrored.setValue(SIDE, state.getValue(SIDE) == Side.LEFT ? Side.RIGHT : Side.LEFT);
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return simpleCodec(settings -> new SplitterBlock(settings, tier()));
    }

    public enum Side implements StringRepresentable {
        LEFT, RIGHT;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }
}
