// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.model;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeederTest {

    private static final LoaderEnergy.Setting LOADERS_UNPOWERED = new LoaderEnergy.Setting(false, 100);
    private static final double SPACING = BeltContents.SPACING;

    @Test
    void aTierOneFeederMovesATenthOfItsLoader() {
        assertEquals(1.5, BeltTier.BELT.feederItemsPerSecond(), 1e-9);
    }

    @Test
    void aTierOneFeederPaysEveryItemWhenLoadersNeedNoPower() {
        var energy = LoaderEnergy.feeder(BeltTier.BELT, LOADERS_UNPOWERED);

        assertTrue(energy.powered());
        assertFalse(energy.canMove());
        energy.insertFe(Long.MAX_VALUE);
        var before = energy.joules();
        energy.move();

        assertEquals(2 * BeltTier.IMPROVED.loaderJoulesPerItem(), before - energy.joules());
    }

    @Test
    void aFeederHasNoIdleDrain() {
        var energy = LoaderEnergy.feeder(BeltTier.BELT, LOADERS_UNPOWERED);
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
}
