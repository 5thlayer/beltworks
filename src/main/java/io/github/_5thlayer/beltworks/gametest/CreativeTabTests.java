// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.gametest;

import io.github._5thlayer.beltworks.Beltworks;
import io.github._5thlayer.beltworks.ItemContent;
import io.github._5thlayer.libworks.WorksTab;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.List;

/** The Mod's items show in the suite's shared Works tab, and it keeps no tab of its own (5thlayer/beltworks#97). */
final class CreativeTabTests {

    private CreativeTabTests() {
    }

    static void register(BeltGameTests.Registrar tests) {
        tests.test("every_item_shows_in_the_works_tab_and_the_mod_has_no_tab", 1, CreativeTabTests::worksTab);
    }

    private static void worksTab(GameTestHelper helper) {
        var level = helper.getLevel();
        CreativeModeTabs.tryRebuildTabContents(level.enabledFeatures(), true, level.registryAccess());
        var tabs = BuiltInRegistries.CREATIVE_MODE_TAB;

        var ownTabs = tabs.listElementIds().filter(key -> key.identifier().getNamespace().equals(Beltworks.MOD_ID)).toList();
        helper.assertTrue(ownTabs.isEmpty(), "the Mod registers creative tabs " + ownTabs);

        CreativeModeTab works = tabs.getValueOrThrow(WorksTab.KEY);
        List<Item> missing = new ArrayList<>();
        for (var item : ItemContent.ITEMS.getEntries()) {
            if (works.getDisplayItems().stream().noneMatch(stack -> stack.is(item.get()))) missing.add(item.get());
        }
        helper.assertTrue(missing.isEmpty(), "the Works tab lacks " + missing.stream().map(BuiltInRegistries.ITEM::getKey).toList());
        helper.assertTrue(!ItemContent.ITEMS.getEntries().isEmpty(), "the Mod registers no items");
        helper.succeed();
    }
}
