// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What a line of tiles carries, how fast and how much (PlanetaryFactory #398). */
class TransportLineTest {

    private static TransportLine<String> line(int tiles, int tier) {
        return new TransportLine<>(Collections.nCopies(tiles, BeltTier.of(tier)));
    }

    @ParameterizedTest
    @CsvSource({"1, 15", "2, 30", "3, 45", "4, 60"})
    void aLineDeliversItsTiersItemsPerSecond(int tier, int itemsPerSecond) {
        var line = line(8, tier);
        var delivered = new int[1];

        for (var tick = 0; tick < 20 * 60; tick++) line.tick(() -> "item", item -> true);
        for (var tick = 0; tick < 20 * 60; tick++) {
            line.tick(() -> "item", item -> {
                delivered[0]++;
                return true;
            });
        }

        assertEquals(itemsPerSecond * 60, delivered[0]);
    }

    // A line is merged by direction of travel, so a run of mixed tiers is one line (#347).
    @ParameterizedTest
    @CsvSource({"1, 3, 15", "3, 1, 15", "4, 2, 30", "3, 3, 45"})
    void aLineOfMixedTiersRunsAtItsSlowestTile(int head, int tail, int itemsPerSecond) {
        var tiers = new ArrayList<BeltTier>();
        for (var tile = 0; tile < 4; tile++) tiers.add(BeltTier.of(head));
        for (var tile = 0; tile < 4; tile++) tiers.add(BeltTier.of(tail));
        var line = new TransportLine<String>(tiers);
        var delivered = new int[1];

        for (var tick = 0; tick < 20 * 60; tick++) line.tick(() -> "item", item -> true);
        for (var tick = 0; tick < 20 * 60; tick++) {
            line.tick(() -> "item", item -> {
                delivered[0]++;
                return true;
            });
        }

        assertEquals(itemsPerSecond * 60, delivered[0]);
    }

    @ParameterizedTest
    @CsvSource({"1, 8", "8, 64", "64, 512"})
    void aBackedUpLineHoldsEightItemsPerTile(int tiles, int holds) {
        var line = line(tiles, 1);

        assertEquals(holds, line.capacity());
        for (var tick = 0; tick < 20 * 20 * tiles; tick++) line.tick(() -> "item", item -> false);

        assertEquals(holds, line.size());
    }

    @ParameterizedTest
    @CsvSource({"1, 15", "2, 30", "3, 45", "4, 60"})
    void aLineNamesItsOwnItemsPerSecond(int tier, int itemsPerSecond) {
        assertEquals(itemsPerSecond, line(4, tier).itemsPerSecond(), 1e-9);
    }

    @Test
    void aLineIsAsLongAsItsTileCount() {
        assertEquals(5, line(5, 1).length());
        assertEquals(5, line(5, 1).tileCount());
    }

    @Test
    void aLineTakenApartHandsEachTileItsShare() {
        var line = line(4, 1);
        for (var tick = 0; tick < 200; tick++) line.tick(() -> "item", item -> false);

        var loads = line.spill();

        assertEquals(line.size(), loads.size());
        for (var load : loads) {
            assertTrue(load.tile() >= 0 && load.tile() < 4, "a load sits on a tile of the line");
            assertTrue(load.offset() >= 0 && load.offset() < 1, "a load sits inside its tile");
        }
    }

    @Test
    void aLineRebuiltFromItsTilesKeepsEveryItemWhereItWas() {
        var line = line(4, 1);
        var names = new ArrayDeque<String>();
        for (var index = 0; index < 8; index++) names.add("item-" + index);
        for (var tick = 0; tick < 100; tick++) line.tick(names::poll, item -> false);
        var before = line.contents().entries().stream().map(BeltContents.Entry::payload).toList();
        var positions = line.contents().entries().stream().map(BeltContents.Entry::position).toList();

        var rebuilt = line(4, 1);
        assertTrue(rebuilt.restore(line.spill()).isEmpty());

        assertEquals(before, rebuilt.contents().entries().stream().map(BeltContents.Entry::payload).toList());
        assertEquals(positions, rebuilt.contents().entries().stream().map(BeltContents.Entry::position).toList());
    }

