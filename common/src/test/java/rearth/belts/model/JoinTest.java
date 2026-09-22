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
            in.tick(4, speed, () -> "item", item -> false);
            loaded += in.size() - took;
            assertEquals(false, Join.pass(new Splitter.Lane<>(in, 4, speed), null));
        }
        assertEquals(32, in.size());
        assertEquals(32, loaded);
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
                Join.pass(new Splitter.Lane<>(in, 8, inTier.blocksPerTick()), new Splitter.Lane<>(out, 8, outTier.blocksPerTick()));
                in.tick(8, inTier.blocksPerTick(), () -> {
                    loaded++;
                    return "item";
                }, item -> false);
                out.tick(8, outTier.blocksPerTick(), () -> null, item -> {
                    delivered++;
                    return true;
                });
            }
        }
    }
}
