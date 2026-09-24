// SPDX-FileCopyrightText: Rearth
// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: CC-BY-4.0 AND MIT

package io.github._5thlayer.beltworks.model;

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

    @Test
    void aHandTakesWhatCrossesItsPointAtTheBeltsRateWhileTheHeadKeepsLoading() {
        var belt = new BeltContents<String>();
        for (int tick = 0; tick < 20 * 10; tick++) belt.tick(8, TIER_1, ENDLESS, ACCEPTS);

        var taken = new int[1];
        var delivered = new int[1];
        var loaded = new int[1];
        var hand = new BeltContents.Hand<String>(4, item -> ++taken[0] > 0);
        var pastTheHand = belt.entries().stream().filter(entry -> entry.position() > 4).count();
        for (int tick = 0; tick < 20 * 60; tick++) {
            belt.tick(8, TIER_1, () -> {
                loaded[0]++;
                return "item";
            }, item -> ++delivered[0] > 0, hand);
        }

        // Tiers 1 and 3 pass a whole number of items only every four ticks, so a minute is exact.
        assertEquals(15 * 60, loaded[0]);
        assertTrue(Math.abs(taken[0] - 15 * 60) <= 1, "taken " + taken[0]);
        assertEquals(pastTheHand, delivered[0]);
        assertTrue(pastTheHand > 0);
    }

    @Test
    void entriesPastTheHandCarryOnToTheEnd() {
        var belt = new BeltContents<Integer>();
        belt.restore(0, 1);
        belt.restore(1, 5);
        var taken = new ArrayList<Integer>();
        var arrived = new ArrayList<Integer>();

        for (int tick = 0; tick < 100; tick++) {
            belt.tick(8, TIER_1, () -> null, arrived::add, new BeltContents.Hand<>(3, taken::add));
        }

        assertEquals(List.of(0), taken);
        assertEquals(List.of(1), arrived);
    }

    @Test
    void aHandTakesFromABackedUpBeltWhatIsStoppedOnItsPointAndLeavesTheRest() {
        var belt = new BeltContents<Integer>();
        var next = new int[1];
        for (int tick = 0; tick < 20 * 20; tick++) belt.tick(4, TIER_1, () -> next[0]++, item -> false);
        var fullAt = belt.size();

        var taken = new ArrayList<Integer>();
        var point = 2.0;
        var stoppedOnPoint = belt.entries().stream()
                .filter(entry -> entry.position() <= point && point < entry.position() + BeltContents.SPACING)
                .findFirst().orElseThrow().payload();
        var downstream = belt.entries().stream().filter(entry -> entry.position() > point)
                .map(BeltContents.Entry::payload).toList();
        for (int tick = 0; tick < 20; tick++) {
            belt.tick(4, TIER_1, () -> null, item -> false, new BeltContents.Hand<>(point, taken::add));
        }

        assertEquals(stoppedOnPoint, taken.getFirst());
        // A second's worth, the belt's rate, not the whole queue behind the point at once.
        assertTrue(Math.abs(taken.size() - 15) <= 1, "taken " + taken.size());
        assertEquals(fullAt - taken.size(), belt.size());
        assertEquals(downstream, belt.entries().stream().filter(entry -> entry.position() > point)
                .map(BeltContents.Entry::payload).toList());
    }

    @Test
    void aBackedUpBeltHeldAtItsEndGivesUpEveryEntry() {
        var belt = new BeltContents<Integer>();
        var next = new int[1];
        for (int tick = 0; tick < 20 * 20; tick++) belt.tick(4, TIER_1, () -> next[0]++, item -> false);
        var fullAt = belt.size();

        var taken = new ArrayList<Integer>();
        for (int tick = 0; tick < 20 * 4; tick++) {
            belt.tick(4, TIER_1, () -> null, item -> false, new BeltContents.Hand<>(4, taken::add));
        }

        assertEquals(fullAt, taken.size());
        assertEquals(0, belt.size());
    }

    @Test
    void aHandThatRefusesHoldsTheEntryAtItsPointAndBacksTheBeltUpBehindIt() {
        var belt = new BeltContents<String>();

        for (int tick = 0; tick < 20 * 20; tick++) {
            belt.tick(8, TIER_1, ENDLESS, ACCEPTS, new BeltContents.Hand<>(2, REFUSES));
        }

        var positions = belt.entries().stream().map(BeltContents.Entry::position).toList();
        assertEquals(17, positions.size());
        for (int i = 0; i < positions.size(); i++) {
            assertEquals(i * BeltContents.SPACING, positions.get(i));
        }
    }

    @Test
    void aHandOnAFasterBeltTakesAtThatBeltsRate() {
        var belt = new BeltContents<String>();
        var taken = new int[1];
        var hand = new BeltContents.Hand<String>(6, item -> ++taken[0] > 0);
        for (int tick = 0; tick < 200; tick++) belt.tick(16, TIER_2, ENDLESS, ACCEPTS, hand);

        taken[0] = 0;
        for (int tick = 0; tick < 20 * 60; tick++) belt.tick(16, TIER_2, ENDLESS, ACCEPTS, hand);

        assertEquals(30 * 60, taken[0]);
    }

    @Test
    void entriesBehindAHandBetweenSlotsKeepTheirSpacingFromThoseAhead() {
        var belt = new BeltContents<String>();
        for (int tick = 0; tick < 20 * 20; tick++) belt.tick(4, TIER_1, ENDLESS, REFUSES);

        for (int tick = 0; tick < 40; tick++) {
            belt.tick(4, TIER_1, ENDLESS, REFUSES, new BeltContents.Hand<>(2.1, REFUSES));
        }

        var positions = belt.entries().stream().map(BeltContents.Entry::position).toList();
        for (int i = 1; i < positions.size(); i++) {
            assertTrue(positions.get(i) - positions.get(i - 1) >= BeltContents.SPACING - 1e-9,
                    "entries at " + positions.get(i - 1) + " and " + positions.get(i));
        }
    }
}
