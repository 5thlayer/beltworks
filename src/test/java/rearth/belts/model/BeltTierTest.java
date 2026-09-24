// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package rearth.belts.model;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BeltTierTest {

    @ParameterizedTest
    @CsvSource({"1, 15", "2, 30", "3, 45", "4, 60"})
    void eachTierDeliversFactoriosItemsPerSecond(int tier, int itemsPerSecond) {
        var speed = BeltTier.of(tier).blocksPerTick();
        var belt = new BeltContents<String>();
        var delivered = new int[1];

        for (int tick = 0; tick < 20 * 60; tick++) belt.tick(8, speed, () -> "item", item -> true);
        for (int tick = 0; tick < 20 * 60; tick++) {
            belt.tick(8, speed, () -> "item", item -> {
                delivered[0]++;
                return true;
            });
        }

        assertEquals(itemsPerSecond * 60, delivered[0]);
    }

    @ParameterizedTest
    @CsvSource({"1, 1.875", "2, 3.75", "3, 5.625", "4, 7.5"})
    void blocksPerSecondIsFactoriosSpeedTimesSixty(int tier, double blocksPerSecond) {
        assertEquals(blocksPerSecond, BeltTier.of(tier).blocksPerSecond());
    }

    @ParameterizedTest
    @CsvSource({"1, belt_tile", "2, improved_belt_tile", "3, express_belt_tile", "4, turbo_belt_tile"})
    void eachTierNamesItsTile(int tier, String item) {
        assertEquals(item, BeltTier.of(tier).tile());
    }

    @ParameterizedTest
    @CsvSource({"1, chute", "2, improved_chute", "3, express_chute", "4, turbo_chute"})
    void eachTierNamesItsLoader(int tier, String loader) {
        assertEquals(loader, BeltTier.of(tier).loader());
    }

    @ParameterizedTest
    @CsvSource({"1, 15", "2, 30", "3, 45", "4, 60"})
    void aTiersLoaderMovesItsBeltsItemsPerSecond(int tier, double itemsPerSecond) {
        assertEquals(itemsPerSecond, BeltTier.of(tier).itemsPerSecond());
    }

    @ParameterizedTest
    @CsvSource({"0, 1", "5, 4"})
    void anOutOfRangeTierClampsToTheLadder(int saved, int tier) {
        assertEquals(tier, BeltTier.of(saved).number());
    }
}
