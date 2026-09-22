package rearth.belts.model;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** A support joining two belts hands each item from one's end to the next one's head (#366). */
class JoinTest {

    private static final int MINUTE = 20 * 60;

    @ParameterizedTest
    @CsvSource({
        "1, 1, 15",
        "1, 3, 15",
        "3, 1, 15",
        "2, 4, 30",
        "4, 4, 60",
    })
    void aJoinRunsAtTheSlowerBelt(int in, int out, int itemsPerSecond) {
        var line = new Line(BeltTier.of(in), BeltTier.of(out));

        line.run(MINUTE);
        var before = line.delivered;
        line.run(MINUTE);

        assertEquals(itemsPerSecond * 60, line.delivered - before, 1);
        assertEquals(line.loaded, line.delivered + line.in.size() + line.out.size());
    }

    @Test
    void aDeadEndBacksUp() {
        var in = new BeltContents<String>();
        var speed = BeltTier.BELT.blocksPerTick();
        var loaded = 0;
        for (int tick = 0; tick < MINUTE; tick++) {
            var took = in.size();
            in.tick(4, speed, () -> "item", item -> Join.offer(item, in.overshoot(4, speed), null));
            loaded += in.size() - took;
        }
        assertEquals(32, in.size());
        assertEquals(32, loaded);
    }

    /** Across the join an item moves its tier's speed each tick, whichever belt ticks first (#373). */
    @ParameterizedTest
    @CsvSource({"true", "false"})
    void anItemCrossesAJoinAtItsSpeed(boolean receiverFirst) {
        var speed = BeltTier.BELT.blocksPerTick();
        var in = new BeltContents<String>();
        var out = new BeltContents<String>();
        var loaded = new boolean[1];
        var positions = new java.util.ArrayList<Double>();
        for (int tick = 0; tick < MINUTE; tick++) {
            if (receiverFirst) out.tick(4, speed, () -> null, item -> false);
            var receiving = new Splitter.Lane<>(out, 4, speed, null, receiverFirst ? 0 : speed);
            in.tick(4, speed, () -> loaded[0] ? null : (loaded[0] = true) ? "item" : null,
              item -> Join.offer(item, in.overshoot(4, speed), receiving));
            if (!receiverFirst) out.tick(4, speed, () -> null, item -> false);
            // A belt's last position is the next one's first.
            for (var entry : in.entries()) positions.add(entry.position());
            for (var entry : out.entries()) positions.add(4 - BeltContents.SPACING + entry.position());
        }

        var end = 2 * (4 - BeltContents.SPACING);
        var steps = 0;
        for (int i = 1; i < positions.size() && positions.get(i) < end; i++, steps++) {
            assertEquals(speed, positions.get(i) - positions.get(i - 1), 1e-9, "step " + i);
        }
        assertEquals(true, steps > 60);
    }

    private static final class Line {
        final BeltContents<String> in = new BeltContents<>();
        final BeltContents<String> out = new BeltContents<>();
        final BeltTier inTier;
        final BeltTier outTier;
        int loaded;
        int delivered;

        Line(BeltTier inTier, BeltTier outTier) {
            this.inTier = inTier;
            this.outTier = outTier;
        }

        void run(int ticks) {
            for (int tick = 0; tick < ticks; tick++) {
                // The receiving belt ticks after, so the join places short by its move.
                var receiving = new Splitter.Lane<>(out, 8, outTier.blocksPerTick(), null, outTier.blocksPerTick());
                in.tick(8, inTier.blocksPerTick(), () -> {
                    loaded++;
                    return "item";
                }, item -> Join.offer(item, in.overshoot(8, inTier.blocksPerTick()), receiving));
                out.tick(8, outTier.blocksPerTick(), () -> null, item -> {
                    delivered++;
                    return true;
                });
            }
        }
    }
}
