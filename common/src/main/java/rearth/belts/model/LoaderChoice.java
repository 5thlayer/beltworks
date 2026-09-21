package rearth.belts.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The loader tier each open end of a new belt takes from the inventory: the belt's own tier, else
 * the highest lower tier held, never a higher one (PlanetaryFactory #354).
 */
public final class LoaderChoice {

    private LoaderChoice() {
    }

    /** One tier per end, or empty when the held loaders cannot cover every end. */
    public static Optional<List<BeltTier>> of(BeltTier belt, int ends, Map<BeltTier, Integer> held) {
        var left = new int[BeltTier.values().length + 1];
        held.forEach((tier, count) -> left[tier.number()] = count);
        var chosen = new ArrayList<BeltTier>();
        for (int end = 0; end < ends; end++) {
            var tier = belt.number();
            while (tier > 0 && left[tier] == 0) tier--;
            if (tier == 0) return Optional.empty();
            left[tier]--;
            chosen.add(BeltTier.of(tier));
        }
        return Optional.of(List.copyOf(chosen));
    }
}
