// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.model;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Each belt piece hands on to each belt piece in front of it through its outlet, and the two run
 * at the slower tier: a line's end or a splitter half's front, into a line's first tile or a half's back
 * (#85, #86).
 */
class OutletTest {

    private static final int MINUTE = 20 * 60;

    @ParameterizedTest(name = "{0} tier {2} into {1} tier {3}")
    @CsvSource({
        "LINE, LINE, 1, 1, 15",
        "LINE, HALF, 1, 1, 15",
        "HALF, LINE, 1, 1, 15",
        "HALF, HALF, 1, 1, 15",
        "HALF, HALF, 3, 1, 15",
        "HALF, HALF, 1, 3, 15",
        "HALF, HALF, 4, 4, 60",
        "LINE, HALF, 3, 2, 30",
        "HALF, LINE, 2, 3, 30",
    })
    void aPieceHandsOnAtTheSlowerTier(Piece sender, Piece receiver, int senderTier, int receiverTier, int itemsPerSecond) {
        var pieces = new Pieces();
        pieces.add(new Line(BeltTier.of(senderTier), true));
        if (sender == Piece.HALF) pieces.add(new Half(BeltTier.of(senderTier)));
        if (receiver == Piece.HALF) pieces.add(new Half(BeltTier.of(receiverTier)));
        var drain = pieces.add(new Line(BeltTier.of(receiverTier), false));

        pieces.run(MINUTE);
        var before = drain.delivered;
        pieces.run(MINUTE);

        assertEquals(itemsPerSecond * 60, drain.delivered - before, 1);
        assertEquals(pieces.loaded(), drain.delivered + pieces.held());
    }

    enum Piece { LINE, HALF }

    /** Belt pieces in travel order, each ticking before the one it hands to. */
    private static final class Pieces {
        final List<Stage> stages = new ArrayList<>();
        long now;

        <S extends Stage> S add(S stage) {
            if (!stages.isEmpty()) stages.getLast().next = stage;
            stages.add(stage);
            return stage;
        }

        void run(int ticks) {
            for (int tick = 0; tick < ticks; tick++, now++) {
                for (var stage : stages) stage.tick(now);
            }
        }

        int loaded() {
            return ((Line) stages.getFirst()).loaded;
        }

        int held() {
            return stages.stream().mapToInt(Stage::held).sum();
        }
    }

    private abstract static class Stage {
        Stage next;

        abstract void tick(long now);

        /** This piece's entry, offered to before it moves this tick. */
        abstract Outlet<String> inlet();

        abstract int held();

        Outlet<String> outlet() {
            return next == null ? null : next.inlet();
        }
    }

    private static final class Line extends Stage {
        final TransportLine<String> line;
        final boolean source;
        int loaded;
        int delivered;

        Line(BeltTier tier, boolean source) {
            line = new TransportLine<>(List.of(tier, tier, tier, tier));
            this.source = source;
        }

        @Override
        void tick(long now) {
            var out = outlet();
            line.tick(() -> {
                if (!source) return null;
                loaded++;
                return "item";
            }, item -> {
                if (out != null) return out.offer(item, line.contents().overshoot(line.length(), line.speed()));
                delivered++;
                return true;
            });
        }

        @Override
        Outlet<String> inlet() {
            return Outlet.entry(new Splitter.Handoff<>(line.contents(), line.length(), line.speed(), null, line.speed()));
        }

        @Override
        int held() {
            return line.size();
        }
    }

    /** A splitter fed at its left half only, whose left half hands on; its right backs up. */
    private static final class Half extends Stage {
        final Splitter<String> splitter;
        final Splitter.Half<String> left;
        final Splitter.Half<String> right;

        Half(BeltTier tier) {
            splitter = new Splitter<>(tier);
            left = new Splitter.Half<>(tier);
            right = new Splitter.Half<>(tier);
        }

        @Override
        void tick(long now) {
            splitter.tick(now, new Splitter.Side<>(left, outlet(), null), new Splitter.Side<>(right, null, null));
        }

        @Override
        Outlet<String> inlet() {
            return Outlet.entry(Splitter.entering(left, left.speed(), null, left.speed()));
        }

        @Override
        int held() {
            return left.size() + right.size();
        }
    }
}
