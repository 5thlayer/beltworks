package rearth.belts.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
            var splitter = new Splitter<String>(tier);
            var halves = new Halves(new Splitter.Half<>(), new Splitter.Half<>());
            // The belts tick first, so the splitter is still to move.
            var speed = tier.blocksPerTick();
            if (inLeft != null) inLeft.sink = item -> Join.offer(item, inLeft.overshoot(),
              Splitter.entering(halves.left, speed, hand, speed));
            if (inRight != null) inRight.sink = item -> Join.offer(item, inRight.overshoot(),
              Splitter.entering(halves.right, speed, null, speed));
            splitters.add(() -> splitter.tick(now,
              new Splitter.Side<>(halves.left, lane(outLeft), hand),
              new Splitter.Side<>(halves.right, lane(outRight), null)));
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

        private static Splitter.Lane<String> lane(Belt belt) {
            return belt == null ? null : new Splitter.Lane<>(belt.contents, belt.length(), belt.speed());
        }
    }

    private record Halves(Splitter.Half<String> left, Splitter.Half<String> right) {
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
}
