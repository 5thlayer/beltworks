package rearth.belts.model;

import java.util.Locale;
import java.util.Optional;

/**
 * A support is a belt end with one slot for a belt arriving and one for a belt leaving, or a point
 * one belt passes through (PlanetaryFactory #366, ADR-0078). Merging and splitting are the
 * splitter's.
 */
public final class SupportSlots {

    private SupportSlots() {
    }

    /** Which slots a support's belts take, or whether one belt passes through it. */
    public record Use(boolean in, boolean out, boolean midBelt) {
    }

    /** The belt item's click on a support: the belt's first click, its last, or a sneak-click. */
    public enum Click {
        START, END, MIDPOINT
    }

    public enum Refusal {
        IN_TAKEN, OUT_TAKEN, JOINED, MID_BELT, NOT_A_MIDPOINT;

        public String messageKey() {
            return "message.belts.support_" + name().toLowerCase(Locale.ROOT);
        }
    }

    /** What an end with no loader or support yet becomes. */
    public enum OpenEnd {
        LOADER, SUPPORT;

        public static OpenEnd of(boolean inventoryBeyond) {
            return inventoryBeyond ? LOADER : SUPPORT;
        }
    }

    public static Optional<Refusal> refusal(Use use, Click click) {
        if (click == Click.MIDPOINT) return Optional.of(Refusal.NOT_A_MIDPOINT);
        if (use.midBelt) return Optional.of(Refusal.MID_BELT);
        if (use.in && use.out) return Optional.of(Refusal.JOINED);
        if (click == Click.START && use.out) return Optional.of(Refusal.OUT_TAKEN);
        if (click == Click.END && use.in) return Optional.of(Refusal.IN_TAKEN);
        return Optional.empty();
    }
}
