// SPDX-FileCopyrightText: Rearth
// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: CC-BY-4.0 AND MIT

package rearth.belts;

import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionResult;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rearth.belts.collision.BeltCollisionRegistry;
import rearth.belts.items.Dismantling;

public final class Belts {
    public static final String MOD_ID = "belts";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static void init(IEventBus modBus) {
        BlockContent.BLOCKS.register(modBus);
        ItemContent.ITEMS.register(modBus);
        BlockEntitiesContent.TYPES.register(modBus);
        ComponentContent.COMPONENTS.register(modBus);
        ItemGroupContent.GROUPS.register(modBus);
        NeoForge.EVENT_BUS.addListener(LevelTickEvent.Pre.class, event -> {
            if (!event.getLevel().isClientSide()) BeltCollisionRegistry.moveServerEntities(event.getLevel());
        });
        // An event rather than the item's own use: the items that dismantle are not the Mod's.
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, PlayerInteractEvent.RightClickBlock.class, event -> {
            var result = Dismantling.useOn(event.getEntity(), event.getHand(), event.getPos());
            if (result == InteractionResult.PASS) return;
            event.setCanceled(true);
            event.setCancellationResult(result);
        });
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, PlayerInteractEvent.RightClickItem.class, event -> {
            var result = Dismantling.use(event.getEntity(), event.getHand());
            if (result == InteractionResult.PASS) return;
            event.setCanceled(true);
            event.setCancellationResult(result);
        });
    }
    
    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
