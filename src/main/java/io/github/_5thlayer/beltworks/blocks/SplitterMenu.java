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
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import io.github._5thlayer.beltworks.BlockEntitiesContent;
import io.github._5thlayer.beltworks.MenuContent;
import io.github._5thlayer.beltworks.model.Splitter;

/**
 * The menu of a splitter's screen (#20, #21, #22): the player's inventory, and a view of the
 * splitter's one {@link SplitterSettings} record that the server alone writes.
 *
 * <p>The priorities and the side each switch shows travel as data slots, the filter as a slot of
 * one item, so vanilla's own sync keeps the client's screen true, including when another player
 * changes the splitter. The client changes nothing: a priority is a menu button, and the filter is
 * the slot's click, read from the cursor stack the server holds, so a client cannot claim to hold
 * what it does not. {@link #setFilter} is the one way the filter is set, for the click and for a
 * recipe viewer's drop alike.
 */
public final class SplitterMenu extends AbstractContainerMenu {

    /** The filter's ghost slot, the menu's first: a click sets it from the cursor stack and takes nothing. */
    public static final int FILTER_SLOT = 0;

    /** Buttons: turn a priority on, on the side its switch shows, or off. */
    public static final int INPUT_TOGGLE = 0;
    public static final int OUTPUT_TOGGLE = 2;
    /** Buttons: flip the side a priority's switch shows, moving the priority if it is on. */
    public static final int INPUT_FLIP = 1;
    public static final int OUTPUT_FLIP = 3;

    private final BlockPos half;
    private final Player player;
    private final Container filter = new SimpleContainer(1);
    private final DataSlot inputPriority = addDataSlot(DataSlot.standalone());
    private final DataSlot outputPriority = addDataSlot(DataSlot.standalone());
    // The side a switch shows: the priority's while it is on, the last one it had while it is off.
    private final DataSlot inputSide = addDataSlot(DataSlot.standalone());
    private final DataSlot outputSide = addDataSlot(DataSlot.standalone());

    /** Opened on the client from the position the server sent. */
    public SplitterMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf data) {
        this(containerId, inventory, data.readBlockPos());
    }

    public SplitterMenu(int containerId, Inventory inventory, BlockPos half) {
        super(MenuContent.SPLITTER.get(), containerId);
        this.half = half;
        player = inventory.player;
        inputSide.set(Splitter.Priority.LEFT.ordinal());
        outputSide.set(Splitter.Priority.LEFT.ordinal());
        addSlot(new FilterSlot(filter, 205, 50));
        addStandardInventorySlots(inventory, 35, 94);
        refresh();
    }

    public BlockPos half() {
        return half;
    }

    public Splitter.Priority inputPriority() {
        return priority(inputPriority);
    }

    public Splitter.Priority outputPriority() {
        return priority(outputPriority);
    }

    /** The side the input switch shows, never none. */
    public Splitter.Priority inputSide() {
        return priority(inputSide);
    }

    public Splitter.Priority outputSide() {
        return priority(outputSide);
    }

    public ItemStack filter() {
        return filter.getItem(0);
    }

    private static Splitter.Priority priority(DataSlot slot) {
        var values = Splitter.Priority.values();
        return values[Math.floorMod(slot.get(), values.length)];
    }

    private @Nullable BeltEndBlockEntity splitter(Player who) {
        var level = who.level();
        if (!level.hasChunkAt(half) || !(level.getBlockState(half).getBlock() instanceof SplitterBlock)) return null;
        return level.getBlockEntity(half, BlockEntitiesContent.BELT_END.get()).orElse(null);
    }

    // Reads the splitter's record into the slots, which the next broadcast sends to the client.
    private void refresh() {
        var entity = splitter(player);
        if (entity == null) return;
        var settings = entity.splitterSettings();
        inputPriority.set(settings.inputPriority().ordinal());
        outputPriority.set(settings.outputPriority().ordinal());
        if (settings.inputPriority() != Splitter.Priority.NONE) inputSide.set(settings.inputPriority().ordinal());
        if (settings.outputPriority() != Splitter.Priority.NONE) outputSide.set(settings.outputPriority().ordinal());
        if (!ItemStack.matches(filter(), settings.filter())) filter.setItem(0, settings.filter().copy());
    }

    @Override
    public void broadcastChanges() {
        if (!player.level().isClientSide()) refresh();
        super.broadcastChanges();
    }

    /**
     * Sets the splitter's filter to a copy of one of the stack, or clears it with an empty stack. The
     * stack is only read: nothing is taken from anyone. A filter set while output priority is off
     * turns it on, on the side its switch shows. On the server only.
     */
    public void setFilter(ItemStack stack) {
        if (player.level().isClientSide()) return;
        var entity = splitter(player);
        if (entity == null) return;
        entity.setSplitterFilter(stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1), outputSide());
        refresh();
    }

    @Override
    public boolean clickMenuButton(Player who, int id) {
        if (who.level().isClientSide()) return false;
        var entity = splitter(who);
        if (entity == null) return false;
        switch (id) {
            case INPUT_TOGGLE -> entity.setInputPriority(inputPriority() == Splitter.Priority.NONE ? inputSide() : Splitter.Priority.NONE);
            case INPUT_FLIP -> {
                inputSide.set(opposite(inputSide()).ordinal());
                if (inputPriority() != Splitter.Priority.NONE) entity.setInputPriority(inputSide());
            }
            case OUTPUT_TOGGLE -> entity.setOutputPriority(outputPriority() == Splitter.Priority.NONE ? outputSide() : Splitter.Priority.NONE);
            case OUTPUT_FLIP -> {
                outputSide.set(opposite(outputSide()).ordinal());
                if (outputPriority() != Splitter.Priority.NONE) entity.setOutputPriority(outputSide());
            }
            default -> {
                return false;
            }
        }
        refresh();
        return true;
    }

    private static Splitter.Priority opposite(Splitter.Priority side) {
        return side == Splitter.Priority.LEFT ? Splitter.Priority.RIGHT : Splitter.Priority.LEFT;
    }

    /**
     * A click on the ghost slot sets the filter from the stack on the cursor, or clears it with an
     * empty cursor, and changes neither stack. Every other input on the slot does nothing. The
     * client does nothing at all: the server's answer arrives as the slot's contents.
     */
    @Override
    public void clicked(int slotId, int button, ContainerInput input, Player who) {
        if (slotId != FILTER_SLOT) {
            super.clicked(slotId, button, input, who);
            return;
        }
        if (input == ContainerInput.PICKUP && (button == 0 || button == 1)) setFilter(getCarried());
    }

    // Nothing shift-clicks into the ghost slot, and a splitter holds no items.
    @Override
    public ItemStack quickMoveStack(Player who, int index) {
        return ItemStack.EMPTY;
    }

    /** Valid while the splitter stands and the player is within reach of the half the menu was opened on. */
    @Override
    public boolean stillValid(Player who) {
        return splitter(who) != null && who.isWithinBlockInteractionRange(half, 1);
    }

    @Override
    public boolean canDragTo(Slot slot) {
        return !(slot instanceof FilterSlot) && super.canDragTo(slot);
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return !(slot instanceof FilterSlot) && super.canTakeItemForPickAll(stack, slot);
    }

    /** The filter shown, which the player can neither put into nor take out of. */
    private static final class FilterSlot extends Slot {

        FilterSlot(Container container, int x, int y) {
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
