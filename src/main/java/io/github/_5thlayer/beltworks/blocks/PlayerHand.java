// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.blocks;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.function.Predicate;

/** The world's side of a belt hand's holder, shared by tiles and splitter halves. */
final class PlayerHand {

    private PlayerHand() {
    }

    /** Whether a holder can still hold a hand in this level. */
    static Predicate<ServerPlayer> alive(Level level) {
        return player -> !player.isRemoved() && player.isAlive() && player.level() == level;
    }

    /** Whether a hand's holder took the item. */
    static boolean intoInventory(ServerPlayer player, ItemStack item) {
        // Asked first: a creative inventory's add answers true when full and voids the item.
        var inventory = player.getInventory();
        return (inventory.getSlotWithRemainingSpace(item) >= 0 || inventory.getFreeSlot() >= 0) && inventory.add(item.copy());
    }
}
