// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ArmSwingTest {

    // A feeder's tick: the arm moves, then grabs or drops where it has arrived.
    private static int grabsIn(int ticks, int halfSwing) {
        var arm = new ArmSwing();
        var grabs = 0;
        for (int tick = 0; tick < ticks; tick++) {
            arm.tick(halfSwing);
            if (arm.phase() == ArmSwing.Phase.AT_HEAD) {
                arm.swingToTail();
                grabs++;
            } else if (arm.phase() == ArmSwing.Phase.AT_TAIL) {
                arm.swingToHead();
            }
        }
        return grabs;
    }

    // Each Minecraft tick is three of Factorio's, and what a half-swing overruns carries into the next,
    // so a cycle shorter than a whole number of ticks keeps its rate: 600 Factorio ticks over 26 is 23.
    @ParameterizedTest
    @CsvSource({"50, 6", "36, 9", "13, 23", "7, 43"})
    void anArmThatNeverWaitsGrabsOncePerCycle(int halfSwing, int grabs) {
        assertEquals(grabs, grabsIn(200, halfSwing));
    }

    @Test
    void anIdleArmWaitsOverTheHead() {
        var arm = new ArmSwing();

        for (int tick = 0; tick < 100; tick++) arm.tick(13);

        assertEquals(ArmSwing.Phase.AT_HEAD, arm.phase());
    }

    @Test
    void anArmArrivesAtTheTailOnceItsHalfSwingHasPassed() {
        var arm = new ArmSwing();
        arm.swingToTail();

        for (int tick = 0; tick < 4; tick++) arm.tick(13);
        assertEquals(ArmSwing.Phase.TO_TAIL, arm.phase());
        arm.tick(13);

        assertEquals(ArmSwing.Phase.AT_TAIL, arm.phase());
    }

    // Time spent waiting at an end is not banked towards the next half-swing.
    @Test
    void anArmThatWaitedSwingsTheWholeWay() {
        var arm = new ArmSwing();
        arm.swingToTail();
        for (int tick = 0; tick < 5; tick++) arm.tick(13);
        for (int tick = 0; tick < 20; tick++) arm.tick(13);
        arm.swingToHead();

        for (int tick = 0; tick < 4; tick++) arm.tick(13);
        assertEquals(ArmSwing.Phase.TO_HEAD, arm.phase());
        arm.tick(13);

        assertEquals(ArmSwing.Phase.AT_HEAD, arm.phase());
    }

    @Test
    void aSavedArmLoadsWhereItWas() {
        var arm = new ArmSwing();
        arm.swingToTail();
        arm.tick(13);

        var loaded = ArmSwing.load(arm.phase().name(), arm.elapsed());

        assertEquals(arm.phase(), loaded.phase());
        assertEquals(arm.elapsed(), loaded.elapsed());
    }

    @Test
    void anArmSavedAsNothingItKnowsRestsOverTheHead() {
        var loaded = ArmSwing.load("", 7);

        assertEquals(ArmSwing.Phase.AT_HEAD, loaded.phase());
        assertEquals(0, loaded.elapsed());
    }
}
