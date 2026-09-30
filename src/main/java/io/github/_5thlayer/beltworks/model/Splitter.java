// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.model;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Two belts in and two out, through two halves that are each a block of belt of the splitter's
 * tier. An item rides the first half of the side it entered and changes to its output side at the
 * midline, as each side of Factorio's splitter is 128 positions in and 128 out (PlanetaryFactory #373).
 *
 * <p>At the midline the two sides are each taken in turn, so one input splits evenly and two merge
 * evenly; a side that cannot move is skipped, so a backed-up output sends everything to the other.
 * Each side passes no more than the splitter's own tier.
 *
 * <p>An input priority takes from its side first, and from the other in the same tick whenever
 * that side has nothing ready, so a stuck priority side never holds up the merge.
 *
 * <p>An output priority sends everything to its side, and to the other only when that side cannot
 * move, as Factorio's does. With none, the outputs alternate.
 */
public final class Splitter<T> {

    /** A half is one block of belt, split at its midline into the segment before and the one after. */
    public static final double MIDLINE = 0.5;

    private final double speed;
    private final FlowLimit[] inputLimits;
    private final FlowLimit[] outputLimits;
    private int nextInput;
    private int nextOutput;
    private Priority outputPriority = Priority.NONE;
    private Priority inputPriority = Priority.NONE;

    public Splitter(BeltTier tier) {
        speed = tier.blocksPerTick();
        var itemsPerTick = tier.itemsPerTick();
        inputLimits = new FlowLimit[] {new FlowLimit(itemsPerTick), new FlowLimit(itemsPerTick)};
        outputLimits = new FlowLimit[] {new FlowLimit(itemsPerTick), new FlowLimit(itemsPerTick)};
    }

    /** Sets the side every item goes to first, or none to alternate. */
    public void outputPriority(Priority priority) {
        outputPriority = priority;
    }

    /**
     * Sets the side taken from first, or none to alternate. Unlike an output priority it never waits:
     * the other side passes at once whenever that side's head cannot move this tick.
     */
    public void inputPriority(Priority priority) {
        inputPriority = priority;
    }

    /**
     * Front to back: hands what reaches each half's front onto its outgoing belt, moves the
     * segments past the midline, passes what reaches it, then moves the segments before it. An
     * incoming belt hands its items on in its own tick, through {@link #entering}.
     *
     * @return whether any item was delivered, taken, moved or passed
     */
    public boolean tick(long gameTime, Side<T> left, Side<T> right) {
        var sides = List.of(left, right);
        var changed = false;
        var backedUp = new boolean[2];
        for (int i = 0; i < 2; i++) {
            var side = sides.get(i);
            var moved = side.out != null && Join.pass(new Handoff<>(side.half.leaving, MIDLINE, speed, side.hand(true)), side.out);
            moved |= side.half.leaving.tick(MIDLINE, speed, () -> null, item -> false, side.hand(true));
            backedUp[i] = !moved && !side.half.leaving.isEmpty();
            changed |= moved;
        }
        // Before the first segments move, as a belt delivers before it moves: the pass looks a
        // tick ahead, so after the move it would carry an item two ticks' travel in one.
        while (passOne(gameTime, sides, backedUp)) changed = true;
        for (var side : sides) {
            changed |= side.half.entering.tick(MIDLINE, speed, side.in, item -> false, side.hand(false));
        }
        return changed;
    }

    /**
     * The segment before a half's midline, as the belt ending there hands it items.
     *
     * @param speed   the splitter's tier's blocks per tick
     * @param hand    a hand held on the half, at a point along the whole half
     * @param pending how far the splitter is still to move this tick
     */
    public static <T> Handoff<T> entering(Half<T> half, double speed, BeltContents.@Nullable Hand<T> hand, double pending) {
        return new Handoff<>(half.entering, MIDLINE, speed, onSegment(hand, false), pending);
    }

    private static <T> BeltContents.@Nullable Hand<T> onSegment(BeltContents.@Nullable Hand<T> hand, boolean pastMidline) {
        if (hand == null || hand.point() >= MIDLINE != pastMidline) return null;
        return pastMidline ? new BeltContents.Hand<>(hand.point() - MIDLINE, hand.taker()) : hand;
    }

