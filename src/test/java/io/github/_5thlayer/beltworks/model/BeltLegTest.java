// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The tiles a belt builds one leg of a stretch with, Groundworks handing it the leg's first anchor,
 * its rise and its route seen from above (Groundworks ADR 0004): level from the anchor, a climb of
 * slopes right after it, and whatever stands in the way refused where it stands.
 */
class BeltLegTest {

    private static final LineScan.Travel EAST = new LineScan.Travel(1, 0);
    private static final LineScan.Travel SOUTH = new LineScan.Travel(0, 1);
    private static final LineScan.Spot FROM = new LineScan.Spot(0, 64, 0);

    /** An open field, with what the test puts down. */
    private static final class Field implements BeltLeg.Terrain {
        final Map<LineScan.Spot, BeltLeg.Ground> ground = new HashMap<>();

        @Override
        public BeltLeg.Ground at(LineScan.Spot spot, LineScan.Travel travel) {
            return ground.getOrDefault(spot, BeltLeg.Ground.FREE);
        }
    }

    /** East from the anchor for {@code length} columns past it. */
    private static List<BeltLeg.Column> east(int length) {
        var route = new ArrayList<BeltLeg.Column>();
        for (var x = 0; x <= length; x++) route.add(new BeltLeg.Column(x, 0, EAST));
        return route;
    }

    private static BeltLeg.Step tile(int x, int y, int z, LineScan.Travel travel) {
        return new BeltLeg.Step(new LineScan.Spot(x, y, z), travel);
    }

    @Test
    void aLevelLegIsATileInEachColumnAtItsAnchorsHeight() {
        var leg = BeltLeg.shape(FROM, 0, east(3), new Field());

        assertEquals(List.of(tile(0, 64, 0, EAST), tile(1, 64, 0, EAST), tile(2, 64, 0, EAST), tile(3, 64, 0, EAST)), leg.tiles());
        assertNull(leg.stop());
    }

    @Test
    void aRiseOfOneIsAFootAndATopRightAfterTheAnchorThenLevelOneUp() {
        var leg = BeltLeg.shape(FROM, 1, east(4), new Field());

        assertEquals(List.of(tile(0, 64, 0, EAST), tile(1, 64, 0, EAST), tile(2, 65, 0, EAST), tile(3, 65, 0, EAST),
                tile(4, 65, 0, EAST)), leg.tiles());
        assertNull(leg.stop());
    }

    @Test
    void aRiseOfTwoHasAMiddleBetweenItsFootAndItsTop() {
        var leg = BeltLeg.shape(FROM, 2, east(4), new Field());

        assertEquals(List.of(64, 64, 65, 66, 66), leg.tiles().stream().map(step -> step.spot().y()).toList());
        assertNull(leg.stop());
    }

    @Test
    void aFallStartsAtTheAnchorsHeightAndEndsBelowIt() {
        var leg = BeltLeg.shape(FROM, -2, east(4), new Field());

        assertEquals(List.of(64, 64, 63, 62, 62), leg.tiles().stream().map(step -> step.spot().y()).toList());
        assertNull(leg.stop());
    }

    // A top on the leg's last anchor would be a slope where the next leg may turn.
    @Test
    void aRiseWhoseTopReachesTheNextAnchorIsRefused() {
        var leg = BeltLeg.shape(FROM, 1, east(2), new Field());

        assertEquals(BeltLeg.Stop.RISE_DOES_NOT_FIT, leg.stop());
        assertNull(leg.at());
    }

    @Test
    void aRiseLongerThanTheLegIsRefusedAndStillDrawn() {
        var leg = BeltLeg.shape(FROM, 3, east(2), new Field());

        assertEquals(BeltLeg.Stop.RISE_DOES_NOT_FIT, leg.stop());
        assertEquals(3, leg.tiles().size());
    }

    @Test
    void aRiseThatWouldTurnIsRefused() {
        var route = new ArrayList<>(east(2));
        route.set(2, new BeltLeg.Column(2, 0, SOUTH));
        route.add(new BeltLeg.Column(2, 1, SOUTH));
        route.add(new BeltLeg.Column(2, 2, SOUTH));

        assertEquals(BeltLeg.Stop.RISE_DOES_NOT_FIT, BeltLeg.shape(FROM, 1, route, new Field()).stop());
    }

