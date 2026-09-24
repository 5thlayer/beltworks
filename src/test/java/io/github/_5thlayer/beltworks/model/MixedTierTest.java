// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.model;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** A belt, the loader that loads it and the loader that unloads it each cap their own flow (ADR 0007). */
class MixedTierTest {

    @ParameterizedTest
    @CsvSource({
        "1, 1, 1, 15",
        "1, 3, 1, 15",
        "4, 1, 4, 15",
        "1, 4, 4, 15",
        "4, 4, 2, 30",
        "3, 3, 3, 45",
    })
    void aLineRunsAtItsSlowestPiece(int loading, int belt, int unloading, int itemsPerSecond) {
        var contents = new BeltContents<String>();
        var speed = BeltTier.of(belt).blocksPerTick();
        var source = new FlowLimit(BeltTier.of(loading).itemsPerTick());
        var sink = new FlowLimit(BeltTier.of(unloading).itemsPerTick());
        var delivered = new int[1];
        var now = new long[1];

        for (int window = 0; window < 2; window++) {
            delivered[0] = 0;
            for (int tick = 0; tick < 20 * 60; tick++, now[0]++) {
                contents.tick(8, speed, () -> {
                    if (!source.ready(now[0])) return null;
                    source.pass();
                    return "item";
                }, item -> {
                    if (!sink.ready(now[0])) return false;
                    sink.pass();
                    delivered[0]++;
                    return true;
                });
            }
        }

        assertEquals(itemsPerSecond * 60, delivered[0], 1);
    }
}
