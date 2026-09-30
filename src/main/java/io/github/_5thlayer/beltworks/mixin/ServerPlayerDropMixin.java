// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.mixin;

import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import io.github._5thlayer.beltworks.blocks.BeltDrop;

/**
 * Vanilla's Q and Ctrl+Q both end in {@code ServerPlayer.drop}, after the item's own check that it
 * may be dropped and before the stack leaves the slot. A one-item drop aimed at a belt tile is put
 * on the belt there instead (#91); Ctrl+Q throws the stack as ever.
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerDropMixin {

    @Inject(method = "drop(Z)V", at = @At(value = "INVOKE",
      target = "Lnet/minecraft/world/entity/player/Inventory;removeFromSelected(Z)Lnet/minecraft/world/item/ItemStack;"), cancellable = true)
    private void beltworks$dropOntoABelt(boolean all, CallbackInfo callback) {
        if (!all && BeltDrop.dropOnto((ServerPlayer) (Object) this)) callback.cancel();
    }
}
