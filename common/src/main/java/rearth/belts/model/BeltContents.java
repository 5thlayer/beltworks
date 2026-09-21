package rearth.belts.model;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * What a belt carries: entries in progress order, head first, each at a distance in blocks from
 * the belt's start and each occupying {@link #SPACING} of the belt.
 *
 * <p>The belt's length and speed are passed to every tick rather than held, because the loader
 * that owns a belt can be re-pathed under it.
 */
public final class BeltContents<T> {

    /** Factorio's belt holds eight items per tile across two lanes; this belt has one lane for all eight (#344). */
    public static final double SPACING = 0.125;

    private final Deque<Entry<T>> entries = new ArrayDeque<>();

    /**
     * Delivers what reaches the end, moves the rest, then loads at the head while the source has
     * items and the head has room. Both ends can pass several entries in one tick.
     *
     * @param source the next entry to load, or null when there is none
     * @param sink   whether the entry at the end was taken; a refusal backs the belt up
     * @return whether any entry was delivered, moved or loaded
     */
    public boolean tick(double length, double speed, Supplier<T> source, Predicate<T> sink) {
        var changed = false;
        var limit = length - SPACING;

        while (!entries.isEmpty() && entries.peekLast().position + speed >= limit
                   && sink.test(entries.peekLast().payload)) {
            entries.pollLast();
            changed = true;
        }

        for (var iterator = entries.descendingIterator(); iterator.hasNext(); ) {
            var entry = iterator.next();
            var moved = Math.min(entry.position + speed, limit);
            changed |= moved != entry.position;
            entry.position = moved;
            limit = moved - SPACING;
        }

        while (true) {
            var at = entries.isEmpty() ? 0 : entries.peekFirst().position - SPACING;
            if (at < 0 || at > length - SPACING) return changed;
            var payload = source.get();
            if (payload == null) return changed;
            entries.addFirst(new Entry<>(payload, at));
            changed = true;
        }
    }

    /**
     * Packs the entries back from the end of a belt of this length and removes the ones that no
     * longer fit, head first, for a belt that was re-pathed shorter or restored from a save.
     */
    public List<T> fit(double length) {
        var floor = 0.0;
        for (var entry : entries) {
            entry.position = Math.max(entry.position, floor);
            floor = entry.position + SPACING;
        }

        var overflow = new ArrayList<T>();
        var limit = length - SPACING;
        for (var iterator = entries.descendingIterator(); iterator.hasNext(); ) {
            var entry = iterator.next();
            entry.position = Math.min(entry.position, limit);
            if (entry.position < 0) {
                overflow.addFirst(entry.payload);
                iterator.remove();
            } else {
                limit = entry.position - SPACING;
            }
        }
        return overflow;
    }

    /** Appends an entry behind the end, for rebuilding a saved belt head first. */
    public void restore(T payload, double position) {
        entries.addLast(new Entry<>(payload, position));
    }

    public List<Entry<T>> entries() {
        return List.copyOf(entries);
    }

    public int size() {
        return entries.size();
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public void clear() {
        entries.clear();
    }

    public static final class Entry<T> {
        private final T payload;
        private double position;

        private Entry(T payload, double position) {
            this.payload = payload;
            this.position = position;
        }

        public T payload() {
            return payload;
        }

        public double position() {
            return position;
        }
    }
}
