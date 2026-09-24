package rearth.belts.model;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The tiles one drag of the tile item lays: one leg or two, the first along the stored look
 * (PlanetaryFactory #393), each tile level, one up or one down from the one before (#421).
 */
class StretchTest {

    private static final LineScan.Travel EAST = new LineScan.Travel(1, 0);
    private static final LineScan.Travel NORTH = new LineScan.Travel(0, -1);
    private static final LineScan.Travel SOUTH = new LineScan.Travel(0, 1);
    private static final LineScan.Spot ORIGIN = new LineScan.Spot(0, 64, 0);

    private static Stretch.Step step(int x, int z, LineScan.Travel travel) {
        return new Stretch.Step(new LineScan.Spot(x, 64, z), travel);
    }

    @Test
    void anEndStraightAheadIsOneLegAlongTheLook() {
        var path = Stretch.path(ORIGIN, EAST, new LineScan.Spot(3, 64, 0)).orElseThrow();

        assertEquals(List.of(step(0, 0, EAST), step(1, 0, EAST), step(2, 0, EAST), step(3, 0, EAST)), path);
    }

    // Looking east, an end two ahead and two south turns right at the corner.
    @Test
    void anEndOffTheLookTurnsOnceWhereTheFirstLegMeetsIt() {
        var path = Stretch.path(ORIGIN, EAST, new LineScan.Spot(2, 64, 2)).orElseThrow();

        assertEquals(List.of(step(0, 0, EAST), step(1, 0, EAST), step(2, 0, SOUTH), step(2, 1, SOUTH), step(2, 2, SOUTH)), path);
    }

    @Test
    void theCornerTurnsLeftAsReadilyAsRight() {
        var path = Stretch.path(ORIGIN, EAST, new LineScan.Spot(1, 64, -1)).orElseThrow();

        assertEquals(List.of(step(0, 0, EAST), step(1, 0, NORTH), step(1, -1, NORTH)), path);
    }

    @Test
    void anEndBesideTheStartIsOneLegSidewaysFromTheStart() {
        var path = Stretch.path(ORIGIN, EAST, new LineScan.Spot(0, 64, 2)).orElseThrow();

        assertEquals(List.of(step(0, 0, SOUTH), step(0, 1, SOUTH), step(0, 2, SOUTH)), path);
    }

    @Test
    void anEndBehindTheLookIsRefused() {
        assertTrue(Stretch.path(ORIGIN, EAST, new LineScan.Spot(-1, 64, 0)).isEmpty());
        assertTrue(Stretch.path(ORIGIN, EAST, new LineScan.Spot(-1, 64, 3)).isEmpty());
    }

    @Test
    void anEndOnTheStartIsOneTileFacingTheLook() {
        assertEquals(List.of(step(0, 0, NORTH)), Stretch.path(ORIGIN, NORTH, ORIGIN).orElseThrow());
    }

    @Test
    void aStretchIsLevelWithItsStart() {
        var path = Stretch.path(ORIGIN, EAST, new LineScan.Spot(1, 70, 0)).orElseThrow();

        assertEquals(List.of(step(0, 0, EAST), step(1, 0, EAST)), path);
    }

    @Test
    void aStretchIsOneTileMoreThanItsTwoLegs() {
        assertEquals(5 + 7 + 1, Stretch.path(ORIGIN, NORTH, new LineScan.Spot(7, 64, -5)).orElseThrow().size());
    }

    @Test
    void aLegAfterACornerHeadsTheWayTheCornersLastTileTravels() {
        var path = Stretch.path(ORIGIN, EAST, List.of(new LineScan.Spot(3, 64, 0)), new LineScan.Spot(3, 64, -2)).orElseThrow();

        assertEquals(List.of(step(0, 0, EAST), step(1, 0, EAST), step(2, 0, EAST), step(3, 0, NORTH), step(3, -1, NORTH), step(3, -2, NORTH)), path);
    }

    // Looking east, a corner two ahead and two south, then an end two further south and two west: a U.
    @Test
    void cornersChainIntoOnePathWithNoTileTwice() {
        var west = new LineScan.Travel(-1, 0);
        var path = Stretch.path(ORIGIN, EAST, List.of(new LineScan.Spot(2, 64, 2)), new LineScan.Spot(0, 64, 4)).orElseThrow();

        assertEquals(List.of(step(0, 0, EAST), step(1, 0, EAST), step(2, 0, SOUTH), step(2, 1, SOUTH), step(2, 2, SOUTH),
          step(2, 3, SOUTH), step(2, 4, west), step(1, 4, west), step(0, 4, west)), path);
    }

    @Test
    void aLegTurningBackOnItsCornersHeadingIsRefused() {
        assertTrue(Stretch.path(ORIGIN, EAST, List.of(new LineScan.Spot(3, 64, 0)), new LineScan.Spot(1, 64, 0)).isEmpty());
    }

    /** The ground along x at z = 0: solid up to each column's height, free above, with any spot overridden. */
    private static final class Ground implements Stretch.Terrain {

        private final Map<Integer, Integer> heights = new HashMap<>();
        private final Map<LineScan.Spot, Stretch.Ground> overrides = new HashMap<>();

        Ground(int... heights) {
            for (var x = 0; x < heights.length; x++) this.heights.put(x, heights[x]);
        }

        Ground with(int x, int y, int z, Stretch.Ground ground) {
            overrides.put(new LineScan.Spot(x, y, z), ground);
            return this;
        }

        @Override
        public Stretch.Ground at(LineScan.Spot spot) {
            var override = overrides.get(spot);
            if (override != null) return override;
            return spot.y() <= heights.getOrDefault(spot.x(), 63) ? Stretch.Ground.SOLID : Stretch.Ground.FREE;
        }
    }

    private static List<Integer> heights(Stretch.Followed followed) {
        return followed.steps().stream().map(step -> step.spot().y()).toList();
    }

    private static List<Stretch.Step> east(int to) {
        return Stretch.path(ORIGIN, EAST, new LineScan.Spot(to, 64, 0)).orElseThrow();
    }

    @Test
    void onLevelGroundEveryTileIsLevelWithTheStart() {
        var followed = Stretch.follow(east(3), new Ground(63, 63, 63, 63), false);

        assertNull(followed.stop());
        assertEquals(List.of(64, 64, 64, 64), heights(followed));
    }

    @Test
    void aStretchStepsUpOntoABlockAndDownOffIt() {
        var followed = Stretch.follow(east(5), new Ground(63, 63, 64, 64, 63, 63), false);

        assertNull(followed.stop());
        assertEquals(List.of(64, 64, 65, 65, 64, 64), heights(followed));
    }

    @Test
    void aStretchClimbsAStaircaseAColumnAtATime() {
        var followed = Stretch.follow(east(5), new Ground(63, 63, 64, 65, 66, 66), false);

        assertNull(followed.stop());
        assertEquals(List.of(64, 64, 65, 66, 67, 67), heights(followed));
    }

    @Test
    void aStretchUnderAnOverhangStaysLevel() {
        var followed = Stretch.follow(east(3), new Ground(63, 63, 63, 63).with(2, 65, 0, Stretch.Ground.SOLID), false);

        assertNull(followed.stop());
        assertEquals(List.of(64, 64, 64, 64), heights(followed));
    }

    @Test
    void aTwoBlockStepUpStopsTheStretchAtItsColumn() {
        var followed = Stretch.follow(east(4), new Ground(63, 63, 65, 65, 65), false);

        assertEquals(Stretch.Stop.UNEVEN, followed.stop());
        assertEquals(new LineScan.Spot(2, 64, 0), followed.column());
        assertEquals(List.of(64, 64), heights(followed));
    }

    @Test
    void aTwoBlockDropStopsTheStretchAtItsColumn() {
        var followed = Stretch.follow(east(4), new Ground(63, 63, 61, 61, 61), false);

        assertEquals(Stretch.Stop.UNEVEN, followed.stop());
        assertEquals(new LineScan.Spot(2, 64, 0), followed.column());
    }

    // A tile a block higher than both its neighbours is a crest, which does not connect.
    @Test
    void aOneBlockBumpStopsTheStretchAtTheBump() {
        var followed = Stretch.follow(east(4), new Ground(63, 63, 64, 63, 63), false);

        assertEquals(Stretch.Stop.UNEVEN, followed.stop());
        assertEquals(new LineScan.Spot(2, 65, 0), followed.column());
    }

    @Test
    void aOneBlockDipStopsTheStretchAtTheDip() {
        var followed = Stretch.follow(east(4), new Ground(63, 63, 62, 63, 63), false);

        assertEquals(Stretch.Stop.UNEVEN, followed.stop());
        assertEquals(new LineScan.Spot(2, 63, 0), followed.column());
    }

    @Test
    void anObstacleWhereTheTileWouldStandBlocksTheStretch() {
        var followed = Stretch.follow(east(3), new Ground(63, 63, 63, 63).with(2, 64, 0, Stretch.Ground.OBSTACLE), false);

        assertEquals(Stretch.Stop.BLOCKED, followed.stop());
        assertEquals(new LineScan.Spot(2, 64, 0), followed.column());
    }

    @Test
    void anObstacleIsNoGroundToStandOn() {
        var followed = Stretch.follow(east(3), new Ground(63, 63, 62, 63).with(2, 63, 0, Stretch.Ground.OBSTACLE), false);

        assertEquals(Stretch.Stop.UNEVEN, followed.stop());
    }

    @Test
    void aTileOnThePathIsTakenLevelWhateverItStandsOn() {
        var followed = Stretch.follow(east(3), new Ground(63, 63, 60, 63).with(2, 64, 0, Stretch.Ground.TILE), false);

        assertNull(followed.stop());
        assertEquals(List.of(64, 64, 64, 64), heights(followed));
    }

    @Test
    void aTilesTopIsGroundForALevelTileOverIt() {
        var followed = Stretch.follow(east(3), new Ground(63, 63, 62, 63).with(2, 63, 0, Stretch.Ground.TILE), false);

        assertNull(followed.stop());
        assertEquals(List.of(64, 64, 64, 64), heights(followed));
    }

    // Looking east, an end two ahead and two south, the corner at x = 2 a block up.
    @Test
    void aCornerOnAStepIsRefused() {
        var path = Stretch.path(ORIGIN, EAST, new LineScan.Spot(2, 64, 2)).orElseThrow();
        var ground = new Ground(63, 63, 64).with(2, 64, 1, Stretch.Ground.SOLID).with(2, 64, 2, Stretch.Ground.SOLID);

        var followed = Stretch.follow(path, ground, false);

        assertEquals(Stretch.Stop.SLOPE_TURNS, followed.stop());
        assertEquals(new LineScan.Spot(2, 65, 0), followed.column());
    }

    @Test
    void aCornerWhoseNextTileStepsIsRefused() {
        var path = Stretch.path(ORIGIN, EAST, new LineScan.Spot(2, 64, 2)).orElseThrow();
        var ground = new Ground(63, 63, 63).with(2, 64, 1, Stretch.Ground.SOLID).with(2, 64, 2, Stretch.Ground.SOLID);

        var followed = Stretch.follow(path, ground, false);

        assertEquals(Stretch.Stop.SLOPE_TURNS, followed.stop());
        assertEquals(new LineScan.Spot(2, 64, 0), followed.column());
    }

    @Test
    void aCornerOnLevelGroundIsLaid() {
        var path = Stretch.path(ORIGIN, EAST, new LineScan.Spot(2, 64, 2)).orElseThrow();

        assertNull(Stretch.follow(path, new Ground(63, 63, 63), false).stop());
    }

    @Test
    void anEndToBeACornerOnAStepIsRefused() {
        var followed = Stretch.follow(east(2), new Ground(63, 63, 64), true);

        assertEquals(Stretch.Stop.SLOPE_TURNS, followed.stop());
        assertEquals(new LineScan.Spot(2, 65, 0), followed.column());
    }

    @Test
    void anEndToBeACornerOnLevelGroundIsKept() {
        assertNull(Stretch.follow(east(2), new Ground(63, 63, 63), true).stop());
    }
}
