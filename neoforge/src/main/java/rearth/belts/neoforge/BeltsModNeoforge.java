package rearth.belts.neoforge;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import rearth.belts.BlockEntitiesContent;
import rearth.belts.Belts;
import rearth.belts.api.item.ItemApi;
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
        
        // Run our common setup.
        Belts.init();
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("2")
          .playToServer(BeltHandPayload.TYPE, BeltHandPayload.STREAM_CODEC, BeltHandPayload::handle)
          .playToClient(BeltChangesPayload.TYPE, BeltChangesPayload.STREAM_CODEC, BeltChangesPayload::handle);
    }

    // A tier-1 loader has no face, so a pole does not count it as a machine.
    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.Energy.BLOCK, BlockEntitiesContent.CHUTE_BLOCK.get(),
          (loader, side) -> loader.getEnergy().powered()
            ? ENERGY_FACES.computeIfAbsent(loader, LoaderEnergyHandler::new) : null);
    }
}
