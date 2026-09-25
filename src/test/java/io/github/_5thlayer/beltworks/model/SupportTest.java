// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.model;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Where a raised level tile shows its support, what holds it up or fixes it, and where its legs stop (ADR 0012). */
class SupportTest {

    private static final LineScan.Travel EAST = new LineScan.Travel(1, 0);
    private static final LineScan.Travel SOUTH = new LineScan.Travel(0, 1);

    // A run east along y = 10, from x = 0 to x = 4.
    private void run() {
        for (var x = 0; x <= 4; x++) tile(x, 10, 0, EAST);
    }

    @Test
    void aFloatingLinesFirstAndLastTilesShowASupport() {
        run();

        assertNotNull(support(0, 10, 0));
        assertNotNull(support(4, 10, 0));
    }

    @Test
    void aStraightTileMidLineShowsNone() {
        run();

        assertNull(support(2, 10, 0));
    }

    @Test
    void aFloatingCornerShowsASupport() {
        // Fed from the north, it turns east.
        tile(0, 10, -1, SOUTH);
        tile(0, 10, 0, EAST);
        tile(1, 10, 0, EAST);
        tile(2, 10, 0, EAST);

        assertEquals(TileShape.FROM_LEFT, TileShape.at(spot(0, 10, 0), EAST, around()));
        assertNotNull(support(0, 10, 0));
        assertNull(support(1, 10, 0));
    }

    // A tile side-loading a straight line is its own line's last tile.
    @Test
    void aTileSideLoadingALineIsItsLinesLast() {
        run();
        tile(2, 10, -2, SOUTH);
        tile(2, 10, -1, SOUTH);

        assertNotNull(support(2, 10, -1));
        assertNull(support(2, 10, 0));
    }

    @Test
    void aTileFedFromBehindByALoaderIsItsLinesFirst() {
        mouth(-1, 10, 0, EAST);
        run();

        assertNotNull(support(0, 10, 0));
    }

    // Slopes get their supports in a later ticket (#48).
    @Test
    void aSlopeShowsNoneYet() {
        tile(0, 10, 0, EAST);
        tile(1, 10, 0, EAST);
        tile(2, 11, 0, EAST);

        assertEquals(Pitch.TOP_UP, Pitch.at(spot(2, 11, 0), EAST, around()));
        assertNull(support(2, 11, 0));
    }

    @Test
    void aTileOnASolidTopOrABeltPieceIsHeldUp() {
        run();
        fill(0, 9, 0, Support.Fill.SOLID);
        fill(4, 9, 0, Support.Fill.BELT);

        assertNull(support(0, 10, 0));
        assertNull(support(4, 10, 0));
    }

    @Test
    void aTileOverAnythingWithoutASolidTopIsNotHeldUp() {
        run();
        fill(0, 9, 0, Support.Fill.OPEN);

        assertNotNull(support(0, 10, 0));
    }

    @Test
    void aStraightTileIsFixedToASolidBlockLeftOrRight() {
        run();
        // North of the first tile, its left; south of the last, its right.
        fixes(0, 10, -1, SOUTH);
        fixes(4, 10, 1, new LineScan.Travel(0, -1));

        assertNull(support(0, 10, 0));
        assertNull(support(4, 10, 0));
    }

    @Test
    void aSolidBlockOnASideTheLineUsesDoesNotFixIt() {
        run();
        // Behind the first tile and in front of the last.
        fixes(-1, 10, 0, EAST);
        fixes(5, 10, 0, new LineScan.Travel(-1, 0));

        assertNotNull(support(0, 10, 0));
        assertNotNull(support(4, 10, 0));
    }

    @Test
    void aCornerIsFixedOnlyAtItsTwoFreeSides() {
        // Fed from the north and leaving east: its free sides are south and west.
        tile(0, 10, -1, SOUTH);
        tile(0, 10, 0, EAST);
        tile(1, 10, 0, EAST);

        fixes(0, 10, 1, new LineScan.Travel(0, -1));
        assertNull(support(0, 10, 0));
        unfix(0, 10, 1);

        fixes(-1, 10, 0, EAST);
        assertNull(support(0, 10, 0));
        unfix(-1, 10, 0);

        // Its entry, to the north, is a side the line uses; so is its front, to the east.
        fixes(0, 10, -1, SOUTH);
        fixes(1, 10, 0, new LineScan.Travel(-1, 0));
        assertNotNull(support(0, 10, 0));
    }

    @Test
    void aFixingBlockMustBeSolidOnTheFaceTurnedToTheTile() {
        run();
        // Solid on its north face only, away from the tile south of it.
        fixes(0, 10, -1, new LineScan.Travel(0, -1));

        assertNotNull(support(0, 10, 0));
    }

    @Test
    void aSupportHasALegAtEachCornerOfItsBlock() {
        run();

        var legs = support(0, 10, 0).legs();

        assertEquals(Set.of(new Support.Corner(0, 0), new Support.Corner(1, 0), new Support.Corner(0, 1), new Support.Corner(1, 1)),
          new HashSet<>(legs.stream().map(Support.Leg::corner).toList()));
    }

    @Test
    void aLegRunsFromTheSurfaceToTheFirstSolidTopBelow() {
        run();
        fill(0, 6, 0, Support.Fill.SOLID);
        fill(0, 3, 0, Support.Fill.SOLID);

        var leg = support(0, 10, 0).legs().getFirst();

        assertEquals(Pitch.SURFACE, leg.top());
        // The top of the block at y = 6 is 3 blocks below the tile's floor at y = 10.
        assertEquals(-3, leg.bottom());
        assertTrue(leg.standing());
    }

