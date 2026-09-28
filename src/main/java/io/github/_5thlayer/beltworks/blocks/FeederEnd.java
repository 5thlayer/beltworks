// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.blocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import io.github._5thlayer.beltworks.api.item.ItemApi;

import java.util.function.Predicate;

/**
 * What a feeder's head takes from or its tail drops into: an inventory, a level tile or a splitter
 * half. A slope or a loader is never one (CONTEXT.md).
 */
sealed interface FeederEnd {

    /** Takes one item {@code wanted} accepts, or null, with how to put it back. */
    @Nullable Taken take(Predicate<ItemStack> wanted);

    /** Whether {@link #put} would take this item now. */
    boolean accepts(ItemStack item);

    /** Puts the item in, answering whether it went. */
    boolean put(ItemStack item);

    /** An item taken, and what puts it back where it was. */
    record Taken(ItemStack item, Runnable undo) {
    }

    /** The end at {@code pos}, reached through its {@code face}, or null where there is none to use. */
    static @Nullable FeederEnd at(Level level, BlockPos pos, Direction face) {
        if (!level.isLoaded(pos)) return null;
        var blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof BeltTileBlockEntity tile) return new Tile(tile);
        if (blockEntity instanceof BeltEndBlockEntity end) return end.isSplitter() ? new Half(end) : null;
        var storage = ItemApi.BLOCK.find(level, pos, null, null, face);
        return storage == null ? null : new Inventory(storage);
    }

    record Inventory(ItemApi.InventoryStorage storage) implements FeederEnd {

        @Override
        public @Nullable Taken take(Predicate<ItemStack> wanted) {
            for (int slot = 0; slot < storage.getSlotCount(); slot++) {
                var available = storage.getStackInSlot(slot);
                if (available.isEmpty()) continue;
                var one = available.copyWithCount(1);
                if (!wanted.test(one) || storage.extract(one, true) != 1) continue;
                if (storage.extract(one, false) != 1) continue;
                return new Taken(one, () -> storage.insert(one, false));
            }
            return null;
        }

        @Override
        public boolean accepts(ItemStack item) {
            return storage.insert(item, true) == item.getCount();
        }

        @Override
        public boolean put(ItemStack item) {
            return storage.insert(item, false) == item.getCount();
        }
    }

    record Tile(BeltTileBlockEntity tile) implements FeederEnd {

        @Override
        public @Nullable Taken take(Predicate<ItemStack> wanted) {
            var taken = tile.feederTake(wanted);
            return taken == null ? null : new Taken(taken.payload(), () -> tile.feederPutBack(taken));
        }

        @Override
        public boolean accepts(ItemStack item) {
            return tile.feederCanDrop();
        }

        @Override
        public boolean put(ItemStack item) {
            return tile.feederDrop(item);
        }
    }

    record Half(BeltEndBlockEntity half) implements FeederEnd {

        @Override
        public @Nullable Taken take(Predicate<ItemStack> wanted) {
            var taken = half.feederTake(wanted);
            return taken == null ? null : new Taken(taken.entry().payload(), () -> half.feederPutBack(taken));
        }

        @Override
        public boolean accepts(ItemStack item) {
            return half.feederCanDrop();
        }

        @Override
        public boolean put(ItemStack item) {
            return half.feederDrop(item);
        }
    }
}
