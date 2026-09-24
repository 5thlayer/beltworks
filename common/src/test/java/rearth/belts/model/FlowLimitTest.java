// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package rearth.belts.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlowLimitTest {

    @ParameterizedTest
    @CsvSource({"1, 15", "2, 30", "3, 45", "4, 60"})
    void aGreedyCallerPassesTheTiersItemsPerSecond(int tier, int itemsPerSecond) {
        var limit = new FlowLimit(BeltTier.of(tier).itemsPerTick());

        var passed = 0;
        for (long tick = 0; tick < 20 * 60; tick++) {
            while (limit.ready(tick)) {
                limit.pass();
                passed++;
            }
        }

        assertEquals(itemsPerSecond * 60, passed, 1);
    }

    @Test
    void severalItemsPassInOneTickAboveTwentyASecond() {
        var limit = new FlowLimit(BeltTier.TURBO.itemsPerTick());
        limit.ready(0);

        var passed = 0;
        while (limit.ready(1)) {
            limit.pass();
            passed++;
        }

        assertTrue(passed >= 3, "passed " + passed);
    }

    @Test
    void anIdleLimitDoesNotBankABurst() {
        var limit = new FlowLimit(BeltTier.BELT.itemsPerTick());
        limit.ready(0);

        var passed = 0;
        for (long tick = 1000; tick < 1020; tick++) {
            while (limit.ready(tick)) {
                limit.pass();
                passed++;
            }
        }

        assertTrue(passed <= 16, "passed " + passed + " in a second after idling");
    }

    @Test
    void readyDoesNotSpend() {
        var limit = new FlowLimit(BeltTier.BELT.itemsPerTick());
        limit.ready(0);
        limit.ready(1);

        assertTrue(limit.ready(1));
        assertTrue(limit.ready(1));
    }
}
