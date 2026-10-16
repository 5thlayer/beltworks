// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.model;

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
     * The pitch of a tile at {@code spot} travelling {@code travel}, from its feeder's height and its
     * fed height (ADR 0017). Each end is asked of the tiles alone, so a connection is both tiles' or
     * neither's and the derivation never recurses.
     */
    public static Pitch at(LineScan.Spot spot, LineScan.Travel travel, TileShape.Around around) {
        var feeder = feeder(spot, travel, around);
        var pitch = of(feeder == null ? 0 : feeder, fed(spot, travel, around));
        return pitch == null ? LEVEL : pitch;
    }

    /**
     * The height of the tile feeding the tile at {@code spot} from behind, in blocks relative to it,
     * or null where nothing does: a level piece behind outputting {@code travel} first, then a tile a
     * block up or down behind whose climb leads here. A tile whose own fed height would make a crest
     * or a valley of it takes none from a block up or down.
     */
    public static @Nullable Integer feeder(LineScan.Spot spot, LineScan.Travel travel, TileShape.Around around) {
        var back = spot.step(travel, -1);
        if (around.feeds(back, travel) && (around.tile(back) == null || around.climb(back) == 0)) return 0;
        var out = reach(spot, travel, around);
        for (var rise : new int[] {1, -1}) {
            var from = back.up(rise);
            if (travel.equals(around.tile(from)) && around.climb(from) == -rise && reach(from, travel, around) == -rise
                && of(rise, out) != null) {
                return rise;
            }
        }
        return null;
    }

    // The fed end only where the tile there takes this one as its feeder.
    private static int fed(LineScan.Spot spot, LineScan.Travel travel, TileShape.Around around) {
        var out = reach(spot, travel, around);
        if (out == 0) return 0;
        var feeder = feeder(spot.step(travel, 1).up(out), travel, around);
        return feeder != null && feeder == -out ? out : 0;
    }

    // A tile's climb, where a tile facing its way stands there to take it, and level where none does.
    private static int reach(LineScan.Spot spot, LineScan.Travel travel, TileShape.Around around) {
        var climb = around.climb(spot);
        return climb != 0 && travel.equals(around.tile(spot.step(travel, 1).up(climb))) ? climb : 0;
    }
}
