// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tiles merging into lines and letting go of them without losing or doubling an item. */
class LineMembershipTest {

    private static final LineScan.Travel EAST = new LineScan.Travel(1, 0);
    private static final LineScan.Travel SOUTH = new LineScan.Travel(0, 1);
    private static final LineScan.Travel WEST = new LineScan.Travel(-1, 0);
    private static final LineScan.Travel NORTH = new LineScan.Travel(0, -1);

    private final Map<LineScan.Spot, LineScan.Travel> travels = new HashMap<>();
    private final Map<LineScan.Spot, LineMembership.Member<String, Void>> tiles = new LinkedHashMap<>();
    private final Set<LineMembership.Member<String, Void>> removed = new HashSet<>();
    private final List<String> dropped = new ArrayList<>();

    private final TileShape.Around around = new TileShape.Around() {
        @Override
        public LineScan.Travel tile(LineScan.Spot spot) {
            return travels.get(spot);
        }

        @Override
        public boolean feeds(LineScan.Spot from, LineScan.Travel travel) {
            return travel.equals(travels.get(from));
        }
    };

    private final LineMembership.World<String, Void> world = new LineMembership.World<>() {
        @Override
        public LineMembership.Member<String, Void> at(LineScan.Spot spot) {
            var member = tiles.get(spot);
            return member == null || member.unloaded() ? null : member;
        }

        @Override
        public LineScan.Piece pieceAt(LineScan.Spot spot) {
            if (at(spot) == null) return null;
            var travel = travels.get(spot);
            return new LineScan.Piece(travel, TileShape.at(spot, travel, around).entry(travel), Pitch.at(spot, travel, around));
        }

        @Override
        public Object chunk(LineScan.Spot spot) {
            return Math.floorDiv(spot.x(), 16);
        }

        @Override
        public boolean removed(LineMembership.Member<String, Void> member) {
            return removed.contains(member);
        }

        @Override
        public void drop(LineScan.Spot at, String item) {
            dropped.add(item);
        }

        @Override
        public void changed(LineMembership.Member<String, Void> member) {
        }

        @Override
        public void chunkChanged(LineScan.Spot spot) {
        }

        @Override
        public void lineBuilt(LineMembership.Member<String, Void> head) {
        }

        @Override
        public void lineGone(LineMembership.Member<String, Void> head) {
        }
    };

    private static LineScan.Spot at(int x, int z) {
        return new LineScan.Spot(x, 0, z);
    }

    private LineMembership.Member<String, Void> place(int x, int z, LineScan.Travel travel, String... items) {
        var spot = at(x, z);
        var member = new LineMembership.Member<String, Void>(spot, BeltTier.BELT, null);
        travels.put(spot, travel);
        tiles.put(spot, member);
        if (items.length > 0) member.carry(List.of(items).stream().map(item -> new TransportLine.Share<>(0.5, item)).toList(), world);
        member.invalidateAround(world);
        return member;
    }

    // Tears the tile out as a break does: its line lets go first, then its own share drops.
    private void breakTile(int x, int z) {
        var member = tiles.remove(at(x, z));
        travels.remove(at(x, z));
        removed.add(member);
        for (var share : member.takeCarried(world)) dropped.add(share.payload());
        member.invalidateAround(world);
    }

    private void tick(Comparator<LineScan.Spot> order) {
        for (var round = 0; round < 3; round++) {
            for (var spot : tiles.keySet().stream().sorted(order).toList()) tiles.get(spot).tick(world);
        }
    }

    private void tick() {
        tick(Comparator.comparingInt(LineScan.Spot::x));
    }

    private List<String> everything() {
        var all = new ArrayList<>(dropped);
        for (var member : tiles.values()) for (var share : member.held()) all.add(share.payload());
        all.sort(null);
        return all;
    }

