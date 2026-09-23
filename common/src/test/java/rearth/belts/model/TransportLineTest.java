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
}
