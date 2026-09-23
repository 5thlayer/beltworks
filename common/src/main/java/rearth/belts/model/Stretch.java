package rearth.belts.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

/**
 * The tiles one drag of the tile item lays (PlanetaryFactory #393): from a stored start to an end,
 * in one straight leg or two joined by one corner, the first leg along the look stored with the
 * start. It is level with its start, and never starts by turning back on its look. A stretch with
 * corners chains such legs, each from where the last ended and heading the way its last tile travels.
 */
public final class Stretch {

    private Stretch() {
    }

    /** One tile of a stretch and the way it travels. */
    public record Step(LineScan.Spot spot, LineScan.Travel travel) {
    }

    /** The stretch's tiles from its start, or empty when the end lies behind the look. */
    public static Optional<List<Step>> path(LineScan.Spot start, LineScan.Travel look, LineScan.Spot end) {
        var right = TileShape.right(look);
        var dx = end.x() - start.x();
        var dz = end.z() - start.z();
        var along = dx * look.x() + dz * look.z();
        var aside = dx * right.x() + dz * right.z();
        if (along < 0) return Optional.empty();

        var steps = new ArrayList<Step>();
        for (var i = 0; i < along; i++) steps.add(new Step(start.step(look, i), look));
        var corner = start.step(look, along);
        var turn = aside == 0 ? look : aside > 0 ? right : TileShape.left(look);
        for (var i = 0; i <= Math.abs(aside); i++) steps.add(new Step(corner.step(turn, i), turn));
        return Optional.of(List.copyOf(steps));
    }

    /** The legs through each corner in turn and on to the end, or empty when any leg turns back. */
    public static Optional<List<Step>> path(LineScan.Spot start, LineScan.Travel look, List<LineScan.Spot> corners, LineScan.Spot end) {
        // A later leg crossing an earlier tile turns it rather than laying it twice.
        var steps = new LinkedHashMap<LineScan.Spot, Step>();
        var from = start;
        var heading = look;
        var ends = new ArrayList<>(corners);
        ends.add(end);
        for (var to : ends) {
            var leg = path(from, heading, to);
            if (leg.isEmpty()) return Optional.empty();
            for (var step : leg.get()) steps.put(step.spot(), step);
            from = leg.get().getLast().spot();
            heading = leg.get().getLast().travel();
        }
        return Optional.of(List.copyOf(steps.values()));
    }
}
