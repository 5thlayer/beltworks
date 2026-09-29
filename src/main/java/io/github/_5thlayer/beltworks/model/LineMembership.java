// SPDX-FileCopyrightText: Rearth
// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: CC-BY-4.0 AND MIT

package io.github._5thlayer.beltworks.model;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Which {@link TransportLine} each tile is in: the scan, the head's election, the build, and the
 * release that hands each tile its share back (PlanetaryFactory #398).
 *
 * <p>The line is derived state: each tile saves only its own share of the items, so the run can be
 * merged, split and cut at an unloaded chunk without a codec of its own (#395). A tile rescans only
 * when something told it to, because a rescan walks the whole run.
 */
public final class LineMembership {

    private LineMembership() {
    }

    /** The world the members live in, as the lifecycle reads and changes it. */
    public interface World<T, O> {

        /** The member at a spot, or null where there is none, or its chunk is not loaded. */
        @Nullable
        Member<T, O> at(LineScan.Spot spot);

        LineScan.@Nullable Piece pieceAt(LineScan.Spot spot);

        /** Whatever tells one chunk from another; members in one chunk share a key. */
        Object chunk(LineScan.Spot spot);

        /** Whether the member's tile has gone from the world. */
        boolean removed(Member<T, O> member);

        void drop(LineScan.Spot at, T item);

        /** Marks a member's tile as needing to be saved. */
        void changed(Member<T, O> member);

        /** Marks the chunk a spot is in as needing to be saved, as a line's items change. */
        void chunkChanged(LineScan.Spot spot);

        /** A line has been built with this member at its head, and its watchers want all of it. */
        void lineBuilt(Member<T, O> head);

        /** The line headed at this spot is gone. */
        void lineGone(Member<T, O> head);
    }

    /** One tile's place in a line: its own share while no line holds it, or the line that does. */
    public static final class Member<T, O> {

        private static final List<LineScan.Travel> SIDES = List.of(
          new LineScan.Travel(0, -1), new LineScan.Travel(1, 0), new LineScan.Travel(0, 1), new LineScan.Travel(-1, 0));

        private final LineScan.Spot spot;
        private final BeltTier tier;
        private final O owner;

        // Only the head of a line holds one; every other tile of the run ticks nothing.
        private @Nullable TransportLine<T> line;
        private List<Member<T, O>> members = List.of();
        private List<LineScan.Spot> tiles = List.of();
        // One tile in each chunk the line crosses.
        private List<LineScan.Spot> inChunks = List.of();
        private @Nullable List<List<TransportLine.Share<T>>> shares;

        private @Nullable Member<T, O> holder;
        private int index;

        private LineScan.@Nullable Spot head;
        private boolean rescan = true;
        // Saved and about to be removed with its chunk, so no line counts it any more (#395).
        private boolean unloaded;

        // This tile's own share of the items while no line holds them.
        private List<TransportLine.Share<T>> carried = new ArrayList<>();

        public Member(LineScan.Spot spot, BeltTier tier, O owner) {
            this.spot = spot;
            this.tier = tier;
            this.owner = owner;
        }

        public LineScan.Spot spot() {
            return spot;
        }

        public BeltTier tier() {
            return tier;
        }

        public O owner() {
            return owner;
        }

        public boolean unloaded() {
            return unloaded;
        }

        /** Scans again where told to, and returns the line this tile heads, if it heads one. */
        public @Nullable TransportLine<T> tick(World<T, O> world) {
            if (unloaded) return null;
            if (rescan) rebuild(world);
            return line;
        }

        /** Tells this tile its run has changed under it, so it scans again on its next tick. */
        public void invalidate() {
            rescan = true;
        }

        /** The member holding this tile's line, if a line holds it. */
        public @Nullable Member<T, O> holder() {
            return line != null ? this : holder;
        }

        /** The line this tile belongs to, whichever tile of the run holds it. */
        public @Nullable TransportLine<T> line() {
            var holding = holder();
            return holding == null ? null : holding.line;
        }

        /** The line this tile heads, or null where it heads none. */
        public @Nullable TransportLine<T> headed() {
            return line;
        }

        /** Where this tile is in its line, counted from the head. */
        public int index() {
            return index;
        }

        /** The tiles of the line this member heads, head first. */
        public List<Member<T, O>> members() {
            return members;
        }

        public List<LineScan.Spot> tiles() {
            return tiles;
        }

        /** One tile in each chunk the line this member heads crosses. */
        public List<LineScan.Spot> inChunks() {
            return inChunks;
        }

        /** Marks every chunk of this tile's line as changed, since each saves its own tiles' items. */
        public void lineChanged(World<T, O> world) {
            var holding = holder();
            if (holding == null) return;
            holding.shares = null;
            for (var tile : holding.inChunks) world.chunkChanged(tile);
        }

        /** What this tile itself carries, and where in the tile, whether or not a line holds it. */
        public List<TransportLine.Share<T>> held() {
            var holding = holder();
            if (holding == null || holding.line == null) return List.copyOf(carried);
            if (holding.shares == null) holding.shares = holding.line.shares();
            return index < holding.shares.size() ? holding.shares.get(index) : List.of();
        }

        /** Lets go of what this tile carries, handing each tile of its line its own share first. */
        public List<TransportLine.Share<T>> takeCarried(World<T, O> world) {
            releaseLine(world);
            var taken = carried;
            carried = new ArrayList<>();
            return taken;
        }

        public void carry(List<TransportLine.Share<T>> shares, World<T, O> world) {
            carried.addAll(shares);
            world.changed(this);
        }

        /** Stops the line holding this tile, handing every tile of it its own share. */
        public void releaseLine(World<T, O> world) {
            var holding = holder();
            if (holding != null) holding.release(world);
        }

        /** Sets the tile scanning its old head and its neighbours, as a change to it cuts its run. */
        public void invalidateAround(World<T, O> world) {
            invalidate(world, head);
            invalidateBeside(world);
        }

        /** Starts again from what the tile saved, as its chunk loads. */
        public void loaded(List<TransportLine.Share<T>> saved) {
            carried = new ArrayList<>(saved);
            clearLine();
            holder = null;
            head = null;
            rescan = true;
        }

        /**
         * Lets go of the tiles of a chunk that has just been saved, with each tile's share in it, and
         * is about to unload. Every tile is flagged before any line is released, so no line hands a
         * share to a tile that is going and would lose it.
         */
        public static <T, O> void chunkUnloading(Iterable<Member<T, O>> going, World<T, O> world) {
            for (var tile : going) tile.unloaded = true;
            for (var tile : going) {
                var holding = tile.holder();
                if (holding == null) continue;
                var former = holding.members;
                holding.release(world);
                for (var member : former) if (!member.unloaded) member.rescan = true;
            }
        }

        private static <T, O> void invalidate(World<T, O> world, LineScan.@Nullable Spot spot) {
            if (spot == null) return;
            var member = world.at(spot);
            if (member != null) member.invalidate();
        }

        // Beside it, and a block up or down beside it, where a slope's other end is (#417).
        private void invalidateBeside(World<T, O> world) {
            for (var side : SIDES) {
                var beside = spot.step(side, 1);
                invalidate(world, beside);
                invalidate(world, beside.up(1));
                invalidate(world, beside.up(-1));
            }
        }

        private void rebuild(World<T, O> world) {
            rescan = false;
            // A tile placed against a run brings the whole run's heads and tails into question.
            var first = head == null;
            var scan = LineScan.through(spot, world::pieceAt);
            if (scan.spots().isEmpty()) return;
            var run = scan.spots();
            var scannedHead = run.getFirst();
            if (!scannedHead.equals(head)) invalidate(world, head);
            head = scannedHead;
            if (first) invalidateBeside(world);

            if (!head.equals(spot)) {
                var handedOver = line != null && !line.contents().isEmpty();
                release(world);
                // Not when the head's line is this run already: every tile of a run scans when its
                // chunk loads, and a nudge each would rebuild the line once per tile.
                var headTile = world.at(head);
                if (headTile != null && (handedOver || !carried.isEmpty() || !headTile.tiles.equals(run))) {
                    headTile.invalidate();
                }
                return;
            }
            build(world, run, scan.ring());
        }

        private void build(World<T, O> world, List<LineScan.Spot> run, boolean ring) {
            var tierList = new ArrayList<BeltTier>(run.size());
            var found = new ArrayList<Member<T, O>>(run.size());
            for (var at : run) {
                var tile = world.at(at);
                // The run was scanned from the members, so a missing one means the world moved
                // under the scan; the next tick scans again.
                if (tile == null) {
                    rescan = true;
                    return;
                }
                tierList.add(tile.tier);
                found.add(tile);
            }

            // Every member lets go before any is drained: a tile joining two runs makes one of their
            // heads a member, and tiles tick in an order nothing here decides, so a member still
            // holding a line would otherwise strand its items where nothing looks again.
            for (var member : found) member.release(world);

            var held = new ArrayList<List<TransportLine.Share<T>>>(found.size());
            var crossed = new LinkedHashMap<Object, LineScan.Spot>();
            for (var at = 0; at < found.size(); at++) {
                var member = found.get(at);
                held.add(member.carried);
                member.carried = new ArrayList<>();
                member.holder = this;
                member.index = at;
                member.head = spot;
                world.changed(member);
                crossed.putIfAbsent(world.chunk(member.spot), member.spot);
            }

            members = List.copyOf(found);
            tiles = run;
            inChunks = List.copyOf(crossed.values());
            line = new TransportLine<>(tierList, ring);
            shares = null;
            for (var spilled : line.restoreShares(held)) world.drop(spot, spilled);
            line.contents().drainChanges();
            world.lineBuilt(this);
            world.changed(this);
        }

        /** Hands this line's items back to its tiles and stops ticking it. */
        private void release(World<T, O> world) {
            if (line == null) return;
            var held = line.shares();
            for (var at = 0; at < members.size(); at++) {
                var member = members.get(at);
                if (member.holder == this) member.holder = null;
                // An unloaded tile has just saved its own share, and loads it again with its chunk.
                if (member.unloaded) continue;
                if (world.removed(member)) {
                    for (var share : held.get(at)) world.drop(spot, share.payload());
                    continue;
                }
                member.carried.addAll(held.get(at));
                world.changed(member);
            }
            world.lineGone(this);
            clearLine();
            world.changed(this);
        }

        private void clearLine() {
            line = null;
            members = List.of();
            tiles = List.of();
            inChunks = List.of();
            shares = null;
        }
    }
}
