// SPDX-FileCopyrightText: Rearth
// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: CC-BY-4.0 AND MIT

package rearth.belts;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ItemGroupContent {
    
    public static final DeferredRegister<CreativeModeTab> GROUPS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Belts.MOD_ID);
    
    // Every item of the Mod's, in the order it registers them.
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> BELTS_GROUP = GROUPS.register("group", () -> CreativeModeTab.builder()
      .title(Component.translatable("itemgroup.belts.items"))
      .icon(() -> new ItemStack(ItemContent.BELT_TILE.get()))
      .displayItems((parameters, output) -> ItemContent.ITEMS.getEntries().forEach(item -> output.accept(item.get())))
      .build());
}
