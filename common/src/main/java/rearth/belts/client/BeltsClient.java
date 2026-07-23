package rearth.belts.client;

import dev.architectury.registry.client.rendering.BlockEntityRendererRegistry;
import rearth.belts.BlockEntitiesContent;
import rearth.belts.client.renderers.ChuteBeltRenderer;

public final class BeltsClient {
    
    public static void init() {
        System.out.println("Hello from belt client!");
        
    }
    
    public static void registerRenderers() {
        System.out.println("Registering renderers");
        
        BlockEntityRendererRegistry.register(BlockEntitiesContent.CHUTE_BLOCK.get(), ctx -> new ChuteBeltRenderer());
    }
    
}
