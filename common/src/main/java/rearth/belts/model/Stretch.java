package rearth.belts.model;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

/**
 * The tiles one drag of the tile item lays (PlanetaryFactory #393): from a stored start to an end,
 * in one straight leg or two joined by one corner, the first leg along the look stored with the
 * start, and never starts by turning back on its look. A stretch with corners chains such legs, each
 * from where the last ended and heading the way its last tile travels. Its columns are laid out level
 * with its start, then {@link #follow followed} over the ground (#421).
 */
public final class Stretch {

    private Stretch() {
    }

    /** One tile of a stretch and the way it travels. */
    public record Step(LineScan.Spot spot, LineScan.Travel travel) {
    }

    /** The stretch's tiles from its start, or empty when the end lies behind the look. */
    public static Optional<List<Step>> path(LineScan.Spot start, LineScan.Travel look, LineScan.Spot end) {
        var right = TileShape.right(look);
        var dx = end.x() - start.x();
        var dz = end.z() - start.z();
        var along = dx * look.x() + dz * look.z();
        var aside = dx * right.x() + dz * right.z();
        if (along < 0) return Optional.empty();

        var steps = new ArrayList<Step>();
        for (var i = 0; i < along; i++) steps.add(new Step(start.step(look, i), look));
        var corner = start.step(look, along);
        var turn = aside == 0 ? look : aside > 0 ? right : TileShape.left(look);
        for (var i = 0; i <= Math.abs(aside); i++) steps.add(new Step(corner.step(turn, i), turn));
        return Optional.of(List.copyOf(steps));
    }

    /** The legs through each corner in turn and on to the end, or empty when any leg turns back. */
    public static Optional<List<Step>> path(LineScan.Spot start, LineScan.Travel look, List<LineScan.Spot> corners, LineScan.Spot end) {
        // A later leg crossing an earlier tile turns it rather than laying it twice.
        var steps = new LinkedHashMap<LineScan.Spot, Step>();
        var from = start;
        var heading = look;
        var ends = new ArrayList<>(corners);
        ends.add(end);
        for (var to : ends) {
            var leg = path(from, heading, to);
            if (leg.isEmpty()) return Optional.empty();
            for (var step : leg.get()) steps.put(step.spot(), step);
            from = leg.get().getLast().spot();
            heading = leg.get().getLast().travel();
        }
        return Optional.of(List.copyOf(steps.values()));
    }

    /** What stands at a spot, as a stretch following the ground reads it. */
    public enum Ground {
        /** Air, a plant or snow: a tile may stand here. */
        FREE,
        /** A belt tile, which the stretch takes level wherever it stands and whose top holds a tile over it. */
        TILE,
        /** A block whose top face a tile may stand on and a stretch may climb onto. */
        SOLID,
        /** Anything else, which neither holds a tile nor makes way for one. */
        OBSTACLE
    }

    /** The world a stretch follows. */
    public interface Terrain {

        Ground at(LineScan.Spot spot);

        /** Whether the tile at this spot belongs to a line running across this travel. */
        default boolean across(LineScan.Spot spot, LineScan.Travel travel) {
            return false;
        }
    }

    /** Why a stretch cannot follow the ground. */
    public enum Stop {
        /** Something stands where the column's tile would. */
        BLOCKED,
        /** The ground steps more than a block, or a tile would be a crest or a valley, which does not connect. */
        UNEVEN,
        /** A corner would be a slope, which never turns (#419). */
        SLOPE_TURNS,
        /** A run of lines across the path has no room beside it for a crossing's top and foot (#422). */
        NO_ROOM_TO_CROSS
    }

    /**
     * A stretch laid over the ground: its tiles at their heights, and where and why it stops, if it
     * does. A stretch stopped by the ground ends on the tile at the column it names, so the preview
     * shows the tile that cannot be laid; one stopped at a corner holds every tile.
     */
    public record Followed(List<Step> steps, @Nullable Stop stop, LineScan.@Nullable Spot column) {

        public Followed {
            steps = List.copyOf(steps);
        }
    }

    /** How far up or down a column is searched for its ground. */
    static final int REACH = 32;

