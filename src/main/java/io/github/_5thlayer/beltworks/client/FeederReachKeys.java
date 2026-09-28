// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;
import org.lwjgl.glfw.GLFW;
import io.github._5thlayer.beltworks.Beltworks;
import io.github._5thlayer.beltworks.model.FeederArms;
import io.github._5thlayer.beltworks.neoforge.FeederReachPayload;

import java.util.Optional;

/**
 * The Mod's own keys, Head Reach ({@code H}) and Tail Reach ({@code J}), in the game only (ADR
 * 0013). As Rotate's, a press only tells the server; a held stack's reach comes back on the synced
 * stack, and the preview redraws from it.
 */
final class FeederReachKeys {

    private static final KeyMapping.Category KEYS = new KeyMapping.Category(Beltworks.id(Beltworks.MOD_ID));
    private static final KeyMapping HEAD_REACH = new KeyMapping("key.beltworks.head_reach",
            KeyConflictContext.IN_GAME, KeyModifier.NONE, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, KEYS);
    private static final KeyMapping TAIL_REACH = new KeyMapping("key.beltworks.tail_reach",
            KeyConflictContext.IN_GAME, KeyModifier.NONE, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J, KEYS);

    private FeederReachKeys() {
    }

    static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        event.registerCategory(KEYS);
        event.register(HEAD_REACH);
        event.register(TAIL_REACH);
    }

    static void onClientTick(ClientTickEvent.Post event) {
        while (HEAD_REACH.consumeClick()) press(FeederArms.Arm.HEAD);
        while (TAIL_REACH.consumeClick()) press(FeederArms.Arm.TAIL);
    }

    private static void press(FeederArms.Arm arm) {
        var minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;
        var aimed = minecraft.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK
                ? Optional.of(hit.getBlockPos()) : Optional.<BlockPos>empty();
        ClientPacketDistributor.sendToServer(new FeederReachPayload(arm, aimed));
    }
}
