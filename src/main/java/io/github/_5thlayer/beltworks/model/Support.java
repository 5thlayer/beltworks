// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.model;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The struts that show a raised line held up, where a builder would put them (ADR 0012): a crossbar
 * under the tile and a leg down each corner of its block. Like a tile's shape and pitch, it is
 * derived from the tile and the world around it, never placed, and never limits how far a line
 * floats. Standing at block corners, a leg never crosses where items pass.
 */
public record Support(List<Leg> legs) {

    /** How far a leg reaches below a level tile's surface before it runs on out of sight, in blocks. */
    public static final int REACH = 64;

    private static final List<Corner> CORNERS = List.of(new Corner(0, 0), new Corner(1, 0), new Corner(0, 1), new Corner(1, 1));

    /** What fills a block, as a support reads it. */
    public enum Fill {
        /** Air, a fluid, a wedge or any block without a solid top: a leg passes it, and it holds nothing up. */
        OPEN,
        /** A block with a solid top: it holds up a tile on it, and a leg stands on it. */
        SOLID,
        /** A tile, loader or splitter half: it holds up a tile on it, and a leg stands on its surface. */
        BELT
    }

    /** The world under and beside a tile, as its support reads it. */
    public interface Ground {

        Fill fill(LineScan.Spot spot);

        /** Where a leg at {@code corner} stops on the block at {@code spot}, in blocks from its floor. */
        double top(LineScan.Spot spot, Corner corner);

        /** Whether the block at {@code spot} is solid on its face turned {@code toward}, so a tile there is fixed to it. */
        boolean fixes(LineScan.Spot spot, LineScan.Travel toward);
    }

    /** A corner of a block, as unit steps east and south of its north-west corner. */
    public record Corner(int x, int z) {
    }

    /**
     * A leg at {@code corner}, from {@code top} down to {@code bottom}, in blocks from its tile's
     * floor. A leg not {@code standing} found nothing within reach and is drawn running on past it.
     */
    public record Leg(Corner corner, double top, double bottom, boolean standing) {
    }

    /**
     * The support a tile at {@code spot} travelling {@code travel} shows, or null where it shows
     * none: only where the tile turns or is its line's first or last, and only over air. Only a
     * level tile shows one yet.
     */
    public static @Nullable Support at(LineScan.Spot spot, LineScan.Travel travel, TileShape.Around around, Ground ground) {
        if (Pitch.at(spot, travel, around) != Pitch.LEVEL) return null;
        var shape = TileShape.at(spot, travel, around);
        if (shape == TileShape.STRAIGHT && !firstOrLast(spot, travel, around)) return null;
        if (heldUp(spot, ground) || fixed(spot, travel, shape, ground)) return null;
        return new Support(CORNERS.stream().map(corner -> leg(spot, corner, ground)).toList());
    }

    // A level tile's line runs on through a level tile behind it travelling its way, and through the
    // tile ahead only if that tile enters from it.
    private static boolean firstOrLast(LineScan.Spot spot, LineScan.Travel travel, TileShape.Around around) {
        if (!travel.equals(around.tile(spot.step(travel, -1)))) return true;
        var ahead = spot.step(travel, 1);
        var onward = around.tile(ahead);
        return onward == null
                 || !TileShape.at(ahead, onward, around).entry(onward).equals(travel)
                 || Pitch.at(ahead, onward, around).feeder() != 0;
    }

    private static boolean heldUp(LineScan.Spot spot, Ground ground) {
        var below = ground.fill(spot.up(-1));
        return below == Fill.SOLID || below == Fill.BELT;
    }

    // Fixed to a solid block on a side its line does not use: left or right of a straight tile, or
    // behind a corner and on the side opposite its entry.
    private static boolean fixed(LineScan.Spot spot, LineScan.Travel travel, TileShape shape, Ground ground) {
        var free = switch (shape) {
            case STRAIGHT -> List.of(TileShape.left(travel), TileShape.right(travel));
            case FROM_LEFT -> List.of(TileShape.right(travel), TileShape.back(travel));
            case FROM_RIGHT -> List.of(TileShape.left(travel), TileShape.back(travel));
        };
        return free.stream().anyMatch(side -> ground.fixes(spot.step(side, 1), TileShape.back(side)));
    }

    private static Leg leg(LineScan.Spot spot, Corner corner, Ground ground) {
        for (var down = 1; down <= REACH + 1; down++) {
            var at = spot.up(-down);
            if (ground.fill(at) == Fill.OPEN) continue;
            var bottom = ground.top(at, corner) - down;
            if (Pitch.SURFACE - bottom > REACH) break;
            return new Leg(corner, Pitch.SURFACE, bottom, true);
        }
        return new Leg(corner, Pitch.SURFACE, Pitch.SURFACE - REACH, false);
    }

    /** The surface of a belt tile of {@code pitch} travelling {@code travel} at {@code corner}, where a leg stands on it. */
    public static double surface(Pitch pitch, LineScan.Travel travel, Corner corner) {
        return pitch.surface((corner.x() - 0.5) * travel.x() + (corner.z() - 0.5) * travel.z() + 0.5);
    }
}
