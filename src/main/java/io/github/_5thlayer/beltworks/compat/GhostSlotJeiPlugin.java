// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.compat;

import java.util.List;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.Identifier;
import io.github._5thlayer.beltworks.Beltworks;

/**
 * Lets an item be dragged from JEI's list onto the ghost slot of a splitter's or a creative crate's
 * screen (#22, #125). JEI finds the plugin by its annotation, and nothing else names it, so the Mod
 * loads without JEI (ADR 0002).
 */
@JeiPlugin
public class GhostSlotJeiPlugin implements IModPlugin {

    private static final Identifier UID = Beltworks.id("ghost_slot");

    private static final class Drop<T extends AbstractContainerScreen<?>> implements IGhostIngredientHandler<T> {

        @Override
        public <I> List<Target<I>> getTargetsTyped(T screen, ITypedIngredient<I> ingredient, boolean doStart) {
            // Only an item is a target: a fluid or another ingredient has none.
            var stack = ingredient.getItemStack();
            if (stack.isEmpty()) return List.of();
            var slot = GhostSlotDrop.slot(screen);
            var area = new Rect2i(screen.getLeftPos() + slot.x, screen.getTopPos() + slot.y, 16, 16);
            return List.of(new Target<>() {
                @Override
                public Rect2i getArea() {
                    return area;
                }

                @Override
                public void accept(I dropped) {
                    GhostSlotDrop.drop(screen, stack.get());
                }
            });
        }

        @Override
        public void onComplete() {
        }
    }

    @Override
    public Identifier getPluginUid() {
        return UID;
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        GhostSlotDrop.SCREENS.forEach(screen -> register(registration, screen));
    }

    private static <T extends AbstractContainerScreen<?>> void register(IGuiHandlerRegistration registration, Class<T> screen) {
        registration.addGhostIngredientHandler(screen, new Drop<>());
    }
}
