// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import io.github._5thlayer.beltworks.gametest.BlockUpdateWatch;

/** Lets a game test see a block update being sent, which nothing else observes on a server. */
@Mixin(ServerLevel.class)
public abstract class ServerLevelUpdateMixin {

    @Inject(method = "sendBlockUpdated", at = @At("HEAD"))
    private void beltworks$watch(BlockPos pos, BlockState oldState, BlockState newState, int flags, CallbackInfo callback) {
        BlockUpdateWatch.saw(pos);
    }
}
