package rearth.belts.model;

/**
 * The part of a middle or top standing over air that holds it up, so a belt can climb through open
 * air (PlanetaryFactory #420, ADR-0085). It is the tile's, placed and broken with it.
 */
public final class Wedge {

    private Wedge() {
    }

    /** What occupies the block under a tile, as a wedge reads it. */
    public enum Below {
        /** Air, a replaceable plant or snow, or the tile's own wedge. */
        REPLACEABLE,
        /** A block whose top face holds the tile up. */
        SOLID,
        /** Anything else: a tile, loader, splitter, machine, fluid, or a block too thin to stand on. */
        OCCUPIED
    }

    public enum Verdict {
        NONE,
        PLACE,
        REFUSED
    }

    public static boolean needed(Pitch pitch) {
        return pitch == Pitch.MIDDLE_UP || pitch == Pitch.TOP_UP || pitch == Pitch.MIDDLE_DOWN || pitch == Pitch.TOP_DOWN;
    }

    public static Verdict under(Pitch pitch, Below below) {
        if (!needed(pitch)) return Verdict.NONE;
        return switch (below) {
            case REPLACEABLE -> Verdict.PLACE;
            case SOLID -> Verdict.NONE;
            case OCCUPIED -> Verdict.REFUSED;
        };
    }

    /** The way the wedge under a tile of this pitch travelling {@code travel} rises. */
    public static LineScan.Travel uphill(Pitch pitch, LineScan.Travel travel) {
        return pitch.fed() > 0 || pitch.feeder() < 0 ? travel : new LineScan.Travel(-travel.x(), -travel.z());
    }
}
