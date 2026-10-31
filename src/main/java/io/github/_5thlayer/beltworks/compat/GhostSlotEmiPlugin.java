// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.compat;

import dev.emi.emi.api.EmiDragDropHandler;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.widget.Bounds;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.item.ItemStack;
import io.github._5thlayer.beltworks.blocks.GhostSlotMenu;

/**
 * Lets an item be dragged from EMI's list onto the ghost slot of a splitter's or a creative crate's
 * screen (#125). EMI finds the plugin by its annotation, and nothing else names it, so the Mod loads
 * without EMI (ADR 0002).
 */
@EmiEntrypoint
public class GhostSlotEmiPlugin implements EmiPlugin {

    @Override
    public void register(EmiRegistry registry) {
        GhostSlotDrop.SCREENS.forEach(screen -> register(registry, screen));
    }

    private static <T extends AbstractContainerScreen<? extends GhostSlotMenu>> void register(EmiRegistry registry, Class<T> screens) {
        registry.addDragDropHandler(screens, new EmiDragDropHandler.BoundsBased<T>((screen, bounds) -> {
            var slot = GhostSlotDrop.slot(screen);
            bounds.accept(new Bounds(screen.getLeftPos() + slot.x, screen.getTopPos() + slot.y, 16, 16), dropped -> {
                var stack = itemOf(dropped);
                if (!stack.isEmpty()) GhostSlotDrop.drop(screen, stack);
            });
        }));
    }

    /** The first item among the ingredient's stacks, or empty when it holds none, as a fluid does. */
    private static ItemStack itemOf(EmiIngredient ingredient) {
        for (var stack : ingredient.getEmiStacks()) {
            var item = stack.getItemStack();
            if (!item.isEmpty()) return item;
        }
        return ItemStack.EMPTY;
    }
}
