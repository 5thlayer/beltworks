// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.model;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The struts that show a raised line held up, where a builder would put them (ADR 0012): a crossbar
 * under the tile and a leg down each corner of its block. Like a tile's shape and pitch, it is
 * derived from the tile and the world around it, never placed, and never limits how far a line
 * floats. Standing at the edge of its tile, a leg never crosses where items pass.
 */
public record Support(List<Leg> legs) {

    /** How far a leg reaches below a level tile's surface before it runs on out of sight, in blocks. */
    public static final int REACH = 64;

    // Round the block, so each corner's neighbours are the corners a strut ties it to.
    private static final List<double[]> CORNERS = List.of(new double[] {0, 0}, new double[] {1, 0}, new double[] {1, 1}, new double[] {0, 1});

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

        /** Where a leg standing at {@code x}, {@code z} in its block stops on the block at {@code spot}, in blocks from its floor. */
        double top(LineScan.Spot spot, double x, double z);

        /** Whether the block at {@code spot} is solid on its face turned {@code toward}, so a tile there is fixed to it. */
        boolean fixes(LineScan.Spot spot, LineScan.Travel toward);
    }

    /**
     * A leg whose outer edge stands at {@code x}, {@code z} in its block, in blocks east and south of
     * its north-west corner, from {@code top} down to {@code bottom}, in blocks from its tile's floor.
     * Its legs are listed round the block. A leg not {@code standing} found nothing within reach and
     * is drawn running on past it.
     */
    public record Leg(double x, double z, double top, double bottom, boolean standing) {
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
        return new Support(CORNERS.stream().map(corner -> foot(corner, travel, shape)).map(foot -> leg(spot, foot, ground)).toList());
    }

    // A turn is a quarter disc about the corner between its entry side and its front, so its outer
    // corner is empty: the leg there stands back on the diagonal, where the curve's outer edge is.
    private static double[] foot(double[] corner, LineScan.Travel travel, TileShape shape) {
        if (shape == TileShape.STRAIGHT) return corner;
        var side = shape == TileShape.FROM_LEFT ? TileShape.left(travel) : TileShape.right(travel);
        var innerX = 0.5 + 0.5 * (side.x() + travel.x());
        var innerZ = 0.5 + 0.5 * (side.z() + travel.z());
        if (corner[0] != 1 - innerX || corner[1] != 1 - innerZ) return corner;
        var back = 1 / Math.sqrt(2);
        return new double[] {innerX + (corner[0] - innerX) * back, innerZ + (corner[1] - innerZ) * back};
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

    private static Leg leg(LineScan.Spot spot, double[] foot, Ground ground) {
        var x = foot[0];
        var z = foot[1];
        for (var down = 1; down <= REACH + 1; down++) {
            var at = spot.up(-down);
            if (ground.fill(at) == Fill.OPEN) continue;
            var bottom = ground.top(at, x, z) - down;
            if (Pitch.SURFACE - bottom > REACH) break;
            return new Leg(x, z, Pitch.SURFACE, bottom, true);
        }
        return new Leg(x, z, Pitch.SURFACE, Pitch.SURFACE - REACH, false);
    }

    /** The surface of a belt tile of {@code pitch} travelling {@code travel} at {@code x}, {@code z} in its block, where a leg stands on it. */
    public static double surface(Pitch pitch, LineScan.Travel travel, double x, double z) {
        return pitch.surface((x - 0.5) * travel.x() + (z - 0.5) * travel.z() + 0.5);
    }
}
