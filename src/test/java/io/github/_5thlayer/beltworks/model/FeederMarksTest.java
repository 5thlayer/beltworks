// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FeederMarksTest {

    private static final LineScan.Travel NORTH = new LineScan.Travel(0, -1);

    private static void assertAt(double x, double y, double z, FeederMarks.Mark mark) {
        assertEquals(x, mark.x(), 1e-9, "x");
        assertEquals(y, mark.y(), 1e-9, "y");
        assertEquals(z, mark.z(), 1e-9, "z");
    }

    // A feeder facing north takes from the south: its head two blocks south lies on a belt's surface.
    @Test
    void theRingLiesOnTheHeadsTopAtItsReach() {
        var marks = FeederMarks.of(new FeederArms(2, 1, FeederArms.Turn.STRAIGHT), NORTH, 0.375, 0);

        assertEquals(FeederMarks.Kind.RING, marks.head().kind());
        assertAt(0.5, 0.375, 2.5, marks.head());
    }

    // Straight ahead of a feeder facing north, the tail two blocks north lies on the ground, the chevron pointing on north.
    @Test
    void theChevronLiesOnAStraightTailPointingOn() {
        var marks = FeederMarks.of(new FeederArms(1, 2, FeederArms.Turn.STRAIGHT), NORTH, 0.375, 0);

        assertAt(0.5, 0, -1.5, marks.tail());
        assertEquals(NORTH, marks.tail().pointing());
        assertAt(0.5, 0.375, 1.5, marks.head());
    }

    // Turned left from north, the tail three blocks west lies on a chest's lid, the chevron pointing on west.
    @Test
    void theChevronLiesOnATurnedTailsTopPointingAwayFromTheFeeder() {
        var marks = FeederMarks.of(new FeederArms(1, 3, FeederArms.Turn.LEFT), NORTH, 0, 0.875);

        assertEquals(FeederMarks.Kind.CHEVRON, marks.tail().kind());
        assertAt(-2.5, 0.875, 0.5, marks.tail());
        assertEquals(new LineScan.Travel(-1, 0), marks.tail().pointing());
    }

    // Facing east, the head three blocks west; turned right, the tail two blocks south, pointing on south.
    @Test
    void theMarksTurnWithTheFeedersFacing() {
        var marks = FeederMarks.of(new FeederArms(3, 2, FeederArms.Turn.RIGHT), new LineScan.Travel(1, 0), 0, 0);

        assertAt(-2.5, 0, 0.5, marks.head());
        assertAt(0.5, 0, 2.5, marks.tail());
        assertEquals(new LineScan.Travel(0, 1), marks.tail().pointing());
    }

    // The ring's diameter is the chevron's width, a tile's belt across.
    @Test
    void theRingIsAsWideAsTheChevron() {
        var marks = FeederMarks.of(FeederArms.ADJACENT, NORTH, 0, 0);

        assertEquals(10 / 16d, marks.head().size(), 1e-9);
        assertEquals(10 / 16d, marks.tail().size(), 1e-9);
    }
}
