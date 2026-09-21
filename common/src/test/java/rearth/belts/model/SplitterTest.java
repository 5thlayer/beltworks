package rearth.belts.model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
        line.run(MINUTE);

        assertEquals((15 + 5) * 60 / 2, left.delivered, 1);
        assertEquals((15 + 5) * 60 / 2, right.delivered, 1);
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

    /** Belts and splitters ticked the way a level ticks them: every belt, then every splitter. */
    private static final class Line {
        private final List<Belt> belts = new ArrayList<>();
        private final List<Runnable> splitters = new ArrayList<>();
        private long now;
        private int inputs;

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

        void splitter(BeltTier tier, Belt inLeft, Belt inRight, Belt outLeft, Belt outRight) {
            var splitter = new Splitter<String>(tier.itemsPerTick());
            splitters.add(() -> splitter.tick(now, lane(inLeft), lane(inRight), lane(outLeft), lane(outRight)));
        }

        void run(int ticks) {
            for (int tick = 0; tick < ticks; tick++, now++) {
                for (var belt : belts) belt.contents.tick(Belt.LENGTH, belt.tier.blocksPerTick(), belt.source, belt.sink);
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
            return belt == null ? null : new Splitter.Lane<>(belt.contents, Belt.LENGTH, belt.tier.blocksPerTick());
        }
    }

    private static final class Belt {
        static final double LENGTH = 4;

        final BeltTier tier;
        final BeltContents<String> contents = new BeltContents<>();
        final Supplier<String> source;
        // A belt that ends at a splitter refuses at its end: the splitter takes from it.
        Predicate<String> sink;
        int delivered;
        final List<String> sources = new ArrayList<>();

        Belt(BeltTier tier, Supplier<String> source, Predicate<String> sink) {
            this.tier = tier;
            this.source = source;
            this.sink = sink == null ? item -> false : sink;
        }

        long from(String input) {
            return sources.stream().filter(input::equals).count();
        }
    }
}
