// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package rearth.belts.model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A line of tiles saved tile by tile, cut at an unloaded chunk and joined again, and the client's
 * copy of it: kept by what it gains and loses and drawn tile by tile (PlanetaryFactory #395).
 */
class TileLineSyncTest {

    private static TransportLine<Integer> line(int tiles, int tier) {
        return new TransportLine<>(Collections.nCopies(tiles, BeltTier.of(tier)));
    }

    private static TransportLine<Integer> backedUp(int tiles) {
        var line = line(tiles, 1);
        var next = new int[1];
        for (var tick = 0; tick < 20 * 20 * tiles; tick++) line.tick(() -> next[0]++, item -> false);
        return line;
    }

    @Test
    void aLineRebuiltFromEachTilesOwnSharesEqualsTheLineThatSavedThem() {
        var line = line(6, 2);
        var next = new int[1];
        for (var tick = 0; tick < 70; tick++) line.tick(() -> next[0] < 20 ? next[0]++ : null, item -> false);
        var shares = line.shares();

        assertEquals(6, shares.size());
        for (var share : shares) {
            for (var held : share) assertTrue(held.offset() >= 0 && held.offset() < 1, "a share sits inside its tile");
        }

        var rebuilt = line(6, 2);
        assertTrue(rebuilt.restoreShares(shares).isEmpty());

        assertEquals(payloads(line), payloads(rebuilt));
        assertEquals(positions(line), positions(rebuilt));
    }

    // Tiles 4 to 7 are in a chunk that unloads: each saved its own share, and the loaded side
    // carries on as a line of its own that nothing past its last tile takes from.
    @Test
    void aLineCutAtAnUnloadedChunkStopsAtTheEdgeAndRejoinsWithEveryItem() {
        var line = line(8, 1);
        var next = new int[1];
        for (var tick = 0; tick < 200; tick++) line.tick(() -> next[0] < 40 ? next[0]++ : null, item -> false);
        var shares = line.shares();
        var saved = shares.subList(4, 8);
        var savedPositions = positions(saved, 4);

        var loaded = line(4, 1);
        assertTrue(loaded.restoreShares(shares.subList(0, 4)).isEmpty());
        for (var tick = 0; tick < 400; tick++) loaded.tick(() -> next[0]++, item -> false);
        assertEquals(loaded.capacity(), loaded.size(), "the loaded side backs up at the edge");

        var rejoined = line(8, 1);
        var whole = new ArrayList<>(loaded.shares());
        whole.addAll(saved);
        assertTrue(rejoined.restoreShares(whole).isEmpty());

        assertEquals(loaded.size() + saved.stream().mapToInt(List::size).sum(), rejoined.size());
        var rejoinedPositions = placed(rejoined);
        for (var entry : savedPositions.entrySet()) {
            assertEquals(entry.getValue(), rejoinedPositions.get(entry.getKey()), 1e-9, "item " + entry.getKey() + " kept its place");
        }
    }

    @Test
    void aCopyResetFromTheLineAndFedItsChangesHoldsTheSameEntries() {
        var server = line(8, 3);
        var next = new int[1];
        for (var tick = 0; tick < 100; tick++) {
            var accepting = tick % 4 == 0;
            server.tick(() -> next[0]++, item -> accepting);
        }
        server.contents().drainChanges();

        var copy = line(8, 3);
        copy.contents().reset(server.contents().snapshot());
        assertEquals(ids(server), ids(copy));
        assertEquals(positions(server), positions(copy));

        for (var tick = 0; tick < 20 * 30; tick++) {
            var accepting = tick % 3 == 0 && (tick < 200 || tick > 400);
            var loading = tick < 450;
            server.tick(() -> loading ? next[0]++ : null, item -> accepting);
            copy.contents().apply(server.contents().drainChanges());

            assertEquals(ids(server), ids(copy), "tick " + tick);
            assertEquals(payloads(server), payloads(copy), "tick " + tick);
            copy.advance();
        }
    }

    @Test
    void everyItemIsDrawnOnceOnTheTileItsCentreIsOn() {
        var line = line(5, 1);
        var next = new int[1];
        for (var tick = 0; tick < 300; tick++) {
            var accepting = tick % 7 == 0;
            line.tick(() -> next[0]++, item -> accepting);
            for (var partial : new float[] {0, 0.25f, 0.5f, 0.99f}) {
                var drawn = line.drawn(partial);
                assertEquals(ids(line), drawn.stream().map(item -> item.entry().id()).toList(), "tick " + tick);
                for (var item : drawn) {
                    assertTrue(item.tile() >= 0 && item.tile() < 5);
                    assertTrue(item.offset() >= 0 && item.offset() < 1, "drawn inside its tile");
                }
            }
        }
    }

    @Test
    void aMovingItemIsDrawnWhereTheNextTickPutsItWithNoJumpAcrossATile() {
        var line = line(4, 1);
        line.contents().restore(0, 0.9);
        var along = new ArrayList<Double>();

        for (var tick = 0; tick < 20; tick++) {
            var before = line.drawn(0).getFirst();
            var late = line.drawn(1).getFirst();
            line.advance();
            var after = line.drawn(0).getFirst();

            assertEquals(after.tile() + after.offset(), late.tile() + late.offset(), 1e-9, "tick " + tick);
            for (var partial = 0f; partial < 1; partial += 0.1f) {
                var at = line.drawn(partial).getFirst();
                along.add(at.tile() + at.offset());
            }
            assertTrue(after.tile() + after.offset() > before.tile() + before.offset());
        }
        for (var index = 1; index < along.size(); index++) {
            var step = along.get(index) - along.get(index - 1);
            assertTrue(step >= 0 && step < BeltTier.of(1).blocksPerTick(), "a step of " + step);
        }
    }

    @Test
    void aBackedUpLineIsDrawnStandingStill() {
        var line = backedUp(3);

        assertEquals(centres(line.drawn(0)), centres(line.drawn(0.5f)));
        assertEquals(centres(line.drawn(0)), centres(line.drawn(1)));
    }

    private static List<Double> centres(List<TransportLine.Drawn<Integer>> drawn) {
        return drawn.stream().map(item -> item.tile() + item.offset()).toList();
    }

    private static List<Integer> ids(TransportLine<Integer> line) {
        return line.contents().entries().stream().map(BeltContents.Entry::id).toList();
    }

    private static List<Integer> payloads(TransportLine<Integer> line) {
        return line.contents().entries().stream().map(BeltContents.Entry::payload).toList();
    }

    private static List<Double> positions(TransportLine<Integer> line) {
        return line.contents().entries().stream().map(BeltContents.Entry::position).toList();
    }

    private static Map<Integer, Double> positions(List<List<TransportLine.Share<Integer>>> shares, int firstTile) {
        var positions = new HashMap<Integer, Double>();
        for (var tile = 0; tile < shares.size(); tile++) {
            for (var held : shares.get(tile)) positions.put(held.payload(), firstTile + tile + held.offset());
        }
        return positions;
    }

    private static Map<Integer, Double> placed(TransportLine<Integer> line) {
        return line.contents().entries().stream()
                 .collect(Collectors.toMap(BeltContents.Entry::payload, BeltContents.Entry::position));
    }
}
