// SPDX-FileCopyrightText: Rearth
// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: CC-BY-4.0 AND MIT

package io.github._5thlayer.beltworks.blocks;

import dev.ftb.mods.ftbfiltersystem.api.FTBFilterSystemAPI;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.fml.ModList;

/**
 * The item a loader or a feeder moves and nothing else: set by clicking it with the item, cleared
 * by clicking it empty-handed, and standing for several when it is a smart filter from the
 * 'FTB Filters' mod. An empty filter matches everything.
 */
public final class ItemFilter {

    private ItemStack item = ItemStack.EMPTY;

    public ItemStack item() {
        return item;
    }

    public boolean matches(Level level, ItemStack stack) {
        if (item.isEmpty()) return true;

        if (ModList.get().isLoaded("ftbfiltersystem")) {
            var filterAPI = FTBFilterSystemAPI.api();
            if (filterAPI.isFilterItem(item))
                return filterAPI.doesFilterMatch(item, stack, level.registryAccess());
        }

        return stack.getItem().equals(item.getItem());
    }

    /** Sets the filter to the stack, or clears it when the stack is empty, and tells the player. */
    public void assign(BlockEntity owner, ItemStack stack, Player player) {
        if (stack.isEmpty()) {
            reset(owner, player);
            return;
        }

        player.sendSystemMessage(Component.translatable("message.beltworks.filter_set"));
        item = stack.copy();
        changed(owner);
    }

    public void reset(BlockEntity owner, Player player) {
        player.sendSystemMessage(Component.translatable("message.beltworks.filter_reset"));
        item = ItemStack.EMPTY;
        changed(owner);
    }

    private static void changed(BlockEntity owner) {
        owner.setChanged();
        if (owner.getLevel() instanceof ServerLevel serverWorld)
            serverWorld.sendBlockUpdated(owner.getBlockPos(), owner.getBlockState(), owner.getBlockState(), Block.UPDATE_ALL);
    }

    public void save(ValueOutput output) {
        output.store("filter", ItemStack.OPTIONAL_CODEC, item);
    }

    public void load(ValueInput input) {
        item = input.read("filter", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
    }
}
