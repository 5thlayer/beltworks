// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.model;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.ToLongFunction;

/**
 * A loader's FE buffer, kept in joules so a fractional FE per item is charged exactly. A loader
 * with no charge for an item moves nothing. A loader pays per item what the server config sets for
 * its tier, and one set to 0, as tier 1 is by default, is unpowered and always moves; so is every
 * tier when the server config says loaders need no power (ADR 0002, ADR 0007 as amended by #84). A feeder follows the
 * same switch: with loaders unpowered it moves for free, and with them powered it pays its own figure
 * for each item its hand grabs, which may be 0 as a loader's may, and never drains (ADR 0014).
 */
public final class LoaderEnergy {

    /**
     * The server config's say on loader power, and on what each tier's loader and feeder pay an item
     * when loaders need power: each figure is its own to set.
     */
    public record Setting(boolean loadersNeedPower, long joulesPerFe, Map<BeltTier, Long> feederJoulesPerItem,
                          Map<BeltTier, Long> loaderJoulesPerItem) {

        public static final Setting DEFAULT = new Setting(false, 100);

        /**
         * The most a loader or feeder may pay for an item: far past any tuning, and small enough that
         * its buffer's size, a few items' worth, stays within a long.
         */
        public static final long MAX_JOULES_PER_ITEM = 1_000_000_000L;

        public Setting(boolean loadersNeedPower, long joulesPerFe) {
            this(loadersNeedPower, joulesPerFe, Map.of());
        }

        public Setting(boolean loadersNeedPower, long joulesPerFe, Map<BeltTier, Long> feederJoulesPerItem) {
            this(loadersNeedPower, joulesPerFe, feederJoulesPerItem, Map.of());
        }

        /**
         * Every tier a map leaves out pays {@link BeltTier#feederJoulesPerItem} or
         * {@link BeltTier#loaderJoulesPerItem}. Either may pay 0.
         */
        public Setting {
            if (joulesPerFe <= 0) throw new IllegalArgumentException("joulesPerFe must be positive: " + joulesPerFe);
            feederJoulesPerItem = perTier("feeder", feederJoulesPerItem, BeltTier::feederJoulesPerItem);
            loaderJoulesPerItem = perTier("loader", loaderJoulesPerItem, BeltTier::loaderJoulesPerItem);
        }

        private static Map<BeltTier, Long> perTier(String what, Map<BeltTier, Long> set,
                                                   ToLongFunction<BeltTier> fallback) {
            var joules = new EnumMap<BeltTier, Long>(BeltTier.class);
            for (var tier : BeltTier.values()) joules.put(tier, fallback.applyAsLong(tier));
            joules.putAll(set);
            joules.forEach((tier, perItem) -> {
                if (perItem < 0 || perItem > MAX_JOULES_PER_ITEM) {
                    throw new IllegalArgumentException(tier + " " + what + " joules per item must be from 0 to "
                      + MAX_JOULES_PER_ITEM + ": " + perItem);
                }
            });
            return Collections.unmodifiableMap(joules);
        }

        public long feederJoules(BeltTier tier) {
            return feederJoulesPerItem.get(tier);
        }

        public long loaderJoules(BeltTier tier) {
            return loaderJoulesPerItem.get(tier);
        }
    }

    private final long joulesPerFe;
    private final long joulesPerItem;
    private final long drainPerTick;
    private final long capacity;
    private long joules;

    public LoaderEnergy(BeltTier tier, Setting setting) {
        this(setting.loadersNeedPower() ? setting.loaderJoules(tier) : 0, tier, setting.joulesPerFe());
    }

    // A loader that pays nothing an item draws nothing idle either, having no energy face to fill it.
    private LoaderEnergy(long joulesPerItem, BeltTier tier, long joulesPerFe) {
        // The largest tick FlowLimit allows and no more: a pole's demand is a machine's room, and a
        // deeper buffer would draw a network's share away from the machines on it.
        this(joulesPerItem, joulesPerItem > 0 ? tier.loaderDrainWatts() / 20 : 0, joulesPerFe,
          (long) Math.ceil(tier.itemsPerTick() + 1) * joulesPerItem);
    }

    /** A splitter's: it draws no power, whatever the config says of loaders (ADR 0007). */
    public static LoaderEnergy unpowered() {
        // Any positive rate serves: a buffer that pays nothing holds nothing to count in it.
        return new LoaderEnergy(0, 0, 1, 0);
    }

    /**
     * A feeder's: it holds a full hand's worth, the most a grab pays at once, and the FE but one more,
     * so whatever fraction of an FE a grab leaves behind, a fill always pays for the next hand.
     */
    public static LoaderEnergy feeder(BeltTier tier, Setting setting) {
        var joulesPerItem = setting.loadersNeedPower() ? setting.feederJoules(tier) : 0;
        var hand = tier.feederSwing().handSize() * joulesPerItem;
        return new LoaderEnergy(joulesPerItem, 0, setting.joulesPerFe(), joulesPerItem > 0 ? hand + setting.joulesPerFe() - 1 : 0);
    }

    // A buffer of whole FE that holds at least a tick's burst of joules and its drain.
    private LoaderEnergy(long joulesPerItem, long drainPerTick, long joulesPerFe, long burstJoules) {
        this.joulesPerFe = joulesPerFe;
        this.joulesPerItem = joulesPerItem;
        this.drainPerTick = drainPerTick;
        var burst = burstJoules + drainPerTick;
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

    /** How many of {@code wanted} items it can pay for now. */
    public int affordable(int wanted) {
        return joulesPerItem == 0 ? wanted : (int) Math.min(wanted, joules / joulesPerItem);
    }

    /** Pays for {@code items}, after {@link #affordable} said it could. */
    public void pay(int items) {
        joules -= items * joulesPerItem;
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
