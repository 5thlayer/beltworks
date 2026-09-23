package rearth.belts.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The tiles one drag of the tile item lays: one leg or two, the first along the stored look (PlanetaryFactory #393). */
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
}
