// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.model;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The struts that show a raised line held up, where a builder would put them (ADR 0012): a frame
 * under the tile and a leg down each corner of its block. Like a tile's shape and pitch, it is
 * derived from the tile and the world around it, never placed, and never limits how far a line
 * floats. Standing at the edge of its tile, a leg never crosses where items pass. A splitter shows
 * one for both its halves; a loader, fixed to its inventory, never shows one. A slope's has no
 * frame: its tilted belt ties the legs, and a frame under its floor would show through it.
 * Whether supports show, how far apart a straight line's are, and how far a leg reaches are each
 * player's own settings.
 */
public record Support(List<Leg> legs, boolean framed) {

    // Round the block, so each corner's neighbours are the corners a strut ties it to.
    private static final List<double[]> CORNERS = List.of(new double[] {0, 0}, new double[] {1, 0}, new double[] {1, 1}, new double[] {0, 1});

    /**
     * How a player sees supports: whether they show at all, how many blocks apart a straight line
     * or a slope mid-line shows them, and how far a leg reaches below a level tile's surface, in
     * blocks, before it runs on out of sight.
     */
    public record Setting(boolean shown, int spacing, int reach) {

        public static final Setting DEFAULT = new Setting(true, 8, 64);

        public Setting {
            if (spacing < 1) throw new IllegalArgumentException("spacing must be at least 1: " + spacing);
            if (reach < 1) throw new IllegalArgumentException("reach must be at least 1: " + reach);
        }
    }

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
     * its north-west corner, or in the next block for a splitter's right half, from {@code top}
     * down to {@code bottom}, in blocks from its tile's floor.
     * Its legs are listed round the block. A leg not {@code standing} found nothing within reach and
     * is drawn running on past it.
     */
    public record Leg(double x, double z, double top, double bottom, boolean standing) {

        /** This leg a block off toward {@code travel}. */
        public Leg shifted(LineScan.Travel travel) {
            return new Leg(x + travel.x(), z + travel.z(), top, bottom, standing);
        }
    }

    /**
     * The support a tile at {@code spot} travelling {@code travel} shows, or null where it shows
     * none: only where the tile turns, is a foot or a top, is its line's first or last, or falls on
     * the spacing, and only over air. Each leg stops at the tile's surface where it stands, and no
     * higher than a level tile's, so a slope's legs pass its wedge and meet its low edge.
     */
    public static @Nullable Support at(LineScan.Spot spot, LineScan.Travel travel, TileShape.Around around, Ground ground, Setting setting) {
        if (!setting.shown()) return null;
        var pitch = Pitch.at(spot, travel, around);
        var shape = TileShape.at(spot, travel, around);
        if (shape == TileShape.STRAIGHT && !footOrTop(pitch) && !spaced(spot, travel, setting)
              && !firstOrLast(spot, travel, pitch, around)) return null;
        if (heldUp(spot, ground) || fixed(spot, travel, shape, ground)) return null;
        return new Support(CORNERS.stream().map(corner -> foot(corner, travel, shape)).map(foot -> {
            var top = Math.min(Pitch.SURFACE, surface(pitch, travel, foot[0], foot[1]));
            return leg(spot, foot, top, ground, setting.reach());
        }).toList(), pitch == Pitch.LEVEL);
    }

    /**
     * The one support of a splitter whose left half is at {@code left}, facing {@code travel}, or
     * null where it shows none: a splitter always shows one when either half stands over air, unless
     * a solid block at either outer side fixes it. Its legs stand at the pair's outer corners, in
     * the left half's block space, each over the ground under its own half.
     */
    public static @Nullable Support splitter(LineScan.Spot left, LineScan.Travel travel, Ground ground, Setting setting) {
        if (!setting.shown()) return null;
        var toRight = TileShape.right(travel);
        var toLeft = TileShape.left(travel);
        var right = left.step(toRight, 1);
        if (heldUp(left, ground) && heldUp(right, ground)) return null;
        if (fixedAt(left, toLeft, ground) || fixedAt(right, toRight, ground)) return null;
        return new Support(CORNERS.stream().map(corner -> {
            // A corner on the left half's right side moves out to the right half's.
            var onRight = (corner[0] - 0.5) * toRight.x() + (corner[1] - 0.5) * toRight.z() > 0;
            return onRight
                     ? leg(right, corner, Pitch.SURFACE, ground, setting.reach()).shifted(toRight)
                     : leg(left, corner, Pitch.SURFACE, ground, setting.reach());
        }).toList(), true);
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

    // Where a slope bends, a builder holds it up whether or not its line stops there.
    private static boolean footOrTop(Pitch pitch) {
        return pitch == Pitch.FOOT_UP || pitch == Pitch.TOP_UP || pitch == Pitch.FOOT_DOWN || pitch == Pitch.TOP_DOWN;
    }

    // Spaced by its place along its travel in the world, so a line shows them at the same blocks
    // whichever end it was built from, with no scan of it.
    private static boolean spaced(LineScan.Spot spot, LineScan.Travel travel, Setting setting) {
        var along = travel.x() != 0 ? spot.x() : spot.z();
        return Math.floorMod(along, setting.spacing()) == 0;
    }

    // A tile's line runs on through the tile behind it, at its feeder's height, that travels its way
    // and feeds it, and through the tile ahead, at its fed height, that enters from it.
    private static boolean firstOrLast(LineScan.Spot spot, LineScan.Travel travel, Pitch pitch, TileShape.Around around) {
        var behind = spot.step(travel, -1).up(pitch.feeder());
        if (!travel.equals(around.tile(behind)) || Pitch.at(behind, travel, around).fed() != -pitch.feeder()) return true;
        var ahead = spot.step(travel, 1).up(pitch.fed());
        var onward = around.tile(ahead);
        return onward == null
                 || !TileShape.at(ahead, onward, around).entry(onward).equals(travel)
                 || Pitch.at(ahead, onward, around).feeder() != -pitch.fed();
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
        return free.stream().anyMatch(side -> fixedAt(spot, side, ground));
    }

    // Fixed to the block on that side of it, when that block is solid on the face turned to it.
    private static boolean fixedAt(LineScan.Spot spot, LineScan.Travel side, Ground ground) {
        return ground.fixes(spot.step(side, 1), TileShape.back(side));
    }

    private static Leg leg(LineScan.Spot spot, double[] foot, double top, Ground ground, int reach) {
        var x = foot[0];
        var z = foot[1];
        for (var down = 1; down <= reach + 1; down++) {
            var at = spot.up(-down);
            if (ground.fill(at) == Fill.OPEN) continue;
            var bottom = ground.top(at, x, z) - down;
            if (Pitch.SURFACE - bottom > reach) break;
            return new Leg(x, z, top, bottom, true);
        }
        return new Leg(x, z, top, Pitch.SURFACE - reach, false);
    }

    /** The surface of a belt tile of {@code pitch} travelling {@code travel} at {@code x}, {@code z} in its block, where a leg stands on it. */
    public static double surface(Pitch pitch, LineScan.Travel travel, double x, double z) {
        return pitch.surface((x - 0.5) * travel.x() + (z - 0.5) * travel.z() + 0.5);
    }
}
