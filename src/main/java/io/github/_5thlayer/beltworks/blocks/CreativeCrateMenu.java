// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.blocks;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import io.github._5thlayer.beltworks.BlockEntitiesContent;
import io.github._5thlayer.beltworks.MenuContent;

/**
 * The menu of a creative crate's screen (#125): the player's inventory, and a view of the item the
 * crate is set to, which the server alone writes.
 *
 * <p>The item is shown as a ghost slot of one item, so vanilla's own slot sync keeps the client's
 * screen true, including when another player changes the crate. The client changes nothing: the
 * slot's click is read from the cursor stack the server holds, so a client cannot claim to hold what
 * it does not. {@link #setCrateItem} is the one way the item is set, for the click, a shift-click
 * from the inventory and a recipe viewer's drop alike.
 */
public final class CreativeCrateMenu extends AbstractContainerMenu implements GhostSlotMenu {

    /** The item's ghost slot, the menu's first: a click sets it from the cursor stack and takes nothing. */
    public static final int CRATE_SLOT = 0;

    @Override
    public int ghostSlot() {
        return CRATE_SLOT;
    }

    @Override
    public void setGhost(ItemStack stack) {
        setCrateItem(stack);
    }

    private final BlockPos crate;
    private final Player player;
    private final Container shown = new SimpleContainer(1);

    /** Opened on the client from the position the server sent. */
    public CreativeCrateMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf data) {
        this(containerId, inventory, data.readBlockPos());
    }

    public CreativeCrateMenu(int containerId, Inventory inventory, BlockPos crate) {
        super(MenuContent.CREATIVE_CRATE.get(), containerId);
        this.crate = crate;
        player = inventory.player;
        addSlot(new GhostSlot(shown, 80, 20));
        addStandardInventorySlots(inventory, 8, 51);
        refresh();
    }

    /** Where the crate stands. */
    public BlockPos crate() {
        return crate;
    }

    /** The item the crate is set to, as its slot shows it, or empty for none. */
    public ItemStack shown() {
        return shown.getItem(0);
    }

    private @Nullable CreativeCrateBlockEntity entity(Player who) {
        var level = who.level();
        if (!level.hasChunkAt(crate)) return null;
        return level.getBlockEntity(crate, BlockEntitiesContent.CREATIVE_CRATE.get()).orElse(null);
    }

    // Reads the crate's item into the slot, which the next broadcast sends to the client.
    private void refresh() {
        if (player.level().isClientSide()) return;
        var entity = entity(player);
        if (entity == null) return;
        var item = entity.item();
        if (!ItemStack.matches(shown(), item)) shown.setItem(0, item);
    }

    @Override
    public void broadcastChanges() {
        refresh();
        super.broadcastChanges();
    }

    /**
     * Sets the crate to a copy of one of the stack, data and all, or clears it with an empty stack.
     * The stack is only read: nothing is taken from anyone. On the server only.
     */
    public void setCrateItem(ItemStack stack) {
        if (player.level().isClientSide()) return;
        var entity = entity(player);
        if (entity == null) return;
        entity.setItem(stack);
        refresh();
    }

    /**
     * A click on the ghost slot sets the crate from the stack on the cursor, or clears it with an
     * empty cursor, and changes neither stack. Every other input on the slot does nothing. The client
     * does nothing at all: the server's answer arrives as the slot's contents.
     */
    @Override
    public void clicked(int slotId, int button, ContainerInput input, Player who) {
        if (slotId != CRATE_SLOT) {
            super.clicked(slotId, button, input, who);
            return;
        }
        if (input == ContainerInput.PICKUP && (button == 0 || button == 1)) setCrateItem(getCarried());
    }

    /** A shift-click on an inventory stack sets the crate to it and moves nothing: a crate holds no items. */
    @Override
    public ItemStack quickMoveStack(Player who, int index) {
        if (index != CRATE_SLOT) {
            var stack = slots.get(index).getItem();
            if (!stack.isEmpty()) setCrateItem(stack);
        }
        return ItemStack.EMPTY;
    }

    /** Valid while the crate stands and the player is within reach of it. */
    @Override
    public boolean stillValid(Player who) {
        return entity(who) != null && who.isWithinBlockInteractionRange(crate, 1);
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
