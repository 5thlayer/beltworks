package rearth.belts.model;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A client's copy of a belt kept by the entries it gains and loses alone, advancing itself between
 * them (PlanetaryFactory #351).
 */
class BeltSyncTest {

    private static final double TIER_1 = 0.09375;
    private static final double TIER_3 = 0.28125;

    @Test
    void aMovingBeltWithNothingLoadedOrDeliveredHasNoChanges() {
        var belt = new BeltContents<String>();
        belt.tick(16, TIER_1, () -> "item", item -> false);
        belt.drainChanges();
        var before = belt.entries().getFirst().position();

        for (int tick = 0; tick < 40; tick++) belt.tick(16, TIER_1, () -> null, item -> false);

        assertNotEquals(before, belt.entries().getFirst().position());
        assertTrue(belt.drainChanges().isEmpty());
    }

    @Test
    void aLoadedEntryIsAnAdditionAndADeliveredOneARemoval() {
        var belt = new BeltContents<String>();
        belt.tick(1, TIER_1, () -> "item", item -> false);
        var loaded = belt.drainChanges();
        var id = belt.entries().getFirst().id();

        assertEquals(List.of(id), loaded.added().stream().map(BeltContents.Added::id).toList());
        assertEquals(List.of(), loaded.removed());

        for (int tick = 0; tick < 20; tick++) belt.tick(1, TIER_1, () -> null, item -> true);

        assertEquals(new BeltContents.Changes<String>(List.of(id), List.of()), belt.drainChanges());
    }

    @Test
    void anEntryLoadedAndDeliveredBetweenTwoDrainsIsNoChange() {
        var belt = new BeltContents<String>();
        var loaded = new int[1];

        for (int tick = 0; tick < 20; tick++) belt.tick(1, TIER_1, () -> loaded[0]++ == 0 ? "item" : null, item -> true);

        assertEquals(0, belt.size());
        assertTrue(belt.drainChanges().isEmpty());
    }

    @Test
    void aSplitterPassIsARemovalFromOneBeltAndAnAdditionToTheOther() {
        var in = new BeltContents<String>();
        var out = new BeltContents<String>();
        for (int tick = 0; tick < 20; tick++) in.tick(1, TIER_1, () -> in.isEmpty() ? "item" : null, item -> false);
        in.drainChanges();
        var passed = in.entries().getFirst().id();

        new Splitter<String>(1).tick(0, new Splitter.Lane<>(in, 1, TIER_1), null, new Splitter.Lane<>(out, 1, TIER_1), null);

        assertEquals(List.of(passed), in.drainChanges().removed());
        assertEquals(List.of("item"), out.drainChanges().added().stream().map(BeltContents.Added::payload).toList());
    }

    @Test
    void aCopyFedOnlyTheChangesHoldsTheSameEntriesInTheSameOrder() {
        var server = new BeltContents<Integer>();
        var client = new BeltContents<Integer>();
        var next = new int[1];

        for (int tick = 0; tick < 20 * 60; tick++) {
            // Loading, a slow end, a stretch backed up and a hand held for a while.
            var accepting = tick % 3 == 0 && (tick < 400 || tick > 700);
            var hand = tick > 900 && tick < 1000 ? new BeltContents.Hand<Integer>(2.5, item -> true) : null;
            server.tick(8, TIER_3, () -> next[0]++, item -> accepting, hand);
            client.apply(server.drainChanges());

            assertEquals(ids(server), ids(client), "tick " + tick);
            assertEquals(payloads(server), payloads(client), "tick " + tick);
            client.advance(8, TIER_3);
        }
    }

    @Test
    void aCopyAdvancingItselfHasEachEntryAtTheEndWhenTheServerDeliversIt() {
        var server = new BeltContents<Integer>();
        var client = new BeltContents<Integer>();
        var next = new int[1];

        for (int tick = 0; tick < 20 * 60; tick++) {
            var clientAt = positions(client);
            var accepting = tick % 5 == 0;
            var loading = tick < 600;
            server.tick(8, TIER_1, () -> loading ? next[0]++ : null, item -> accepting);
            var changes = server.drainChanges();
            for (var delivered : changes.removed()) {
                var at = clientAt.get(delivered);
                assertTrue(at >= 8 - BeltContents.SPACING - TIER_1, "delivered at " + at + " on tick " + tick);
            }
            client.apply(changes);
            client.advance(8, TIER_1);
        }
    }

    @Test
    void aCopyThatLagsTheServerNeverDrawsTwoEntriesOnTopOfEachOther() {
        var server = new BeltContents<Integer>();
        var client = new BeltContents<Integer>();
        var next = new int[1];
        var pending = new ArrayDeque<BeltContents.Changes<Integer>>();
        Supplier<Integer> source = () -> next[0]++;
        Predicate<Integer> sink = item -> true;

        // Three ticks of latency on every change.
        for (int tick = 0; tick < 20 * 30; tick++) {
            server.tick(8, TIER_1, source, sink);
            pending.addLast(server.drainChanges());
            if (pending.size() > 3) client.apply(pending.removeFirst());

            // Read before the copy advances, since a frame can be drawn between the two.
            var positions = client.entries().stream().map(BeltContents.Entry::position).toList();
            for (int i = 1; i < positions.size(); i++) {
                assertTrue(positions.get(i) - positions.get(i - 1) >= BeltContents.SPACING - 1e-9, "overlap on tick " + tick);
            }
            client.advance(8, TIER_1);
        }
        while (!pending.isEmpty()) client.apply(pending.removeFirst());

        assertEquals(ids(server), ids(client));
    }

    @Test
    void aStaleChangeChangesNothing() {
        var belt = new BeltContents<String>();
        belt.restore("item", 1, 7);

        belt.apply(new BeltContents.Changes<>(List.of(3), List.of(new BeltContents.Added<>(7, "other", 2))));

        assertEquals(List.of(7), belt.entries().stream().map(BeltContents.Entry::id).toList());
        assertEquals("item", belt.entries().getFirst().payload());
    }

    @Test
    void entriesLoadedAfterARestoreTakeIdsNoRestoredEntryHolds() {
        var belt = new BeltContents<String>();
        belt.restore("item", 1, 40);

        belt.tick(4, TIER_1, () -> "item", item -> false);

        var ids = ids(belt);
        assertTrue(ids.size() > 1);
        assertEquals(ids.size(), ids.stream().distinct().count());
    }

    private static List<Integer> ids(BeltContents<?> belt) {
        return belt.entries().stream().map(BeltContents.Entry::id).toList();
    }

    private static <T> List<T> payloads(BeltContents<T> belt) {
        return belt.entries().stream().map(BeltContents.Entry::payload).toList();
    }

    private static Map<Integer, Double> positions(BeltContents<?> belt) {
        return belt.entries().stream().collect(Collectors.toMap(BeltContents.Entry::id, BeltContents.Entry::position));
    }
}
