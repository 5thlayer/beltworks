// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import io.github._5thlayer.beltworks.blocks.BeltEndBlockEntity;

/**
 * Keeps every loader in a test's structure charged each tick, as a creative energy source would.
 * The Mod ships no energy source (ADR 0002), so tests of tiers 2 to 4 are fed by hand. Splitters
 * share the loaders' block entity and are topped up too, which changes nothing: they draw no power.
 */
final class LoaderPower {

    private boolean on = true;

    private LoaderPower() {
    }

    static LoaderPower feed(GameTestHelper helper) {
        var power = new LoaderPower();
        helper.onEachTick(() -> {
            if (!power.on) return;
            BlockPos.betweenClosedStream(helper.getBounds()).forEach(pos -> {
                if (helper.getLevel().getBlockEntity(pos) instanceof BeltEndBlockEntity end) end.getEnergy().insertFe(Long.MAX_VALUE);
            });
        });
        return power;
    }

    /** Cuts the supply: loaders keep what they hold and draw it down. */
    void stop() {
        on = false;
    }
}
