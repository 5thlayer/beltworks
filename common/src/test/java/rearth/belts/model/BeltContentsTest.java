package rearth.belts.model;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BeltContentsTest {

    // Factorio's transport-belt and fast-transport-belt, 1.875 and 3.75 blocks a second.
    private static final double TIER_1 = 0.09375;
    private static final double TIER_2 = 0.1875;

    private static final Supplier<String> ENDLESS = () -> "item";
    private static final Predicate<String> ACCEPTS = item -> true;
    private static final Predicate<String> REFUSES = item -> false;

    @Test
    void tierOneDeliversFifteenItemsPerSecond() {
        var belt = new BeltContents<String>();
        var delivered = new int[1];
        Predicate<String> counting = item -> {
            delivered[0]++;
            return true;
        };

        for (int tick = 0; tick < 20 * 60; tick++) belt.tick(8, TIER_1, ENDLESS, ACCEPTS);
        delivered[0] = 0;
        for (int tick = 0; tick < 20 * 60; tick++) belt.tick(8, TIER_1, ENDLESS, counting);

        assertEquals(15 * 60, delivered[0]);
    }

    @Test
    void aBackedUpBeltHoldsEightEntriesPerBlock() {
        var belt = new BeltContents<String>();

        for (int tick = 0; tick < 20 * 60; tick++) belt.tick(64, TIER_1, ENDLESS, REFUSES);

        assertEquals(512, belt.size());
    }

    @Test
    void aBackedUpBeltKeepsItsSpacing() {
        var belt = new BeltContents<String>();

        for (int tick = 0; tick < 20 * 20; tick++) belt.tick(4, TIER_1, ENDLESS, REFUSES);

        var positions = belt.entries().stream().map(BeltContents.Entry::position).toList();
        for (int i = 0; i < positions.size(); i++) {
            assertEquals(i * BeltContents.SPACING, positions.get(i));
        }
    }

    @Test
    void aFasterBeltLoadsMoreThanOneEntryInATick() {
        var belt = new BeltContents<String>();
        int mostInOneTick = 0;

        for (int tick = 0; tick < 200; tick++) {
            var loaded = new int[1];
            belt.tick(16, TIER_2, () -> {
                loaded[0]++;
                return "item";
            }, ACCEPTS);
            if (tick > 0) mostInOneTick = Math.max(mostInOneTick, loaded[0]);
        }

        assertEquals(2, mostInOneTick);
    }

    @Test
    void aFasterBeltDeliversMoreThanOneEntryInATick() {
        var belt = new BeltContents<String>();
        for (int tick = 0; tick < 200; tick++) belt.tick(16, TIER_2, ENDLESS, ACCEPTS);

        int mostInOneTick = 0;
        for (int tick = 0; tick < 200; tick++) {
            var delivered = new int[1];
            belt.tick(16, TIER_2, ENDLESS, item -> {
                delivered[0]++;
                return true;
            });
            mostInOneTick = Math.max(mostInOneTick, delivered[0]);
        }

        assertEquals(2, mostInOneTick);
    }

    @Test
    void entriesArriveInTheOrderTheyWereLoaded() {
        var belt = new BeltContents<Integer>();
        var next = new int[1];
        List<Integer> arrived = new ArrayList<>();

        for (int tick = 0; tick < 400; tick++) {
            belt.tick(8, TIER_2, () -> next[0]++, arrived::add);
        }

        for (int i = 0; i < arrived.size(); i++) assertEquals(i, arrived.get(i));
        assertTrue(arrived.size() > 100);
    }

    @Test
    void anEntryTravelsTheBeltBeforeItIsDelivered() {
        var belt = new BeltContents<String>();
        var ticks = 0;

        belt.tick(3, TIER_1, new ArrayDeque<>(List.of("item"))::poll, ACCEPTS);
        while (belt.size() > 0) {
            belt.tick(3, TIER_1, () -> null, ACCEPTS);
            ticks++;
        }

        // From 0 to the last slot, 3 - 1/8 blocks, at 3/32 a tick: 30.67, so the 31st tick.
        assertEquals(31, ticks);
    }

    @Test
    void aRefusedEntryWaitsAtTheEndAndIsDeliveredOnceAccepted() {
        var belt = new BeltContents<String>();
        belt.restore("item", 2 - BeltContents.SPACING);

        belt.tick(2, TIER_1, () -> null, REFUSES);
        assertEquals(1, belt.size());
        assertEquals(2 - BeltContents.SPACING, belt.entries().getFirst().position());

        belt.tick(2, TIER_1, () -> null, ACCEPTS);
        assertEquals(0, belt.size());
    }

    @Test
    void aBackedUpBeltReportsNoChange() {
        var belt = new BeltContents<String>();
        for (int tick = 0; tick < 200; tick++) belt.tick(4, TIER_1, ENDLESS, REFUSES);

        assertFalse(belt.tick(4, TIER_1, ENDLESS, REFUSES));
        assertTrue(belt.tick(4, TIER_1, ENDLESS, ACCEPTS));
    }

    @Test
    void fittingAShorterBeltSpillsFromTheHead() {
        var belt = new BeltContents<Integer>();
        var next = new int[1];
        for (int tick = 0; tick < 400; tick++) belt.tick(4, TIER_1, () -> next[0]++, item -> false);

        var spilled = belt.fit(2);

        assertEquals(16, belt.size());
        assertEquals(List.of(), belt.fit(2));
        assertEquals(16, spilled.size());
        assertTrue(spilled.getLast() > belt.entries().getFirst().payload());
        var positions = belt.entries().stream().map(BeltContents.Entry::position).toList();
        for (int i = 0; i < positions.size(); i++) {
            assertEquals(i * BeltContents.SPACING, positions.get(i));
        }
    }

    @Test
    void entriesRestoredOnTopOfEachOtherAreSpacedOut() {
        var belt = new BeltContents<String>();
        for (int i = 0; i < 3; i++) belt.restore("item", 0);

        belt.fit(4);

        assertEquals(List.of(0.0, 0.125, 0.25),
                belt.entries().stream().map(BeltContents.Entry::position).toList());
    }

    @Test
    void anEmptySupplierLoadsNothing() {
        var belt = new BeltContents<String>();

        belt.tick(8, TIER_1, () -> null, ACCEPTS);

        assertEquals(0, belt.size());
    }
}
