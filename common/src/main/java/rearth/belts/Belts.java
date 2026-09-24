package rearth.belts;

import dev.architectury.event.events.common.InteractionEvent;
import dev.architectury.event.events.common.TickEvent;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rearth.belts.collision.BeltCollisionRegistry;
import rearth.belts.items.Dismantling;

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
        // An event rather than the item's own use: the items that dismantle are not the fork's.
        InteractionEvent.RIGHT_CLICK_BLOCK.register((player, hand, pos, face) -> Dismantling.useOn(player, hand, pos));
        InteractionEvent.RIGHT_CLICK_ITEM.register(Dismantling::use);
    }
    
    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
