// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import io.github._5thlayer.beltworks.blocks.CreativeCrateMenu;
import io.github._5thlayer.beltworks.blocks.SplitterMenu;

public class MenuContent {

    public static final DeferredRegister<MenuType<?>> TYPES = DeferredRegister.create(Registries.MENU, Beltworks.MOD_ID);

    // The extra data the server sends on opening is the splitter half's position.
    public static final DeferredHolder<MenuType<?>, MenuType<SplitterMenu>> SPLITTER = TYPES.register(
            "splitter", () -> IMenuTypeExtension.create(SplitterMenu::new));

    // The extra data is the crate's position.
    public static final DeferredHolder<MenuType<?>, MenuType<CreativeCrateMenu>> CREATIVE_CRATE = TYPES.register(
            "creative_crate", () -> IMenuTypeExtension.create(CreativeCrateMenu::new));
}
