// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.compat;

import java.util.List;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import io.github._5thlayer.beltworks.blocks.GhostSlotMenu;
import io.github._5thlayer.beltworks.client.CreativeCrateScreen;
import io.github._5thlayer.beltworks.client.SplitterScreen;
import io.github._5thlayer.beltworks.neoforge.GhostSlotPayload;

/**
 * What the JEI and EMI plugins share (#22, #125): the screens whose ghost slot takes a dropped item,
 * where that slot is drawn, and the drop itself, sent as {@link GhostSlotPayload} for the server to
 * set through the menu's {@link GhostSlotMenu#setGhost}.
 */
final class GhostSlotDrop {

    /** The screens of a menu with a ghost slot, each a {@link GhostSlotMenu}'s. */
    static final List<Class<? extends AbstractContainerScreen<?>>> SCREENS = List.of(SplitterScreen.class, CreativeCrateScreen.class);

    private GhostSlotDrop() {
    }

    /** The screen's ghost slot, whose position is relative to the screen's left and top. */
    static Slot slot(AbstractContainerScreen<?> screen) {
        return screen.getMenu().slots.get(((GhostSlotMenu) screen.getMenu()).ghostSlot());
    }

    /** Asks the server to set the screen's ghost slot to one of the item dropped on it. */
    static void drop(AbstractContainerScreen<?> screen, ItemStack stack) {
        ClientPacketDistributor.sendToServer(new GhostSlotPayload(screen.getMenu().containerId, stack.copyWithCount(1)));
    }
}
