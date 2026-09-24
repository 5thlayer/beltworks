// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package rearth.belts.model;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

/**
 * Which tiles are one transport line: the contiguous run of tiles each feeding the next, through a
 * tile, head first (PlanetaryFactory #398, #391), up and down its slopes (#417).
 *
 * <p>A tile feeds the tile in front of it only when that tile enters from its side, so a side-load
 * is two lines meeting rather than one. A run whose last tile feeds its first is a ring.
 */
public final class LineScan {

    private LineScan() {
    }

    /** A tile's position, in blocks. */
    public record Spot(int x, int y, int z) {

        public Spot step(Travel travel, int times) {
            return new Spot(x + travel.x() * times, y, z + travel.z() * times);
        }

        public Spot up(int blocks) {
            return new Spot(x, y + blocks, z);
        }
    }

    /** A direction of travel, as unit steps on the x and z axes. */
    public record Travel(int x, int z) {
    }

    /** A tile as the scan sees it: the way items leave it, the way they enter it, and its pitch. */
    public record Piece(Travel travel, Travel entry, Pitch pitch) {
    }

    /** The world the scan reads: the tile at a spot, or null where there is none. */
    public interface Tiles {

        @Nullable
        Piece pieceAt(Spot spot);
    }

    /**
     * A line's tiles, head first. A ring's head is its least spot, so every tile of it scans to the
     * same line.
     */
    public record Scan(List<Spot> spots, boolean ring) {

        static final Scan NONE = new Scan(List.of(), false);
    }

    private static final Comparator<Spot> ORDER =
      Comparator.comparingInt(Spot::x).thenComparingInt(Spot::y).thenComparingInt(Spot::z);

    /** The line through this tile, or nothing when there is no tile there. */
    public static Scan through(Spot spot, Tiles tiles) {
        var piece = tiles.pieceAt(spot);
        if (piece == null) return Scan.NONE;

        var line = new ArrayList<Spot>();
        var seen = new HashSet<Spot>();
        line.add(spot);
        seen.add(spot);
        for (var at = spot; ; ) {
            var here = tiles.pieceAt(at);
            var next = at.step(here.travel(), 1).up(here.pitch().fed());
            var ahead = tiles.pieceAt(next);
            if (ahead == null || !ahead.entry().equals(here.travel()) || ahead.pitch().feeder() != -here.pitch().fed()) break;
            if (next.equals(spot)) return ring(line);
            seen.add(next);
            line.add(next);
            at = next;
        }
        for (var at = spot; ; ) {
            var here = tiles.pieceAt(at);
            var entry = here.entry();
            var back = at.step(entry, -1).up(here.pitch().feeder());
            var behind = tiles.pieceAt(back);
            if (behind == null || !behind.travel().equals(entry) || behind.pitch().fed() != -here.pitch().feeder() || !seen.add(back)) break;
            line.addFirst(back);
            at = back;
        }
        return new Scan(List.copyOf(line), false);
    }

    private static Scan ring(List<Spot> line) {
        var head = line.indexOf(line.stream().min(ORDER).orElseThrow());
        var rotated = new ArrayList<Spot>(line.size());
        for (var at = 0; at < line.size(); at++) rotated.add(line.get((head + at) % line.size()));
        return new Scan(List.copyOf(rotated), true);
    }
}
