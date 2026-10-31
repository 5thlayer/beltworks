// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import io.github._5thlayer.beltworks.blocks.CreativeCrateMenu;

/**
 * A creative crate's screen, in vanilla's colours: one ghost slot for the crate's item on top, the
 * player's inventory below. A click on the slot with an item sets the crate, and with an empty
 * cursor clears it; a shift-click on an inventory stack sets it too.
 *
 * <p>It holds no state of its own: the item is the slot's contents, which the server keeps true.
 * Public so the recipe viewer compat, which drops items on the slot, can name it.
 */
public final class CreativeCrateScreen extends AbstractContainerScreen<CreativeCrateMenu> {

    private static final int WIDTH = 176;
    private static final int HEIGHT = 133;

    public CreativeCrateScreen(CreativeCrateMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, WIDTH, HEIGHT);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        ContainerPanel.draw(graphics, menu, leftPos, topPos, WIDTH, HEIGHT);
        graphics.text(font, title, leftPos + 8, topPos + 6, ContainerPanel.LABEL, false);
        graphics.text(font, playerInventoryTitle, leftPos + 8, topPos + 39, ContainerPanel.LABEL, false);
    }

    // The labels are drawn with the background, in colours that read on it.
    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        var slot = hoveredSlot;
        if (slot != null && slot.index == CreativeCrateMenu.CRATE_SLOT && !slot.hasItem() && menu.getCarried().isEmpty()) {
            graphics.setTooltipForNextFrame(font, Component.translatable("screen.beltworks.creative_crate.item"), mouseX, mouseY);
        }
    }
}
