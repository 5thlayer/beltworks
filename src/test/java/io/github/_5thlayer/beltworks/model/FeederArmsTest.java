// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FeederArmsTest {

    private static final LineScan.Spot FEEDER = new LineScan.Spot(10, 64, -5);
    private static final LineScan.Travel NORTH = new LineScan.Travel(0, -1);
    private static final LineScan.Travel EAST = new LineScan.Travel(1, 0);
    private static final LineScan.Travel SOUTH = new LineScan.Travel(0, 1);
    private static final LineScan.Travel WEST = new LineScan.Travel(-1, 0);
    // Facing, then the way left and right of it, as a player facing that way sees them.
    private static final List<List<LineScan.Travel>> FACINGS = List.of(
      List.of(NORTH, WEST, EAST), List.of(EAST, NORTH, SOUTH), List.of(SOUTH, EAST, WEST), List.of(WEST, SOUTH, NORTH));

    @Test
    void theHeadReachesBehindTheFacingOnTheFeedersLevel() {
        for (var facing : FACINGS) {
            var ahead = facing.get(0);
            for (int reach = 1; reach <= 3; reach++) {
                var target = new FeederArms(reach, 1, FeederArms.Turn.STRAIGHT).head(FEEDER, ahead);
                assertEquals(FEEDER.step(ahead, -reach), target.spot(), "facing " + ahead + " reach " + reach);
                assertEquals(TileShape.back(ahead), target.pointing());
            }
        }
    }

    @Test
    void theTailReachesAheadLeftOrRightOnTheFeedersLevel() {
        for (var facing : FACINGS) {
            var ahead = facing.get(0);
            var turned = List.of(ahead, facing.get(1), facing.get(2));
            for (var turn : FeederArms.Turn.values()) {
                var way = turned.get(turn.ordinal());
                for (int reach = 1; reach <= 3; reach++) {
                    var target = new FeederArms(1, reach, turn).tail(FEEDER, ahead);
                    var expected = new LineScan.Spot(FEEDER.x() + way.x() * reach, FEEDER.y(), FEEDER.z() + way.z() * reach);
                    assertEquals(expected, target.spot(), "facing " + ahead + " " + turn + " reach " + reach);
                    assertEquals(way, target.pointing());
                }
            }
        }
    }

    @Test
    void headAndTailReachApart() {
        var arms = new FeederArms(3, 1, FeederArms.Turn.STRAIGHT);

        assertEquals(FEEDER.step(EAST, -3), arms.head(FEEDER, EAST).spot());
        assertEquals(FEEDER.step(EAST, 1), arms.tail(FEEDER, EAST).spot());
    }

    @Test
    void aReachIsOneToThreeBlocks() {
        assertThrows(IllegalArgumentException.class, () -> new FeederArms(0, 1, FeederArms.Turn.STRAIGHT));
        assertThrows(IllegalArgumentException.class, () -> new FeederArms(1, 4, FeederArms.Turn.STRAIGHT));
    }

    @Test
    void eachPressLengthensOneArmABlockAndWrapsFromThreeToOne() {
        var arms = new FeederArms(1, 2, FeederArms.Turn.LEFT);
        var heads = new ArrayList<Integer>();
        for (int press = 0; press < 4; press++) {
            arms = arms.lengthened(FeederArms.Arm.HEAD);
            heads.add(arms.headReach());
        }
        assertEquals(List.of(2, 3, 1, 2), heads);
        assertEquals(new FeederArms(2, 3, FeederArms.Turn.LEFT), arms.lengthened(FeederArms.Arm.TAIL));
        assertEquals(new FeederArms(2, 1, FeederArms.Turn.LEFT), arms.lengthened(FeederArms.Arm.TAIL).lengthened(FeederArms.Arm.TAIL));
    }

    @Test
    void rotateTurnsTheTailAQuarterSkippingTheWayBack() {
        assertEquals(FeederArms.Turn.RIGHT, FeederArms.Turn.STRAIGHT.turned(false));
        assertEquals(FeederArms.Turn.LEFT, FeederArms.Turn.RIGHT.turned(false));
        assertEquals(FeederArms.Turn.STRAIGHT, FeederArms.Turn.LEFT.turned(false));
        for (var turn : FeederArms.Turn.values()) {
            assertEquals(turn, turn.turned(false).turned(true));
            assertEquals(turn, turn.turned(true).turned(true).turned(true));
        }
    }
}
