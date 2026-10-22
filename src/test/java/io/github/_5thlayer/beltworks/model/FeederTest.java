// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeederTest {

    private static final LoaderEnergy.Setting LOADERS_UNPOWERED = new LoaderEnergy.Setting(false, 100);
    private static final LoaderEnergy.Setting LOADERS_POWERED = new LoaderEnergy.Setting(true, 100);
    private static final double SPACING = BeltContents.SPACING;

    @ParameterizedTest
    @CsvSource({"1, feeder", "2, improved_feeder", "3, express_feeder", "4, turbo_feeder"})
    void eachTierNamesItsFeeder(int tier, String feeder) {
        assertEquals(feeder, BeltTier.of(tier).feeder());
    }

    @ParameterizedTest
    @CsvSource({"2", "3", "4"})
    void aFeederAboveTierOnePaysOnlyWhenLoadersNeedPower(int tier) {
        assertTrue(LoaderEnergy.feeder(BeltTier.of(tier), LOADERS_POWERED).powered());
        assertFalse(LoaderEnergy.feeder(BeltTier.of(tier), LOADERS_UNPOWERED).powered());
    }

    @Test
    void aTierOneFeederStartsUnpoweredEvenWhenLoadersNeedPower() {
        var energy = LoaderEnergy.feeder(BeltTier.BELT, LOADERS_POWERED);

        assertEquals(0, BeltTier.BELT.feederJoulesPerItem());
        assertFalse(energy.powered());
        assertEquals(1, energy.affordable(1));
    }

    @ParameterizedTest
    @EnumSource(BeltTier.class)
    void anUnfedFeederMovesForFreeWhenLoadersNeedNoPower(BeltTier tier) {
        var energy = LoaderEnergy.feeder(tier, LOADERS_UNPOWERED);

        assertTrue(energy.canMove());
        energy.move();
        assertTrue(energy.canMove());
        assertEquals(0, energy.capacityFe());
    }

    @Test
    void aTierOneFeederSetAboveZeroPaysEveryItemItsHandGrabs() {
        var energy = LoaderEnergy.feeder(BeltTier.BELT, new LoaderEnergy.Setting(true, 100, Map.of(BeltTier.BELT, 13300L)));

        assertTrue(energy.powered());
        assertEquals(0, energy.affordable(1));
        energy.insertFe(Long.MAX_VALUE);
        var before = energy.joules();
        energy.pay(energy.affordable(1));

        assertEquals(13300, before - energy.joules());
    }

    @ParameterizedTest
    @CsvSource({"2", "3", "4"})
    void aFullFeederAffordsAWholeHand(int tier) {
        var of = BeltTier.of(tier);
        var energy = LoaderEnergy.feeder(of, LOADERS_POWERED);
        energy.insertFe(Long.MAX_VALUE);

        assertEquals(of.feederSwing().handSize(), energy.affordable(of.feederSwing().handSize()));
    }

    // Tier 3 pays 162.4 FE an item: each grab leaves a fraction of an FE, which must never starve the next fill.
    @ParameterizedTest
    @CsvSource({"2", "3", "4"})
    void aFedFeederRefillsToAWholeHandWhateverFractionItsGrabsLeave(int tier) {
        var of = BeltTier.of(tier);
        var hand = of.feederSwing().handSize();
        var energy = LoaderEnergy.feeder(of, LOADERS_POWERED);

        for (int grab = 0; grab < 200; grab++) {
            energy.insertFe(Long.MAX_VALUE);
            assertEquals(hand, energy.affordable(hand));
            energy.pay(hand);
        }
    }

    // A tier-4 hand holds two, so its buffer holds two items' worth and a grab pays for what it took.
    @Test
    void aFedFeederAffordsUpToAHandAndPaysForWhatItGrabbed() {
        var energy = LoaderEnergy.feeder(BeltTier.TURBO, LOADERS_POWERED);
        energy.insertFe(Long.MAX_VALUE);

        assertEquals(2, energy.affordable(2));
        energy.pay(1);
        assertEquals(1, energy.affordable(2));
        energy.pay(1);
        assertEquals(0, energy.affordable(2));
    }

    @Test
    void aFeederHasNoIdleDrain() {
        var energy = LoaderEnergy.feeder(BeltTier.IMPROVED, LOADERS_POWERED);
        energy.insertFe(Long.MAX_VALUE);
        var before = energy.joules();

        for (int tick = 0; tick < 20; tick++) energy.drain();

        assertEquals(before, energy.joules());
    }

    @Test
    void aHeadTakesFromTheMiddleOfALineAndTheLineRunsOn() {
        var line = backedUp(3);
        var taken = line.take(1, item -> true);

        assertNotNull(taken);
        assertTrue(taken.position() >= 1 && taken.position() < 2);
        assertEquals(23, line.size());
        var delivered = new int[1];
        for (int tick = 0; tick < 5; tick++) line.tick(() -> null, item -> ++delivered[0] > 0);
        assertTrue(delivered[0] > 0);
    }

    @Test
    void aHeadTakesOnlyWhatTheTailWants() {
        var line = new TransportLine<String>(List.of(BeltTier.BELT, BeltTier.BELT));
        line.contents().restore("iron", 0.5);
        line.contents().restore("coal", 0.75);

        assertNull(line.take(1, item -> true));
        var taken = line.take(0, item -> item.equals("iron"));

        assertNotNull(taken);
        assertEquals("iron", taken.payload());
        assertEquals(1, line.size());
    }

    @Test
    void aTailDropsAtTheTilesMidpoint() {
        var line = new TransportLine<String>(List.of(BeltTier.BELT, BeltTier.BELT, BeltTier.BELT));

        assertTrue(line.drop("item", 1));

        assertEquals(1.5 - SPACING / 2, line.contents().entries().getFirst().position(), 1e-9);
    }

    @Test
    void aTailWaitsWhileTheLineHasNoGapAtTheMidpoint() {
        var line = backedUp(3);

        assertFalse(line.canDrop(1));
        assertFalse(line.drop("item", 1));
        assertEquals(24, line.size());
    }

    @Test
    void aTailDropsIntoAOneItemGapAsItCrossesTheMidpoint() {
        var line = new TransportLine<String>(List.of(BeltTier.BELT, BeltTier.BELT, BeltTier.BELT));
        for (int slot = 0; slot < 24; slot++) if (slot != 2) line.contents().restore("item", slot * SPACING);

        var dropped = false;
        for (int tick = 0; tick < 4 && !dropped; tick++) {
            line.tick(() -> null, item -> true);
            dropped = line.drop("dropped", 0);
        }

        assertTrue(dropped);
        var positions = line.contents().entries().stream().mapToDouble(BeltContents.Entry::position).toArray();
        for (int at = 1; at < positions.length; at++) assertTrue(positions[at] - positions[at - 1] >= SPACING - 1e-9);
    }

    @Test
    void aDropNeverLooksForRoomOutsideItsWindow() {
        var belt = new BeltContents<String>();
        belt.restore("item", 0);
        belt.restore("item", SPACING);

        assertTrue(Double.isNaN(belt.dropPlacement(0, 0, SPACING / 2, 0.5, false)));
        assertEquals(2 * SPACING, belt.dropPlacement(2 * SPACING, 0, 0.5, 0.5, false), 1e-9);
    }

    @Test
    void anItemPutBackReturnsWhereItWas() {
        var line = new TransportLine<String>(List.of(BeltTier.BELT, BeltTier.BELT));
        line.contents().restore("item", 0.25);
        line.contents().restore("item", 0.75);
        var taken = line.take(0, item -> true);

        line.contents().place(taken.payload(), taken.position());

        assertEquals(List.of(0.25, 0.75), line.contents().entries().stream().map(BeltContents.Entry::position).toList());
    }

    private static TransportLine<String> backedUp(int tiles) {
        var line = new TransportLine<String>(Collections.nCopies(tiles, BeltTier.BELT));
        for (int tick = 0; tick < 20 * 20; tick++) line.tick(() -> "item", item -> false);
        return line;
    }

    @Test
    void aTiersFeederPaysWhatTheSettingSaysAndTheDefaultForTheRest() {
        var setting = new LoaderEnergy.Setting(true, 100, Map.of(BeltTier.TURBO, 500L));

        assertEquals(500, setting.feederJoules(BeltTier.TURBO));
        assertEquals(BeltTier.BELT.feederJoulesPerItem(), setting.feederJoules(BeltTier.BELT));
        assertEquals(BeltTier.EXPRESS.feederJoulesPerItem(), LoaderEnergy.Setting.DEFAULT.feederJoules(BeltTier.EXPRESS));

        var energy = LoaderEnergy.feeder(BeltTier.TURBO, setting);
        energy.insertFe(Long.MAX_VALUE);
        var before = energy.joules();
        energy.move();
        assertEquals(500, before - energy.joules());
    }

    @Test
    void aFeederSetToZeroAtAnyTierIsUnpowered() {
        var setting = new LoaderEnergy.Setting(true, 100, Map.of(BeltTier.TURBO, 0L));

        assertFalse(LoaderEnergy.feeder(BeltTier.TURBO, setting).powered());
        assertEquals(0, LoaderEnergy.feeder(BeltTier.TURBO, setting).capacityFe());
    }

    @Test
    void aSettingRefusesAFeederThatPaysLessThanNothing() {
        assertThrows(IllegalArgumentException.class,
          () -> new LoaderEnergy.Setting(false, 100, Map.of(BeltTier.IMPROVED, -1L)));
    }

    @Test
    void aSettingRefusesAFeederThatPaysMoreThanTheMost() {
        assertThrows(IllegalArgumentException.class, () -> new LoaderEnergy.Setting(false, 100,
          Map.of(BeltTier.IMPROVED, LoaderEnergy.Setting.MAX_JOULES_PER_ITEM + 1)));
    }

    @ParameterizedTest
    @EnumSource(BeltTier.class)
    void aFeederPayingTheMostStillFillsAndMoves(BeltTier tier) {
        var most = LoaderEnergy.Setting.MAX_JOULES_PER_ITEM;
        var energy = LoaderEnergy.feeder(tier, new LoaderEnergy.Setting(true, 1, Map.of(tier, most)));

        assertTrue(energy.capacityFe() > 0);
        energy.insertFe(Long.MAX_VALUE);
        assertTrue(energy.canMove());
        var before = energy.joules();
        energy.move();
        assertEquals(most, before - energy.joules());
    }
}
