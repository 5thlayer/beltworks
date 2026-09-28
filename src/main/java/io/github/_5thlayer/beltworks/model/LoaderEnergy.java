// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.model;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * A loader's FE buffer, kept in joules so a fractional FE per item is charged exactly. A loader
 * with no charge for an item moves nothing. Tier 1 is unpowered and always moves, and so is every
 * tier when the server config says loaders need no power (ADR 0002). A feeder's buffer pays every
 * item at every tier, whatever the config says of loaders, and never drains.
 */
public final class LoaderEnergy {

    /**
     * The server config's say on loader power, and on what each tier's feeder pays an item: a feeder
     * pays whatever loaders need, so its cost is its own to set.
     */
    public record Setting(boolean loadersNeedPower, long joulesPerFe, Map<BeltTier, Long> feederJoulesPerItem) {

        public static final Setting DEFAULT = new Setting(false, 100);

        /**
         * The most a feeder may pay for an item: far past any tuning, and small enough that its
         * buffer's size, a couple of items' worth, stays within a long.
         */
        public static final long MAX_FEEDER_JOULES_PER_ITEM = 1_000_000_000L;

        public Setting(boolean loadersNeedPower, long joulesPerFe) {
            this(loadersNeedPower, joulesPerFe, Map.of());
        }

        /** Every tier a map leaves out pays {@link BeltTier#feederJoulesPerItem}. */
        public Setting {
            if (joulesPerFe <= 0) throw new IllegalArgumentException("joulesPerFe must be positive: " + joulesPerFe);
            var joules = new EnumMap<BeltTier, Long>(BeltTier.class);
            for (var tier : BeltTier.values()) joules.put(tier, tier.feederJoulesPerItem());
            joules.putAll(feederJoulesPerItem);
            joules.forEach((tier, perItem) -> {
                if (perItem <= 0 || perItem > MAX_FEEDER_JOULES_PER_ITEM) {
                    throw new IllegalArgumentException(tier + " feeder joules per item must be from 1 to "
                      + MAX_FEEDER_JOULES_PER_ITEM + ": " + perItem);
                }
            });
            feederJoulesPerItem = Collections.unmodifiableMap(joules);
        }

        public long feederJoules(BeltTier tier) {
            return feederJoulesPerItem.get(tier);
        }
    }

    private final long joulesPerFe;
    private final long joulesPerItem;
    private final long drainPerTick;
    private final long capacity;
    private long joules;

    public LoaderEnergy(BeltTier tier, Setting setting) {
        this(setting.loadersNeedPower() ? tier.loaderJoulesPerItem() : 0,
          setting.loadersNeedPower() ? tier.loaderDrainWatts() / 20 : 0, tier.itemsPerTick(), setting.joulesPerFe());
    }

    public static LoaderEnergy feeder(BeltTier tier, Setting setting) {
        return new LoaderEnergy(setting.feederJoules(tier), 0, tier.feederItemsPerTick(), setting.joulesPerFe());
    }

    private LoaderEnergy(long joulesPerItem, long drainPerTick, double itemsPerTick, long joulesPerFe) {
        this.joulesPerFe = joulesPerFe;
        this.joulesPerItem = joulesPerItem;
        this.drainPerTick = drainPerTick;
        // The largest tick FlowLimit allows and no more: a pole's demand is a machine's room, and a
        // deeper buffer would draw a network's share away from the machines on it.
        var burst = (long) Math.ceil(itemsPerTick + 1) * joulesPerItem + drainPerTick;
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
