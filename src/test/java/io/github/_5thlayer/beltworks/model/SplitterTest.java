// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SplitterTest {

    private static final int MINUTE = 20 * 60;
    private static final int TIER_1_PER_MINUTE = 15 * 60;

    @Test
    void oneInputSplitsEvenlyAcrossBothOutputs() {
        var line = new Line();
        var in = line.fed(BeltTier.BELT);
        var left = line.drained(BeltTier.BELT);
        var right = line.drained(BeltTier.BELT);
        line.splitter(BeltTier.BELT, in, null, left, right);

        line.run(MINUTE);
        line.resetCounts();
        line.run(MINUTE);

        assertEquals(TIER_1_PER_MINUTE / 2, left.delivered, 1);
        assertEquals(TIER_1_PER_MINUTE / 2, right.delivered, 1);
    }

    @Test
    void twoInputsMergeByAlternating() {
        var line = new Line();
        var left = line.fed(BeltTier.BELT);
        var right = line.fed(BeltTier.BELT);
        var out = line.drained(BeltTier.BELT);
        line.splitter(BeltTier.BELT, left, right, out, null);

        line.run(MINUTE);
        line.resetCounts();
        line.run(MINUTE);

        assertEquals(TIER_1_PER_MINUTE, out.delivered, 1);
        assertEquals(TIER_1_PER_MINUTE / 2, out.from("0"), 1);
        assertEquals(TIER_1_PER_MINUTE / 2, out.from("1"), 1);
    }

    @Test
    void anInputPriorityDrainsItsSideFirst() {
        var line = new Line();
        var left = line.fed(BeltTier.BELT);
        var right = line.fed(BeltTier.BELT);
        var out = line.drained(BeltTier.BELT);
        line.splitter(BeltTier.BELT, left, right, out, null).splitter.inputPriority(Splitter.Priority.RIGHT);

        line.run(MINUTE);
        line.resetCounts();
        line.run(MINUTE);

        assertEquals(TIER_1_PER_MINUTE, out.from("1"), 1);
        assertEquals(0, out.from("0"));
    }

    @Test
    void anInputPriorityPassesTheOtherSideWhenItsSideIsEmpty() {
        var line = new Line();
        var right = line.fed(BeltTier.BELT);
        var out = line.drained(BeltTier.BELT);
        line.splitter(BeltTier.BELT, null, right, out, null).splitter.inputPriority(Splitter.Priority.LEFT);

        line.run(MINUTE);
        line.resetCounts();
        line.run(MINUTE);

        assertEquals(TIER_1_PER_MINUTE, out.delivered, 1);
    }

    // A head that is not at the midline yet does not hold the other side back: the other fills the gaps.
    @Test
    void anInputPriorityPassesTheOtherSideWhenItsHeadIsNotReady() {
        var line = new Line();
        var slow = line.fedAt(BeltTier.BELT, 5.0 / 20);
        var fast = line.fed(BeltTier.BELT);
        var out = line.drained(BeltTier.BELT);
        line.splitter(BeltTier.BELT, slow, fast, out, null).splitter.inputPriority(Splitter.Priority.LEFT);

        line.run(MINUTE);
        line.resetCounts();
        line.run(MINUTE);

        assertEquals(TIER_1_PER_MINUTE, out.delivered, 1);
        assertEquals(5 * 60, out.from("0"), 1);
        assertEquals(TIER_1_PER_MINUTE - 5 * 60, out.from("1"), 1);
    }

    @Test
    void aBackedUpOutputSendsEverythingToTheOther() {
        var line = new Line();
        var in = line.fed(BeltTier.BELT);
        var blocked = line.blocked(BeltTier.BELT);
        var free = line.drained(BeltTier.BELT);
        line.splitter(BeltTier.BELT, in, null, blocked, free);

        line.run(MINUTE);
        line.resetCounts();
        line.run(MINUTE);

        assertEquals(TIER_1_PER_MINUTE, free.delivered, 1);
    }

    @Test
    void anOutputPriorityFillsItsSide() {
        var line = new Line();
        var in = line.fed(BeltTier.BELT);
        var left = line.drained(BeltTier.BELT);
        var right = line.drained(BeltTier.BELT);
        line.splitter(BeltTier.BELT, in, null, left, right).splitter.outputPriority(Splitter.Priority.RIGHT);

        line.run(MINUTE);
        line.resetCounts();
        line.run(MINUTE);

        assertEquals(TIER_1_PER_MINUTE, right.delivered, 1);
        assertEquals(0, left.delivered);
    }

    @Test
    void anOutputPriorityOverflowsWhenItsSideBacksUp() {
        var line = new Line();
        var in = line.fed(BeltTier.BELT);
        var blocked = line.blocked(BeltTier.BELT);
        var free = line.drained(BeltTier.BELT);
        line.splitter(BeltTier.BELT, in, null, blocked, free).splitter.outputPriority(Splitter.Priority.LEFT);

        line.run(MINUTE);
        line.resetCounts();
        line.run(MINUTE);

        assertEquals(TIER_1_PER_MINUTE, free.delivered, 1);
    }

    @Test
    void anOutputPriorityTakesTwoInputsToItsSide() {
        var line = new Line();
        var a = line.fedAt(BeltTier.BELT, 5.0 / 20);
        var b = line.fedAt(BeltTier.BELT, 5.0 / 20);
        var left = line.drained(BeltTier.BELT);
        var right = line.drained(BeltTier.BELT);
        line.splitter(BeltTier.BELT, a, b, left, right).splitter.outputPriority(Splitter.Priority.LEFT);

        line.run(MINUTE);
        line.resetCounts();
        line.run(MINUTE);

        assertEquals(10 * 60, left.delivered, 1);
        assertEquals(0, right.delivered);
    }

    @Test
    void aFilterSendsWhatItMatchesToThePrioritySideAndTheRestToTheOther() {
        var line = new Line();
        var a = line.fedAt(BeltTier.BELT, 5.0 / 20);
        var b = line.fedAt(BeltTier.BELT, 5.0 / 20);
        var left = line.drained(BeltTier.BELT);
        var right = line.drained(BeltTier.BELT);
        var splitter = line.splitter(BeltTier.BELT, a, b, left, right).splitter;
        splitter.outputPriority(Splitter.Priority.LEFT);
        splitter.filter("0"::equals);

        line.run(MINUTE);
        line.resetCounts();
        line.run(MINUTE);

        assertEquals(5 * 60, left.from("0"), 1);
        assertEquals(0, left.from("1"));
        assertEquals(5 * 60, right.from("1"), 1);
        assertEquals(0, right.from("0"));
    }

    @Test
    void aFilteredItemWaitsWhenItsSideIsBackedUp() {
        var line = new Line();
        var in = line.fed(BeltTier.BELT);
        var blocked = line.blocked(BeltTier.BELT);
        var free = line.drained(BeltTier.BELT);
        var splitter = line.splitter(BeltTier.BELT, in, null, blocked, free).splitter;
        splitter.outputPriority(Splitter.Priority.LEFT);
        splitter.filter("0"::equals);

        line.run(2 * MINUTE);

        assertEquals(0, free.delivered);
    }

    @Test
    void anUnfilteredItemWaitsWhenTheOtherSideIsBackedUp() {
        var line = new Line();
        var in = line.fed(BeltTier.BELT);
        var free = line.drained(BeltTier.BELT);
        var blocked = line.blocked(BeltTier.BELT);
        var splitter = line.splitter(BeltTier.BELT, in, null, free, blocked).splitter;
        splitter.outputPriority(Splitter.Priority.LEFT);
        splitter.filter("other"::equals);

        line.run(2 * MINUTE);

        assertEquals(0, free.delivered);
    }

    @Test
    void aBlockedHeadHoldsItsLaneWhileTheOtherInputStillFlows() {
        var line = new Line();
        var stuck = line.fed(BeltTier.BELT);
        var flowing = line.fed(BeltTier.BELT);
        var blocked = line.blocked(BeltTier.BELT);
        var free = line.drained(BeltTier.BELT);
        var splitter = line.splitter(BeltTier.BELT, stuck, flowing, blocked, free).splitter;
        splitter.outputPriority(Splitter.Priority.LEFT);
        splitter.inputPriority(Splitter.Priority.LEFT);
        splitter.filter("0"::equals);

        line.run(MINUTE);
        line.resetCounts();
        line.run(MINUTE);

        assertEquals(TIER_1_PER_MINUTE, free.from("1"), 1);
        assertEquals(0, free.from("0"));
    }

    @Test
    void aFilterIsIgnoredWithoutAnOutputPriority() {
        var line = new Line();
        var in = line.fed(BeltTier.BELT);
        var left = line.drained(BeltTier.BELT);
        var right = line.drained(BeltTier.BELT);
        line.splitter(BeltTier.BELT, in, null, left, right).splitter.filter("0"::equals);

        line.run(MINUTE);
        line.resetCounts();
        line.run(MINUTE);

        assertEquals(TIER_1_PER_MINUTE / 2, left.delivered, 1);
        assertEquals(TIER_1_PER_MINUTE / 2, right.delivered, 1);
    }

    // A loader at a half's back loads it as it loads a line's first tile (#89).
    @Test
    void aSourceAtAHalfsBackFillsItAtItsTier() {
        var line = new Line();
        var out = line.drained(BeltTier.BELT);
        line.splitter(BeltTier.BELT, null, null, out, null, () -> "loaded");

        line.run(MINUTE);
        line.resetCounts();
        line.run(MINUTE);

        assertEquals(TIER_1_PER_MINUTE, out.delivered, 1);
    }

    @Test
    void aSplitterCapsTheLineAtItsOwnTier() {
        var line = new Line();
        var in = line.fed(BeltTier.EXPRESS);
        var out = line.drained(BeltTier.EXPRESS);
        line.splitter(BeltTier.BELT, in, null, out, null);

        line.run(MINUTE);
        line.resetCounts();
        line.run(MINUTE);

        assertEquals(TIER_1_PER_MINUTE, out.delivered, 1);
    }

    @Test
    void oneSplitterBalancesUnequalInputs() {
        var line = new Line();
        var full = line.fed(BeltTier.BELT);
        var trickle = line.fedAt(BeltTier.BELT, 5.0 / 20);
        var left = line.drained(BeltTier.BELT);
        var right = line.drained(BeltTier.BELT);
        line.splitter(BeltTier.BELT, full, trickle, left, right);

        line.run(MINUTE);
        line.resetCounts();
        line.run(5 * MINUTE);

        // A window's edges can each catch a half's first segment part full.
        assertEquals((15 + 5) * 60 * 5 / 2, left.delivered, 4);
        assertEquals((15 + 5) * 60 * 5 / 2, right.delivered, 4);
    }

    /** Two splitters, then two more fed crosswise, as Factorio's 4x4 balancer is built. */
    @Test
    void fourSplittersBalanceUnequalInputsAcrossFourOutputs() {
        var line = new Line();
        var full = line.fed(BeltTier.BELT);
        var trickle = line.fedAt(BeltTier.BELT, 5.0 / 20);
        var aLeft = line.between(BeltTier.BELT);
        var aRight = line.between(BeltTier.BELT);
        var bLeft = line.between(BeltTier.BELT);
        var bRight = line.between(BeltTier.BELT);
        var outs = List.of(line.drained(BeltTier.BELT), line.drained(BeltTier.BELT),
          line.drained(BeltTier.BELT), line.drained(BeltTier.BELT));
        line.splitter(BeltTier.BELT, full, null, aLeft, aRight);
        line.splitter(BeltTier.BELT, trickle, null, bLeft, bRight);
        line.splitter(BeltTier.BELT, aLeft, bLeft, outs.get(0), outs.get(1));
        line.splitter(BeltTier.BELT, aRight, bRight, outs.get(2), outs.get(3));

        line.run(MINUTE);
        line.resetCounts();
        line.run(MINUTE);

        for (var out : outs) assertEquals((15 + 5) * 60 / 4, out.delivered, 1);
    }

    /** Both inputs fed and no belt leaving: each half backs up to one block of belt (#373). */
    @Test
    void aBackedUpHalfHoldsEight() {
        var line = new Line();
        var splitter = line.splitter(BeltTier.BELT, line.fed(BeltTier.BELT), line.fed(BeltTier.BELT), null, null);

        line.run(MINUTE);

        assertEquals(8, splitter.left.size());
        assertEquals(8, splitter.right.size());
    }

    @Test
    void anItemCrossesAHalfAtTheSplittersOwnSpeed() {
        var line = new Line();
        var in = line.fedCount(BeltTier.IMPROVED, 1);
        var splitter = line.splitter(BeltTier.BELT, in, null, line.drained(BeltTier.IMPROVED), null);

        var positions = new ArrayList<Double>();
        for (int tick = 0; tick < MINUTE; tick++) {
            line.run(1);
            for (var entry : splitter.left.entering().entries()) positions.add(entry.position());
        }

        assertTrue(positions.size() >= 3, "the item was seen on the half's first segment " + positions.size() + " times");
        // Short of the segment's last position, where it stops until the midline takes it.
        var last = Splitter.MIDLINE - BeltContents.SPACING;
        var steps = 0;
        for (int i = 1; i < positions.size() && positions.get(i) < last; i++, steps++) {
            assertEquals(BeltTier.BELT.blocksPerTick(), positions.get(i) - positions.get(i - 1), 1e-9);
        }
        assertTrue(steps >= 1, "no step short of the segment's end was seen");
    }

    /**
     * An item never moves further in a tick than its tier's speed, plus, where it crosses the
     * midline, the one entry's spacing between a segment's last position and the next one's first.
     */
    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4})
    void anItemCrossingTheMidlineMovesNoFurtherThanOneTick(int number) {
        var tier = BeltTier.of(number);
        var line = new Line();
        var in = line.fedCount(tier, 1);
        var splitter = line.splitter(tier, in, null, line.drained(tier), null);

        var positions = new ArrayList<Double>();
        for (int tick = 0; tick < MINUTE; tick++) {
            line.run(1);
            for (var entry : splitter.left.entering().entries()) positions.add(entry.position());
            for (var entry : splitter.left.leaving().entries()) positions.add(Splitter.MIDLINE + entry.position());
        }

        assertTrue(positions.size() >= 2, "the item was seen on the half " + positions.size() + " times");
        var step = tier.blocksPerTick() + BeltContents.SPACING;
        for (int i = 1; i < positions.size(); i++) {
            var moved = positions.get(i) - positions.get(i - 1);
            assertTrue(moved <= step + 1e-9, "moved " + moved + " in one tick at tier " + number);
        }
    }

    /** Fed on the left and leaving on the right: nothing rides the right's first half or the left's second. */
    @Test
    void anItemChangesSideOnlyAtTheMidline() {
        var line = new Line();
        var in = line.fed(BeltTier.BELT);
        var out = line.drained(BeltTier.BELT);
        var splitter = line.splitter(BeltTier.BELT, in, null, null, out);

        for (int tick = 0; tick < MINUTE; tick++) {
            line.run(1);
            assertTrue(splitter.right.entering().isEmpty());
        }

        assertTrue(out.delivered > 0);
        assertEquals(4, splitter.left.leaving().size(), "the left's second half backs up, as no belt leaves it");
    }

    @Test
    void aBalancerLosesAndCreatesNothing() {
        var line = new Line();
        var a = line.fedCount(BeltTier.BELT, 300);
        var b = line.fedCount(BeltTier.BELT, 100);
        var aLeft = line.between(BeltTier.BELT);
        var aRight = line.between(BeltTier.BELT);
        var outs = List.of(line.drained(BeltTier.BELT), line.drained(BeltTier.BELT));
        line.splitter(BeltTier.BELT, a, b, aLeft, aRight);
        line.splitter(BeltTier.BELT, aLeft, aRight, outs.get(0), outs.get(1));

        line.run(3 * MINUTE);

        assertEquals(400, outs.get(0).delivered + outs.get(1).delivered);
    }

    /** Held at points before the midline and past it (#350, #373). */
    @ParameterizedTest
    @ValueSource(doubles = {0.1, 0.25, 0.4, 0.6, 0.9})
    void aHandHeldOnAHalfTakesAtTheHalfsRate(double at) {
        var line = new Line();
        var in = line.fed(BeltTier.BELT);
        var splitter = line.splitter(BeltTier.BELT, in, null, line.drained(BeltTier.BELT), line.drained(BeltTier.BELT));
        var taken = new int[1];
        line.hand = new BeltContents.Hand<>(at - BeltContents.SPACING / 2, item -> {
            taken[0]++;
            return true;
        });

        line.run(MINUTE);
        taken[0] = 0;
        line.run(MINUTE);

        // Past the midline the hand sees only the items split to its side.
        assertEquals(at < Splitter.MIDLINE ? TIER_1_PER_MINUTE : TIER_1_PER_MINUTE / 2, taken[0], 1);
    }

    /** Tile lines and splitters ticked the way a level ticks them: every line, then every splitter. */
    private static final class Line {
        private final List<Belt> belts = new ArrayList<>();
        private final List<Runnable> splitters = new ArrayList<>();
        private long now;
        private int inputs;
        BeltContents.Hand<String> hand;

        Belt fed(BeltTier tier) {
            return fedAt(tier, Double.MAX_VALUE);
        }

        Belt fedAt(BeltTier tier, double itemsPerTick) {
            var name = String.valueOf(inputs++);
            var limit = itemsPerTick == Double.MAX_VALUE ? null : new FlowLimit(itemsPerTick);
            return add(new Belt(tier, () -> {
                if (limit == null) return name;
                if (!limit.ready(now)) return null;
                limit.pass();
                return name;
            }, null));
        }

        Belt fedCount(BeltTier tier, int count) {
            var name = String.valueOf(inputs++);
            var left = new int[] {count};
            return add(new Belt(tier, () -> left[0]-- > 0 ? name : null, null));
        }

        Belt drained(BeltTier tier) {
            var belt = new Belt(tier, () -> null, null);
            belt.sink = item -> {
                belt.delivered++;
                belt.sources.add(item);
                return true;
            };
            return add(belt);
        }

        Belt blocked(BeltTier tier) {
            return add(new Belt(tier, () -> null, item -> false));
        }

        Belt between(BeltTier tier) {
            return add(new Belt(tier, () -> null, item -> false));
        }

        Halves splitter(BeltTier tier, Belt inLeft, Belt inRight, Belt outLeft, Belt outRight) {
            return splitter(tier, inLeft, inRight, outLeft, outRight, () -> null);
        }

        Halves splitter(BeltTier tier, Belt inLeft, Belt inRight, Belt outLeft, Belt outRight, Supplier<String> loadLeft) {
            var splitter = new Splitter<String>(tier);
            var halves = new Halves(splitter, new Splitter.Half<>(tier), new Splitter.Half<>(tier));
            // The belts tick first, so the splitter is still to move.
            var speed = tier.blocksPerTick();
            if (inLeft != null) inLeft.sink = item -> Join.offer(item, inLeft.overshoot(),
              Splitter.entering(halves.left, speed, hand, speed));
            if (inRight != null) inRight.sink = item -> Join.offer(item, inRight.overshoot(),
              Splitter.entering(halves.right, speed, null, speed));
            splitters.add(() -> splitter.tick(now,
              new Splitter.Side<>(halves.left, loadLeft, handoff(outLeft), hand),
              new Splitter.Side<>(halves.right, handoff(outRight), null)));
            return halves;
        }

        void run(int ticks) {
            for (int tick = 0; tick < ticks; tick++, now++) {
                for (var belt : belts) belt.tick();
                splitters.forEach(Runnable::run);
            }
        }

        void resetCounts() {
            for (var belt : belts) {
                belt.delivered = 0;
                belt.sources.clear();
            }
        }

        private Belt add(Belt belt) {
            belts.add(belt);
            return belt;
        }

        private static Outlet<String> handoff(Belt belt) {
            return belt == null ? null : Outlet.entry(new Splitter.Handoff<>(belt.contents, belt.length(), belt.speed()));
        }
    }

    private record Halves(Splitter<String> splitter, Splitter.Half<String> left, Splitter.Half<String> right) {
    }

    private static final class Belt {
        static final double LENGTH = 4;

        final BeltTier tier;
        final TransportLine<String> line;
        final BeltContents<String> contents;
        final Supplier<String> source;
        // A belt that ends at a splitter hands its end on to the splitter's half.
        Predicate<String> sink;
        int delivered;
        final List<String> sources = new ArrayList<>();

        Belt(BeltTier tier, Supplier<String> source, Predicate<String> sink) {
            this.tier = tier;
            line = new TransportLine<>(Collections.nCopies((int) LENGTH, tier));
            contents = line.contents();
            this.source = source;
            this.sink = sink == null ? item -> false : sink;
        }

        double length() {
            return line.length();
        }

        double speed() {
            return line.speed();
        }

        void tick() {
            line.tick(source, sink);
        }

        double overshoot() {
            return contents.overshoot(length(), speed());
        }

        long from(String input) {
            return sources.stream().filter(input::equals).count();
        }
    }

    @Test
    void aFeederTakesFromPastTheMidlineFirst() {
        var half = new Splitter.Half<String>(BeltTier.BELT);
        half.entering().place("in", 0.1);
        half.leaving().place("out", 0.1);

        var taken = half.take(item -> true);

        assertEquals("out", taken.entry().payload());
        assertTrue(taken.leaving());
        assertEquals(List.of("in"), half.payloads());
    }

    @Test
    void aFeederTakesFromBeforeTheMidlineWhenNothingIsPastIt() {
        var half = new Splitter.Half<String>(BeltTier.BELT);
        half.entering().place("in", 0.1);

        var taken = half.take(item -> true);

        assertEquals("in", taken.entry().payload());
        assertFalse(taken.leaving());
        assertNull(half.take(item -> true));
    }

    @Test
    void anItemPutBackIsWhereItWasTaken() {
        var half = new Splitter.Half<String>(BeltTier.BELT);
        half.leaving().place("out", 0.2);

        half.putBack(half.take(item -> true));

        assertEquals(List.of(0.2), half.leaving().entries().stream().map(BeltContents.Entry::position).toList());
    }

    @Test
    void aFeederDropsJustPastTheMidline() {
        var half = new Splitter.Half<String>(BeltTier.BELT);

        assertTrue(half.canDrop());
        assertTrue(half.drop("dropped"));

        assertEquals(List.of(0.0), half.leaving().entries().stream().map(BeltContents.Entry::position).toList());
        assertTrue(half.entering().isEmpty());
    }

    @Test
    void aFeederWaitsWhileThereIsNoGapPastTheMidline() {
        var half = new Splitter.Half<String>(BeltTier.BELT);
        half.leaving().place("a", 0);
        half.leaving().place("b", BeltContents.SPACING);

        assertFalse(half.canDrop());
        assertFalse(half.drop("dropped"));
        assertEquals(2, half.size());
    }

    @Test
    void aFasterHalfCatchesAGapAsWideAsItMovesInATick() {
        var half = new Splitter.Half<String>(BeltTier.TURBO);
        half.leaving().place("a", 0);
        half.leaving().place("b", BeltContents.SPACING + half.speed());

        assertTrue(half.drop("dropped"));
        assertEquals(3, half.leaving().size());
    }
}
