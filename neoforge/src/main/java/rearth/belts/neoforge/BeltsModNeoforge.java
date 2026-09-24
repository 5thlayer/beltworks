// SPDX-FileCopyrightText: Rearth
// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: CC-BY-4.0 AND MIT

package rearth.belts.neoforge;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.ChunkWatchEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import rearth.belts.BeltSync;
import rearth.belts.BlockEntitiesContent;
import rearth.belts.Belts;
import rearth.belts.api.item.ItemApi;
import rearth.belts.blocks.BeltTileBlockEntity;
import rearth.belts.blocks.ChuteBlockEntity;

@Mod(Belts.MOD_ID)
public final class BeltsModNeoforge {

    // One face per loader: two journals on one buffer in one transaction would revert out of order.
    private static final Map<ChuteBlockEntity, LoaderEnergyHandler> ENERGY_FACES =
      Collections.synchronizedMap(new WeakHashMap<>());

    public BeltsModNeoforge(IEventBus modBus) {
        
        ItemApi.BLOCK = new NeoforgeItemApiImpl();
        modBus.addListener(BeltsModNeoforge::registerCapabilities);
        modBus.addListener(BeltsModNeoforge::registerPayloads);
        NeoForge.EVENT_BUS.addListener(BeltsModNeoforge::sendLinesOfChunk);
        NeoForge.EVENT_BUS.addListener(BeltsModNeoforge::releaseLinesOfChunk);
        
        // Run our common setup.
        Belts.init();
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("3")
          .playToServer(BeltHandPayload.TYPE, BeltHandPayload.STREAM_CODEC, BeltHandPayload::handle)
          .playToClient(BeltChangesPayload.TYPE, BeltChangesPayload.STREAM_CODEC, BeltChangesPayload::handle)
          .playToClient(BeltLinePayload.TYPE, BeltLinePayload.STREAM_CODEC, BeltLinePayload::handle);
    }

    // Posted after the chunk is saved and before any of its block entities is removed (#395).
    private static void releaseLinesOfChunk(ChunkEvent.Unload event) {
        if (event.getLevel().isClientSide() || !(event.getChunk() instanceof LevelChunk chunk)) return;
        BeltTileBlockEntity.chunkUnloading(List.copyOf(chunk.getBlockEntities().values()));
    }

    // A line is otherwise sent whole only when it is built, so a player who comes into sight of
    // one later is sent it here, after the chunk that shows its tiles (#395).
    private static void sendLinesOfChunk(ChunkWatchEvent.Sent event) {
        var holders = new LinkedHashSet<BeltTileBlockEntity>();
        for (var blockEntity : event.getChunk().getBlockEntities().values()) {
            if (blockEntity instanceof BeltTileBlockEntity tile && tile.holder() != null) holders.add(tile.holder());
        }
        for (var holder : holders) {
            var update = holder.lineUpdate();
            if (update != null) BeltSync.sendLine(event.getPlayer(), update);
        }
    }

    // A tier-1 loader has no face, so a pole does not count it as a machine.
    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.Energy.BLOCK, BlockEntitiesContent.CHUTE_BLOCK.get(),
          (loader, side) -> loader.getEnergy().powered()
            ? ENERGY_FACES.computeIfAbsent(loader, LoaderEnergyHandler::new) : null);
    }
}
