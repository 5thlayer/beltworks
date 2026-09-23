package rearth.belts.model;

/**
 * A tile's shape, derived from what feeds it and never chosen: Factorio's rule, under which a tile
 * fed from exactly one side and not from behind is a corner (PlanetaryFactory #391).
 */
public enum TileShape {
    STRAIGHT,
    FROM_LEFT,
    FROM_RIGHT;

    /** Whether the belt piece at {@code from} outputs into its neighbour travelling {@code travel}. */
    public interface Feeds {

        boolean feeds(LineScan.Spot from, LineScan.Travel travel);
    }

    public static TileShape of(boolean behind, boolean left, boolean right) {
        if (behind || left == right) return STRAIGHT;
        return left ? FROM_LEFT : FROM_RIGHT;
    }

    /** The shape of a tile at {@code spot} travelling {@code travel}, asked of its three neighbours. */
    public static TileShape at(LineScan.Spot spot, LineScan.Travel travel, Feeds feeds) {
        var left = left(travel);
        var right = right(travel);
        return of(
          feeds.feeds(spot.step(travel, -1), travel),
          feeds.feeds(spot.step(left, 1), right),
          feeds.feeds(spot.step(right, 1), left));
    }

    /** The way items travel as they enter a tile of this shape travelling {@code travel}. */
    public LineScan.Travel entry(LineScan.Travel travel) {
        return switch (this) {
            case STRAIGHT -> travel;
            case FROM_LEFT -> right(travel);
            case FROM_RIGHT -> left(travel);
        };
    }

    // Minecraft's z grows southward, so a quarter turn anticlockwise seen from above is (z, -x).
    static LineScan.Travel left(LineScan.Travel travel) {
        return new LineScan.Travel(travel.z(), -travel.x());
    }

    static LineScan.Travel right(LineScan.Travel travel) {
        return new LineScan.Travel(-travel.z(), travel.x());
    }
}
