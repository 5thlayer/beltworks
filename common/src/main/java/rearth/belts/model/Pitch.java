// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package rearth.belts.model;

import org.jetbrains.annotations.Nullable;

/**
 * Whether a tile rises, is level or descends along its travel, derived from the heights of the
 * tile feeding it and the tile it feeds and never chosen, as its corner is (PlanetaryFactory #417).
 * A foot, its middles and its top draw one straight 45° line, each inside its own block.
 */
public enum Pitch {
    LEVEL(0, 0),
    FOOT_UP(0, 1),
    MIDDLE_UP(-1, 1),
    TOP_UP(-1, 0),
    FOOT_DOWN(1, 0),
    MIDDLE_DOWN(1, -1),
    TOP_DOWN(0, -1);

    /** A level tile's surface, in blocks from its floor. */
    public static final double SURFACE = 6 / 16d;

    private final int feeder;
    private final int fed;

    Pitch(int feeder, int fed) {
        this.feeder = feeder;
        this.fed = fed;
    }

    /** The height of the tile feeding this one, in blocks relative to it. */
    public int feeder() {
        return feeder;
    }

    /** The height of the tile this one feeds, in blocks relative to it. */
    public int fed() {
        return fed;
    }

    /** The pitch between these two heights, or null for a crest or a valley, which do not connect. */
    public static @Nullable Pitch of(int feeder, int fed) {
        for (var pitch : values()) if (pitch.feeder == feeder && pitch.fed == fed) return pitch;
        return null;
    }

    /** The surface this far along a tile of this pitch, in blocks from its floor. */
    public double surface(double offset) {
        return switch (this) {
            case LEVEL -> SURFACE;
            case FOOT_UP -> Math.max(SURFACE, offset);
            case MIDDLE_UP -> offset;
            case TOP_UP -> Math.min(SURFACE, offset);
            case FOOT_DOWN -> Math.max(SURFACE, 1 - offset);
            case MIDDLE_DOWN -> 1 - offset;
            case TOP_DOWN -> Math.min(SURFACE, 1 - offset);
        };
    }

    /** How steeply the surface climbs this far along: 1 rising, -1 descending, 0 flat. */
    public double rise(double offset) {
        return switch (this) {
            case LEVEL -> 0;
            case FOOT_UP -> offset > SURFACE ? 1 : 0;
            case MIDDLE_UP -> 1;
            case TOP_UP -> offset < SURFACE ? 1 : 0;
            case FOOT_DOWN -> offset < 1 - SURFACE ? -1 : 0;
            case MIDDLE_DOWN -> -1;
            case TOP_DOWN -> offset > 1 - SURFACE ? -1 : 0;
        };
    }

    /**
     * The pitch of a tile at {@code spot} travelling {@code travel}. A step is taken only where the
     * tile at the other end would take it too, so a connection is both tiles' or neither's; the
     * neighbour is asked from the tiles alone, which keeps the derivation from recursing.
     */
    public static Pitch at(LineScan.Spot spot, LineScan.Travel travel, TileShape.Around around) {
        var own = unpaired(spot, travel, around);
        var feeder = own.feeder;
        if (feeder != 0 && unpaired(spot.step(travel, -1).up(feeder), travel, around).fed != -feeder) feeder = 0;
        var fed = own.fed;
        if (fed != 0 && unpaired(spot.step(travel, 1).up(fed), travel, around).feeder != -fed) fed = 0;
        return of(feeder, fed);
    }

    private static Pitch unpaired(LineScan.Spot spot, LineScan.Travel travel, TileShape.Around around) {
        var pitch = of(height(spot.step(travel, -1), travel, around), height(spot.step(travel, 1), travel, around));
        return pitch == null ? LEVEL : pitch;
    }

    /**
     * Which of a column's pieces travelling {@code travel} a tile meets, in blocks relative to it: a
     * level one first, then a tile a block up, then one a block down, and level where there is none.
     * A level tile facing another way is met only after both, so a tile beside a line climbs over it
     * rather than feeding its side (#412).
     */
    static int height(LineScan.Spot column, LineScan.Travel travel, TileShape.Around around) {
        if (around.feeds(column, travel)) return 0;
        if (travel.equals(around.tile(column.up(1)))) return 1;
        if (travel.equals(around.tile(column.up(-1)))) return -1;
        return 0;
    }
}
