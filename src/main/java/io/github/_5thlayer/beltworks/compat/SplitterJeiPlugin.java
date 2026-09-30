// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.compat;

import java.util.List;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import io.github._5thlayer.beltworks.Beltworks;
import io.github._5thlayer.beltworks.blocks.SplitterMenu;
import io.github._5thlayer.beltworks.client.SplitterScreen;
import io.github._5thlayer.beltworks.neoforge.SplitterFilterPayload;

/**
 * Lets an item be dragged from JEI's list onto the filter slot of a splitter's screen (#22). JEI
 * finds the plugin by its annotation, and nothing else names it, so the Mod loads without JEI
 * (ADR 0002).
 *
 * <p>The drop sends {@link SplitterFilterPayload}, which the server answers through the same
 * {@link SplitterMenu#setFilter} a click on the slot uses.
 */
@JeiPlugin
public class SplitterJeiPlugin implements IModPlugin {

    private static final Identifier UID = Beltworks.id("splitter");

    private static final IGhostIngredientHandler<SplitterScreen> FILTER_DROP = new IGhostIngredientHandler<>() {
        @Override
        public <I> List<Target<I>> getTargetsTyped(SplitterScreen screen, ITypedIngredient<I> ingredient, boolean doStart) {
            // Only items filter: a fluid or another ingredient has no target.
            if (ingredient.getItemStack().isEmpty()) return List.of();
            var slot = screen.getMenu().slots.get(SplitterMenu.FILTER_SLOT);
            var area = new Rect2i(screen.getLeftPos() + slot.x, screen.getTopPos() + slot.y, 16, 16);
            return List.of(new Target<>() {
                @Override
                public Rect2i getArea() {
                    return area;
                }

                @Override
                public void accept(I dropped) {
                    var stack = ingredient.getItemStack().orElse(ItemStack.EMPTY);
                    ClientPacketDistributor.sendToServer(new SplitterFilterPayload(screen.getMenu().containerId, stack.copyWithCount(1)));
                }
            });
        }

        @Override
        public void onComplete() {
        }
    };

    @Override
    public Identifier getPluginUid() {
        return UID;
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGhostIngredientHandler(SplitterScreen.class, FILTER_DROP);
    }
}
