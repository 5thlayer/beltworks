// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Factorio's corner rule: a tile fed from exactly one side and not from behind turns (PlanetaryFactory #391);
 * and a tile's pitch, from the heights of the tiles feeding it and fed by it (#417).
 */
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
        tile(0, 0, 0, EAST);
        tile(0, 0, -1, SOUTH);

        assertEquals(TileShape.FROM_LEFT, TileShape.at(spot(0, 0, 0), EAST, around()));
    }

    @Test
    void aTileFedFromBehindAndBelowIsStraight() {
        tile(-1, -1, 0, EAST);
        tile(0, 0, 0, EAST);
        tile(0, 0, -1, SOUTH);

        assertEquals(TileShape.STRAIGHT, TileShape.at(spot(0, 0, 0), EAST, around()));
    }

    // The line a block up has a tile of its own ahead of the one behind, so it does not feed this one.
    @Test
    void aStackedLineIsNotBehind() {
        tile(-1, 1, 0, EAST);
        tile(0, 1, 0, EAST);
        tile(0, 0, 0, EAST);
        tile(0, 0, -1, SOUTH);

        assertEquals(TileShape.FROM_LEFT, TileShape.at(spot(0, 0, 0), EAST, around()));
    }

    // A tile beside another climbs over it rather than feeding its side (#412).
    @Test
    void aTileClimbingPastASideIsNoFeeder() {
        tile(0, 0, 0, EAST);
        tile(0, 0, -1, SOUTH);
        tile(0, 1, 0, SOUTH);

        assertEquals(TileShape.STRAIGHT, TileShape.at(spot(0, 0, 0), EAST, around()));
    }

    private final Map<LineScan.Spot, LineScan.Travel> tiles = new HashMap<>();
    private final Map<LineScan.Spot, LineScan.Travel> mouths = new HashMap<>();

    private void tile(int x, int y, int z, LineScan.Travel travel) {
        tiles.put(spot(x, y, z), travel);
    }

    /** A loader or splitter half facing {@code facing}. */
    private void mouth(int x, int y, int z, LineScan.Travel facing) {
        mouths.put(spot(x, y, z), facing);
    }

    private static LineScan.Spot spot(int x, int y, int z) {
        return new LineScan.Spot(x, y, z);
    }

    private TileShape.Around around() {
        return new TileShape.Around() {
            @Override
            public LineScan.Travel tile(LineScan.Spot spot) {
                return tiles.get(spot);
            }

            @Override
            public boolean feeds(LineScan.Spot from, LineScan.Travel travel) {
                return travel.equals(tiles.get(from)) || travel.equals(mouths.get(from));
            }
        };
    }

    private Pitch pitch(int x, int y, int z) {
        return Pitch.at(spot(x, y, z), tiles.get(spot(x, y, z)), around());
    }

    // The table of #412: the feeder's height down the side, the fed tile's across the top.
    @Test
    void pitchIsTheFeedersAndTheFedTilesHeights() {
        assertNull(Pitch.of(-1, -1));
        assertEquals(Pitch.TOP_UP, Pitch.of(-1, 0));
        assertEquals(Pitch.MIDDLE_UP, Pitch.of(-1, 1));
        assertEquals(Pitch.TOP_DOWN, Pitch.of(0, -1));
        assertEquals(Pitch.LEVEL, Pitch.of(0, 0));
        assertEquals(Pitch.FOOT_UP, Pitch.of(0, 1));
        assertEquals(Pitch.MIDDLE_DOWN, Pitch.of(1, -1));
        assertEquals(Pitch.FOOT_DOWN, Pitch.of(1, 0));
        assertNull(Pitch.of(1, 1));
    }

    @Test
    void aTilePlacedAboveAndAheadMakesAFootAndATop() {
        tile(0, 0, 0, EAST);
        tile(1, 0, 0, EAST);
        tile(2, 1, 0, EAST);

        assertEquals(Pitch.LEVEL, pitch(0, 0, 0));
        assertEquals(Pitch.FOOT_UP, pitch(1, 0, 0));
        assertEquals(Pitch.TOP_UP, pitch(2, 1, 0));
    }

    @Test
    void aTilePlacedBelowAndAheadMakesTheMirroredDescent() {
        tile(0, 0, 0, EAST);
        tile(1, 0, 0, EAST);
        tile(2, -1, 0, EAST);

        assertEquals(Pitch.TOP_DOWN, pitch(1, 0, 0));
        assertEquals(Pitch.FOOT_DOWN, pitch(2, -1, 0));
    }

    @Test
    void aClimbOfTwoBlocksHasAMiddle() {
        tile(0, 0, 0, EAST);
        tile(1, 1, 0, EAST);
        tile(2, 2, 0, EAST);

        assertEquals(Pitch.FOOT_UP, pitch(0, 0, 0));
        assertEquals(Pitch.MIDDLE_UP, pitch(1, 1, 0));
        assertEquals(Pitch.TOP_UP, pitch(2, 2, 0));
    }

    @Test
    void aClimbOfThreeBlocksHasTwoMiddles() {
        for (int x = 0; x < 6; x++) tile(x, Math.clamp(x - 1, 0, 3), 0, EAST);

        assertEquals(Pitch.LEVEL, pitch(0, 0, 0));
        assertEquals(Pitch.FOOT_UP, pitch(1, 0, 0));
        assertEquals(Pitch.MIDDLE_UP, pitch(2, 1, 0));
        assertEquals(Pitch.MIDDLE_UP, pitch(3, 2, 0));
        assertEquals(Pitch.TOP_UP, pitch(4, 3, 0));
        assertEquals(Pitch.LEVEL, pitch(5, 3, 0));
    }

    @Test
    void aDescentOfThreeBlocksHasTwoMiddles() {
        for (int x = 0; x < 6; x++) tile(x, 3 - Math.clamp(x - 1, 0, 3), 0, EAST);

        assertEquals(Pitch.TOP_DOWN, pitch(1, 3, 0));
        assertEquals(Pitch.MIDDLE_DOWN, pitch(2, 2, 0));
        assertEquals(Pitch.MIDDLE_DOWN, pitch(3, 1, 0));
        assertEquals(Pitch.FOOT_DOWN, pitch(4, 0, 0));
    }

    // Two blocks apart is no step: the tiles either side of a broken middle each level out.
    @Test
    void aClimbWithAMiddleGoneConnectsNothingAcrossTheGap() {
        for (int x = 0; x < 6; x++) if (x != 2) tile(x, Math.clamp(x - 1, 0, 3), 0, EAST);

        assertEquals(Pitch.LEVEL, pitch(1, 0, 0));
        assertEquals(Pitch.FOOT_UP, pitch(3, 2, 0));
        assertEquals(Pitch.TOP_UP, pitch(4, 3, 0));
    }

    // A crest's neighbours do not climb to it either: a connection is both tiles' or neither's.
    @Test
    void aCrestConnectsToNeither() {
        tile(0, 0, 0, EAST);
        tile(1, 1, 0, EAST);
        tile(2, 0, 0, EAST);

        assertEquals(Pitch.LEVEL, pitch(0, 0, 0));
        assertEquals(Pitch.LEVEL, pitch(1, 1, 0));
        assertEquals(Pitch.LEVEL, pitch(2, 0, 0));
    }

    @Test
    void aValleyConnectsToNeither() {
        tile(0, 1, 0, EAST);
        tile(1, 0, 0, EAST);
        tile(2, 1, 0, EAST);

        assertEquals(Pitch.LEVEL, pitch(0, 1, 0));
        assertEquals(Pitch.LEVEL, pitch(1, 0, 0));
        assertEquals(Pitch.LEVEL, pitch(2, 1, 0));
    }

    @Test
    void aLevelTileAheadIsFedBeforeOneAboveIt() {
        tile(0, 0, 0, EAST);
        tile(1, 0, 0, EAST);
        tile(1, 1, 0, EAST);

        assertEquals(Pitch.LEVEL, pitch(0, 0, 0));
        assertEquals(Pitch.LEVEL, pitch(1, 1, 0));
    }

    @Test
    void aTileFacingAnotherWayIsNoStep() {
        tile(0, 0, 0, EAST);
        tile(1, 1, 0, NORTH);

        assertEquals(Pitch.LEVEL, pitch(0, 0, 0));
    }

    // A slope takes no side-load, so a tile that would be both a corner and a slope is a slope, and
    // a placement that turns a corner into one is refused instead (#419).
    @Test
    void aSlopeIsNeverACorner() {
        tile(0, 0, 0, EAST);
        tile(0, 0, -1, SOUTH);
        tile(1, 1, 0, EAST);

        assertEquals(Pitch.FOOT_UP, pitch(0, 0, 0));
        assertEquals(TileShape.STRAIGHT, TileShape.at(spot(0, 0, 0), EAST, around()));
        assertEquals(Pitch.TOP_UP, pitch(1, 1, 0));
    }

    @Test
    void aTileFacingTheSideOfATopDoesNotTurnIt() {
        tile(0, 0, 0, EAST);
        tile(1, -1, 0, EAST);
        tile(0, 0, -1, SOUTH);

        assertEquals(Pitch.TOP_DOWN, pitch(0, 0, 0));
        assertEquals(TileShape.STRAIGHT, TileShape.at(spot(0, 0, 0), EAST, around()));
    }

    @Test
    void aLoaderBesideAFootDoesNotTurnIt() {
        tile(0, 0, 0, EAST);
        tile(1, 1, 0, EAST);
        mouth(0, 0, -1, SOUTH);

        assertEquals(TileShape.STRAIGHT, TileShape.at(spot(0, 0, 0), EAST, around()));
    }

    // In pixels from each tile's own floor, over its 16 of travel (#412).
    @Test
    void aFootIsSixPixelsFlatThenRisesToItsBlocksTop() {
        near(6 / 16d, Pitch.FOOT_UP.surface(0));
        near(6 / 16d, Pitch.FOOT_UP.surface(6 / 16d));
        near(11 / 16d, Pitch.FOOT_UP.surface(11 / 16d));
        near(1, Pitch.FOOT_UP.surface(1));
    }

    @Test
    void aTopRisesFromItsFloorThenIsTenPixelsFlat() {
        near(0, Pitch.TOP_UP.surface(0));
        near(3 / 16d, Pitch.TOP_UP.surface(3 / 16d));
        near(6 / 16d, Pitch.TOP_UP.surface(6 / 16d));
        near(6 / 16d, Pitch.TOP_UP.surface(1));
    }

    // Foot, middles and top are one straight 45-degree line, each drawn inside its own block.
    @Test
    void aClimbIsOneStraightLine() {
        for (var offset = 0d; offset <= 1; offset += 1 / 16d) {
            if (offset >= 6 / 16d) near(offset, Pitch.FOOT_UP.surface(offset));
            near(offset, Pitch.MIDDLE_UP.surface(offset));
            if (offset <= 6 / 16d) near(offset, Pitch.TOP_UP.surface(offset));
            near(Pitch.FOOT_UP.surface(offset), Pitch.FOOT_DOWN.surface(1 - offset));
            near(Pitch.TOP_UP.surface(offset), Pitch.TOP_DOWN.surface(1 - offset));
            assertTrue(Pitch.MIDDLE_UP.surface(offset) >= 0 && Pitch.MIDDLE_UP.surface(offset) <= 1);
        }
        near(1, Pitch.FOOT_UP.rise(0.5));
        near(0, Pitch.FOOT_UP.rise(0.2));
        near(-1, Pitch.TOP_DOWN.rise(0.9));
        near(0, Pitch.LEVEL.rise(0.5));
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

    // Where a drop is aimed, projected back onto the line: the inverse of point() (#91).
    @Test
    void aPointOnAStraightTileProjectsAlongItsTravel() {
        near(0, TileShape.STRAIGHT.project(-0.5, 0.3, EAST));
        near(0.25, TileShape.STRAIGHT.project(-0.25, -0.4, EAST));
        near(1, TileShape.STRAIGHT.project(0.5, 0, EAST));
        near(0.25, TileShape.STRAIGHT.project(0.25, 0.1, new LineScan.Travel(-1, 0)));
        near(0.75, TileShape.STRAIGHT.project(0.3, 0.25, SOUTH));
        near(0.75, TileShape.STRAIGHT.project(-0.1, -0.25, NORTH));
    }

    @Test
    void aPointOutsideATileProjectsOntoItsEnds() {
        near(0, TileShape.STRAIGHT.project(-2, 0, EAST));
        near(1, TileShape.STRAIGHT.project(2, 0, EAST));
    }

    @ParameterizedTest
    @EnumSource(value = TileShape.class, names = {"FROM_LEFT", "FROM_RIGHT"})
    void aPointOnACornersArcProjectsToItsOwnOffset(TileShape corner) {
        for (var travel : List.of(EAST, NORTH, SOUTH, new LineScan.Travel(-1, 0))) {
            for (var offset = 0.0; offset <= 1.0; offset += 0.125) {
                var on = corner.point(offset, travel);

                near(offset, corner.project(on.x(), on.z(), travel));
            }
        }
    }

    // A corner's arc turns about the block's corner the line enters beside: aimed inside or outside the arc,
    // along the same radius, the point is the same distance along the line.
    @Test
    void aPointOffACornersArcProjectsAlongItsRadius() {
        var on = TileShape.FROM_LEFT.point(0.5, EAST);
        // Its centre is (0.5, -0.5); push the point twice as far from it along the radius.
        var x = 0.5 + (on.x() - 0.5) * 1.6;
        var z = -0.5 + (on.z() + 0.5) * 1.6;

        near(0.5, TileShape.FROM_LEFT.project(x, z, EAST));
        near(0.5, TileShape.FROM_LEFT.project(0.5 + (on.x() - 0.5) * 0.3, -0.5 + (on.z() + 0.5) * 0.3, EAST));
    }

    @Test
    void aPointAtACornersCentreProjectsToItsEntry() {
        near(0, TileShape.FROM_LEFT.project(0.5, -0.5, EAST));
    }
}