    private boolean passOne(long gameTime, List<Side<T>> sides, boolean[] backedUp) {
        var first = inputPriority == Priority.NONE ? nextInput : inputPriority.ordinal() - 1;
        for (int i = 0; i < 2; i++) {
            var in = (first + i) % 2;
            var input = sides.get(in).half.entering;
            // Not an entry a hand holds back: the hand takes it next tick.
            var hand = sides.get(in).hand(false);
            if (!input.endReady(MIDLINE, speed, hand == null ? -1 : hand.point())
                  || !inputLimits[in].ready(gameTime)) continue;
            var at = input.overshoot(MIDLINE, speed);
            var preferred = outputPriority == Priority.NONE ? -1 : outputPriority.ordinal() - 1;
            for (int o = 0; o < 2; o++) {
                var out = ((preferred < 0 ? nextOutput : preferred) + o) % 2;
                // Past a preferred side with no room only when it is backed up, its items not moving:
                // one still moving has room again in a tick or two.
                if (preferred >= 0 && out != preferred && !backedUp[preferred]) continue;
                var output = sides.get(out).half.leaving;
                var outHand = sides.get(out).hand(true);
                if (!output.canOffer(MIDLINE, at, outHand) || !outputLimits[out].ready(gameTime)) continue;
                output.offer(input.takeEnd(), MIDLINE, at, outHand);
                inputLimits[in].pass();
                outputLimits[out].pass();
                nextInput = 1 - in;
                nextOutput = 1 - out;
                return true;
            }
        }
        return false;
    }

    /** A splitter's preferred side, of its outputs or its inputs; none alternates. */
    public enum Priority {
        NONE, LEFT, RIGHT
    }

    /** One half's belt: the segment before its midline and the one after, each with positions of its own. */
    public static final class Half<T> {
        private final BeltContents<T> entering = new BeltContents<>();
        private final BeltContents<T> leaving = new BeltContents<>();
        private final double speed;

        public Half(BeltTier tier) {
            speed = tier.blocksPerTick();
        }

        /** Blocks per tick its items move: its own tier's belt speed (ADR 0007). */
        public double speed() {
            return speed;
        }

        /**
         * Takes the frontmost item {@code wanted} accepts, for a feeder's head, from past the
         * midline first, or null where there is none.
         */
        public @Nullable Taken<T> take(Predicate<T> wanted) {
            var taken = leaving.take(0, MIDLINE, wanted);
            if (taken != null) return new Taken<>(taken, true);
            taken = entering.take(0, MIDLINE, wanted);
            return taken == null ? null : new Taken<>(taken, false);
        }

        /** Puts an item a feeder took back where it was, when the far end refused it after all. */
        public void putBack(Taken<T> taken) {
            (taken.leaving() ? leaving : entering).place(taken.entry().payload(), taken.entry().position());
        }

        /** Whether a feeder's tail could {@link #drop} an item now. */
        public boolean canDrop() {
            return !Double.isNaN(dropPlacement());
        }

        /**
         * Drops an item for a feeder's tail just past the midline, where a tile's midpoint is, when
         * there is a gap there; it leaves by the half's own front.
         */
        public boolean drop(T item) {
            var at = dropPlacement();
            if (Double.isNaN(at)) return false;
            leaving.place(item, at);
            return true;
        }

        /** An item a feeder took from a half, and whether it was past the midline. */
        public record Taken<T>(BeltContents.Entry<T> entry, boolean leaving) {
        }

        // As wide as the half moves in a tick, so a one-item gap is caught as it crosses the midline.
        private double dropPlacement() {
            return leaving.dropPlacement(0, 0, Math.max(BeltContents.SPACING, speed), MIDLINE, false);
        }

        public BeltContents<T> entering() {
            return entering;
        }

        public BeltContents<T> leaving() {
            return leaving;
        }

        public int size() {
            return entering.size() + leaving.size();
        }

        public List<T> payloads() {
            var payloads = new ArrayList<T>();
            for (var entry : entering.entries()) payloads.add(entry.payload());
            for (var entry : leaving.entries()) payloads.add(entry.payload());
            return payloads;
        }

        public void clear() {
            entering.clear();
            leaving.clear();
        }
    }

    /**
     * One half as the splitter ticks it: what loads its back, as a loader loads a line's first
     * tile (#89), what its front hands on to, and any hand held on it.
     */
    public record Side<T>(Half<T> half, Supplier<T> in, @Nullable Outlet<T> out, BeltContents.@Nullable Hand<T> hand) {

        /** A half with nothing loading its back. */
        public Side(Half<T> half, @Nullable Outlet<T> out, BeltContents.@Nullable Hand<T> hand) {
            this(half, () -> null, out, hand);
        }

        private BeltContents.@Nullable Hand<T> hand(boolean pastMidline) {
            return onSegment(hand, pastMidline);
        }
    }

    /**
     * A belt an entry is handed across, with the length and speed its own tick uses, any hand held
     * on it, and how far it is still to move this tick.
     */
    public record Handoff<T>(BeltContents<T> belt, double length, double speed, BeltContents.@Nullable Hand<T> hand, double pending) {

        public Handoff(BeltContents<T> belt, double length, double speed) {
            this(belt, length, speed, null, 0);
        }

        public Handoff(BeltContents<T> belt, double length, double speed, BeltContents.@Nullable Hand<T> hand) {
            this(belt, length, speed, hand, 0);
        }
    }
}