    // The foot and the top run straight on from the anchor, and the corner comes after the top.
    @Test
    void aRiseFitsWhenTheLegTurnsRightAfterItsTop() {
        var route = new ArrayList<>(east(2));
        route.add(new BeltLeg.Column(3, 0, SOUTH));
        route.add(new BeltLeg.Column(3, 1, SOUTH));

        var leg = BeltLeg.shape(FROM, 1, route, new Field());

        assertNull(leg.stop());
        assertEquals(tile(3, 65, 0, SOUTH), leg.tiles().get(3));
    }

    @Test
    void aBlockInTheWayIsRefusedWhereItStands() {
        var field = new Field();
        field.ground.put(new LineScan.Spot(2, 64, 0), BeltLeg.Ground.OBSTACLE);

        var leg = BeltLeg.shape(FROM, 0, east(4), field);

        assertEquals(BeltLeg.Stop.BLOCKED, leg.stop());
        assertEquals(new LineScan.Spot(2, 64, 0), leg.at());
        assertEquals(5, leg.tiles().size());
    }

    // After the climb the leg runs one up, so what stands on the ground there is no obstacle.
    @Test
    void theRiseLiftsTheLegOverWhatStandsOnTheGround() {
        var field = new Field();
        field.ground.put(new LineScan.Spot(3, 64, 0), BeltLeg.Ground.OBSTACLE);

        assertNull(BeltLeg.shape(FROM, 1, east(4), field).stop());
    }

    @Test
    void aLineCrossingTheLegIsRefusedWhereItCrosses() {
        var field = new Field();
        field.ground.put(new LineScan.Spot(2, 64, 0), BeltLeg.Ground.ACROSS);

        var leg = BeltLeg.shape(FROM, 0, east(4), field);

        assertEquals(BeltLeg.Stop.CROSSES_A_LINE, leg.stop());
        assertEquals(new LineScan.Spot(2, 64, 0), leg.at());
    }

    // The anchor that starts a leg on a line would turn one of its tiles and cut it.
    @Test
    void aLegStartingAcrossALineIsRefusedAtItsAnchor() {
        var field = new Field();
        field.ground.put(FROM, BeltLeg.Ground.ACROSS);

        var leg = BeltLeg.shape(FROM, 0, east(3), field);

        assertEquals(BeltLeg.Stop.CROSSES_A_LINE, leg.stop());
        assertEquals(FROM, leg.at());
    }

    @Test
    void aLegEndingOnALineJoinsItsSideAndLaysNothingThere() {
        var field = new Field();
        field.ground.put(new LineScan.Spot(3, 64, 0), BeltLeg.Ground.ACROSS);

        var leg = BeltLeg.shape(FROM, 0, east(3), field);

        assertNull(leg.stop());
        assertEquals(List.of(tile(0, 64, 0, EAST), tile(1, 64, 0, EAST), tile(2, 64, 0, EAST)), leg.tiles());
    }

    @Test
    void aTileRunningAlongTheLegIsKept() {
        var field = new Field();
        field.ground.put(new LineScan.Spot(2, 64, 0), BeltLeg.Ground.ALONG);
        field.ground.put(FROM, BeltLeg.Ground.ALONG);

        var leg = BeltLeg.shape(FROM, 0, east(3), field);

        assertNull(leg.stop());
        assertEquals(4, leg.tiles().size());
    }

    @Test
    void theFirstObstacleAlongTheLegIsTheOneNamed() {
        var field = new Field();
        field.ground.put(new LineScan.Spot(3, 64, 0), BeltLeg.Ground.OBSTACLE);
        field.ground.put(new LineScan.Spot(1, 64, 0), BeltLeg.Ground.ACROSS);

        var leg = BeltLeg.shape(FROM, 0, east(4), field);

        assertEquals(BeltLeg.Stop.CROSSES_A_LINE, leg.stop());
        assertEquals(new LineScan.Spot(1, 64, 0), leg.at());
    }

    // No detour can move a climb, so a rise that doesn't fit is named before any obstacle.
    @Test
    void aRiseThatDoesNotFitIsNamedBeforeAnObstacle() {
        var field = new Field();
        field.ground.put(new LineScan.Spot(1, 64, 0), BeltLeg.Ground.OBSTACLE);

        assertEquals(BeltLeg.Stop.RISE_DOES_NOT_FIT, BeltLeg.shape(FROM, 2, east(3), field).stop());
    }
}