    private void joinTwoRuns(Comparator<LineScan.Spot> order) {
        place(0, 0, EAST, "a");
        place(1, 0, EAST, "b");
        place(3, 0, EAST, "c");
        place(4, 0, EAST, "d");
        tick(order);
        place(2, 0, EAST, "e");
        tick(order);

        var line = tiles.get(at(0, 0)).line();
        assertNotNull(line);
        assertEquals(5, line.tileCount());
        for (var member : tiles.values()) assertSame(line, member.line());
        assertEquals(List.of("a", "b", "c", "d", "e"), everything());
        assertTrue(dropped.isEmpty());
    }

    @Test
    void twoRunsJoinedHeadFirstKeepEveryItem() {
        joinTwoRuns(Comparator.comparingInt(LineScan.Spot::x));
    }

    @Test
    void twoRunsJoinedTailFirstKeepEveryItem() {
        joinTwoRuns(Comparator.comparingInt(LineScan.Spot::x).reversed());
    }

    @Test
    void breakingTheMiddleTileDropsOnlyItsShare() {
        place(0, 0, EAST, "a");
        place(1, 0, EAST, "b");
        place(2, 0, EAST, "c");
        tick();
        breakTile(1, 0);
        tick();

        assertEquals(List.of("b"), dropped);
        assertEquals(List.of("a", "b", "c"), everything());
        assertNotNull(tiles.get(at(2, 0)).line());
        assertEquals(1, tiles.get(at(2, 0)).line().tileCount());
    }

    @Test
    void aChunkUnloadedAndLoadedAgainKeepsEveryItem() {
        for (var x = 14; x < 18; x++) place(x, 0, EAST, "i" + x);
        tick();
        assertEquals(4, tiles.get(at(14, 0)).line().tileCount());

        // Saved first, as the game saves a chunk before it unloads it.
        var going = tiles.values().stream().filter(member -> member.spot().x() >= 16).toList();
        var saved = new HashMap<LineScan.Spot, List<TransportLine.Share<String>>>();
        for (var member : going) saved.put(member.spot(), member.held());
        LineMembership.Member.chunkUnloading(going, world);
        for (var member : going) tiles.remove(member.spot());
        tick();
        assertEquals(2, tiles.get(at(14, 0)).line().tileCount());

        for (var entry : saved.entrySet()) {
            var member = new LineMembership.Member<String, Void>(entry.getKey(), BeltTier.BELT, null);
            member.loaded(entry.getValue());
            tiles.put(entry.getKey(), member);
        }
        tick();

        assertEquals(4, tiles.get(at(14, 0)).line().tileCount());
        assertEquals(List.of("i14", "i15", "i16", "i17"), everything());
        assertTrue(dropped.isEmpty());
    }

    @Test
    void aRingBuildsAndReleases() {
        place(0, 0, EAST, "a");
        place(1, 0, SOUTH, "b");
        place(1, 1, WEST, "c");
        place(0, 1, NORTH, "d");
        tick();

        var line = tiles.get(at(0, 0)).line();
        assertNotNull(line);
        assertTrue(line.ring());
        assertEquals(4, line.tileCount());

        breakTile(1, 1);
        tick();
        assertTrue(tiles.values().stream().noneMatch(member -> member.line() != null && member.line().ring()));
        assertEquals(List.of("a", "b", "c", "d"), everything());
        assertEquals(List.of("c"), dropped);
    }

    @Test
    void aNewHeadTakesOverWhenTheHeadIsRemoved() {
        place(0, 0, EAST, "a");
        place(1, 0, EAST, "b");
        place(2, 0, EAST, "c");
        tick();
        assertNotNull(tiles.get(at(0, 0)).headed());

        breakTile(0, 0);
        tick();

        var head = tiles.get(at(1, 0));
        assertNotNull(head.headed());
        assertNull(tiles.get(at(2, 0)).headed());
        assertSame(head, tiles.get(at(2, 0)).holder());
        assertEquals(List.of("a", "b", "c"), everything());
        assertEquals(List.of("a"), dropped);
    }
}
