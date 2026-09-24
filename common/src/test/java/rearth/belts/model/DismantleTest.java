package rearth.belts.model;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Which tiles a dismantle takes up between a start and an end (PlanetaryFactory #404). */
class DismantleTest {

    private static final LineScan.Travel EAST = new LineScan.Travel(1, 0);
    private static final LineScan.Travel WEST = new LineScan.Travel(-1, 0);
    private static final LineScan.Travel NORTH = new LineScan.Travel(0, -1);
    private static final LineScan.Travel SOUTH = new LineScan.Travel(0, 1);

    private final Map<LineScan.Spot, LineScan.Travel> world = new HashMap<>();

    private final TileShape.Around around = new TileShape.Around() {
        @Override
        public LineScan.Travel tile(LineScan.Spot spot) {
            return world.get(spot);
        }

        @Override
        public boolean feeds(LineScan.Spot from, LineScan.Travel travel) {
            return travel.equals(world.get(from));
        }
    };

    private void tile(int x, int y, int z, LineScan.Travel travel) {
        world.put(at(x, y, z), travel);
    }

    private LineScan.Piece piece(LineScan.Spot spot) {
        var travel = world.get(spot);
        if (travel == null) return null;
        return new LineScan.Piece(travel, TileShape.at(spot, travel, around).entry(travel), Pitch.at(spot, travel, around));
    }

    private Dismantle.Span span(LineScan.Spot start, LineScan.Spot end) {
        return Dismantle.span(start, end, this::piece);
    }

    private static LineScan.Spot at(int x, int y, int z) {
        return new LineScan.Spot(x, y, z);
    }

    @Test
    void aSpanDownstreamRunsFromStartToEnd() {
        for (var x = 0; x < 6; x++) tile(x, 0, 0, EAST);

        var span = span(at(1, 0, 0), at(4, 0, 0));

        assertNull(span.refusal());
        assertEquals(List.of(at(1, 0, 0), at(2, 0, 0), at(3, 0, 0), at(4, 0, 0)), span.spots());
    }

    @Test
    void aSpanUpstreamRunsFromStartBackToEnd() {
        for (var x = 0; x < 6; x++) tile(x, 0, 0, EAST);

        assertEquals(List.of(at(4, 0, 0), at(3, 0, 0), at(2, 0, 0), at(1, 0, 0)), span(at(4, 0, 0), at(1, 0, 0)).spots());
    }

    @Test
    void anEndOnTheStartIsThatOneTile() {
        for (var x = 0; x < 3; x++) tile(x, 0, 0, EAST);

        assertEquals(List.of(at(1, 0, 0)), span(at(1, 0, 0), at(1, 0, 0)).spots());
    }

    @Test
    void aSpanFollowsItsLineRoundACorner() {
        tile(0, 0, 2, NORTH);
        tile(0, 0, 1, NORTH);
        tile(0, 0, 0, EAST);
        tile(1, 0, 0, EAST);

        assertEquals(List.of(at(0, 0, 2), at(0, 0, 1), at(0, 0, 0), at(1, 0, 0)), span(at(0, 0, 2), at(1, 0, 0)).spots());
    }

    @Test
    void aSpanFollowsItsLineUpAndDownASlope() {
        int[] heights = {0, 0, 1, 1, 0, 0};
        for (var x = 0; x < heights.length; x++) tile(x, heights[x], 0, EAST);

        assertEquals(List.of(at(5, 0, 0), at(4, 0, 0), at(3, 1, 0), at(2, 1, 0), at(1, 0, 0), at(0, 0, 0)),
          span(at(5, 0, 0), at(0, 0, 0)).spots());
    }

    @Test
    void aSpanRoundARingRunsDownstreamFromTheStart() {
        tile(0, 0, 0, EAST);
        tile(1, 0, 0, SOUTH);
        tile(1, 0, 1, WEST);
        tile(0, 0, 1, NORTH);

        assertEquals(List.of(at(1, 0, 1), at(0, 0, 1), at(0, 0, 0)), span(at(1, 0, 1), at(0, 0, 0)).spots());
    }

    @Test
    void anEndOffTheStartsLineIsRefusedAndSpansNothing() {
        for (var x = 0; x < 3; x++) tile(x, 0, 0, EAST);
        for (var x = 0; x < 3; x++) tile(x, 0, 2, EAST);

        var span = span(at(0, 0, 0), at(2, 0, 2));

        assertEquals(Dismantle.Refusal.OFF_LINE, span.refusal());
        assertEquals(List.of(), span.spots());
    }

    @Test
    void aSideLoadingLineIsAnotherLine() {
        for (var x = 0; x < 3; x++) tile(x, 0, 0, EAST);
        tile(1, 0, 2, NORTH);
        tile(1, 0, 1, NORTH);

        assertEquals(Dismantle.Refusal.OFF_LINE, span(at(0, 0, 0), at(1, 0, 2)).refusal());
    }

    @Test
    void anEndWhereThereIsNoTileIsRefused() {
        for (var x = 0; x < 3; x++) tile(x, 0, 0, EAST);

        assertEquals(Dismantle.Refusal.OFF_LINE, span(at(0, 0, 0), at(5, 0, 0)).refusal());
    }
}
