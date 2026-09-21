package rearth.belts.model;

/** A belt costs one belt item per block of its length, rounded up (PlanetaryFactory #346). */
public final class BeltCost {

    private BeltCost() {
    }

    // Rounded to whole slots first: a spline's length carries float error, and a straight
    // 64-block belt must hold 512 and cost 64 (#344).
    public static long slots(double length) {
        return Math.round(length / BeltContents.SPACING);
    }

    public static int of(double length) {
        var slotsPerBlock = Math.round(1 / BeltContents.SPACING);
        return (int) Math.ceilDiv(slots(length), slotsPerBlock);
    }
}
