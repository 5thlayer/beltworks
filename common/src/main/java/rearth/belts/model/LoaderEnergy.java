package rearth.belts.model;

/**
 * A loader's FE buffer, kept in joules so a fractional FE per item is charged exactly. A loader
 * with no charge for an item moves nothing; tier 1 is unpowered and always moves.
 */
public final class LoaderEnergy {

    public static final long JOULES_PER_FE = 100;

    private final long joulesPerItem;
    private final long drainPerTick;
    private final long capacity;
    private long joules;

    public LoaderEnergy(BeltTier tier) {
        joulesPerItem = tier.loaderJoulesPerItem();
        drainPerTick = tier.loaderDrainWatts() / 20;
        // The largest tick FlowLimit allows and no more: a pole's demand is a machine's room, and a
        // deeper buffer would draw a network's share away from the machines on it.
        var burst = (long) Math.ceil(tier.itemsPerTick() + 1) * joulesPerItem + drainPerTick;
        capacity = Math.ceilDiv(burst, JOULES_PER_FE) * JOULES_PER_FE;
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
        var taken = Math.min(Math.max(0, fe), (capacity - joules) / JOULES_PER_FE);
        joules += taken * JOULES_PER_FE;
        return taken;
    }

    public long storedFe() {
        return joules / JOULES_PER_FE;
    }

    public long capacityFe() {
        return capacity / JOULES_PER_FE;
    }

    public long joules() {
        return joules;
    }

    /** Restores a saved or journalled amount, clamped to the buffer. */
    public void setJoules(long joules) {
        this.joules = Math.clamp(joules, 0, capacity);
    }
}
