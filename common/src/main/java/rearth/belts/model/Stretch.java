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
    }

    /** Why a stretch cannot follow the ground. */
    public enum Stop {
        /** Something stands where the column's tile would. */
        BLOCKED,
        /** The ground steps more than a block, or a tile would be a crest or a valley, which does not connect. */
        UNEVEN,
        /** A corner would be a slope, which never turns (#419). */
        SLOPE_TURNS
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

    /**
     * The {@code path}'s tiles over the ground, each at the height the one before it leaves it: level
     * where there is ground, else a block up onto a step, else a block down off one (#421). Only a
     * path's columns count; the first tile starts from the start's own height. With
     * {@code cornerAtEnd} the last tile is a corner still to be turned, so it must be level with the
     * one before it.
     */
    public static Followed follow(List<Step> path, Terrain terrain, boolean cornerAtEnd) {
        var laid = new ArrayList<Step>();
        var height = path.isEmpty() ? 0 : path.getFirst().spot().y();
        for (var step : path) {
            var here = new LineScan.Spot(step.spot().x(), height, step.spot().z());
            var at = terrain.at(here);
            int rise;
            if (at == Ground.TILE || (at == Ground.FREE && holds(terrain.at(here.up(-1))))) rise = 0;
            else if (at == Ground.SOLID && clear(terrain.at(here.up(1)))) rise = 1;
            else if (at == Ground.FREE && terrain.at(here.up(-1)) == Ground.FREE && holds(terrain.at(here.up(-2)))) rise = -1;
            else {
                laid.add(new Step(here, step.travel()));
                return new Followed(laid, at == Ground.OBSTACLE ? Stop.BLOCKED : Stop.UNEVEN, here);
            }

            var placed = new Step(here.up(rise), step.travel());
            if (laid.size() >= 2 && rise != 0 && rise == -riseInto(laid, laid.size() - 1)) {
                return new Followed(laid, Stop.UNEVEN, laid.getLast().spot());
            }
            laid.add(placed);
            height = placed.spot().y();
        }

        for (var i = 1; i < laid.size(); i++) {
            if (laid.get(i).travel().equals(laid.get(i - 1).travel())) continue;
            if (riseInto(laid, i) != 0 || i + 1 < laid.size() && riseInto(laid, i + 1) != 0) {
                return new Followed(laid, Stop.SLOPE_TURNS, laid.get(i).spot());
            }
        }
        if (cornerAtEnd && laid.size() >= 2 && riseInto(laid, laid.size() - 1) != 0) {
            return new Followed(laid, Stop.SLOPE_TURNS, laid.getLast().spot());
        }
        return new Followed(laid, null, null);
    }

    private static boolean clear(Ground above) {
        return above == Ground.FREE || above == Ground.TILE;
    }

    private static boolean holds(Ground below) {
        return below == Ground.SOLID || below == Ground.TILE;
    }

    private static int riseInto(List<Step> laid, int index) {
        return laid.get(index).spot().y() - laid.get(index - 1).spot().y();
    }
}
