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
}
