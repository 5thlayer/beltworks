// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.mixin;

import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import io.github._5thlayer.beltworks.client.BeltworksClient;

// NeoForge's client level tick event comes after entities and block entities have ticked, too late
// to push the local player or advance the lines' copies before they move.
@Mixin(ClientLevel.class)
public abstract class ClientLevelTickMixin {

    @Inject(method = "tickEntities", at = @At("HEAD"))
    private void beltworks$beforeEntitiesTick(CallbackInfo callback) {
        BeltworksClient.tick((ClientLevel) (Object) this);
    }
}
