// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.model;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Where a raised tile, slope or splitter shows its support, what holds it up or fixes it, and where its legs stop (ADR 0012). */
class SupportTest {

    private static final LineScan.Travel EAST = new LineScan.Travel(1, 0);
    private static final LineScan.Travel SOUTH = new LineScan.Travel(0, 1);
    private static final LineScan.Travel WEST = new LineScan.Travel(-1, 0);
    private static final LineScan.Travel NORTH = new LineScan.Travel(0, -1);

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

    // A climb east from y = 10 to y = 12: level, foot, middle, top, level.
    private void climb() {
        tile(0, 10, 0, EAST);
        tile(1, 10, 0, EAST);
        tile(2, 11, 0, EAST);
        tile(3, 12, 0, EAST);
        tile(4, 12, 0, EAST);
    }

    @Test
    void aFloatingLineEndingOnATopShowsASupport() {
        tile(0, 10, 0, EAST);
        tile(1, 10, 0, EAST);
        tile(2, 11, 0, EAST);

        assertEquals(Pitch.TOP_UP, Pitch.at(spot(2, 11, 0), EAST, around()));
        assertNotNull(support(2, 11, 0));
    }

    @Test
    void aFloatingLineStartingOnAFootShowsASupport() {
        tile(0, 10, 0, EAST);
        tile(1, 11, 0, EAST);

        assertEquals(Pitch.FOOT_UP, Pitch.at(spot(0, 10, 0), EAST, around()));
        assertNotNull(support(0, 10, 0));
    }

    @Test
    void aFloatingLineEndingOnADescentsFootShowsASupport() {
        tile(0, 11, 0, EAST);
        tile(1, 11, 0, EAST);
        tile(2, 10, 0, EAST);

        assertEquals(Pitch.FOOT_DOWN, Pitch.at(spot(2, 10, 0), EAST, around()));
        assertNotNull(support(2, 10, 0));
    }

    @Test
    void aClimbMidLineShowsNone() {
        climb();

        assertEquals(Pitch.FOOT_UP, Pitch.at(spot(1, 10, 0), EAST, around()));
        assertEquals(Pitch.MIDDLE_UP, Pitch.at(spot(2, 11, 0), EAST, around()));
        assertEquals(Pitch.TOP_UP, Pitch.at(spot(3, 12, 0), EAST, around()));
        assertNull(support(1, 10, 0));
        assertNull(support(2, 11, 0));
        assertNull(support(3, 12, 0));
    }

    @Test
    void aDescentMidLineShowsNone() {
        tile(0, 12, 0, EAST);
        tile(1, 12, 0, EAST);
        tile(2, 11, 0, EAST);
        tile(3, 10, 0, EAST);
        tile(4, 10, 0, EAST);

        assertEquals(Pitch.TOP_DOWN, Pitch.at(spot(1, 12, 0), EAST, around()));
        assertEquals(Pitch.MIDDLE_DOWN, Pitch.at(spot(2, 11, 0), EAST, around()));
        assertEquals(Pitch.FOOT_DOWN, Pitch.at(spot(3, 10, 0), EAST, around()));
        assertNull(support(1, 12, 0));
        assertNull(support(2, 11, 0));
        assertNull(support(3, 10, 0));
    }

    // A level tile's line runs on through a foot ahead of it and a top behind it.
    @Test
    void aLevelTileBesideAClimbMidLineShowsNone() {
        climb();
        tile(-1, 10, 0, EAST);
        tile(5, 12, 0, EAST);

        assertNull(support(0, 10, 0));
        assertNull(support(4, 12, 0));
    }

    // Its wedge, in the block under it, holds nothing up: the legs pass it.
    @Test
    void aSlopesLegsStartAtALevelTilesSurfaceAndPassItsWedge() {
        tile(0, 10, 0, EAST);
        tile(1, 10, 0, EAST);
        tile(2, 11, 0, EAST);
        fill(2, 10, 0, Support.Fill.OPEN);
        fill(2, 8, 0, Support.Fill.SOLID);

        var legs = support(2, 11, 0).legs();

        assertEquals(4, legs.size());
        for (var leg : legs) {
            assertEquals(Pitch.SURFACE, leg.top());
            // The top of the block at y = 8 is 2 blocks below the tile's floor at y = 11.
            assertEquals(-2, leg.bottom());
        }
    }

