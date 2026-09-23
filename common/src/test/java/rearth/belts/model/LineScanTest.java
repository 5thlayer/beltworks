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

    private static final LineScan.Travel SOUTH = new LineScan.Travel(0, 1);

    private final Map<LineScan.Spot, LineScan.Travel> world = new HashMap<>();

    private void tile(int x, int z, LineScan.Travel travel) {
        world.put(new LineScan.Spot(x, 0, z), travel);
    }

    // Each tile's shape is derived from the tiles around it, as the world derives it.
    private LineScan.Piece piece(LineScan.Spot spot) {
        var travel = world.get(spot);
        if (travel == null) return null;
        var shape = TileShape.at(spot, travel, (from, feeding) -> feeding.equals(world.get(from)));
        return new LineScan.Piece(travel, shape.entry(travel));
    }

    private LineScan.Scan scan(int x, int z) {
        return LineScan.through(new LineScan.Spot(x, 0, z), this::piece);
    }

    private List<LineScan.Spot> through(int x, int z) {
        return scan(x, z).spots();
    }

    private static LineScan.Spot at(int x, int z) {
        return new LineScan.Spot(x, 0, z);
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

    // The L of #391: up a column, then east along a row, one line through the corner.
    @Test
    void aCornerIsInsideItsLine() {
        tile(0, 2, NORTH);
        tile(0, 1, NORTH);
        tile(0, 0, EAST);
        tile(1, 0, EAST);
        tile(2, 0, EAST);

        var line = through(2, 0);

        assertEquals(List.of(at(0, 2), at(0, 1), at(0, 0), at(1, 0), at(2, 0)), line);
        assertEquals(line, through(0, 2));
    }

    @Test
    void aSideLoadIsTwoLines() {
        tile(0, 0, EAST);
        tile(1, 0, EAST);
        tile(2, 0, EAST);
        tile(1, 2, NORTH);
        tile(1, 1, NORTH);

        assertEquals(List.of(at(0, 0), at(1, 0), at(2, 0)), through(1, 0));
        assertEquals(List.of(at(1, 2), at(1, 1)), through(1, 1));
    }

    @Test
    void aTileFedFromBothSidesStartsTheLine() {
        tile(1, 0, EAST);
        tile(2, 0, EAST);
        tile(1, -1, SOUTH);
        tile(1, 1, NORTH);

        assertEquals(List.of(at(1, 0), at(2, 0)), through(2, 0));
        assertEquals(List.of(at(1, -1)), through(1, -1));
    }

    @Test
    void aRingIsOneLineWithTheSameHeadFromEveryTile() {
        tile(0, 0, EAST);
        tile(1, 0, SOUTH);
        tile(1, 1, WEST);
        tile(0, 1, NORTH);

        var scan = scan(1, 1);

        assertTrue(scan.ring());
        assertEquals(4, scan.spots().size());
        for (var spot : List.of(at(0, 0), at(1, 0), at(0, 1))) assertEquals(scan, scan(spot.x(), spot.z()));
    }

    @Test
    void aLineWithAnEndIsNoRing() {
        tile(0, 0, EAST);
        tile(1, 0, EAST);

        assertEquals(false, scan(0, 0).ring());
    }

    // Tiles are merged by their direction of travel, whatever tier they are (#347).
    @Test
    void tiersDoNotSplitALine() {
        for (var x = 0; x < 3; x++) tile(x, 0, EAST);

        assertEquals(3, through(0, 0).size());
    }
}