    // A tile broken off the end takes the line's length with it; what no longer fits is handed back.
    @Test
    void aShorterLineHandsBackWhatNoLongerFits() {
        var line = line(2, 1);
        for (var tick = 0; tick < 200; tick++) line.tick(() -> "item", item -> false);
        assertEquals(16, line.size());

        var shorter = line(1, 1);
        var overflow = shorter.restore(line.spill());

        assertEquals(8, shorter.size());
        assertEquals(8, overflow.size());
    }

    @Test
    void aLineOfNoTilesIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> new TransportLine<String>(List.of()));
    }

    // A ring has no end: its items go round, none leaves and none is made (#391).
    @Test
    void aRingMovesItsItemsRoundAtItsTiersSpeed() {
        var ring = new TransportLine<String>(Collections.nCopies(6, BeltTier.of(1)), true);
        ring.contents().restore("a", 0);
        ring.contents().restore("b", 5.5);

        // Tier 1 moves 3/32 of a block a tick, so 64 ticks is six blocks: once round.
        for (var tick = 0; tick < 64; tick++) ring.tick(() -> "never", item -> { throw new AssertionError(); });
        assertEquals(List.of(0.0, 5.5), ring.contents().entries().stream().map(BeltContents.Entry::position).toList());

        for (var tick = 0; tick < 8; tick++) ring.tick(() -> "never", item -> false);
        assertEquals(List.of("b", "a"), ring.contents().entries().stream().map(BeltContents.Entry::payload).toList());
        assertEquals(List.of(0.25, 0.75), ring.contents().entries().stream().map(BeltContents.Entry::position).toList());
    }

    @Test
    void aFullRingHoldsEightATileAndStillMoves() {
        var ring = new TransportLine<Integer>(Collections.nCopies(4, BeltTier.of(1)), true);
        for (var slot = 0; slot < 32; slot++) ring.contents().restore(slot, slot * BeltContents.SPACING);

        assertEquals(32, ring.capacity());
        assertTrue(ring.tick(() -> null, item -> false));
        assertEquals(32, ring.size());
        assertEquals(0.09375, ring.contents().entries().getFirst().position());
    }

    // A ring's rear entry may sit within a spacing of its length, which a line's end would push back.
    @Test
    void aRingRestoresItsItemsWhereTheyWere() {
        var ring = new TransportLine<String>(Collections.nCopies(4, BeltTier.of(1)), true);
        ring.contents().restore("a", 0.5);
        ring.contents().restore("b", 3.95);

        var rebuilt = new TransportLine<String>(Collections.nCopies(4, BeltTier.of(1)), true);
        var overflow = rebuilt.restoreShares(ring.shares());

        assertTrue(overflow.isEmpty());
        assertEquals(List.of(0.5, 3.95), rebuilt.contents().entries().stream().map(BeltContents.Entry::position).toList());
    }

    // A side-load is two lines meeting: the fed line takes it into a gap at the fed tile (#409).
    @Test
    void aSideLoadEntersAGapAtTheMiddleOfTheFedTile() {
        var line = line(4, 1);

        assertTrue(line.sideLoad("side", 2));

        assertEquals(List.of(2.4375), line.contents().entries().stream().map(BeltContents.Entry::position).toList());
    }

    @Test
    void aSideLoadIntoAFullLineBacksUp() {
        var line = line(4, 1);
        for (var tick = 0; tick < 20 * 20; tick++) line.tick(() -> "item", item -> false);

        assertEquals(32, line.size());
        assertTrue(!line.sideLoad("side", 2));
        assertEquals(32, line.size());
    }

    @Test
    void theLineFromBehindGoesFirst() {
        var line = line(8, 1);
        var delivered = new ArrayList<String>();
        var sideTaken = 0;
        // Until the line from behind reaches the fed tile, the side has it to itself.
        for (var tick = 0; tick < 20 * 5; tick++) line.tick(() -> "behind", item -> true);

        for (var tick = 0; tick < 20 * 60; tick++) {
            line.tick(() -> "behind", item -> delivered.add(item));
            if (line.sideLoad("side", 4)) sideTaken++;
        }

        assertEquals(0, sideTaken);
        assertEquals(15 * 60, delivered.size());
    }

    // A through line fed every other slot leaves gaps, and the side fills them with nothing lost.
    @Test
    void aSideLoadFillsTheGapsTheLineFromBehindLeaves() {
        var line = line(8, 1);
        var delivered = new int[2];
        var offered = new int[2];

        for (var tick = 0; tick < 20 * 60; tick++) {
            var feed = tick % 4 < 2;
            line.tick(() -> {
                if (!feed || offered[0] >= 400) return null;
                offered[0]++;
                return "behind";
            }, item -> {
                delivered[item.equals("behind") ? 0 : 1]++;
                return true;
            });
            if (offered[1] < 400 && line.sideLoad("side", 4)) offered[1]++;
        }
        for (var tick = 0; tick < 20 * 10; tick++) line.tick(() -> null, item -> {
            delivered[item.equals("behind") ? 0 : 1]++;
            return true;
        });

        assertEquals(offered[0], delivered[0]);
        assertEquals(offered[1], delivered[1]);
        assertTrue(offered[1] > 0);
        assertTrue(line.contents().isEmpty());
    }

    // A ring has no source of its own; a side-load fills it to eight a tile and it keeps moving (#391).
    @Test
    void aSideLoadedRingFillsToEightATileAndMoves() {
        var ring = new TransportLine<Integer>(Collections.nCopies(8, BeltTier.of(1)), true);
        var loaded = 0;

        for (var tick = 0; tick < 20 * 20; tick++) {
            ring.tick(() -> null, item -> { throw new AssertionError(); });
            if (ring.sideLoad(loaded, 1)) loaded++;
        }

        assertEquals(64, loaded);
        assertEquals(64, ring.size());
        var before = positionOf(ring, 0);
        ring.tick(() -> null, item -> false);
        assertEquals((before + 0.09375) % 8, positionOf(ring, 0), 1e-9);
    }

    // A side line hands items on as they reach its end, three every four ticks at tier 1, so the
    // ring's free room passes the fed tile between arrivals and must not be cut into slivers.
    @Test
    void aRingSideLoadedAtABeltsPaceStillFills() {
        var ring = new TransportLine<Integer>(Collections.nCopies(8, BeltTier.of(1)), true);
        var loaded = 0;
        var waiting = false;

        for (var tick = 0; tick < 20 * 40; tick++) {
            ring.tick(() -> null, item -> { throw new AssertionError(); });
            if (tick % 4 != 3) waiting = true;
            if (waiting && ring.sideLoad(loaded, 1)) {
                loaded++;
                waiting = false;
            }
        }

        assertEquals(64, ring.size());
    }

    // A drop onto a belt puts the item where it is aimed, which is centre of the item at the offset (#91).
    @Test
    void anInsertOntoAnEmptyTileLandsAtTheAim() {
        var line = line(4, 1);

        assertTrue(line.insert("dropped", 2, 0.75));

        assertNear(List.of(2.75 - BeltContents.SPACING / 2), positions(line));
    }

    @Test
    void anInsertAtTheMiddleIsWhereASideLoadLands() {
        var inserted = line(4, 1);
        var sideLoaded = line(4, 1);

        assertTrue(inserted.insert("item", 2, 0.5));
        assertTrue(sideLoaded.sideLoad("item", 2));

        assertEquals(positions(sideLoaded), positions(inserted));
    }

    // Aimed past the edge of the tile, the item rests at the nearest place it is still on the tile.
    @ParameterizedTest
    @CsvSource({"0, 2.0", "0.03, 2.0", "1, 2.875", "0.97, 2.875"})
    void anInsertAimedAtATilesEdgeRestsOnThatTile(double offset, double position) {
        var line = line(4, 1);

        assertTrue(line.insert("item", 2, offset));

        assertNear(List.of(position), positions(line));
    }

    @Test
    void anInsertAimedFurtherAlongLandsFurtherAlong() {
        var upstream = line(4, 1);
        var downstream = line(4, 1);

        assertTrue(upstream.insert("item", 1, 0.25));
        assertTrue(downstream.insert("item", 1, 0.75));

        assertTrue(positions(downstream).getFirst() > positions(upstream).getFirst());
    }

    // The aimed point is taken, so the item goes to the nearest free place on the tile, abutting the entry.
    @Test
    void anInsertAimedAtATakenPointGoesToTheNearestGapOnTheTile() {
        var line = line(4, 1);
        assertTrue(line.insert("first", 2, 0.5));
        var first = 2.5 - BeltContents.SPACING / 2;

        assertTrue(line.insert("second", 2, 0.55));

        assertNear(List.of(first, first + BeltContents.SPACING), positions(line));
    }

    @Test
    void anInsertAimedJustBehindATakenPointGoesBehindIt() {
        var line = line(4, 1);
        assertTrue(line.insert("first", 2, 0.5));
        var first = 2.5 - BeltContents.SPACING / 2;

        assertTrue(line.insert("second", 2, 0.45));

        assertNear(List.of(first - BeltContents.SPACING, first), positions(line));
    }

    @Test
    void anInsertNeverMovesAnEntry() {
        var line = line(4, 1);
        for (var offset : new double[] {0.1, 0.3, 0.5, 0.52, 0.9}) line.insert("item", 2, offset);
        var before = positions(line);

        var inserted = line.insert("late", 2, 0.4);

        assertTrue(inserted);
        assertTrue(positions(line).containsAll(before), "an entry moved: " + before + " then " + positions(line));
        assertEquals(before.size() + 1, line.size());
    }

    @Test
    void anInsertNeverBreaksTheLinesSpacing() {
        var line = line(4, 1);
        for (var attempt = 0; attempt < 40; attempt++) line.insert("item", 2, attempt * 0.0251 % 1);

        assertSpaced(positions(line));
        assertEquals(8, line.size());
    }

    // A tile holds eight, and a tile with none of them free refuses rather than spill onto the next.
    @Test
    void anInsertIntoAFullTileIsRefusedAndAddsNothing() {
        var line = line(4, 1);
        for (var slot = 0; slot < 8; slot++) assertTrue(line.insert("item", 2, slot / 8.0 + 0.0625));
        var before = positions(line);

        assertEquals(8, line.size());
        assertTrue(!line.insert("late", 2, 0.5));
        assertTrue(!line.insert("late", 2, 0.01));
        assertTrue(!line.insert("late", 2, 0.99));
        assertEquals(before, positions(line));
    }

    // Loose entries packed across the tile can leave no room for a whole item between them.
    @Test
    void anInsertIntoATileWhoseGapsAreTooSmallIsRefused() {
        var line = line(4, 1);
        for (var position : new double[] {0.0, 0.2, 0.4, 0.6, 0.8}) {
            assertTrue(line.insert("item", 2, position + BeltContents.SPACING / 2));
        }
        // Five entries 0.2 apart: every gap between them is 0.075, short of the 0.125 an item needs.
        var before = positions(line);

        assertNear(List.of(2.0, 2.2, 2.4, 2.6, 2.8), before);
        assertTrue(!line.insert("late", 2, 0.5));
        assertEquals(before, positions(line));
    }

    @Test
    void anInsertNeverGoesToAnotherTile() {
        var line = line(4, 1);
        for (var slot = 0; slot < 8; slot++) assertTrue(line.insert("item", 2, slot / 8.0 + 0.0625));

        assertTrue(!line.insert("late", 2, 0.99));
        assertTrue(positions(line).stream().allMatch(position -> position >= 2 && position <= 2.875), positions(line).toString());
    }

    // An entry at the front edge of the next tile still takes the room an item's length behind it.
    @Test
    void anInsertKeepsASpacingFromAnEntryOnTheNextTile() {
        var line = line(4, 1);
        assertTrue(line.insert("next", 3, 0.0));

        assertTrue(line.insert("late", 2, 1.0));
        assertTrue(line.insert("later", 2, 1.0));

        assertNear(List.of(3.0 - 2 * BeltContents.SPACING, 3.0 - BeltContents.SPACING, 3.0), positions(line));
    }

    @Test
    void anInsertIntoABackedUpLineWithRoomOnTheTileLandsInTheRoom() {
        var line = line(4, 1);
        // Backed up against a blocked end, the line holds 32 and has no room anywhere.
        for (var tick = 0; tick < 20 * 20; tick++) line.tick(() -> "item", item -> false);
        assertEquals(32, line.size());
        var taken = line.contents().take(2, 3, item -> true);
        assertEquals(2.875, taken.position(), 1e-9);

        assertTrue(line.insert("dropped", 2, 0.1));
        assertEquals(32, line.size());
        assertTrue(!line.insert("again", 2, 0.1));
    }

    @Test
    void anInsertOnARingTakesTheAimAndNeverMovesAnEntry() {
        var ring = new TransportLine<String>(Collections.nCopies(8, BeltTier.of(1)), true);
        assertTrue(ring.insert("a", 3, 0.5));
        var at = positions(ring);

        assertNear(List.of(3.5 - BeltContents.SPACING / 2), at);
        assertTrue(ring.insert("b", 3, 0.5));
        assertTrue(positions(ring).containsAll(at));
        assertEquals(2, ring.size());
    }

    @Test
    void anInsertOnARingKeepsTheSpacingAndNeverExceedsEightATile() {
        var ring = new TransportLine<Integer>(Collections.nCopies(8, BeltTier.of(1)), true);
        var placed = 0;

        for (var attempt = 0; attempt < 40; attempt++) {
            if (ring.insert(attempt, 7, attempt * 0.0251 % 1)) placed++;
        }

        assertEquals(placed, ring.size());
        assertTrue(placed <= 8);
        assertSpacedOnARing(ring.contents().entries().stream().map(BeltContents.Entry::position).sorted().toList(), 8);
    }

    // The last tile of a ring sits beside its first, so entries wrap past the seam and count there.
    @Test
    void anInsertOnTheFirstTileOfARingKeepsASpacingFromTheLastTilesEntries() {
        var ring = new TransportLine<String>(Collections.nCopies(8, BeltTier.of(1)), true);
        assertTrue(ring.insert("last", 7, 1.0));
        var last = 8 - BeltContents.SPACING;

        assertTrue(ring.insert("first", 0, 0.0));

        var positions = positions(ring);
        assertNear(List.of(0.0, last), positions);
        assertTrue(ring.insert("between", 0, 0.05));
        assertNear(List.of(0.0, BeltContents.SPACING, last), positions(ring));
    }

    // A hand on a tile takes whatever is on it, so its point is the tile's front (#396).
    @Test
    void aHandsPointIsItsTilesFront() {
        assertEquals(4 - BeltContents.SPACING, TransportLine.handPoint(3), 1e-9);
    }

    @Test
    void aHandOnATileTakesAtTheLinesRateWhileItKeepsLoading() {
        var line = line(8, 1);
        for (var tick = 0; tick < 20 * 10; tick++) line.tick(() -> "item", item -> true);

        var taken = new int[1];
        var delivered = new int[1];
        var point = TransportLine.handPoint(4);
        var pastTheTile = line.contents().entries().stream().filter(entry -> entry.position() > point).count();
        var hand = new BeltContents.Hand<String>(point, item -> ++taken[0] > 0);
        for (var tick = 0; tick < 20 * 60; tick++) line.tick(() -> "item", item -> ++delivered[0] > 0, hand);

        assertTrue(Math.abs(taken[0] - 15 * 60) <= 1, "taken " + taken[0]);
        assertEquals(pastTheTile, delivered[0]);
    }

    // A backed-up line's head sits flush with its last tile's front, where a hand on that tile holds (#408).
    @Test
    void aHandOnTheLastTileOfABackedUpLineTakesAtTheLinesRate() {
        var line = line(8, 1);
        for (var tick = 0; tick < 20 * 20; tick++) line.tick(() -> "item", item -> false);

        var taken = new int[1];
        var hand = new BeltContents.Hand<String>(TransportLine.handPoint(7), item -> ++taken[0] > 0);
        for (var tick = 0; tick < 20 * 60; tick++) line.tick(() -> "item", item -> false, hand);

        assertTrue(Math.abs(taken[0] - 15 * 60) <= 1, "taken " + taken[0]);
    }

    // The entries under a loose item carry it, so what they moved this tick is how far it goes (#92).
    @ParameterizedTest
    @CsvSource({"0, 0.01", "0, 0.5", "3, 0.5", "7, 0.99"})
    void aBackedUpPointMovedNothing(int tile, double offset) {
        var line = line(8, 1);
        for (var tick = 0; tick < 20 * 20; tick++) line.tick(() -> "item", item -> false);

        assertEquals(line.capacity(), line.size());
        assertEquals(0, line.movedAt(tile, offset));
    }

    @ParameterizedTest
    @CsvSource({"1, 0, 0.01", "1, 3, 0.5", "2, 7, 0.99", "3, 4, 0.25", "4, 0, 0.5"})
    void aPointOnAMovingFullLineMovedTheLinesStep(int tier, int tile, double offset) {
        var line = line(8, tier);
        for (var tick = 0; tick < 20 * 20; tick++) line.tick(() -> "item", item -> true);

        assertEquals(line.speed(), line.movedAt(tile, offset), 1e-12);
    }

    // The line stops and runs again, and the point follows it tick by tick.
    @Test
    void aPointStopsWithABackedUpLineAndMovesAgainWhenItRuns() {
        var line = line(8, 1);
        for (var tick = 0; tick < 20 * 20; tick++) line.tick(() -> "item", item -> false);
        assertEquals(0, line.movedAt(4, 0.5));

        line.tick(() -> "item", item -> true);
        assertEquals(line.speed(), line.movedAt(4, 0.5), 1e-12);
        for (var tick = 0; tick < 20 * 20; tick++) line.tick(() -> "item", item -> false);
        assertEquals(0, line.movedAt(4, 0.5));
    }

    // A point no entry overlaps has nothing to rest on, so it reports the line's step: it would join,
    // and an item never asks this of a tile it could join.
    @Test
    void aPointWithNoEntriesUnderItReportsTheLinesStep() {
        var empty = line(4, 2);
        var loose = line(8, 1);
        loose.insert("item", 0, 0.5);
        for (var tick = 0; tick < 20 * 20; tick++) loose.tick(() -> null, item -> false);

        assertEquals(empty.speed(), empty.movedAt(2, 0.5), 1e-12);
        assertEquals(1, loose.size());
        assertEquals(loose.speed(), loose.movedAt(5, 0.5), 1e-12);
    }

    // A hand is a second end for what is behind it: the entries past it run on, the ones behind back up.
    @Test
    void aPointBehindARefusingHandMovedNothingWhileOneThatRunsPastItMovedTheLinesStep() {
        var line = line(8, 1);
        for (var tick = 0; tick < 20 * 10; tick++) line.tick(() -> "item", item -> true);
        var hand = new BeltContents.Hand<String>(TransportLine.handPoint(4), item -> false);

        line.tick(() -> "item", item -> true, hand);
        assertEquals(line.speed(), line.movedAt(6, 0.5), 1e-12);

        for (var tick = 0; tick < 20 * 10; tick++) line.tick(() -> "item", item -> true, hand);
        assertEquals(0, line.movedAt(2, 0.5));
        assertEquals(0, line.movedAt(4, 0.5));
    }

    // The one ahead stops an entry short of a full step, and that is all an item on it moves.
    @Test
    void aPointOnAnEntryStoppedShortByTheOneAheadMovedOnlyTheDistanceItGained() {
        var line = line(2, 1);
        line.insert("ahead", 1, 1.0);
        // 0.01 behind the spacing it will close to, in a tick that moves 0.03125.
        var behind = 2 - 2 * BeltContents.SPACING - 0.01;
        line.insert("behind", 1, behind + BeltContents.SPACING / 2 - 1);
        line.tick(() -> null, item -> false);

        assertNear(List.of(2 - 2 * BeltContents.SPACING, 2 - BeltContents.SPACING), positions(line));
        assertEquals(0.01, line.movedAt(1, 0.8125), 1e-9);
        assertEquals(0, line.movedAt(1, 1.0));
    }

    // An item resting on two entries goes no faster than the slower.
    @Test
    void aPointOverTwoEntriesMovedTheLeastOfThem() {
        var line = line(2, 1);
        line.insert("ahead", 1, 1.0);
        line.insert("behind", 1, 1.7 + BeltContents.SPACING / 2 - 1);
        line.tick(() -> null, item -> false);

        // The one behind closed to a spacing from the one ahead, 0.05 on, and the one ahead sits at the end.
        assertNear(List.of(2 - 2 * BeltContents.SPACING, 2 - BeltContents.SPACING), positions(line));
        // 1.8 is within an item of both, and 1.65 of the one behind only.
        assertEquals(0, line.movedAt(1, 1.8 + BeltContents.SPACING / 2 - 1));
        assertEquals(0.05, line.movedAt(1, 1.65 + BeltContents.SPACING / 2 - 1), 1e-9);
    }

    // Held to the tile as an insert is, so an item at a tile's edge reads the entries on that tile.
    @Test
    void aPointAtATilesEdgeReadsTheEntriesOnThatTile() {
        var line = line(4, 1);
        for (var tick = 0; tick < 20 * 20; tick++) line.tick(() -> "item", item -> false);

        assertEquals(0, line.movedAt(3, 1.0));
        assertEquals(0, line.movedAt(3, 1.5));
        assertEquals(0, line.movedAt(0, -0.5));
    }

    @Test
    void aPointOnARingMovedTheLinesStepAtTheSeamToo() {
        var line = new TransportLine<String>(Collections.nCopies(4, BeltTier.of(1)), true);
        for (var slot = 0; slot < 8; slot++) assertTrue(line.insert("item", 2, slot / 8.0 + 0.0625));
        line.tick(() -> null, item -> false);

        assertEquals(line.speed(), line.movedAt(2, 0.5), 1e-12);
        assertEquals(line.speed(), line.movedAt(0, 0.01), 1e-12);
        assertEquals(line.speed(), line.movedAt(3, 0.99), 1e-12);
    }

    // What was placed this tick moved nothing in it, however fast the line runs.
    @Test
    void aJustInsertedEntryHasNotMoved() {
        var line = line(4, 1);
        for (var tick = 0; tick < 5; tick++) line.tick(() -> null, item -> false);
        line.insert("item", 1, 0.5);

        assertEquals(0, line.movedAt(1, 0.5));
    }

    private static List<Double> positions(TransportLine<?> line) {
        return line.contents().entries().stream().map(BeltContents.Entry::position).sorted().toList();
    }

    private static void assertNear(List<Double> expected, List<Double> actual) {
        assertEquals(expected.size(), actual.size(), "expected " + expected + " but was " + actual);
        for (var index = 0; index < expected.size(); index++) {
            assertEquals(expected.get(index), actual.get(index), 1e-9, "expected " + expected + " but was " + actual);
        }
    }

    private static void assertSpaced(List<Double> sorted) {
        for (var index = 1; index < sorted.size(); index++) {
            assertTrue(sorted.get(index) - sorted.get(index - 1) >= BeltContents.SPACING - 1e-9, "spacing broken in " + sorted);
        }
    }

    private static void assertSpacedOnARing(List<Double> sorted, double length) {
        assertSpaced(sorted);
        if (sorted.size() > 1) {
            assertTrue(sorted.getFirst() + length - sorted.getLast() >= BeltContents.SPACING - 1e-9, "seam spacing broken in " + sorted);
        }
    }

    private static double positionOf(TransportLine<Integer> line, int payload) {
        return line.contents().entries().stream().filter(entry -> entry.payload() == payload).findFirst().orElseThrow().position();
    }
}
