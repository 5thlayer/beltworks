// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.model;

/**
 * How far above the others an item on a belt is drawn, by its entry's id, so items that overlap
 * never lie in one plane and z-fight (5thlayer/beltworks#120). Ids are handed out one after
 * another and alternate rows, so four levels part an item from those beside it in the other row
 * and from its neighbours ahead and behind in its own. A block item, a quarter block across, only
 * touches its neighbours, and takes the same lift so their faces where they meet don't fight.
 */
public final class ItemLift {

    // Wide enough to part two faces in the depth buffer from a few dozen blocks away, and narrow
    // enough that three steps, about 0.012, stay under a flat item's own thickness, about 0.022 at
    // the 0.35 it is drawn at, so no item looks to float.
    static final double STEP = 1 / 256d;
    static final int LEVELS = 4;

    private ItemLift() {
    }

    /** The lift, in blocks, of the item whose entry's id is {@code id}. */
    public static double of(int id) {
        return (id & (LEVELS - 1)) * STEP;
    }
}
