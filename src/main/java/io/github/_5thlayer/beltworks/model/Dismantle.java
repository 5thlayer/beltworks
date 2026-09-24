// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.model;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The tiles a dismantle takes up (PlanetaryFactory #404): the run of the start's line from the start
 * to the end, both included, either way along it, start first. Round a ring it runs downstream.
 */
public final class Dismantle {

    private Dismantle() {
    }

    public enum Refusal {
        /** The end is not a tile of the start's line. */
        OFF_LINE
    }

    public record Span(List<LineScan.Spot> spots, @Nullable Refusal refusal) {

        public Span {
            spots = List.copyOf(spots);
        }
    }

    public static Span span(LineScan.Spot start, LineScan.Spot end, LineScan.Tiles tiles) {
        var scan = LineScan.through(start, tiles);
        var line = scan.spots();
        var from = line.indexOf(start);
        var to = line.indexOf(end);
        if (from < 0 || to < 0) return new Span(List.of(), Refusal.OFF_LINE);

        var spots = new ArrayList<LineScan.Spot>();
        if (scan.ring()) {
            for (var at = from; ; at = (at + 1) % line.size()) {
                spots.add(line.get(at));
                if (at == to) break;
            }
        } else {
            var step = to >= from ? 1 : -1;
            for (var at = from; at != to + step; at += step) spots.add(line.get(at));
        }
        return new Span(spots, null);
    }
}
