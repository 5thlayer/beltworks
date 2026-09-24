// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.model;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Two belts in and two out, through two halves that are each a block of belt of the splitter's
 * tier. An item rides the first half of the side it entered and changes to its output side at the
 * midline, as Factorio's splitter lane is 128 positions in and 128 out (PlanetaryFactory #373).
 *
 * <p>At the midline the two sides are each taken in turn, so one input splits evenly and two merge
 * evenly; a side that cannot move is skipped, so a backed-up output sends everything to the other.
 * Each side passes no more than the splitter's own tier.
 */
public final class Splitter<T> {

    /** A half is one block of belt, split at its midline into the segment before and the one after. */
    public static final double MIDLINE = 0.5;

    private final double speed;
    private final FlowLimit[] inputLimits;
    private final FlowLimit[] outputLimits;
    private int nextInput;
    private int nextOutput;

    public Splitter(BeltTier tier) {
        speed = tier.blocksPerTick();
        var itemsPerTick = tier.itemsPerTick();
        inputLimits = new FlowLimit[] {new FlowLimit(itemsPerTick), new FlowLimit(itemsPerTick)};
        outputLimits = new FlowLimit[] {new FlowLimit(itemsPerTick), new FlowLimit(itemsPerTick)};
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
        for (var side : sides) {
            if (side.out != null) changed |= Join.pass(new Lane<>(side.half.leaving, MIDLINE, speed, side.hand(true)), side.out);
            changed |= side.half.leaving.tick(MIDLINE, speed, () -> null, item -> false, side.hand(true));
        }
        // Before the first segments move, as a belt delivers before it moves: the pass looks a
        // tick ahead, so after the move it would carry an item two ticks' travel in one.
        while (passOne(gameTime, sides)) changed = true;
        for (var side : sides) {
            changed |= side.half.entering.tick(MIDLINE, speed, () -> null, item -> false, side.hand(false));
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
    public static <T> Lane<T> entering(Half<T> half, double speed, BeltContents.@Nullable Hand<T> hand, double pending) {
        return new Lane<>(half.entering, MIDLINE, speed, onSegment(hand, false), pending);
    }

    private static <T> BeltContents.@Nullable Hand<T> onSegment(BeltContents.@Nullable Hand<T> hand, boolean pastMidline) {
        if (hand == null || hand.point() >= MIDLINE != pastMidline) return null;
        return pastMidline ? new BeltContents.Hand<>(hand.point() - MIDLINE, hand.taker()) : hand;
    }

    private boolean passOne(long gameTime, List<Side<T>> sides) {
        for (int i = 0; i < 2; i++) {
            var in = (nextInput + i) % 2;
            var input = sides.get(in).half.entering;
            // Not an entry a hand holds back: the hand takes it next tick.
            var hand = sides.get(in).hand(false);
            if (!input.endReady(MIDLINE, speed, hand == null ? -1 : hand.point())
                  || !inputLimits[in].ready(gameTime)) continue;
            var at = input.overshoot(MIDLINE, speed);
            for (int o = 0; o < 2; o++) {
                var out = (nextOutput + o) % 2;
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

    /** One half's belt: the segment before its midline and the one after, each with positions of its own. */
    public static final class Half<T> {
        private final BeltContents<T> entering = new BeltContents<>();
        private final BeltContents<T> leaving = new BeltContents<>();

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
     * A half with the belt leaving it, null when there is none, and a player's hand on it at a
     * point along the whole half.
     */
    public record Side<T>(Half<T> half, @Nullable Lane<T> out, BeltContents.@Nullable Hand<T> hand) {

        private BeltContents.@Nullable Hand<T> hand(boolean pastMidline) {
            return onSegment(hand, pastMidline);
        }
    }

    /**
     * A belt an entry is handed across, with the length and speed its own tick uses, any hand held
     * on it, and how far it is still to move this tick.
     */
    public record Lane<T>(BeltContents<T> belt, double length, double speed, BeltContents.@Nullable Hand<T> hand, double pending) {

        public Lane(BeltContents<T> belt, double length, double speed) {
            this(belt, length, speed, null, 0);
        }

        public Lane(BeltContents<T> belt, double length, double speed, BeltContents.@Nullable Hand<T> hand) {
            this(belt, length, speed, hand, 0);
        }
    }
}
