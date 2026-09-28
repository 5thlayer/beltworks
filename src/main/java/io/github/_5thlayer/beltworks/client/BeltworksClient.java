// SPDX-FileCopyrightText: Rearth
// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: CC-BY-4.0 AND MIT

package io.github._5thlayer.beltworks.client;

import io.github._5thlayer.beltworks.Beltworks;
import io.github._5thlayer.beltworks.BlockEntitiesContent;
import io.github._5thlayer.beltworks.client.renderers.BeltTileRenderer;
import io.github._5thlayer.beltworks.collision.BeltCollisionRegistry;
import io.github._5thlayer.beltworks.neoforge.client.BeltHandClient;
import io.github._5thlayer.beltworks.neoforge.client.NeoForgeBeltEndRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = Beltworks.MOD_ID, dist = Dist.CLIENT)
public final class BeltworksClient {

    public BeltworksClient(IEventBus eventBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, BeltworksClientConfig.SPEC);
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        eventBus.addListener(BeltworksClient::registerRenderers);
        eventBus.addListener(FeederReachKeys::onRegisterKeys);
        NeoForge.EVENT_BUS.addListener(FeederReachKeys::onClientTick);
        NeoForge.EVENT_BUS.addListener(BeltHandClient::tick);
        NeoForge.EVENT_BUS.addListener(BeltHandClient::interact);
        BeltPreviews.register();
    }

    /** Runs at the start of each client level tick, before its entities move. */
    public static void tick(ClientLevel level) {
        var player = Minecraft.getInstance().player;
        if (player != null) BeltCollisionRegistry.moveLocalPlayer(level, player);
        TileLines.tick(level);
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(BlockEntitiesContent.BELT_END.get(), context -> new NeoForgeBeltEndRenderer());
        event.registerBlockEntityRenderer(BlockEntitiesContent.BELT_TILE.get(), context -> new BeltTileRenderer());
    }
}
