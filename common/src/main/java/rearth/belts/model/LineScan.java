package rearth.belts.model;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Which tiles are one transport line: the contiguous run of tiles of one direction of travel
 * through a tile, head first (PlanetaryFactory #398).
 *
 * <p>A run is collinear, since every tile of it travels the same way, so the walk is monotone on
 * one axis and cannot close on itself.
 */
public final class LineScan {

    private LineScan() {
    }

    /** A tile's position, in blocks. */
    public record Spot(int x, int y, int z) {

        public Spot step(Travel travel, int times) {
            return new Spot(x + travel.x() * times, y, z + travel.z() * times);
        }
    }

    /** A direction of travel, as unit steps on the x and z axes. */
    public record Travel(int x, int z) {
    }

    /** The world the scan reads: a tile's direction of travel, or null where there is no tile. */
    public interface Tiles {

        @Nullable
        Travel travelAt(Spot spot);
    }

    /**
     * The line through this tile, head first, or nothing when there is no tile there. The head is
     * the tile nothing of the line feeds, and the last is the one the line delivers from.
     */
    public static List<Spot> through(Spot spot, Tiles tiles) {
        var travel = tiles.travelAt(spot);
        if (travel == null) return List.of();

        var line = new ArrayList<Spot>();
        for (var back = spot.step(travel, -1); travel.equals(tiles.travelAt(back)); back = back.step(travel, -1)) {
            line.addFirst(back);
        }
        line.add(spot);
        for (var on = spot.step(travel, 1); travel.equals(tiles.travelAt(on)); on = on.step(travel, 1)) {
            line.add(on);
        }
        return List.copyOf(line);
    }
}
