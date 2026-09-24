// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package rearth.belts.model;

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

    private static double positionOf(TransportLine<Integer> line, int payload) {
        return line.contents().entries().stream().filter(entry -> entry.payload() == payload).findFirst().orElseThrow().position();
    }
}
