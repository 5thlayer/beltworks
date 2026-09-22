package rearth.belts.model;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Which tiles merge into one transport line (PlanetaryFactory #398). */
class LineScanTest {

    private static final LineScan.Travel EAST = new LineScan.Travel(1, 0);
    private static final LineScan.Travel WEST = new LineScan.Travel(-1, 0);
    private static final LineScan.Travel NORTH = new LineScan.Travel(0, -1);

    private final Map<LineScan.Spot, LineScan.Travel> world = new HashMap<>();

    private void tile(int x, int z, LineScan.Travel travel) {
        world.put(new LineScan.Spot(x, 0, z), travel);
    }

    private List<LineScan.Spot> through(int x, int z) {
        return LineScan.through(new LineScan.Spot(x, 0, z), world::get);
    }

    @Test
    void noTileIsNoLine() {
        assertTrue(through(0, 0).isEmpty());
    }

    @Test
    void oneTileIsALineOfOne() {
        tile(0, 0, EAST);
        assertEquals(List.of(new LineScan.Spot(0, 0, 0)), through(0, 0));
    }

    @Test
    void contiguousTilesOfOneDirectionAreOneLineHeadFirst() {
        for (var x = 0; x < 4; x++) tile(x, 0, EAST);

        var line = through(2, 0);

        assertEquals(4, line.size());
        assertEquals(new LineScan.Spot(0, 0, 0), line.getFirst());
        assertEquals(new LineScan.Spot(3, 0, 0), line.getLast());
    }

    @Test
    void everyTileOfALineScansToTheSameLine() {
        for (var x = 0; x < 4; x++) tile(x, 0, EAST);

        for (var x = 0; x < 4; x++) assertEquals(through(0, 0), through(x, 0));
    }

    @Test
    void aTileOfAnotherDirectionEndsTheLine() {
        tile(0, 0, EAST);
        tile(1, 0, EAST);
        tile(2, 0, NORTH);

        assertEquals(List.of(new LineScan.Spot(0, 0, 0), new LineScan.Spot(1, 0, 0)), through(1, 0));
    }

    @Test
    void aFacingTileDoesNotJoinTheLine() {
        tile(0, 0, EAST);
        tile(1, 0, WEST);

        assertEquals(List.of(new LineScan.Spot(0, 0, 0)), through(0, 0));
    }

    @Test
    void aGapSplitsTheLine() {
        tile(0, 0, EAST);
        tile(2, 0, EAST);
        tile(3, 0, EAST);

        assertEquals(1, through(0, 0).size());
        assertEquals(2, through(3, 0).size());
    }

    @Test
    void aTileOnAnotherRowIsAnotherLine() {
        tile(0, 0, EAST);
        tile(0, 1, EAST);

        assertEquals(List.of(new LineScan.Spot(0, 0, 0)), through(0, 0));
    }

    // Tiles are merged by their direction of travel, whatever tier they are (#347).
    @Test
    void tiersDoNotSplitALine() {
        for (var x = 0; x < 3; x++) tile(x, 0, EAST);

        assertEquals(3, through(0, 0).size());
    }
}