    @Test
    void aSlopeOnASolidTopShowsNone() {
        tile(0, 10, 0, EAST);
        tile(1, 11, 0, EAST);
        fill(0, 9, 0, Support.Fill.SOLID);

        assertNull(support(0, 10, 0));
    }

    @Test
    void aSlopeIsFixedToASolidBlockLeftOrRight() {
        tile(0, 10, 0, EAST);
        tile(1, 11, 0, EAST);
        fixes(0, 10, -1, SOUTH);

        assertNull(support(0, 10, 0));
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
        fixes(4, 10, 1, NORTH);

        assertNull(support(0, 10, 0));
        assertNull(support(4, 10, 0));
    }

    @Test
    void aSolidBlockOnASideTheLineUsesDoesNotFixIt() {
        run();
        // Behind the first tile and in front of the last.
        fixes(-1, 10, 0, EAST);
        fixes(5, 10, 0, WEST);

        assertNotNull(support(0, 10, 0));
        assertNotNull(support(4, 10, 0));
    }

    @Test
    void aCornerIsFixedOnlyAtItsTwoFreeSides() {
        // Fed from the north and leaving east: its free sides are south and west.
        tile(0, 10, -1, SOUTH);
        tile(0, 10, 0, EAST);
        tile(1, 10, 0, EAST);

        fixes(0, 10, 1, NORTH);
        assertNull(support(0, 10, 0));
        unfix(0, 10, 1);

        fixes(-1, 10, 0, EAST);
        assertNull(support(0, 10, 0));
        unfix(-1, 10, 0);

        // Its entry, to the north, is a side the line uses; so is its front, to the east.
        fixes(0, 10, -1, SOUTH);
        fixes(1, 10, 0, WEST);
        assertNotNull(support(0, 10, 0));
    }

    @Test
    void aFixingBlockMustBeSolidOnTheFaceTurnedToTheTile() {
        run();
        // Solid on its north face only, away from the tile south of it.
        fixes(0, 10, -1, NORTH);

        assertNotNull(support(0, 10, 0));
    }

    @Test
    void aSupportHasALegAtEachCornerOfItsBlock() {
        run();

        assertEquals(Set.of(List.of(0d, 0d), List.of(1d, 0d), List.of(0d, 1d), List.of(1d, 1d)), feet(support(0, 10, 0)));
    }

    // A turn is a quarter disc about its inner corner, so its outer corner is empty: the leg there
    // stands back on the diagonal, where the curve's outer edge crosses it.
    @Test
    void aTurnsOuterLegStandsOnItsCurvesOuterEdge() {
        // Fed from the north and leaving east: its inner corner is the north-east, its outer the south-west.
        tile(0, 10, -1, SOUTH);
        tile(0, 10, 0, EAST);
        tile(1, 10, 0, EAST);

        var back = 1 / Math.sqrt(2);
        var feet = feet(support(0, 10, 0));

        assertTrue(feet.containsAll(Set.of(List.of(0d, 0d), List.of(1d, 0d), List.of(1d, 1d))), feet.toString());
        assertTrue(feet.stream().anyMatch(foot -> Math.abs(foot.get(0) - (1 - back)) < 1e-9 && Math.abs(foot.get(1) - back) < 1e-9),
          feet.toString());
    }

