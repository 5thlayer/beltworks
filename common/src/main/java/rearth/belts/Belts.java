package rearth.belts;

import dev.architectury.event.events.common.TickEvent;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rearth.belts.collision.BeltCollisionRegistry;

public final class Belts {
    public static final String MOD_ID = "belts";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static void init() {
        BlockContent.BLOCKS.register();
        ItemContent.ITEMS.register();
        BlockEntitiesContent.TYPES.register();
        ComponentContent.COMPONENTS.register();
        ItemGroupContent.GROUPS.register();
        TickEvent.SERVER_LEVEL_PRE.register(BeltCollisionRegistry::moveServerEntities);
    }
    
    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
