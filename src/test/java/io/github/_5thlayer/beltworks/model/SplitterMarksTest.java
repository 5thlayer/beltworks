// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.model;

import org.junit.jupiter.api.Test;

import static io.github._5thlayer.beltworks.model.Splitter.Priority.LEFT;
import static io.github._5thlayer.beltworks.model.Splitter.Priority.NONE;
import static io.github._5thlayer.beltworks.model.Splitter.Priority.RIGHT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SplitterMarksTest {

    private static final LineScan.Travel NORTH = new LineScan.Travel(0, -1);

    // Facing north, items come in from the south: input priority's triangle sits at the back, pointing north.
    @Test
    void inputPriorityMarksThePreferredHalfsBackEdge() {
        var marks = SplitterMarks.of(LEFT, LEFT, NONE, NORTH, 0.375);

        assertEquals(1, marks.size());
        var mark = marks.getFirst();
        assertEquals(WorldMark.Kind.TRIANGLE, mark.kind());
        assertEquals(0.5, mark.x(), 1e-9);
        assertEquals(0.375, mark.y(), 1e-9);
        assertEquals(0.5 + SplitterMarks.OFFSET, mark.z(), 1e-9);
        assertEquals(NORTH, mark.pointing());
    }

    @Test
    void outputPriorityMarksThePreferredHalfsFrontEdge() {
        var marks = SplitterMarks.of(RIGHT, NONE, RIGHT, new LineScan.Travel(1, 0), 0.375);

        assertEquals(1, marks.size());
        assertEquals(0.5 + SplitterMarks.OFFSET, marks.getFirst().x(), 1e-9);
        assertEquals(0.5, marks.getFirst().z(), 1e-9);
    }

    @Test
    void aHalfCarriesBothMarksWhenBothPrioritiesPreferIt() {
        assertEquals(2, SplitterMarks.of(LEFT, LEFT, LEFT, NORTH, 0).size());
    }

    @Test
    void theOtherHalfAndNoPriorityCarryNone() {
        assertTrue(SplitterMarks.of(RIGHT, LEFT, LEFT, NORTH, 0).isEmpty());
        assertTrue(SplitterMarks.of(LEFT, NONE, NONE, NORTH, 0).isEmpty());
    }
}
