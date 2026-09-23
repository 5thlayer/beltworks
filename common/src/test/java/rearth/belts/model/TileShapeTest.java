package rearth.belts.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Factorio's corner rule: a tile fed from exactly one side and not from behind turns (PlanetaryFactory #391). */
class TileShapeTest {

    private static final LineScan.Travel EAST = new LineScan.Travel(1, 0);
    private static final LineScan.Travel NORTH = new LineScan.Travel(0, -1);
    private static final LineScan.Travel SOUTH = new LineScan.Travel(0, 1);

    @Test
    void aTileFedByNothingIsStraight() {
        assertEquals(TileShape.STRAIGHT, TileShape.of(false, false, false));
    }

    @Test
    void aTileFedFromBehindIsStraight() {
        assertEquals(TileShape.STRAIGHT, TileShape.of(true, false, false));
    }

    @Test
    void aTileFedFromOneSideOnlyIsACorner() {
        assertEquals(TileShape.FROM_LEFT, TileShape.of(false, true, false));
        assertEquals(TileShape.FROM_RIGHT, TileShape.of(false, false, true));
    }

    @Test
    void aTileFedFromBothSidesIsStraight() {
        assertEquals(TileShape.STRAIGHT, TileShape.of(false, true, true));
    }

    @Test
    void aSideLoadedTileWithATileBehindIsStraight() {
        assertEquals(TileShape.STRAIGHT, TileShape.of(true, true, false));
        assertEquals(TileShape.STRAIGHT, TileShape.of(true, false, true));
    }

    // Facing east, north is on the left; an item fed from there is travelling south as it enters.
    @Test
    void aCornersEntryIsTheWayItsFeederTravels() {
        assertEquals(EAST, TileShape.STRAIGHT.entry(EAST));
        assertEquals(SOUTH, TileShape.FROM_LEFT.entry(EAST));
        assertEquals(NORTH, TileShape.FROM_RIGHT.entry(EAST));
    }

    @Test
    void aTileAsksItsNeighboursWhetherTheyFeedIt() {
        // Facing east at the origin; a feeder to the north outputs south, into it.
        TileShape.Feeds feeds = (from, travel) -> from.equals(new LineScan.Spot(0, 0, -1)) && travel.equals(SOUTH);

        assertEquals(TileShape.FROM_LEFT, TileShape.at(new LineScan.Spot(0, 0, 0), EAST, feeds));
    }

    private static void near(double expected, double actual) {
        assertEquals(expected, actual, 1e-9);
    }

    @Test
    void aStraightTileIsCrossedAlongItsTravel() {
        var start = TileShape.STRAIGHT.point(0, EAST);
        var end = TileShape.STRAIGHT.point(1, EAST);

        near(-0.5, start.x());
        near(0.5, end.x());
        near(0, end.z());
        near(1, end.headingX());
    }

    // Facing east and fed from the north: in at the middle of the north edge heading south, out at
    // the middle of the east edge heading east.
    @Test
    void aCornerIsAQuarterCircleFromItsEntryEdgeToItsExitEdge() {
        var start = TileShape.FROM_LEFT.point(0, EAST);
        var middle = TileShape.FROM_LEFT.point(0.5, EAST);
        var end = TileShape.FROM_LEFT.point(1, EAST);

        near(0, start.x());
        near(-0.5, start.z());
        near(1, start.headingZ());
        near(0.5, end.x());
        near(0, end.z());
        near(1, end.headingX());
        // Half a block from the corner it turns about, (0.5, -0.5).
        near(0.5, Math.hypot(middle.x() - 0.5, middle.z() + 0.5));
    }

    @Test
    void aCornerFedFromTheRightMirrorsOneFedFromTheLeft() {
        var start = TileShape.FROM_RIGHT.point(0, EAST);

        near(0, start.x());
        near(0.5, start.z());
        near(-1, start.headingZ());
    }
}