    // Around the block, so each leg's neighbours in the list are the legs a strut ties it to.
    @Test
    void aSupportsLegsGoRoundItsBlock() {
        run();

        assertEquals(List.of(List.of(0d, 0d), List.of(1d, 0d), List.of(1d, 1d), List.of(0d, 1d)), round(support(0, 10, 0)));
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
    void aLegStandsOnABeltPiecesSurfaceWhereItStands() {
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

    // A slope's surface under a leg is its height that far along, so a leg lands on it there.
    @Test
    void aBeltTilesSurfaceUnderALegFollowsItsPitch() {
        assertEquals(Pitch.SURFACE, Support.surface(Pitch.LEVEL, EAST, 0, 0));
        assertEquals(0, Support.surface(Pitch.MIDDLE_UP, EAST, 0, 1));
        assertEquals(1, Support.surface(Pitch.MIDDLE_UP, EAST, 1, 0));
        assertEquals(1, Support.surface(Pitch.MIDDLE_UP, SOUTH, 0, 1));
        assertEquals(0, Support.surface(Pitch.MIDDLE_UP, SOUTH, 1, 0));
    }

    @Test
    void aFloatingSplitterShowsOneSupportWithLegsAtThePairsOuterCorners() {
        // Its left half at z = 0, its right half south of it.
        assertEquals(List.of(List.of(0d, 0d), List.of(1d, 0d), List.of(1d, 2d), List.of(0d, 2d)), round(splitter(0, 10, 0, EAST)));
    }

    // The right half of a splitter facing west is north of its left.
    @Test
    void aSplittersLegsGoRoundThePairWhicheverWayItFaces() {
        assertEquals(List.of(List.of(0d, -1d), List.of(1d, -1d), List.of(1d, 1d), List.of(0d, 1d)), round(splitter(0, 10, 0, WEST)));
    }

    @Test
    void aSplitterWithOnlyOneHalfOverAirShowsASupport() {
        fill(0, 9, 0, Support.Fill.SOLID);
        assertNotNull(splitter(0, 10, 0, EAST));

        fill(0, 9, 0, Support.Fill.OPEN);
        fill(0, 9, 1, Support.Fill.BELT);
        assertNotNull(splitter(0, 10, 0, EAST));
    }

    @Test
    void aSplitterWithBothHalvesHeldUpShowsNone() {
        fill(0, 9, 0, Support.Fill.SOLID);
        fill(0, 9, 1, Support.Fill.BELT);

        assertNull(splitter(0, 10, 0, EAST));
    }

    @Test
    void aSplitterIsFixedToASolidBlockAtEitherOuterSide() {
        // North of its left half.
        fixes(0, 10, -1, SOUTH);
        assertNull(splitter(0, 10, 0, EAST));
        unfix(0, 10, -1);

        // South of its right half.
        fixes(0, 10, 2, NORTH);
        assertNull(splitter(0, 10, 0, EAST));
    }

    // Behind or ahead of a half is where its belts run.
    @Test
    void aSolidBlockBehindOrAheadOfASplitterDoesNotFixIt() {
        fixes(-1, 10, 0, EAST);
        fixes(1, 10, 1, WEST);

        assertNotNull(splitter(0, 10, 0, EAST));
    }

    @Test
    void aSplittersLegsStandOnTheGroundUnderTheirOwnHalf() {
        fill(0, 7, 0, Support.Fill.SOLID);
        fill(0, 5, 1, Support.Fill.SOLID);

        var bottoms = splitter(0, 10, 0, EAST).legs().stream().map(Support.Leg::bottom).toList();

        assertEquals(List.of(-2d, -2d, -4d, -4d), bottoms);
    }

    /** Where each leg stands, in order round the block. */
    private static List<List<Double>> round(Support support) {
        return support.legs().stream().map(leg -> List.of(leg.x(), leg.z())).toList();
    }

    private static Set<List<Double>> feet(Support support) {
        return new HashSet<>(support.legs().stream().map(leg -> List.of(leg.x(), leg.z())).toList());
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

    /** The support of a splitter whose left half is at {@code x}, {@code y}, {@code z}, travelling {@code travel}. */
    private Support splitter(int x, int y, int z, LineScan.Travel travel) {
        return Support.splitter(spot(x, y, z), travel, ground());
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
            public double top(LineScan.Spot spot, double x, double z) {
                return tops.getOrDefault(spot, 1d);
            }

            @Override
            public boolean fixes(LineScan.Spot spot, LineScan.Travel toward) {
                return toward.equals(fixing.get(spot));
            }
        };
    }
}
