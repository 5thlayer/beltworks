// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeederTest {

    private static final LoaderEnergy.Setting LOADERS_UNPOWERED = new LoaderEnergy.Setting(false, 100);

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
}
