// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.model;

/**
 * The inserter a loader tier is crafted from, typed from its Factorio prototype (ADR 0007). One
 * swing costs two half-spins of whole ticks at {@code energyPerRotation × rotationSpeed} a tick, plus
 * an item spike at each end, the rule the wiki's per-cycle table is derived from. A feeder's arm
 * swings at its speeds, in Factorio's ticks (ADR 0018).
 */
public record InserterSwing(double rotationSpeed, double energyPerRotation, double extensionSpeed,
                            double energyPerMovement, int spikeTicks, int handSize, long drainWatts) {

    public static final InserterSwing NONE = new InserterSwing(0, 0, 0, 0, 0, 1, 0);

    /**
     * Factorio's burner inserter, which only a tier-1 feeder swings at: the loader it would be crafted
     * from has no fuel slot, so tier 1's loader is {@link #NONE}. Its energy is fuel, which nothing reads.
     */
    public static final InserterSwing BURNER = new InserterSwing(0.01, 50000, 0.0214, 50000, 5, 1, 0);

    /** Factorio runs 60 ticks a second to Minecraft's 20. */
    public static final int FACTORIO_TICKS_PER_TICK = 3;

    // Floating-point slack, so a speed that divides a turn evenly takes no extra tick.
    private static final double WHOLE_TICK_SLACK = 1e-9;

    public long joulesPerSwing() {
        if (rotationSpeed == 0) return 0;
        var halfSpinTicks = Math.floor(0.5 / rotationSpeed);
        var rotation = 2 * halfSpinTicks * energyPerRotation * rotationSpeed;
        var movement = 2 * spikeTicks * energyPerMovement * extensionSpeed;
        return Math.round(rotation + movement);
    }

    /**
     * The Factorio ticks a feeder's arm takes from one end to the other: it turns {@link FeederArms#revolutions}
     * and stretches {@link FeederArms#stretch} blocks together, lasting as long as the slower in whole ticks.
     */
    public int halfSwingTicks(FeederArms arms) {
        var turning = arms.revolutions() / rotationSpeed;
        var stretching = arms.stretch() / extensionSpeed;
        return (int) Math.ceil(Math.max(turning, stretching) - WHOLE_TICK_SLACK);
    }

    public long joulesPerItem() {
        return joulesPerSwing() / handSize;
    }
}
