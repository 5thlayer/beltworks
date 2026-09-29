// SPDX-FileCopyrightText: Rearth
// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: CC-BY-4.0 AND MIT

package io.github._5thlayer.beltworks.model;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    // A ring's positions wrap by a modulo each tick and drift by rounding, which would leave its last
    // slot a hair short of a spacing and never filled (#409).
    private static final double GAP_TOLERANCE = 1e-9;

    private final Deque<Entry<T>> entries = new ArrayDeque<>();
    private int nextId;
    private final List<Integer> removed = new ArrayList<>();
    private final Map<Integer, Entry<T>> added = new LinkedHashMap<>();
    // Gained mid-belt rather than at the head, so a copy places them by position (#409).
    private final Set<Integer> sided = new HashSet<>();

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

    /**
     * One tick of a ring, which has no end: every entry moves on together, wrapping past the last
     * block to the first, so the gaps between them never close (PlanetaryFactory #391).
     */
    public boolean cycle(double length, double speed) {
        if (entries.isEmpty()) return false;
        var moved = new ArrayList<>(entries);
        for (var entry : moved) entry.position = (entry.position + speed) % length;
        moved.sort(Comparator.comparingDouble(Entry::position));
        entries.clear();
        entries.addAll(moved);
        return true;
    }

    /** Moves the entries one tick with nothing loaded or delivered, as a client's copy does between changes. */
    public void advance(double length, double speed) {
        tick(length, speed, () -> null, payload -> false);
    }

    /** Whether the entry at the end is within one tick of it, so a splitter may take it this tick. */
    public boolean endReady(double length, double speed) {
        return endReady(length, speed, -1);
    }

    /** As {@link #endReady(double, double)}, for an end entry past a hand held at {@code point}. */
    public boolean endReady(double length, double speed, double point) {
        return !entries.isEmpty() && entries.peekLast().position > point
                 && entries.peekLast().position + speed >= length - SPACING;
    }

    /**
     * How far past the end's last position the entry there would move this tick: where it lands
     * on the belt that takes it, since an entry's last position on one belt is the next one's first.
     */
    public double overshoot(double length, double speed) {
        return entries.peekLast().position + speed - (length - SPACING);
    }

    /** The payload at the end, after {@link #endReady} said there is one. */
    public T end() {
        return entries.peekLast().payload;
    }

    /** Removes the entry at the end, after {@link #endReady} said there is one. */
    public T takeEnd() {
        var entry = entries.pollLast();
        removed(entry);
        return entry.payload;
    }

    /** Whether {@link #offer} would place an entry at the head. */
    public boolean canOffer(double length, double at, @Nullable Hand<T> hand) {
        return placement(length, at, hand) >= Math.min(at, 0);
    }

    /**
     * Places an entry handed on from another belt's end at its {@link #overshoot}, or as far
     * short of it as the head and a hand hold it. Placed any nearer the start, a handed-on entry
     * loses travel each handoff and the entry behind it waits for room (#373).
     *
     * <p>{@code at} is below zero by as much as this belt is still to move this tick, and the move
     * brings the entry onto the belt.
     */
    public void offer(T payload, double length, double at, @Nullable Hand<T> hand) {
        load(payload, placement(length, at, hand));
    }

    private double placement(double length, double at, @Nullable Hand<T> hand) {
        var placed = at;
        if (!entries.isEmpty()) placed = Math.min(placed, entries.peekFirst().position - SPACING);
        if (hand != null) placed = Math.min(placed, Math.max(hand.point, 0));
        return Math.min(placed, length - SPACING);
    }

    /**
     * Places an entry side-loaded onto a tile spanning {@code [from, from + 1)}, never into the room
     * an entry already on the belt needs: they are never slowed, so the belt from behind goes first
     * (PlanetaryFactory #409). It abuts the nearest entry on the tile where it can, and sits at
     * {@code at} only on a tile with room and nothing to abut, so the free room is never cut into
     * slivers too short for an item and a ring loaded from its side fills. A ring's positions wrap.
     *
     * @return whether there was a gap for it
     */
    public boolean insert(T payload, double from, double at, double length, boolean ring) {
        var positions = positions(length, ring);
        var upper = Math.min(from + 1, length) - SPACING;
        var placed = Double.NaN;
        for (var position : positions) {
            for (var abutting : new double[] {position - SPACING, position + SPACING}) {
                if (fits(abutting, from, upper, positions) && !(Math.abs(abutting - at) >= Math.abs(placed - at))) placed = abutting;
            }
        }
        if (Double.isNaN(placed)) {
            if (!fits(at, from, upper, positions)) return false;
            placed = at;
        }
        place(payload, placed);
        return true;
    }

    /**
     * Where an entry dropped at {@code at} goes: there when it has room, else the nearest spot within
     * {@code [lower, upper]} abutting an entry, or NaN when there is no gap. Unlike {@link #insert}, it
     * never looks past its window for room.
     */
    public double dropPlacement(double at, double lower, double upper, double length, boolean ring) {
        var positions = positions(length, ring);
        var from = Math.max(lower, 0);
        var to = Math.min(upper, length - SPACING);
        if (fits(at, from, to, positions)) return at;
        var placed = Double.NaN;
        for (var position : positions) {
            for (var abutting : new double[] {position - SPACING, position + SPACING}) {
                if (fits(abutting, from, to, positions) && !(Math.abs(abutting - at) >= Math.abs(placed - at))) placed = abutting;
            }
        }
        return placed;
    }

    /** Places an entry among the others at this position, as one gained mid-belt, which a copy places by position. */
    public void place(T payload, double at) {
        var sorted = new ArrayList<>(entries);
        var entry = new Entry<>(nextId++, payload, at);
        sorted.add(entry);
        sorted.sort(Comparator.comparingDouble(Entry::position));
        entries.clear();
        entries.addAll(sorted);
        added.put(entry.id, entry);
        sided.add(entry.id);
    }

    /**
     * Removes the frontmost entry in {@code [from, to)} that {@code wanted} accepts, as a feeder's
     * head takes one from anywhere on a tile: the entries around it run on past the gap.
     */
    public @Nullable Entry<T> take(double from, double to, Predicate<T> wanted) {
        for (var iterator = entries.descendingIterator(); iterator.hasNext(); ) {
            var entry = iterator.next();
            if (entry.position < from || entry.position >= to || !wanted.test(entry.payload)) continue;
            iterator.remove();
            removed(entry);
            return entry;
        }
        return null;
    }

    // A ring's positions wrap, so each is also counted a length either side.
    private List<Double> positions(double length, boolean ring) {
        var positions = new ArrayList<Double>();
        for (var entry : entries) {
            positions.add(entry.position);
            if (ring) {
                positions.add(entry.position - length);
                positions.add(entry.position + length);
            }
        }
        return positions;
    }

    private static boolean fits(double placed, double from, double upper, List<Double> positions) {
        if (placed < from - GAP_TOLERANCE || placed > upper + GAP_TOLERANCE) return false;
        for (var position : positions) if (Math.abs(position - placed) < SPACING - GAP_TOLERANCE) return false;
        return true;
    }

    /** Removes the entries at or past this position, head first, as a belt cut short there loses them. */
    public List<Entry<T>> takeFrom(double position) {
        var taken = new ArrayList<Entry<T>>();
        while (!entries.isEmpty() && entries.peekLast().position >= position) {
            var entry = entries.pollLast();
            removed(entry);
            taken.addFirst(entry);
        }
        return taken;
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
          added.values().stream().map(entry -> new Added<>(entry.id, entry.payload, entry.position, sided.contains(entry.id))).toList());
        removed.clear();
        added.clear();
        sided.clear();
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
            if (!gained.side()) {
                var at = entries.isEmpty() ? gained.position() : Math.min(gained.position(), entries.peekFirst().position - SPACING);
                entries.addFirst(new Entry<>(gained.id(), gained.payload(), at));
            } else {
                var sorted = new ArrayList<>(entries);
                sorted.add(new Entry<>(gained.id(), gained.payload(), gained.position()));
                sorted.sort(Comparator.comparingDouble(Entry::position));
                entries.clear();
                entries.addAll(sorted);
            }
            nextId = Math.max(nextId, gained.id() + 1);
        }
    }

    /** Every entry as an addition, start first, for a copy that starts from nothing (#395). */
    public List<Added<T>> snapshot() {
        return entries.stream().map(entry -> new Added<>(entry.id, entry.payload, entry.position)).toList();
    }

    /** Replaces this belt's entries with a {@link #snapshot} of another's, ids and all. */
    public void reset(List<Added<T>> snapshot) {
        clear();
        for (var entry : snapshot) restore(entry.payload(), entry.position(), entry.id());
    }

    private void load(T payload, double at) {
        var entry = new Entry<>(nextId++, payload, at);
        entries.addFirst(entry);
        added.put(entry.id, entry);
    }

    private void removed(Entry<T> entry) {
        sided.remove(entry.id);
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
        sided.clear();
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

    /**
     * An entry gained, in the order it was loaded, at its position when drained, and whether it was
     * side-loaded mid-belt rather than loaded at the head.
     */
    public record Added<T>(int id, T payload, double position, boolean side) {

        public Added(int id, T payload, double position) {
            this(id, payload, position, false);
        }
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
