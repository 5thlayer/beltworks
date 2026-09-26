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
 * height after it. The climb never turns and ends before the leg's last anchor, where the next leg
 * may turn, or it is refused. A tile of a line crossing the leg and anything a tile can't take the
 * place of are refused where they stand, so Groundworks can go round them; a stretch never cuts a
 * line. The one exception is a line across the leg's last anchor, which the leg joins by feeding its
 * side.
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
        /** The rise doesn't fit before the leg turns or reaches its last anchor, and a slope never turns. */
        SLOPE_TURNS,
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

    /** The tiles of the leg from {@code from} rising {@code rise} along {@code route}, which starts at {@code from}'s column. */
    public static Shaped shape(LineScan.Spot from, int rise, List<Column> route, Terrain terrain) {
        var last = route.size() - 1;
        var tiles = new ArrayList<Step>();
        Stop stop = fits(rise, route) ? null : Stop.SLOPE_TURNS;
        LineScan.Spot at = null;
        for (var i = 0; i <= last; i++) {
            var column = route.get(i);
            var spot = new LineScan.Spot(column.x(), from.y() + height(i, rise), column.z());
            var ground = terrain.at(spot, column.travel());
            if (i == last && i > 0 && ground == Ground.ACROSS) break;
            tiles.add(new Step(spot, column.travel()));
            if (stop != null) continue;
            if (ground == Ground.ACROSS) stop = Stop.CROSSES_A_LINE;
            else if (ground == Ground.OBSTACLE) stop = Stop.BLOCKED;
            if (stop != null) at = spot;
        }
        return new Shaped(tiles, stop, at);
    }

    /** The height of the leg's {@code index}th column above its first anchor. */
    static int height(int index, int rise) {
        var slopes = Math.abs(rise) + 1;
        if (rise == 0 || index == 0) return 0;
        if (index > slopes) return rise;
        return Integer.signum(rise) * (index - 1);
    }

    // The anchor and the slopes run one way, the tile after the top stands before the last anchor.
    private static boolean fits(int rise, List<Column> route) {
        if (rise == 0) return true;
        var afterTop = Math.abs(rise) + 2;
        if (afterTop >= route.size()) return false;
        for (var i = 1; i < afterTop; i++) {
            if (!route.get(i).travel().equals(route.getFirst().travel())) return false;
        }
        return true;
    }
}
