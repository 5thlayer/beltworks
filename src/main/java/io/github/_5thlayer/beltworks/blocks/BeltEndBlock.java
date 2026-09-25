// SPDX-FileCopyrightText: Rearth
// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: CC-BY-4.0 AND MIT

package io.github._5thlayer.beltworks.blocks;

import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
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
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;
import io.github._5thlayer.beltworks.BlockEntitiesContent;
import io.github._5thlayer.beltworks.model.BeltTier;
import io.github._5thlayer.beltworks.model.Support;

import java.util.Objects;
import java.util.function.Consumer;

/** The block of a belt end: a loader, or through {@link SplitterBlock} a splitter half. */
public class BeltEndBlock extends HorizontalDirectionalBlock implements EntityBlock {
    
    private final BeltTier tier;

    public BeltEndBlock(BlockBehaviour.Properties settings, BeltTier tier) {
        super(settings);
        this.tier = tier;
        registerDefaultState(defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
    }
    
    /** The tier whose items a second this loader moves, whatever belt it is on (ADR 0007). */
    public BeltTier tier() {
        return tier;
    }

    /**
     * The support the belt end at {@code pos} shows, or null: a loader never shows one, being fixed
     * to its inventory (ADR 0012).
     */
    public @Nullable Support support(BlockGetter level, BlockPos pos, BlockState state) {
        return null;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        
        var candidate = world.getBlockEntity(pos, BlockEntitiesContent.BELT_END.get());
        if (candidate.isPresent()) {
            var entity = candidate.get();
            // An empty hand is a reset, and only the main hand's reaches useWithoutItem; an empty
            // off hand tried after the main hand set a filter must not undo it.
            if (!setsFilter(entity, stack)) return super.useItemOn(stack, state, world, pos, player, hand, hit);
            if (!world.isClientSide())
                entity.assignFilterItem(stack, player);
            return InteractionResult.SUCCESS;
        }
        
        return super.useItemOn(stack, state, world, pos, player, hand, hit);
    }
    
    /**
     * Whether a click holding this stack sets the filter. A loader with no belt yet takes one too, so it
     * is set before the line reaches it; placing against a loader takes a sneak (ADR 0004).
     */
    public boolean setsFilter(BeltEndBlockEntity entity, ItemStack stack) {
        return !stack.isEmpty();
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player, BlockHitResult hit) {
        
        var candidate = world.getBlockEntity(pos, BlockEntitiesContent.BELT_END.get());
        if (candidate.isPresent()) {
            var entity = candidate.get();
            if (!world.isClientSide())
                entity.resetFilterItem(player);
            return InteractionResult.SUCCESS;
        }
        return super.useWithoutItem(state, world, pos, player, hit);
    }
    
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BlockStateProperties.HORIZONTAL_FACING);
    }
    
    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        
        var targetFacing = ctx.getClickedFace();
        if (targetFacing.getAxis().isVertical())
            targetFacing = ctx.getHorizontalDirection().getOpposite();
        
        return Objects.requireNonNull(super.getStateForPlacement(ctx))
                 .setValue(BlockStateProperties.HORIZONTAL_FACING, targetFacing);
    }
    
    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return simpleCodec(settings -> new BeltEndBlock(settings, tier));
    }
    
    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BeltEndBlockEntity(pos, state);
    }
    
    @Override
    protected boolean triggerEvent(BlockState state, Level world, BlockPos pos, int type, int data) {
        super.triggerEvent(state, world, pos, type, data);
        var blockEntity = world.getBlockEntity(pos);
        return blockEntity != null && blockEntity.triggerEvent(type, data);
    }
    
    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level world, BlockState state, BlockEntityType<T> type) {
        return ((world1, pos, state1, blockEntity) -> {
            if (blockEntity instanceof BeltEndBlockEntity beltEnd)
                beltEnd.tick(world1, pos, state1, beltEnd);
        });
    }
    
    @Override
    public BlockState playerWillDestroy(Level world, BlockPos pos, BlockState state, Player player) {
        
        if (world.isClientSide()) return super.playerWillDestroy(world, pos, state, player);
        
        world.getBlockEntity(pos, BlockEntitiesContent.BELT_END.get()).ifPresent(beltEnd -> beltEnd.releaseBelts(player));
        
        return super.playerWillDestroy(world, pos, state, player);
    }
}
