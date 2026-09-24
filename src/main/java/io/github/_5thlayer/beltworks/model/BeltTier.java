// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.model;

/**
 * The belt ladder, typed from Factorio's belt prototypes: {@code speed} is tiles per Factorio
 * tick, so a belt moves {@code speed × 60} blocks a second and carries {@code speed × 480} items a
 * second at {@link BeltContents#SPACING}. A tier's loader moves the same items a second as its belt,
 * and so does each side of its splitter.
 * A loader pays per item and drains what the inserter its recipe is built from does (ADR 0007).
 */
public enum BeltTier {
    // Tier 1's burner inserter burns fuel the loader has no slot for, so it runs unpowered.
    BELT(1, "belt", "loader", "splitter", "conveyorbelt", 0.03125, InserterSwing.NONE),
    IMPROVED(2, "improved_belt", "improved_loader", "improved_splitter", "improved_conveyorbelt", 0.0625,
      new InserterSwing(0.014, 5000, 0.035, 5000, 5, 1, 400)),
    EXPRESS(3, "express_belt", "express_loader", "express_splitter", "express_conveyorbelt", 0.09375,
      new InserterSwing(0.04, 7000, 0.1, 7000, 1, 1, 500)),
    TURBO(4, "turbo_belt", "turbo_loader", "turbo_splitter", "turbo_conveyorbelt", 0.125,
      new InserterSwing(0.04, 20000, 0.1, 20000, 1, 2, 1000));

    private final int number;
    private final String stem;
    private final String loader;
    private final String splitter;
    private final String beltTexture;
    private final double factorioSpeed;
    private final InserterSwing inserter;

    BeltTier(int number, String stem, String loader, String splitter, String beltTexture, double factorioSpeed,
             InserterSwing inserter) {
        this.number = number;
        this.stem = stem;
        this.loader = loader;
        this.splitter = splitter;
        this.beltTexture = beltTexture;
        this.factorioSpeed = factorioSpeed;
        this.inserter = inserter;
    }

    /** The tier a saved number names, clamped to the ladder so an old or hand-edited save still loads. */
    public static BeltTier of(int number) {
        var tiers = values();
        return tiers[Math.clamp(number, 1, tiers.length) - 1];
    }

    public int number() {
        return number;
    }

    /** The belt tile's block and item registry path in the {@code beltworks} namespace (PlanetaryFactory #398). */
    public String tile() {
        return stem + "_tile";
    }

    /** The loader's block and item registry path in the {@code beltworks} namespace. */
    public String loader() {
        return loader;
    }

    /** The splitter's block and item registry path in the {@code beltworks} namespace. */
    public String splitter() {
        return splitter;
    }

    /** The directory of the belt's animation frames under {@code textures/block}. */
    public String beltTexture() {
        return beltTexture;
    }

    public double blocksPerSecond() {
        return factorioSpeed * 60;
    }

    public double blocksPerTick() {
        return blocksPerSecond() / 20;
    }

    public double itemsPerSecond() {
        return blocksPerSecond() / BeltContents.SPACING;
    }

    public double itemsPerTick() {
        return itemsPerSecond() / 20;
    }

    public InserterSwing inserter() {
        return inserter;
    }

    public long loaderJoulesPerItem() {
        return inserter.joulesPerItem();
    }

    public long loaderDrainWatts() {
        return inserter.drainWatts();
    }
}
