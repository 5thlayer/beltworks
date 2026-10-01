// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.items;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * What a placement that takes blocks' places hands back for the one item it spends from the held
 * stack, by the rules Groundworks' Fast Replace and Stretch keep in a class of their own (#94): what
 * comes back goes into the slot the spending freed, the held one when it took its last item, and
 * otherwise onto a stack of its own, then into the first empty slot of the main inventory. A
 * player with infinite materials spends nothing and is handed nothing.
 */
final class HandBack {

    private HandBack() {
    }

    /**
     * Whether {@code back} fits once the held item is spent, as {@link #give} puts it. Proved in the
     * main inventory and the held slot alone, so it may refuse what the off hand would take but
     * never passes what would drop.
     */
    static boolean fits(@Nullable Player player, InteractionHand hand, List<ItemStack> back) {
        if (back.isEmpty() || player == null || player.hasInfiniteMaterials()) return true;
        var inventory = player.getInventory();
        var slots = new ArrayList<ItemStack>();
        for (var slot : inventory.getNonEquipmentItems()) slots.add(slot.copy());
        // The held slot, the selected one of the main inventory or the off hand after it.
        var held = hand == InteractionHand.MAIN_HAND ? inventory.getSelectedSlot() : slots.size();
        if (held == slots.size()) slots.add(player.getItemInHand(hand).copy());
        slots.get(held).shrink(1);
        var freed = slots.get(held).isEmpty();
        for (var stack : back) {
            var rest = stack.copy();
            if (freed && slots.get(held).isEmpty()) slots.set(held, rest.split(rest.getMaxStackSize()));
            for (var slot : slots) {
                if (slot.isEmpty() || !ItemStack.isSameItemSameComponents(slot, rest)) continue;
                var moved = Math.max(0, Math.min(rest.getCount(), slot.getMaxStackSize() - slot.getCount()));
                slot.grow(moved);
                rest.shrink(moved);
            }
            for (var at = 0; at < inventory.getNonEquipmentItems().size() && !rest.isEmpty(); at++) {
                if (slots.get(at).isEmpty()) slots.set(at, rest.split(rest.getMaxStackSize()));
            }
            if (!rest.isEmpty()) return false;
        }
        return true;
    }

    /** Hands {@code back} to the player once the held item is spent, after {@link #fits} said it fits. */
    static void give(@Nullable Player player, InteractionHand hand, List<ItemStack> back) {
        if (player == null || player.hasInfiniteMaterials()) return;
        for (var stack : back) {
            if (player.getItemInHand(hand).isEmpty()) player.setItemInHand(hand, stack.copy());
            else player.getInventory().placeItemBackInInventory(stack.copy());
        }
    }
}