    @Test
    void aLegPassesOpenBlocks() {
        run();
        fill(0, 9, 0, Support.Fill.OPEN);
        fill(0, 8, 0, Support.Fill.OPEN);
        fill(0, 7, 0, Support.Fill.SOLID);

        assertEquals(-2, support(0, 10, 0).legs().getFirst().bottom());
    }

    // A splitter half's top is lower than its block's.
    @Test
    void aLegStandsOnABeltPiecesTop() {
        run();
        fill(0, 7, 0, Support.Fill.BELT);
        top(0, 7, 0, 0.5);

        assertEquals(-2.5, support(0, 10, 0).legs().getFirst().bottom());
    }

    @Test
    void aLegStandsOnABeltPiecesSurfaceAtItsCorner() {
        run();
        fill(0, 7, 0, Support.Fill.BELT);
        top(0, 7, 0, Pitch.SURFACE);

        assertEquals(-3 + Pitch.SURFACE, support(0, 10, 0).legs().getFirst().bottom());
    }

    // Ground just past reach is not stood on: the leg runs on past the reach toward it.
    @Test
    void aLegOverGroundJustPastReachRunsOnPastIt() {
        run();
        var y = (int) Math.ceil(10 + Pitch.SURFACE - Support.REACH) - 2;
        fill(0, y, 0, Support.Fill.SOLID);

        var leg = support(0, 10, 0).legs().getFirst();

        assertEquals(Pitch.SURFACE - Support.REACH, leg.bottom());
        assertFalse(leg.standing());
    }

    @Test
    void aLegWithNothingWithinReachRunsOnPastIt() {
        run();
        fill(0, 10 - Support.REACH - 2, 0, Support.Fill.SOLID);

        var leg = support(0, 10, 0).legs().getFirst();

        assertEquals(Pitch.SURFACE - Support.REACH, leg.bottom());
        assertFalse(leg.standing());
    }

    @Test
    void aLegStandsOnGroundJustWithinReach() {
        run();
        // The highest block top no further than REACH below the surface.
        var y = (int) Math.ceil(10 + Pitch.SURFACE - Support.REACH) - 1;
        fill(0, y, 0, Support.Fill.SOLID);

        var leg = support(0, 10, 0).legs().getFirst();

        assertTrue(leg.top() - leg.bottom() <= Support.REACH);
        assertTrue(leg.standing());
    }

    // A slope's surface at a corner is its height at that end, so a leg lands on it there.
    @Test
    void aBeltTilesSurfaceAtACornerFollowsItsPitch() {
        assertEquals(Pitch.SURFACE, Support.surface(Pitch.LEVEL, EAST, new Support.Corner(0, 0)));
        assertEquals(0, Support.surface(Pitch.MIDDLE_UP, EAST, new Support.Corner(0, 1)));
        assertEquals(1, Support.surface(Pitch.MIDDLE_UP, EAST, new Support.Corner(1, 0)));
        assertEquals(1, Support.surface(Pitch.MIDDLE_UP, SOUTH, new Support.Corner(0, 1)));
        assertEquals(0, Support.surface(Pitch.MIDDLE_UP, SOUTH, new Support.Corner(1, 0)));
    }

    private final Map<LineScan.Spot, LineScan.Travel> tiles = new HashMap<>();
    private final Map<LineScan.Spot, LineScan.Travel> mouths = new HashMap<>();
    private final Map<LineScan.Spot, Support.Fill> fills = new HashMap<>();
    private final Map<LineScan.Spot, Double> tops = new HashMap<>();
    private final Map<LineScan.Spot, LineScan.Travel> fixing = new HashMap<>();

    private void tile(int x, int y, int z, LineScan.Travel travel) {
        tiles.put(spot(x, y, z), travel);
    }

    /** A loader or splitter half facing {@code facing}. */
    private void mouth(int x, int y, int z, LineScan.Travel facing) {
        mouths.put(spot(x, y, z), facing);
    }

    private void fill(int x, int y, int z, Support.Fill fill) {
        fills.put(spot(x, y, z), fill);
    }

    private void top(int x, int y, int z, double top) {
        tops.put(spot(x, y, z), top);
    }

    /** A block solid on its face turned {@code toward}. */
    private void fixes(int x, int y, int z, LineScan.Travel toward) {
        fixing.put(spot(x, y, z), toward);
    }

    private void unfix(int x, int y, int z) {
        fixing.remove(spot(x, y, z));
    }

    private static LineScan.Spot spot(int x, int y, int z) {
        return new LineScan.Spot(x, y, z);
    }

    private Support support(int x, int y, int z) {
        var spot = spot(x, y, z);
        return Support.at(spot, tiles.get(spot), around(), ground());
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

    // Tiles and mouths are belt pieces; every other block is open unless filled.
    private Support.Ground ground() {
        return new Support.Ground() {
            @Override
            public Support.Fill fill(LineScan.Spot spot) {
                if (tiles.containsKey(spot) || mouths.containsKey(spot)) return Support.Fill.BELT;
                return fills.getOrDefault(spot, Support.Fill.OPEN);
            }

            @Override
            public double top(LineScan.Spot spot, Support.Corner corner) {
                return tops.getOrDefault(spot, 1d);
            }

            @Override
            public boolean fixes(LineScan.Spot spot, LineScan.Travel toward) {
                return toward.equals(fixing.get(spot));
            }
        };
    }
}
