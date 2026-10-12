// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.blocks;

import java.util.HashSet;
import java.util.function.Predicate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jetbrains.annotations.Nullable;

/**
 * The loader's and feeder's door to another block's items (ADR 0008): NeoForge's item handler on the
 * face that touches it, looked up afresh for every move, moved only by untargeted inserts and
 * extracts. Each move happens inside the caller's transaction and is kept there only when it moved
 * the whole amount, so the caller commits what really moved or nothing.
 */
final class Inventories {

    private Inventories() {
    }

    /** The item handler of the block at {@code pos}, through its {@code face}, or null where it has none. */
    static @Nullable ResourceHandler<ItemResource> at(Level level, BlockPos pos, Direction face) {
        return level.getCapability(Capabilities.Item.BLOCK, pos, face);
    }

    /** Inserts the whole stack into {@code handler} within {@code transaction}, answering whether it went; none of it stays otherwise. */
    static boolean insertAll(ResourceHandler<ItemResource> handler, ItemStack item, TransactionContext transaction) {
        if (item.isEmpty()) return false;
        try (var move = Transaction.open(transaction)) {
            if (handler.insert(ItemResource.of(item), item.getCount(), move) != item.getCount()) return false;
            move.commit();
            return true;
        }
    }

    /**
     * Extracts one item {@code wanted} accepts from {@code handler} within {@code transaction}, or
     * answers null. What the handler holds only chooses what to try: each kind it holds is tried once,
     * and what the extract really took decides.
     */
    static @Nullable ItemStack extractOne(ResourceHandler<ItemResource> handler, Predicate<ItemStack> wanted,
                                          TransactionContext transaction) {
        var tried = new HashSet<ItemResource>();
        for (int index = 0; index < handler.size(); index++) {
            var resource = handler.getResource(index);
            if (resource.isEmpty() || !tried.add(resource)) continue;
            var one = resource.toStack(1);
            if (!wanted.test(one)) continue;
            try (var move = Transaction.open(transaction)) {
                if (handler.extract(resource, 1, move) != 1) continue;
                move.commit();
                return one;
            }
        }
        return null;
    }
}
