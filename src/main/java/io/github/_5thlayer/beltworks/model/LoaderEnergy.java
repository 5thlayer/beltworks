// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.model;

/**
 * A loader's FE buffer, kept in joules so a fractional FE per item is charged exactly. A loader
 * with no charge for an item moves nothing. Tier 1 is unpowered and always moves, and so is every
 * tier when the server config says loaders need no power (ADR 0002).
 */
public final class LoaderEnergy {

    /** The server config's say on loader power. */
    public record Setting(boolean loadersNeedPower, long joulesPerFe) {

        public static final Setting DEFAULT = new Setting(false, 100);

        public Setting {
            if (joulesPerFe <= 0) throw new IllegalArgumentException("joulesPerFe must be positive: " + joulesPerFe);
        }
    }

    private final long joulesPerFe;
    private final long joulesPerItem;
    private final long drainPerTick;
    private final long capacity;
    private long joules;

    public LoaderEnergy(BeltTier tier, Setting setting) {
        joulesPerFe = setting.joulesPerFe();
        joulesPerItem = setting.loadersNeedPower() ? tier.loaderJoulesPerItem() : 0;
        drainPerTick = setting.loadersNeedPower() ? tier.loaderDrainWatts() / 20 : 0;
        // The largest tick FlowLimit allows and no more: a pole's demand is a machine's room, and a
        // deeper buffer would draw a network's share away from the machines on it.
        var burst = (long) Math.ceil(tier.itemsPerTick() + 1) * joulesPerItem + drainPerTick;
        capacity = Math.ceilDiv(burst, joulesPerFe) * joulesPerFe;
    }

    public boolean powered() {
        return joulesPerItem > 0;
    }

    public boolean canMove() {
        return joules >= joulesPerItem;
    }

    /** Pays for one item, after {@link #canMove} said it may. */
    public void move() {
        joules -= joulesPerItem;
    }

    /** One tick's idle drain. */
    public void drain() {
        joules = Math.max(0, joules - drainPerTick);
    }

    /** Takes up to {@code fe}, in whole FE, and returns how much it took. */
    public long insertFe(long fe) {
        var taken = Math.min(Math.max(0, fe), (capacity - joules) / joulesPerFe);
        joules += taken * joulesPerFe;
        return taken;
    }

    public long storedFe() {
        return joules / joulesPerFe;
    }

    public long capacityFe() {
        return capacity / joulesPerFe;
    }

    public long joules() {
        return joules;
    }

    /** Restores a saved or journalled amount, clamped to the buffer. */
    public void setJoules(long joules) {
        this.joules = Math.clamp(joules, 0, capacity);
    }
}
