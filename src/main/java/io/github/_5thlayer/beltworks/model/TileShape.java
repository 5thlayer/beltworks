// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.model;

import org.jetbrains.annotations.Nullable;

/**
 * A tile's shape, derived from what feeds it and never chosen: Factorio's rule, under which a tile
 * fed from exactly one side and not from behind is a corner (PlanetaryFactory #391).
 */
public enum TileShape {
    STRAIGHT,
    FROM_LEFT,
    FROM_RIGHT;

    /** The world a tile's shape and pitch are derived from. */
    public interface Around {

        /** The way the belt tile at {@code spot} travels, or null where there is no tile. */
        @Nullable
        LineScan.Travel tile(LineScan.Spot spot);

        /** Whether the belt piece at {@code from}, a tile, loader or splitter half, outputs {@code travel}. */
        boolean feeds(LineScan.Spot from, LineScan.Travel travel);
    }

    public static TileShape of(boolean behind, boolean left, boolean right) {
        if (behind || left == right) return STRAIGHT;
        return left ? FROM_LEFT : FROM_RIGHT;
    }

    /**
     * The shape of a tile at {@code spot} travelling {@code travel}, asked of its three neighbours.
     * A tile a block up or down behind it counts as behind it, and a tile beside it that climbs
     * over it does not feed it (#417). A slope takes no side-load, so it is never a corner (#419).
     */
    public static TileShape at(LineScan.Spot spot, LineScan.Travel travel, Around around) {
        var left = left(travel);
        var right = right(travel);
        var back = spot.step(travel, -1);
        var shape = of(
          around.feeds(back, travel) || feedsFrom(back, 1, spot, travel, around) || feedsFrom(back, -1, spot, travel, around),
          sideFeeds(spot.step(left, 1), right, around),
          sideFeeds(spot.step(right, 1), left, around));
        return shape == STRAIGHT || Pitch.at(spot, travel, around) == Pitch.LEVEL ? shape : STRAIGHT;
    }

    // A tile a block up or down behind feeds this one only if it has no level tile ahead of its own.
    private static boolean feedsFrom(LineScan.Spot back, int rise, LineScan.Spot spot, LineScan.Travel travel, Around around) {
        return travel.equals(around.tile(back.up(rise))) && Pitch.height(spot.up(rise), travel, around) == -rise;
    }

    private static boolean sideFeeds(LineScan.Spot from, LineScan.Travel travel, Around around) {
        if (!around.feeds(from, travel)) return false;
        return around.tile(from) == null || Pitch.height(from.step(travel, 1), travel, around) == 0;
    }

    /** The way items travel as they enter a tile of this shape travelling {@code travel}. */
    public LineScan.Travel entry(LineScan.Travel travel) {
        return switch (this) {
            case STRAIGHT -> travel;
            case FROM_LEFT -> right(travel);
            case FROM_RIGHT -> left(travel);
        };
    }

    /**
     * Where an item this far along a tile of this shape is, from the tile's centre, and which way it
     * is heading. A corner is a quarter circle joining the middles of its entry and exit edges, so
     * it is one block of line like any tile and only drawn round (#391).
     */
    public Point point(double offset, LineScan.Travel travel) {
        if (this == STRAIGHT) {
            var along = offset - 0.5;
            return new Point(travel.x() * along, travel.z() * along, travel.x(), travel.z());
        }
        var side = this == FROM_LEFT ? left(travel) : right(travel);
        var angle = offset * Math.PI / 2;
        var cos = Math.cos(angle);
        var sin = Math.sin(angle);
        var centreX = 0.5 * (side.x() + travel.x());
        var centreZ = 0.5 * (side.z() + travel.z());
        return new Point(
          centreX - 0.5 * (cos * travel.x() + sin * side.x()),
          centreZ - 0.5 * (cos * travel.z() + sin * side.z()),
          sin * travel.x() - cos * side.x(),
          sin * travel.z() - cos * side.z());
    }

    /**
     * How far along a tile of this shape the point {@code (x, z)}, from the tile's centre, is: the
     * inverse of {@link #point}, in {@code [0, 1]}. On a corner it is the point's angle about the
     * block corner the arc turns about, so a point inside or outside the arc lands on the offset
     * nearest it, and a point beyond the tile lands on its near end (#91).
     */
    public double project(double x, double z, LineScan.Travel travel) {
        if (this == STRAIGHT) return Math.clamp(0.5 + x * travel.x() + z * travel.z(), 0, 1);
        var side = this == FROM_LEFT ? left(travel) : right(travel);
        var fromX = x - 0.5 * (side.x() + travel.x());
        var fromZ = z - 0.5 * (side.z() + travel.z());
        // The arc is at (-cos, -sin) of this radius on (travel, side), so the angle is that of the negated components.
        var angle = Math.atan2(-(fromX * side.x() + fromZ * side.z()), -(fromX * travel.x() + fromZ * travel.z()));
        return Math.clamp(angle / (Math.PI / 2), 0, 1);
    }

    /** A point on a tile, from its centre, and the unit heading of an item there. */
    public record Point(double x, double z, double headingX, double headingZ) {
    }

    // Minecraft's z grows southward, so a quarter turn anticlockwise seen from above is (z, -x).
    static LineScan.Travel left(LineScan.Travel travel) {
        return new LineScan.Travel(travel.z(), -travel.x());
    }

    static LineScan.Travel right(LineScan.Travel travel) {
        return new LineScan.Travel(-travel.z(), travel.x());
    }

    static LineScan.Travel back(LineScan.Travel travel) {
        return new LineScan.Travel(-travel.x(), -travel.z());
    }
}
