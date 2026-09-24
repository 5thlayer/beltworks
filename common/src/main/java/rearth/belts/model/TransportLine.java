// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package rearth.belts.model;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * A run of belt tiles carrying items as one (PlanetaryFactory #398). Contiguous tiles of one
 * direction of travel are merged into a line that ticks once for all of them, so a base of
 * thousands of tiles does not tick thousands of belts.
 *
 * <p>A tile is one block long and holds {@link BeltContents#SPACING}'s worth of items in it, so a
 * line of {@code n} tiles holds {@code 8n}. The line runs at its slowest tile, which is what makes
 * a line of mixed tiers run at its slowest piece (ADR 0007).
 */
public final class TransportLine<T> {

    private final List<BeltTier> tiers;
    private final double speed;
    private final boolean ring;
    private final BeltContents<T> contents = new BeltContents<>();

    public TransportLine(List<BeltTier> tiers) {
        this(tiers, false);
    }

    /** A line whose last tile feeds its first, which loads and delivers nothing (#391). */
    public TransportLine(List<BeltTier> tiers, boolean ring) {
        this.ring = ring;
        if (tiers.isEmpty()) throw new IllegalArgumentException("a transport line has at least one tile");
        this.tiers = List.copyOf(tiers);
        this.speed = tiers.stream().mapToDouble(BeltTier::blocksPerTick).min().orElseThrow();
    }

    /** The line's length in blocks, which is its tile count. */
    public double length() {
        return tiers.size();
    }

    public int tileCount() {
        return tiers.size();
    }

    /** Blocks per tick, the slowest tile's. */
    public double speed() {
        return speed;
    }

    /** The items a second the line delivers, which is its slowest tile's. */
    public double itemsPerSecond() {
        return speed * 20 / BeltContents.SPACING;
    }

    /** How many items a backed-up line holds. */
    public int capacity() {
        return (int) Math.round(length() / BeltContents.SPACING);
    }

    public boolean ring() {
        return ring;
    }

    /** One tick of the whole line: loaded at the first tile, delivered past the last. */
    public boolean tick(Supplier<T> source, Predicate<T> sink) {
        return tick(source, sink, null);
    }

    /** A tick with a player's hand held on the line; a ring takes no hand. */
    public boolean tick(Supplier<T> source, Predicate<T> sink, BeltContents.@Nullable Hand<T> hand) {
        if (ring) return contents.cycle(length(), speed);
        return contents.tick(length(), speed, source, sink, hand);
    }

    /**
     * The entry position a hand on this tile holds: the tile's front, so it takes whatever is on
     * the tile rather than what crosses an aimed point (#396).
     */
    public static double handPoint(int tile) {
        return tile + 1 - BeltContents.SPACING;
    }

    /**
     * Takes an item side-loaded onto this tile of the line, centred on it, when the line has a gap
     * there (PlanetaryFactory #409).
     */
    public boolean sideLoad(T payload, int tile) {
        return contents.insert(payload, tile, tile + 0.5 - BeltContents.SPACING / 2, length(), ring);
    }

    /** Moves the items one tick with nothing loaded or delivered, as a client's copy does. */
    public void advance() {
        if (ring) {
            contents.cycle(length(), speed);
            return;
        }
        contents.advance(length(), speed);
    }

    public BeltContents<T> contents() {
        return contents;
    }

    public int size() {
        return contents.size();
    }

    /** What each tile carries, in travel order, so a line taken apart hands its items to its tiles. */
    public List<Load<T>> spill() {
        var loads = new ArrayList<Load<T>>();
        for (var entry : contents.entries()) {
            var tile = Math.clamp((int) entry.position(), 0, tiers.size() - 1);
            loads.add(new Load<>(tile, entry.position() - tile, entry.payload()));
        }
        return loads;
    }

    /**
     * Puts back what {@link #spill} handed out, in the same order, and returns what no longer fits
     * because the line grew shorter.
     */
    public List<T> restore(List<Load<T>> loads) {
        var overflow = new ArrayList<T>();
        for (var load : loads) {
            if (load.tile() >= tiers.size()) {
                overflow.add(load.payload());
                continue;
            }
            contents.restore(load.payload(), load.tile() + load.offset());
        }
        // A ring has no end to back up against, so its entries stay where they were saved.
        if (!ring) overflow.addAll(contents.fit(length()));
        return overflow;
    }

    /**
     * What each tile of the line carries, in travel order and indexed by tile, so each tile can
     * save its own items and a line can stop at a chunk's edge without the tiles past it (#395).
     */
    public List<List<Share<T>>> shares() {
        var shares = new ArrayList<List<Share<T>>>(tiers.size());
        for (var tile = 0; tile < tiers.size(); tile++) shares.add(new ArrayList<>());
        for (var load : spill()) shares.get(load.tile()).add(new Share<>(load.offset(), load.payload()));
        return shares;
    }

    /** Puts back each tile's {@link #shares}, first tile first, and returns what no longer fits. */
    public List<T> restoreShares(List<List<Share<T>>> shares) {
        var loads = new ArrayList<Load<T>>();
        for (var tile = 0; tile < shares.size(); tile++) {
            for (var held : shares.get(tile)) loads.add(new Load<>(tile, held.offset(), held.payload()));
        }
        return restore(loads);
    }

    /**
     * Where each item is drawn this far into the next tick: moved on at the line's speed and held
     * back behind the item ahead and at the end, as a tick would, and on the one tile its centre
     * is on.
     */
    public List<Drawn<T>> drawn(float partialTicks) {
        var entries = contents.entries();
        var advance = speed * partialTicks;
        if (ring) {
            var drawn = new ArrayList<Drawn<T>>(entries.size());
            for (var entry : entries) {
                var centre = (entry.position() + advance + BeltContents.SPACING / 2) % length();
                var tile = (int) centre;
                drawn.add(new Drawn<>(tile, centre - tile, entry));
            }
            return drawn;
        }
        var positions = new double[entries.size()];
        var limit = length() - BeltContents.SPACING;
        for (var index = entries.size() - 1; index >= 0; index--) {
            positions[index] = Math.min(entries.get(index).position() + advance, limit);
            limit = positions[index] - BeltContents.SPACING;
        }

        var drawn = new ArrayList<Drawn<T>>(entries.size());
        for (var index = 0; index < entries.size(); index++) {
            // A gained entry the copy placed behind a lagging head can start short of the line (#351).
            var centre = Math.clamp(positions[index] + BeltContents.SPACING / 2, 0, length() - 1e-6);
            var tile = (int) centre;
            drawn.add(new Drawn<>(tile, centre - tile, entries.get(index)));
        }
        return drawn;
    }

    /** One tile's share of a line's items: its index along the line and where in the tile it sits. */
    public record Load<T>(int tile, double offset, T payload) {
    }

    /** An item a tile holds, and where in the tile it sits, from its back edge. */
    public record Share<T>(double offset, T payload) {
    }

    /** An item drawn on a tile of the line, its centre this far along the tile. */
    public record Drawn<T>(int tile, double offset, BeltContents.Entry<T> entry) {
    }
}
