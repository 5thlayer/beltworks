// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.blocks;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
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
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import io.github._5thlayer.beltworks.model.BeltTier;
import io.github._5thlayer.beltworks.model.FeederArms;
import io.github._5thlayer.groundworks.TurnsInPlace;

import java.util.Locale;
import java.util.Objects;

/**
 * A feeder: its head reaches behind its facing and its tail in front, or turned left or right, each
 * one to three blocks ({@link io.github._5thlayer.beltworks.model.FeederArms}), as far as the held
 * stack's {@linkplain FeederReach reach} when placed. It is placed as a loader is, so against a
 * block's side its head is at that block, and it takes a loader's filter the way a loader does.
 * Rotate turns only its tail, as it turns a tile's way out, and leaves its head where it reaches.
 */
public class FeederBlock extends HorizontalDirectionalBlock implements EntityBlock, TurnsInPlace {

    public static final EnumProperty<TailTurn> TAIL_TURN = EnumProperty.create("tail_turn", TailTurn.class);

    // Its slate base at belt height and the column at its centre with the hub its arms hang from;
    // the arms themselves collide with nothing (CONTEXT.md, Arm).
    private static final VoxelShape SHAPE = Shapes.or(Block.box(1, 0, 1, 15, 6, 15), Block.box(6, 6, 6, 10, 17, 10),
            Block.box(5, 17, 5, 11, 20, 11));

    private final BeltTier tier;

    public FeederBlock(BlockBehaviour.Properties settings, BeltTier tier) {
        super(settings);
        this.tier = tier;
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH).setValue(TAIL_TURN, TailTurn.STRAIGHT));
    }

    public BeltTier tier() {
        return tier;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, TAIL_TURN);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        var facing = ctx.getClickedFace();
        if (facing.getAxis().isVertical()) facing = ctx.getHorizontalDirection().getOpposite();
        return Objects.requireNonNull(super.getStateForPlacement(ctx)).setValue(FACING, facing);
    }

    // The tail turns straight, right, left and back, and the facing, so the head, stays (#66, story 6).
    @Override
    public TurnsInPlace.Verdict<BlockState> turnInPlace(BlockState state, Level level, BlockPos pos, boolean reverse) {
        return TurnsInPlace.turned(state.setValue(TAIL_TURN, TailTurn.of(state.getValue(TAIL_TURN).turn().turned(reverse))));
    }

    // The held stack's reach, set by Head Reach and Tail Reach before placing (ADR 0013). A feeder
    // put in another's place by a tier swap keeps the old one's, and the held reach stays on the
    // stack (#94).
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof FeederBlockEntity feeder && !feeder.swappedIn()) {
            feeder.setArms(FeederReach.held(stack));
        }
    }

    // As a loader's: a click with an item sets the filter to it, and an empty hand clears it. Only the
    // main hand's empty click reaches useWithoutItem, so an empty off hand tried after the main hand
    // set a filter does not undo it.
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.isEmpty() || !(level.getBlockEntity(pos) instanceof FeederBlockEntity feeder))
            return super.useItemOn(stack, state, level, pos, player, hand, hit);
        if (!level.isClientSide()) feeder.assignFilterItem(stack, player);
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof FeederBlockEntity feeder))
            return super.useWithoutItem(state, level, pos, player, hit);
        if (!level.isClientSide()) feeder.resetFilterItem(player);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return simpleCodec(settings -> new FeederBlock(settings, tier));
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FeederBlockEntity(pos, state);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        return (tickLevel, pos, tickState, blockEntity) -> {
            if (blockEntity instanceof FeederBlockEntity feeder) feeder.tick(tickLevel, pos, tickState);
        };
    }

    /** A {@link FeederArms.Turn} as a block state property names it. */
    public enum TailTurn implements StringRepresentable {
        STRAIGHT(FeederArms.Turn.STRAIGHT),
        LEFT(FeederArms.Turn.LEFT),
        RIGHT(FeederArms.Turn.RIGHT);

        private final FeederArms.Turn turn;

        TailTurn(FeederArms.Turn turn) {
            this.turn = turn;
        }

        public FeederArms.Turn turn() {
            return turn;
        }

        public static TailTurn of(FeederArms.Turn turn) {
            return valueOf(turn.name());
        }

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }
}
