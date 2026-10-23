// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemLiftTest {

    // An item overlaps those up to three ids away: beside it in the other row, and ahead and behind in its own.
    @Test
    void itemsThatCanOverlapAreDrawnAtDifferentHeights() {
        for (var id = 0; id < 64; id++) {
            for (var apart = 1; apart < ItemLift.LEVELS; apart++) {
                assertNotEquals(ItemLift.of(id), ItemLift.of(id + apart), "ids " + id + " and " + (id + apart));
            }
        }
    }

    @Test
    void anItemsLiftStaysWithinThreeSteps() {
        for (var id = 0; id < 64; id++) {
            var lift = ItemLift.of(id);
            assertTrue(lift >= 0 && lift <= 3 * ItemLift.STEP, "id " + id + " lifted " + lift);
        }
    }

    // Four ids on, same row two slots ahead and clear of it, the levels come round again.
    @Test
    void theLevelsRepeatEveryFourIds() {
        assertEquals(ItemLift.of(1), ItemLift.of(5));
    }
}
