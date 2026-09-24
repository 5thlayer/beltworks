// SPDX-FileCopyrightText: Rearth
// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: CC-BY-4.0 AND MIT

package rearth.belts.neoforge.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import rearth.belts.Belts;
import rearth.belts.BlockEntitiesContent;
import rearth.belts.client.renderers.BeltTileRenderer;

@Mod(value = Belts.MOD_ID, dist = Dist.CLIENT)
public final class BeltsModClientNeoForge {

    public BeltsModClientNeoForge(IEventBus eventBus) {
        eventBus.addListener(this::registerRenderers);
        NeoForge.EVENT_BUS.addListener(BeltHandClient::tick);
        NeoForge.EVENT_BUS.addListener(BeltHandClient::interact);
    }

    private void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(BlockEntitiesContent.CHUTE_BLOCK.get(), context -> new NeoForgeChuteBeltRenderer());
        event.registerBlockEntityRenderer(BlockEntitiesContent.BELT_TILE.get(), context -> new BeltTileRenderer());
    }
}
