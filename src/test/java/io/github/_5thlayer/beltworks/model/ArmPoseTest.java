// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ArmPoseTest {

    private static final LineScan.Travel NORTH = new LineScan.Travel(0, -1);
    // Turns half a turn in 50 Factorio ticks and stretches a block in 50.
    private static final InserterSwing EVEN = new InserterSwing(0.01, 0, 0.02, 0, 0, 1, 0);

    private static void assertPose(double x, double z, double reach, ArmPose pose) {
        assertEquals(x, pose.x(), 1e-9, "x");
        assertEquals(z, pose.z(), 1e-9, "z");
        assertEquals(reach, pose.reach(), 1e-9, "reach");
    }

    @Test
    void anArmAtRestPointsAtTheHeadAtItsReach() {
        var arms = new FeederArms(2, 1, FeederArms.Turn.STRAIGHT);

        assertPose(0, 1, 2, ArmPose.of(arms, NORTH, EVEN, ArmSwing.Phase.AT_HEAD, 0));
    }

    @Test
    void anArmOverTheTailPointsAtItAtItsReach() {
        var arms = new FeederArms(1, 3, FeederArms.Turn.RIGHT);

        assertPose(1, 0, 3, ArmPose.of(arms, NORTH, EVEN, ArmSwing.Phase.AT_TAIL, 0));
    }

    // Half a turn passes over the feeder's left, a quarter turn the short way to a turned tail.
    @Test
    void anArmSwingsTheShortWayRound() {
        var straight = FeederArms.ADJACENT;
        var left = straight.withTailTurn(FeederArms.Turn.LEFT);
        var right = straight.withTailTurn(FeederArms.Turn.RIGHT);
        var diagonal = Math.sqrt(0.5);

        assertPose(-1, 0, 1, ArmPose.of(straight, NORTH, EVEN, ArmSwing.Phase.TO_TAIL, 25));
        assertPose(-diagonal, diagonal, 1, ArmPose.of(left, NORTH, EVEN, ArmSwing.Phase.TO_TAIL, 12.5));
        assertPose(diagonal, diagonal, 1, ArmPose.of(right, NORTH, EVEN, ArmSwing.Phase.TO_TAIL, 12.5));
    }

    // Each finishes in its own time, as Factorio's inserter turns and stretches at once.
    @Test
    void anArmTurnsAndStretchesEachAtItsOwnSpeed() {
        var arms = new FeederArms(1, 3, FeederArms.Turn.STRAIGHT);

        assertPose(0, -1, 2, ArmPose.of(arms, NORTH, EVEN, ArmSwing.Phase.TO_TAIL, 50));
        assertPose(0, -1, 3, ArmPose.of(arms, NORTH, EVEN, ArmSwing.Phase.TO_TAIL, 100));
    }

    @Test
    void anArmSwingingBackRetracesItsWay() {
        var arms = new FeederArms(1, 3, FeederArms.Turn.STRAIGHT);

        assertPose(0, -1, 3, ArmPose.of(arms, NORTH, EVEN, ArmSwing.Phase.TO_HEAD, 0));
        assertPose(-1, 0, 2.5, ArmPose.of(arms, NORTH, EVEN, ArmSwing.Phase.TO_HEAD, 25));
        assertPose(0, 1, 1, ArmPose.of(arms, NORTH, EVEN, ArmSwing.Phase.TO_HEAD, 100));
    }

    // A client between ticks may be past a half-swing's end before its arm arrives.
    @Test
    void anArmNeverSwingsPastItsEnd() {
        assertPose(0, -1, 1, ArmPose.of(FeederArms.ADJACENT, NORTH, EVEN, ArmSwing.Phase.TO_TAIL, 80));
    }
}
