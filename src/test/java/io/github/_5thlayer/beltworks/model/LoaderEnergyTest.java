// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoaderEnergyTest {

    private static final LoaderEnergy.Setting POWERED = new LoaderEnergy.Setting(true, 100);

    @ParameterizedTest
    @CsvSource({"2, 6650", "3, 8120", "4, 11600"})
    void eachItemCostsTheInsertersSwing(int tier, long joules) {
        var energy = full(BeltTier.of(tier));
        var before = energy.joules();

        energy.move();

        assertEquals(joules, before - energy.joules());
    }

    @ParameterizedTest
    @CsvSource({"2, 400", "3, 500", "4, 1000"})
    void theDrainIsTheInsertersOverTwentyTicks(int tier, long watts) {
        var energy = full(BeltTier.of(tier));
        var before = energy.joules();

        for (int tick = 0; tick < 20; tick++) energy.drain();

        assertEquals(watts, before - energy.joules());
    }

    @Test
    void aShortChargeMovesNothing() {
        var energy = new LoaderEnergy(BeltTier.IMPROVED, POWERED);
        energy.insertFe(66);

        assertFalse(energy.canMove());
        energy.insertFe(1);
        assertTrue(energy.canMove());
    }

    @Test
    void theDrainStopsAtZero() {
        var energy = new LoaderEnergy(BeltTier.IMPROVED, POWERED);
        energy.insertFe(1);

        for (int tick = 0; tick < 10; tick++) energy.drain();

        assertEquals(0, energy.joules());
    }

    @Test
    void aTierOneLoaderMovesUnpowered() {
        var energy = new LoaderEnergy(BeltTier.BELT, POWERED);

        assertFalse(energy.powered());
        assertTrue(energy.canMove());
        energy.move();
        energy.drain();
        assertEquals(0, energy.joules());
        assertEquals(0, energy.insertFe(100));
    }

    @ParameterizedTest
    @CsvSource({"2, 200", "3, 326", "4, 465"})
    void theBufferHoldsTheLargestTickTheFlowLimitAllows(int tier, long fe) {
        var energy = new LoaderEnergy(BeltTier.of(tier), POWERED);

        assertEquals(fe, energy.capacityFe());
        assertEquals(fe, energy.insertFe(Long.MAX_VALUE));
        assertEquals(0, energy.insertFe(1));
    }

    @Test
    void storedFeRoundsAPartSpentUnitDown() {
        var energy = full(BeltTier.IMPROVED);

        energy.move();

        assertEquals(200 - 67, energy.storedFe());
    }

    @ParameterizedTest
    @CsvSource({"1", "2", "3", "4"})
    void byDefaultNoLoaderNeedsPower(int tier) {
        var energy = new LoaderEnergy(BeltTier.of(tier), LoaderEnergy.Setting.DEFAULT);

        assertFalse(energy.powered());
        assertTrue(energy.canMove());
        energy.move();
        energy.drain();
        assertEquals(0, energy.joules());
        assertEquals(0, energy.insertFe(100));
        assertEquals(0, energy.capacityFe());
    }

    @Test
    void anItemCostsItsJoulesAtTheSettingsRate() {
        var energy = new LoaderEnergy(BeltTier.IMPROVED, new LoaderEnergy.Setting(true, 50));
        energy.insertFe(132);

        assertFalse(energy.canMove());
        energy.insertFe(1);
        assertTrue(energy.canMove());
        assertEquals(133 * 50, energy.joules());
    }

    @Test
    void theBufferIsCountedInTheSettingsFe() {
        var energy = new LoaderEnergy(BeltTier.IMPROVED, new LoaderEnergy.Setting(true, 50));

        assertEquals(400, energy.capacityFe());
    }

    private static LoaderEnergy full(BeltTier tier) {
        var energy = new LoaderEnergy(tier, POWERED);
        energy.insertFe(Long.MAX_VALUE);
        return energy;
    }
}
