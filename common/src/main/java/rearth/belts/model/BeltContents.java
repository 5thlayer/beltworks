package rearth.belts.model;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * What a belt carries: entries in progress order, head first, each at a distance in blocks from
 * the belt's start and each occupying {@link #SPACING} of the belt.
 *
 * <p>The belt's length and speed are passed to every tick rather than held, because the loader
 * that owns a belt can be re-pathed under it.
 *
 * <p>Every entry has an id of its own on this belt, and the entries gained and lost since the last
 * {@link #drainChanges} are recorded, so a client's copy can be kept by those alone while it
 * {@link #advance}s itself between them (PlanetaryFactory #351).
 */
public final class BeltContents<T> {

    /** Factorio's belt holds eight items per tile across two lanes; this belt has one lane for all eight (#344). */
    public static final double SPACING = 0.125;

    private final Deque<Entry<T>> entries = new ArrayDeque<>();
    private int nextId;
    private final List<Integer> removed = new ArrayList<>();
    private final Map<Integer, Entry<T>> added = new LinkedHashMap<>();

    public boolean tick(double length, double speed, Supplier<T> source, Predicate<T> sink) {
        return tick(length, speed, source, sink, null);
    }

    /**
     * Delivers what reaches the end, moves the rest, then loads at the head while the source has
     * items and the head has room. Both ends can pass several entries in one tick.
     *
     * <p>A hand is a second end at its point for the entries behind it: they are offered to it as
     * they reach the point and back up behind it when it refuses, while the entries past it carry
     * on to the real end.
     *
     * @param source the next entry to load, or null when there is none
     * @param sink   whether the entry at the end was taken; a refusal backs the belt up
     * @return whether any entry was delivered, taken, moved or loaded
     */
    public boolean tick(double length, double speed, Supplier<T> source, Predicate<T> sink, @Nullable Hand<T> hand) {
        var changed = false;
        var limit = length - SPACING;
        // Below every position when there is no hand, so every entry is past it.
        var point = hand == null ? -1 : Math.clamp(hand.point, 0, limit);

        while (!entries.isEmpty() && entries.peekLast().position > point
                   && entries.peekLast().position + speed >= limit && sink.test(entries.peekLast().payload)) {
            removed(entries.pollLast());
            changed = true;
        }

        var handTaking = hand != null;
        var behindHand = false;
        for (var iterator = entries.descendingIterator(); iterator.hasNext(); ) {
            var entry = iterator.next();
            if (!behindHand && entry.position <= point) {
                behindHand = true;
                limit = Math.min(limit, point);
            }
            if (behindHand && handTaking && entry.position + speed >= point) {
                if (hand.taker.test(entry.payload)) {
                    iterator.remove();
                    removed(entry);
                    changed = true;
                    continue;
                }
                handTaking = false;
            }
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
            load(payload, at);
            changed = true;
        }
    }

    /** Moves the entries one tick with nothing loaded or delivered, as a client's copy does between changes. */
    public void advance(double length, double speed) {
        tick(length, speed, () -> null, payload -> false);
    }

    /** Whether the entry at the end is within one tick of it, so a splitter may take it this tick. */
    public boolean endReady(double length, double speed) {
        return !entries.isEmpty() && entries.peekLast().position + speed >= length - SPACING;
    }

    /** Removes the entry at the end, after {@link #endReady} said there is one. */
    public T takeEnd() {
        var entry = entries.pollLast();
        removed(entry);
        return entry.payload;
    }

    /** Whether {@link #offer} would place an entry at the head. */
    public boolean canOffer(double length, double speed) {
        var at = offerAt(speed);
        return at >= 0 && at <= length - SPACING;
    }

    /**
     * Places an entry at the head, at most one tick's travel along it, so a splitter can hand on
     * more than one entry a tick without an entry jumping ahead to a sparse belt's last one.
     */
    public void offer(T payload, double speed) {
        load(payload, offerAt(speed));
    }

    private double offerAt(double speed) {
        return entries.isEmpty() ? 0 : Math.min(entries.peekFirst().position - SPACING, speed);
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
                removed(entry);
            } else {
                limit = entry.position - SPACING;
            }
        }
        return overflow;
    }

    /** Appends an entry with a fresh id behind the end. */
    public void restore(T payload, double position) {
        restore(payload, position, nextId);
    }

    /** Appends an entry with the id it held, for rebuilding a saved or sent belt head first. */
    public void restore(T payload, double position, int id) {
        entries.addLast(new Entry<>(id, payload, position));
        nextId = Math.max(nextId, id + 1);
    }

    /**
     * The entries gained and lost since the last drain, each gained one at where it is now. One
     * gained and lost in between is neither.
     */
    public Changes<T> drainChanges() {
        if (removed.isEmpty() && added.isEmpty()) return Changes.none();
        var changes = new Changes<>(List.copyOf(removed),
          added.values().stream().map(entry -> new Added<>(entry.id, entry.payload, entry.position)).toList());
        removed.clear();
        added.clear();
        return changes;
    }

    /**
     * Brings a copy of a belt up to the changes drained from it. The copy's entries may lag the
     * belt's, so a gained entry is placed no closer than the spacing behind the copy's head. A
     * change the copy already holds, or an entry it no longer has, is passed over.
     */
    public void apply(Changes<T> changes) {
        for (var id : changes.removed()) {
            // Mostly the end, so searched from there.
            for (var iterator = entries.descendingIterator(); iterator.hasNext(); ) {
                if (iterator.next().id == id) {
                    iterator.remove();
                    break;
                }
            }
        }
        for (var gained : changes.added()) {
            if (entries.stream().anyMatch(entry -> entry.id == gained.id())) continue;
            var at = entries.isEmpty() ? gained.position() : Math.min(gained.position(), entries.peekFirst().position - SPACING);
            entries.addFirst(new Entry<>(gained.id(), gained.payload(), at));
            nextId = Math.max(nextId, gained.id() + 1);
        }
    }

    private void load(T payload, double at) {
        var entry = new Entry<>(nextId++, payload, at);
        entries.addFirst(entry);
        added.put(entry.id, entry);
    }

    private void removed(Entry<T> entry) {
        if (added.remove(entry.id) == null) removed.add(entry.id);
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

    /** Empties the belt, and forgets its changes: whoever clears it resends it whole. */
    public void clear() {
        entries.clear();
        removed.clear();
        added.clear();
    }

    /**
     * A player's hand held on the belt at a point, in the same blocks from the start as an entry's
     * position.
     *
     * @param taker whether the hand took the entry; a refusal stops it until the next tick
     */
    public record Hand<T>(double point, Predicate<T> taker) {
    }

    /** What a belt gained and lost between two drains, removals first. */
    public record Changes<T>(List<Integer> removed, List<Added<T>> added) {

        public static <T> Changes<T> none() {
            return new Changes<>(List.of(), List.of());
        }

        public boolean isEmpty() {
            return removed.isEmpty() && added.isEmpty();
        }
    }

    /** An entry gained, in the order it was loaded, at its position when drained. */
    public record Added<T>(int id, T payload, double position) {
    }

    public static final class Entry<T> {
        private final int id;
        private final T payload;
        private double position;

        private Entry(int id, T payload, double position) {
            this.id = id;
            this.payload = payload;
            this.position = position;
        }

        public int id() {
            return id;
        }

        public T payload() {
            return payload;
        }

        public double position() {
            return position;
        }
    }
}