    /**
     * The {@code path}'s tiles over the ground, as low as they can go (#421). Each column's floor is
     * the height a tile would stand on its ground; the tiles take the lowest heights at or above every
     * floor that change by at most a block a column, so a stretch climbs early through the air to
     * clear a wall and comes down through the air off a drop, on wedges (ADR-0085). A top never
     * crests: a one-column peak is widened to a two-tile top. A line running across the path is
     * climbed over, the tile over it level and the tile each side of it level with that one (#422);
     * the start is taken as it stands, and an end on such a line is left for the stretch to feed its
     * side. The start keeps its height, and with {@code cornerAtEnd} the last tile is a corner still
     * to be turned.
     */
    public static Followed follow(List<Step> path, Terrain terrain, boolean cornerAtEnd) {
        return follow(path, terrain, cornerAtEnd, true);
    }

    private static Followed follow(List<Step> path, Terrain terrain, boolean cornerAtEnd, boolean endMayJoin) {
        if (path.isEmpty()) return new Followed(List.of(), null, null);
        var n = path.size();
        var floors = new int[n];
        var crossed = new boolean[n];
        var reference = path.getFirst().spot().y();
        for (var i = 0; i < n; i++) {
            var column = path.get(i).spot();
            var travel = path.get(i).travel();
            var scan = floor(new LineScan.Spot(column.x(), reference, column.z()), terrain);
            if (scan.stop() != null) {
                var laid = laidTo(path, floors, i);
                laid.add(at(path.get(i), scan.height()));
                return stopped(laid, i, scan.stop(), crossed);
            }
            var height = scan.height();
            var stand = new LineScan.Spot(column.x(), height, column.z());
            var onLine = i > 0 && lineAt(terrain, stand, travel);
            if (i > 0 && i == n - 1 && (onLine || lineAt(terrain, stand.up(-1), travel))) {
                if (endMayJoin) return follow(path.subList(0, i), terrain, cornerAtEnd, false);
                // A line behind the one aimed at leaves the crossing no room (#422).
                var laid = laidTo(path, floors, i);
                laid.add(at(path.get(i), onLine ? height + 1 : height));
                return new Followed(laid, Stop.NO_ROOM_TO_CROSS, laid.getLast().spot());
            }
            if (onLine) height++;
            floors[i] = height;
            crossed[i] = i > 0 && i < n - 1 && lineAt(terrain, new LineScan.Spot(column.x(), height - 1, column.z()), travel);
            reference = floors[i];
        }
        if (floors[0] != path.getFirst().spot().y()) {
            return new Followed(List.of(at(path.getFirst(), floors[0])), Stop.UNEVEN, at(path.getFirst(), floors[0]).spot());
        }
        // Lines side by side are crossed as one, level over each, a top either side of the run.
        for (var i = 1; i + 1 < n; i++) {
            if (!crossed[i]) continue;
            floors[i - 1] = Math.max(floors[i - 1], floors[i]);
            floors[i + 1] = Math.max(floors[i + 1], floors[i]);
        }

        var heights = envelope(floors);
        for (var fixes = 0; ; fixes++) {
            var turn = firstTurn(heights);
            if (turn < 0 || fixes > n * REACH) break;
            if (heights[turn] < heights[turn - 1]) floors[turn] = heights[turn] + 1;
            else floors[turn - 1 > 0 ? turn - 1 : turn + 1] = heights[turn];
            heights = envelope(floors);
        }
        var laid = new ArrayList<Step>();
        for (var i = 0; i < n; i++) laid.add(at(path.get(i), heights[i]));

        if (heights[0] != path.getFirst().spot().y()) {
            var forcing = 1;
            for (var j = 1; j < n; j++) if (floors[j] - j > floors[forcing] - forcing) forcing = j;
            return stopped(laid, forcing, Stop.UNEVEN, crossed);
        }
        if (firstTurn(heights) >= 0) return stopped(laid, firstTurn(heights), Stop.UNEVEN, crossed);
        for (var i = 0; i < n; i++) {
            var spot = laid.get(i).spot();
            var here = terrain.at(spot);
            if (here == Ground.TILE) continue;
            if (here != Ground.FREE) return stopped(laid, i, Stop.BLOCKED, crossed);
            var wedged = i > 0 && riseInto(laid, i) > 0 || i + 1 < n && riseInto(laid, i + 1) < 0;
            if (!wedged && !holds(terrain.at(spot.up(-1)))) return stopped(laid, i, Stop.UNEVEN, crossed);
        }

        for (var i = 1; i < n; i++) {
            if (laid.get(i).travel().equals(laid.get(i - 1).travel())) continue;
            if (riseInto(laid, i) != 0 || i + 1 < n && riseInto(laid, i + 1) != 0) {
                return new Followed(laid, Stop.SLOPE_TURNS, laid.get(i).spot());
            }
        }
        if (cornerAtEnd && n >= 2 && riseInto(laid, n - 1) != 0) {
            return new Followed(laid, Stop.SLOPE_TURNS, laid.getLast().spot());
        }
        return new Followed(laid, null, null);
    }

