package rearth.belts.model;

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
 * a line of mixed tiers run at its slowest piece (ADR-0076).
 */
public final class TransportLine<T> {

    private final List<BeltTier> tiers;
    private final double speed;
    private final BeltContents<T> contents = new BeltContents<>();

    public TransportLine(List<BeltTier> tiers) {
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

    /** One tick of the whole line: loaded at the first tile, delivered past the last. */
    public boolean tick(Supplier<T> source, Predicate<T> sink) {
        return contents.tick(length(), speed, source, sink);
    }

    /** Moves the items one tick with nothing loaded or delivered, as a client's copy does. */
    public void advance() {
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
        overflow.addAll(contents.fit(length()));
        return overflow;
    }

    /** One tile's share of a line's items: its index along the line and where in the tile it sits. */
    public record Load<T>(int tile, double offset, T payload) {
    }
}
