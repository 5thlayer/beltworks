// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    @ParameterizedTest
    @EnumSource(BeltTier.class)
    void byDefaultEachTierPaysItsInsertersSwing(BeltTier tier) {
        assertEquals(tier.loaderJoulesPerItem(), LoaderEnergy.Setting.DEFAULT.loaderJoules(tier));
    }

    @Test
    void byDefaultATierOneLoaderPaysNothing() {
        assertEquals(0, LoaderEnergy.Setting.DEFAULT.loaderJoules(BeltTier.BELT));
    }

    @Test
    void aTierOneLoaderSetAboveZeroPaysEachItem() {
        var energy = new LoaderEnergy(BeltTier.BELT, new LoaderEnergy.Setting(true, 100, Map.of(), Map.of(BeltTier.BELT, 2500L)));

        assertTrue(energy.powered());
        assertFalse(energy.canMove());
        energy.insertFe(Long.MAX_VALUE);
        var before = energy.joules();
        energy.move();
        assertEquals(2500, before - energy.joules());
    }

    @Test
    void aTierOneLoaderSetAboveZeroHasNoDrain() {
        var energy = new LoaderEnergy(BeltTier.BELT, new LoaderEnergy.Setting(true, 100, Map.of(), Map.of(BeltTier.BELT, 2500L)));
        energy.insertFe(Long.MAX_VALUE);
        var before = energy.joules();

        for (int tick = 0; tick < 20; tick++) energy.drain();

        assertEquals(before, energy.joules());
    }

    @ParameterizedTest
    @EnumSource(BeltTier.class)
    void aTierSetToZeroRunsUnpowered(BeltTier tier) {
        var energy = new LoaderEnergy(tier, new LoaderEnergy.Setting(true, 100, Map.of(), Map.of(tier, 0L)));

        assertFalse(energy.powered());
        assertTrue(energy.canMove());
        energy.move();
        energy.drain();
        assertEquals(0, energy.joules());
        assertEquals(0, energy.insertFe(100));
        assertEquals(0, energy.capacityFe());
    }

    @Test
    void aTierSetAboveZeroRunsUnpoweredWhenLoadersNeedNoPower() {
        var energy = new LoaderEnergy(BeltTier.BELT, new LoaderEnergy.Setting(false, 100, Map.of(), Map.of(BeltTier.BELT, 2500L)));

        assertFalse(energy.powered());
        assertTrue(energy.canMove());
    }

    @Test
    void aTiersLoaderPaysWhatTheSettingSaysAndTheDefaultForTheRest() {
        var setting = new LoaderEnergy.Setting(true, 100, Map.of(), Map.of(BeltTier.TURBO, 500L));

        assertEquals(500, setting.loaderJoules(BeltTier.TURBO));
        assertEquals(BeltTier.EXPRESS.loaderJoulesPerItem(), setting.loaderJoules(BeltTier.EXPRESS));
    }

    @Test
    void aSettingRefusesALoaderThatPaysLessThanNothing() {
        assertThrows(IllegalArgumentException.class,
          () -> new LoaderEnergy.Setting(true, 100, Map.of(), Map.of(BeltTier.IMPROVED, -1L)));
    }

    @Test
    void aSettingRefusesALoaderThatPaysMoreThanTheMost() {
        assertThrows(IllegalArgumentException.class, () -> new LoaderEnergy.Setting(true, 100, Map.of(),
          Map.of(BeltTier.IMPROVED, LoaderEnergy.Setting.MAX_JOULES_PER_ITEM + 1)));
    }

    @ParameterizedTest
    @EnumSource(BeltTier.class)
    void aLoaderPayingTheMostStillFillsAndMoves(BeltTier tier) {
        var most = LoaderEnergy.Setting.MAX_JOULES_PER_ITEM;
        var energy = new LoaderEnergy(tier, new LoaderEnergy.Setting(true, 1, Map.of(), Map.of(tier, most)));

        energy.insertFe(Long.MAX_VALUE);
        assertTrue(energy.canMove());
        var before = energy.joules();
        energy.move();
        assertEquals(most, before - energy.joules());
    }

    private static LoaderEnergy full(BeltTier tier) {
        var energy = new LoaderEnergy(tier, POWERED);
        energy.insertFe(Long.MAX_VALUE);
        return energy;
    }
}