    private record Scan(int height, @Nullable Stop stop) {
    }

    // The height a tile stands at on this column's ground, searched from the column before's.
    private static Scan floor(LineScan.Spot from, Terrain terrain) {
        var at = terrain.at(from);
        if (at == Ground.TILE) return new Scan(from.y(), null);
        if (at == Ground.OBSTACLE) return new Scan(from.y(), Stop.BLOCKED);
        var spot = from;
        if (at == Ground.SOLID) {
            for (var i = 0; terrain.at(spot) == Ground.SOLID; i++) {
                if (i == REACH) return new Scan(from.y(), Stop.UNEVEN);
                spot = spot.up(1);
            }
            return terrain.at(spot) == Ground.OBSTACLE ? new Scan(spot.y(), Stop.BLOCKED) : new Scan(spot.y(), null);
        }
        for (var i = 0; terrain.at(spot.up(-1)) == Ground.FREE; i++) {
            if (i == REACH) return new Scan(from.y(), Stop.UNEVEN);
            spot = spot.up(-1);
        }
        return holds(terrain.at(spot.up(-1))) ? new Scan(spot.y(), null) : new Scan(spot.y(), Stop.UNEVEN);
    }

    // The lowest heights at or above every floor that change by at most a block a column.
    private static int[] envelope(int[] floors) {
        var heights = floors.clone();
        for (var i = 1; i < heights.length; i++) heights[i] = Math.max(heights[i], heights[i - 1] - 1);
        for (var i = heights.length - 2; i >= 0; i--) heights[i] = Math.max(heights[i], heights[i + 1] - 1);
        return heights;
    }

    // The first tile that is a crest or a valley, which does not connect, or -1.
    private static int firstTurn(int[] heights) {
        for (var i = 1; i + 1 < heights.length; i++) {
            var in = heights[i] - heights[i - 1];
            var out = heights[i + 1] - heights[i];
            if (in != 0 && in == -out) return i;
        }
        return -1;
    }

    // A stop where a crossing's top or foot must stand is the nearest crossing's (#422).
    private static Followed stopped(List<Step> laid, int column, Stop stop, boolean[] crossed) {
        for (var distance = 0; distance <= 2; distance++) {
            for (var c : new int[]{column - distance, column + distance}) {
                if (c >= 0 && c < laid.size() && crossed[c]) {
                    return new Followed(laid.subList(0, c + 1), Stop.NO_ROOM_TO_CROSS, laid.get(c).spot());
                }
            }
        }
        return new Followed(laid.subList(0, column + 1), stop, laid.get(column).spot());
    }

    private static boolean lineAt(Terrain terrain, LineScan.Spot spot, LineScan.Travel travel) {
        return terrain.at(spot) == Ground.TILE && terrain.across(spot, travel);
    }

    private static List<Step> laidTo(List<Step> path, int[] floors, int end) {
        var laid = new ArrayList<Step>();
        for (var j = 0; j < end; j++) laid.add(at(path.get(j), floors[j]));
        return laid;
    }

    private static Step at(Step step, int height) {
        return new Step(new LineScan.Spot(step.spot().x(), height, step.spot().z()), step.travel());
    }

    private static boolean holds(Ground below) {
        return below == Ground.SOLID || below == Ground.TILE;
    }

    private static int riseInto(List<Step> laid, int index) {
        return laid.get(index).spot().y() - laid.get(index - 1).spot().y();
    }
}
