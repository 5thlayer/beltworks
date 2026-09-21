package rearth.belts.model;

/**
 * The belt ladder, typed from Factorio's belt prototypes: {@code speed} is tiles per Factorio
 * tick, so a belt moves {@code speed × 60} blocks a second and carries {@code speed × 480} items a
 * second at {@link BeltContents#SPACING}. A tier's loader moves the same items a second as its belt.
 */
public enum BeltTier {
    BELT(1, "belt", "chute", "conveyorbelt", 0.03125),
    IMPROVED(2, "improved_belt", "improved_chute", "improved_conveyorbelt", 0.0625),
    EXPRESS(3, "express_belt", "express_chute", "express_conveyorbelt", 0.09375),
    TURBO(4, "turbo_belt", "turbo_chute", "turbo_conveyorbelt", 0.125);

    private final int number;
    private final String beltItem;
    private final String loader;
    private final String beltTexture;
    private final double factorioSpeed;

    BeltTier(int number, String beltItem, String loader, String beltTexture, double factorioSpeed) {
        this.number = number;
        this.beltItem = beltItem;
        this.loader = loader;
        this.beltTexture = beltTexture;
        this.factorioSpeed = factorioSpeed;
    }

    /** The tier a saved number names, clamped to the ladder so an old or hand-edited save still loads. */
    public static BeltTier of(int number) {
        var tiers = values();
        return tiers[Math.clamp(number, 1, tiers.length) - 1];
    }

    public int number() {
        return number;
    }

    /** The belt item's registry path in the {@code belts} namespace. */
    public String beltItem() {
        return beltItem;
    }

    /** The loader's block and item registry path in the {@code belts} namespace. */
    public String loader() {
        return loader;
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
}
