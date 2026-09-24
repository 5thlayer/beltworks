// SPDX-FileCopyrightText: Rearth
// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: CC-BY-4.0 AND MIT

package rearth.belts.client;

import dev.architectury.event.events.client.ClientTickEvent;
import net.minecraft.client.Minecraft;
import dev.architectury.registry.client.rendering.BlockEntityRendererRegistry;
import rearth.belts.BlockEntitiesContent;
import rearth.belts.client.renderers.ChuteBeltRenderer;
import rearth.belts.collision.BeltCollisionRegistry;

public final class BeltsClient {
    
    public static void init() {
        System.out.println("Hello from belt client!");
        ClientTickEvent.CLIENT_LEVEL_PRE.register(level -> {
            var player = Minecraft.getInstance().player;
            if (player != null) BeltCollisionRegistry.moveLocalPlayer(level, player);
            TileLines.tick(level);
        });
    }
    
    public static void registerRenderers() {
        System.out.println("Registering renderers");
        
        BlockEntityRendererRegistry.register(BlockEntitiesContent.CHUTE_BLOCK.get(), ctx -> new ChuteBeltRenderer());
    }
    
}
