// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.blocks;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import io.github._5thlayer.beltworks.BlockEntitiesContent;
import io.github._5thlayer.beltworks.MenuContent;

/**
 * The menu of a creative crate's screen (#125): the player's inventory, and a view of the item the
 * crate is set to, which the server alone writes.
 *
 * <p>The item is the menu's {@linkplain GhostSlotMenu ghost slot}, which stays true when another
 * player changes the crate. {@link #setGhostItem} is the one way the item is set, for the click, a
 * shift-click from the inventory and a recipe viewer's drop alike.
 */
public final class CreativeCrateMenu extends GhostSlotMenu {

    /** The item's ghost slot: a click sets it from the cursor stack and takes nothing. */
    public static final int CRATE_SLOT = GHOST_SLOT;

    private final BlockPos crate;
    private final Player player;

    /** Opened on the client from the position the server sent. */
    public CreativeCrateMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf data) {
        this(containerId, inventory, data.readBlockPos());
    }

    public CreativeCrateMenu(int containerId, Inventory inventory, BlockPos crate) {
        super(MenuContent.CREATIVE_CRATE.get(), containerId, 80, 20);
        this.crate = crate;
        player = inventory.player;
        addStandardInventorySlots(inventory, 8, 51);
        refresh();
    }

    /** Where the crate stands. */
    public BlockPos crate() {
        return crate;
    }

    /** The item the crate is set to, as its slot shows it, or empty for none. */
    public ItemStack shown() {
        return ghostItem();
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
        showGhostItem(entity.item());
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
    @Override
    public void setGhostItem(ItemStack stack) {
        if (player.level().isClientSide()) return;
        var entity = entity(player);
        if (entity == null) return;
        entity.setItem(stack);
        refresh();
    }

    /** A shift-click on an inventory stack sets the crate to it and moves nothing: a crate holds no items. */
    @Override
    public ItemStack quickMoveStack(Player who, int index) {
        if (index != CRATE_SLOT) {
            var stack = slots.get(index).getItem();
            if (!stack.isEmpty()) setGhostItem(stack);
        }
        return ItemStack.EMPTY;
    }

    /** Valid while the crate stands and the player is within reach of it. */
    @Override
    public boolean stillValid(Player who) {
        return entity(who) != null && who.isWithinBlockInteractionRange(crate, 1);
    }
}
