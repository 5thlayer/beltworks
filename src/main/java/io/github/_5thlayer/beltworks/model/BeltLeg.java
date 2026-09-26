// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.model;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The tiles a belt builds one leg of a stretch with. Groundworks runs the stretch and hands the belt
 * each leg: its first anchor, its rise and its route seen from above (Groundworks ADR 0004). The leg
 * never follows the ground: it climbs only by its rise.
 *
 * <p>A rise of k is k+1 slopes right after the first anchor, along the leg's first direction: a
 * foot at the anchor's height, its middles, and a top k up, the leg running level at the top's
 * height after it. The climb never turns, or it is refused. It ends before an intermediate anchor,
 * where the next leg may turn, and may end on the stretch's end, which nothing follows.
 *
 * <p>A tile of a line crossing the leg and anything a tile can't take the place of are refused
 * where they stand, so Groundworks can go round them; a stretch never cuts a line. It joins one
 * only where that cuts nothing: its end feeds the side of a line across it, and its start takes up
 * a line's last tile, which the line then flows on through.
 */
public final class BeltLeg {

    private BeltLeg() {
    }

    /** A column the leg passes, seen from above, and the way it travels through it. */
    public record Column(int x, int z, LineScan.Travel travel) {
    }

    /** One tile of a leg, where it stands and the way it travels. */
    public record Step(LineScan.Spot spot, LineScan.Travel travel) {
    }

    /** What stands where one of the leg's tiles would, as the leg reads it. */
    public enum Ground {
        /** Air, a plant or snow: a tile may stand here. */
        FREE,
        /** A tile running either way along the leg's travel, which the leg keeps as its own. */
        ALONG,
        /** A tile of a line crossing the leg's travel. */
        ACROSS,
        /** The last tile of a line crossing the leg's travel, level and feeding nothing. */
        LINE_END,
        /** Anything else, which a tile can't take the place of. */
        OBSTACLE
    }

    /** The world a leg is laid in. */
    public interface Terrain {

        /** What stands at {@code spot}, for a tile travelling {@code travel} there. */
        Ground at(LineScan.Spot spot, LineScan.Travel travel);
    }

    /** Why a leg can't be laid. */
    public enum Stop {
        /** The rise doesn't fit before the leg turns or reaches its last anchor. */
        RISE_DOES_NOT_FIT,
        /** Something a tile can't take the place of stands in the way. */
        BLOCKED,
        /** A line crosses the leg, which the leg would cut. */
        CROSSES_A_LINE
    }

    /**
     * The leg's tiles, each at its height, less one where the leg joins a line; and why it can't be
     * laid, if it can't, with the spot that stops it when that is one, the first along the leg. A
     * refused leg still holds every tile, so it is drawn where it would go.
     */
    public record Shaped(List<Step> tiles, @Nullable Stop stop, LineScan.@Nullable Spot at) {

        public Shaped {
            tiles = List.copyOf(tiles);
        }
    }

    /**
     * The tiles of the leg from {@code from} rising {@code rise} along {@code route}, which starts at
     * {@code from}'s column. The stretch arrives at {@code from} travelling {@code arrives}, or
     * starts there when it is null, and {@code ends} says whether the leg's last anchor is the
     * stretch's end.
     */
    public static Shaped shape(LineScan.Spot from, int rise, List<Column> route, LineScan.@Nullable Travel arrives,
                               boolean ends, Terrain terrain) {
        var last = route.size() - 1;
        var joins = ends && last > 0 && isLine(terrain.at(spot(from, rise, route, last), route.getLast().travel()));
        var tiles = new ArrayList<Step>();
        Stop stop = fits(rise, route, ends ? (joins ? last - 1 : last) : last - 1) ? null : Stop.RISE_DOES_NOT_FIT;
        LineScan.Spot at = null;
        for (var i = 0; i <= last; i++) {
            var column = route.get(i);
            var spot = spot(from, rise, route, i);
            if (i == last && joins) break;
            var ground = terrain.at(spot, column.travel());
            tiles.add(new Step(spot, column.travel()));
            if (stop != null) continue;
            var takesItsEnd = i == 0 && arrives == null && ground == Ground.LINE_END;
            if (isLine(ground) && !takesItsEnd) stop = Stop.CROSSES_A_LINE;
            else if (ground == Ground.OBSTACLE) stop = Stop.BLOCKED;
            if (stop != null) at = spot;
        }
        return new Shaped(tiles, stop, at);
    }

    private static boolean isLine(Ground ground) {
        return ground == Ground.ACROSS || ground == Ground.LINE_END;
    }

    private static LineScan.Spot spot(LineScan.Spot from, int rise, List<Column> route, int index) {
        var column = route.get(index);
        return new LineScan.Spot(column.x(), from.y() + height(index, rise), column.z());
    }

    /** The height of the leg's {@code index}th column above its first anchor. */
    static int height(int index, int rise) {
        var slopes = Math.abs(rise) + 1;
        if (rise == 0 || index == 0) return 0;
        if (index > slopes) return rise;
        return Integer.signum(rise) * (index - 1);
    }

    // The anchor and the slopes run one way, and the last slope stands at or before {@code lastSlope}.
    private static boolean fits(int rise, List<Column> route, int lastSlope) {
        if (rise == 0) return true;
        var slopes = Math.abs(rise) + 1;
        if (slopes > lastSlope) return false;
        for (var i = 1; i <= slopes; i++) {
            if (!route.get(i).travel().equals(route.getFirst().travel())) return false;
        }
        return true;
    }
}
