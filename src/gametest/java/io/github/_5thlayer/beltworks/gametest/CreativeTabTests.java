// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.gametest;

import java.util.List;

import io.github._5thlayer.beltworks.Beltworks;
import io.github._5thlayer.libworks.testkit.WorksTabCheck;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;

/** The Mod's items show in the suite's shared Works tab, in order and in no other tab, and it keeps no tab of its own (5thlayer/beltworks#97). */
final class CreativeTabTests {

    private static final List<String> WORKS_TAB_ORDER = List.of(
            "loader", "improved_loader", "express_loader", "turbo_loader",
            "belt_tile", "improved_belt_tile", "express_belt_tile", "turbo_belt_tile",
            "splitter", "improved_splitter", "express_splitter", "turbo_splitter",
            "feeder", "improved_feeder", "express_feeder", "turbo_feeder");

    private CreativeTabTests() {
    }

    static void register(BeltGameTests.Registrar tests) {
        tests.test("every_item_shows_in_the_works_tab_in_order", 1,
                helper -> WorksTabCheck.inOrder(helper, Beltworks.MOD_ID, WORKS_TAB_ORDER));
        tests.test("no_other_tab_holds_a_beltworks_item", 1,
                helper -> WorksTabCheck.noOtherTab(helper, Beltworks.MOD_ID));
        tests.test("the_mod_registers_no_creative_tab", 1, CreativeTabTests::noOwnTab);
    }

    private static void noOwnTab(GameTestHelper helper) {
        var own = BuiltInRegistries.CREATIVE_MODE_TAB.keySet().stream()
                .filter(id -> id.getNamespace().equals(Beltworks.MOD_ID))
                .toList();
        if (!own.isEmpty()) {
            helper.fail("the Mod registers creative tabs " + own);
            return;
        }
        helper.succeed();
    }
}
