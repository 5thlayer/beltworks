// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.blocks;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * A menu with one ghost slot, which names an item without holding one: the splitter's filter and
 * the creative crate's item (#22, #125). The slot is the menu's first, and it shows what the server
 * last set, so vanilla's own slot sync keeps the client's screen true.
 *
 * <p>The client changes nothing. A click on the slot is read from the cursor stack the server holds,
 * so a client cannot claim to hold what it does not, and a recipe viewer's drop names its item in a
 * packet. Both take {@link #setGhostItem}, the menu's one entry point, and neither takes anything
 * from anyone.
 */
public abstract class GhostSlotMenu extends AbstractContainerMenu {

    /** The ghost slot's index, the menu's first slot. */
    public static final int GHOST_SLOT = 0;

    private final Container ghost = new SimpleContainer(1);

    /** Adds the ghost slot, at {@code x} and {@code y} on the screen, before any other slot. */
    protected GhostSlotMenu(MenuType<?> type, int containerId, int x, int y) {
        super(type, containerId);
        addSlot(new GhostSlot(ghost, x, y));
    }

    /** Sets what the slot names to a copy of one of the stack, or clears it with an empty stack. On the server only. */
    public abstract void setGhostItem(ItemStack stack);

    /** What the slot shows, or empty for nothing. */
    protected final ItemStack ghostItem() {
        return ghost.getItem(0);
    }

    /** Shows the item the server holds in the slot, which the next broadcast sends to the client. */
    protected final void showGhostItem(ItemStack stack) {
        if (!ItemStack.matches(ghostItem(), stack)) ghost.setItem(0, stack.copy());
    }

    /**
     * A click on the ghost slot sets it from the stack on the cursor, or clears it with an empty
     * cursor, and changes neither stack. Every other input on the slot does nothing. The client does
     * nothing at all: the server's answer arrives as the slot's contents.
     */
    @Override
    public void clicked(int slotId, int button, ContainerInput input, Player who) {
        if (slotId != GHOST_SLOT) {
            super.clicked(slotId, button, input, who);
            return;
        }
        if (input == ContainerInput.PICKUP && (button == 0 || button == 1)) setGhostItem(getCarried());
    }

    @Override
    public boolean canDragTo(Slot slot) {
        return !(slot instanceof GhostSlot) && super.canDragTo(slot);
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return !(slot instanceof GhostSlot) && super.canTakeItemForPickAll(stack, slot);
    }

    /** The item shown, which the player can neither put into nor take out of. */
    private static final class GhostSlot extends Slot {

        GhostSlot(Container container, int x, int y) {
            super(container, 0, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }
    }
}
