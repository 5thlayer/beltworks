// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.blocks;

import net.minecraft.world.item.ItemStack;

/**
 * A menu with one ghost slot, which names an item without holding one: the splitter's filter and
 * the creative crate's item (#22, #125). A recipe viewer drops an item on that slot, and the server
 * sets it through {@link #setGhost}, the menu's own entry point that a click on the slot also uses.
 */
public interface GhostSlotMenu {

    /** The index of the ghost slot among the menu's slots. */
    int ghostSlot();

    /** Sets what the slot names to a copy of one of the stack, or clears it with an empty stack. On the server only. */
    void setGhost(ItemStack stack);
}
