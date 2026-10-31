// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.blocks;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A creative crate (#125): an endless supply of the one item it is set to, and a void for whatever
 * goes in, kept by its {@link CreativeCrateBlockEntity}. A plain use opens its screen, which sets the
 * item. It has no facing, no power, no config and no recipe, which keeps it out of survival.
 */
public final class CreativeCrateBlock extends BaseEntityBlock {

    public static final MapCodec<CreativeCrateBlock> CODEC = simpleCodec(CreativeCrateBlock::new);

    public CreativeCrateBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<CreativeCrateBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CreativeCrateBlockEntity(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    // With an item in hand too: the use falls through to here, and the screen sets the crate from it.
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        // The server opens the menu, which brings the client's screen.
        if (player instanceof ServerPlayer server) server.openMenu(menuProvider(pos), data -> data.writeBlockPos(pos));
        return InteractionResult.SUCCESS;
    }

    private static MenuProvider menuProvider(BlockPos pos) {
        return new SimpleMenuProvider((id, inventory, who) -> new CreativeCrateMenu(id, inventory, pos),
          Component.translatable("block.beltworks.creative_crate"));
    }
}
