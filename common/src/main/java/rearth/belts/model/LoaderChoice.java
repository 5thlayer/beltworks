package rearth.belts.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The loader tier each open end of a new belt takes from the inventory: the belt's own tier, else
 * the nearest higher tier held, else the nearest lower one (PlanetaryFactory #354).
 */
public final class LoaderChoice {

    private LoaderChoice() {
    }

    /** One tier per end, or empty when the held loaders cannot cover every end. */
    public static Optional<List<BeltTier>> of(BeltTier belt, int ends, Map<BeltTier, Integer> held) {
        var left = new int[BeltTier.values().length + 1];
        held.forEach((tier, count) -> left[tier.number()] = count);
        var preference = new ArrayList<Integer>();
        for (int tier = belt.number(); tier <= BeltTier.values().length; tier++) preference.add(tier);
        for (int tier = belt.number() - 1; tier > 0; tier--) preference.add(tier);
        var chosen = new ArrayList<BeltTier>();
        for (int end = 0; end < ends; end++) {
            var tier = preference.stream().filter(t -> left[t] > 0).findFirst();
            if (tier.isEmpty()) return Optional.empty();
            left[tier.get()]--;
            chosen.add(BeltTier.of(tier.get()));
        }
        return Optional.of(List.copyOf(chosen));
    }
}
