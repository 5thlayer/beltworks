package rearth.belts.model;

/**
 * The inserter a loader tier is crafted from, typed from its Factorio prototype (ADR 0007). One
 * swing is two half-spins of whole ticks at {@code energyPerRotation × rotationSpeed} a tick, plus
 * an item spike at each end, the rule the wiki's per-cycle table is derived from.
 */
public record InserterSwing(double rotationSpeed, double energyPerRotation, double extensionSpeed,
                            double energyPerMovement, int spikeTicks, int handSize, long drainWatts) {

    public static final InserterSwing NONE = new InserterSwing(0, 0, 0, 0, 0, 1, 0);

    public long joulesPerSwing() {
        if (rotationSpeed == 0) return 0;
        var halfSpinTicks = Math.floor(0.5 / rotationSpeed);
        var rotation = 2 * halfSpinTicks * energyPerRotation * rotationSpeed;
        var movement = 2 * spikeTicks * energyPerMovement * extensionSpeed;
        return Math.round(rotation + movement);
    }

    public long joulesPerItem() {
        return joulesPerSwing() / handSize;
    }
}
