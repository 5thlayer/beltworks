package rearth.belts.model;

import org.jetbrains.annotations.Nullable;

/**
 * A support joining two belts passes each item from the arriving belt's end to the leaving belt's
 * head, as a splitter does, so the join runs at the slower belt (PlanetaryFactory #366, ADR-0076).
 * With no belt leaving, nothing passes and the arriving belt backs up.
 */
public final class Join {

    private Join() {
    }

    /** @return whether any item passed */
    public static <T> boolean pass(Splitter.Lane<T> in, Splitter.@Nullable Lane<T> out) {
        if (out == null) return false;
        var passed = false;
        while (in.belt().endReady(in.length(), in.speed()) && out.belt().canOffer(out.length(), out.speed())) {
            out.belt().offer(in.belt().takeEnd(), out.speed());
            passed = true;
        }
        return passed;
    }
}
