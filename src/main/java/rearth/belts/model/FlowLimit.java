// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package rearth.belts.model;

/**
 * A loader's own cap on the items it moves, so a line runs at its slowest piece (ADR 0007).
 * Refilled lazily from the game time, so a loader that is not ticking still unloads on time.
 */
public final class FlowLimit {

    private final double itemsPerTick;
    // One tick's refill on top of a spent item's leftover fraction: less clips the rate, more
    // lets an idle loader bank a burst.
    private final double ceiling;
    private double allowance;
    private long refilledAt = Long.MIN_VALUE;

    public FlowLimit(double itemsPerTick) {
        this.itemsPerTick = itemsPerTick;
        this.ceiling = itemsPerTick + 1;
    }

    /** Whether one more item may pass at this game time. */
    public boolean ready(long gameTime) {
        if (gameTime != refilledAt) {
            var ticks = refilledAt == Long.MIN_VALUE ? 1 : Math.max(0, gameTime - refilledAt);
            allowance = Math.min(ceiling, allowance + ticks * itemsPerTick);
            refilledAt = gameTime;
        }
        return allowance >= 1;
    }

    /** Spends one item's allowance, after {@link #ready} said it may pass. */
    public void pass() {
        allowance -= 1;
    }
}
