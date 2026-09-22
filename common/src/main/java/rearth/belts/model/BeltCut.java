package rearth.belts.model;

import java.util.ArrayList;
import java.util.List;

/**
 * A belt cut in two by a splitter half placed across it (PlanetaryFactory #361). The half covers
 * one block of the belt: the belt before it ends at the half's back face and the belt after it
 * starts at its front face. Positions here are along the belt, in blocks, as an entry's are.
 */
public final class BeltCut {

    private BeltCut() {
    }

    public enum Section {
        UPSTREAM, SPLITTER, DOWNSTREAM
    }

    /** Where an entry or a hand at a position on the cut belt is now, and at what position there. */
    public record Place(Section section, double position) {
    }

    /**
     * Where the entry at this position, or a hand at this point, lands when the belt is cut
     * {@code at} along it. An entry goes by its centre; a hand's point is read the same way, so
     * it keeps holding where it held.
     */
    public static Place locate(double position, double at) {
        var centre = position + BeltContents.SPACING / 2;
        if (centre < at) return new Place(Section.UPSTREAM, position);
        if (centre < at + 1) return new Place(Section.SPLITTER, position - at);
        return new Place(Section.DOWNSTREAM, position - at - 1);
    }

    /**
     * The cut belt's cost split between its two halves and one belt item refunded for the block
     * the splitter takes. The upstream half stores its own length's cost and the downstream half
     * the rest, so the three always add up to what the belt cost.
     */
    public record Costs(int upstream, int downstream, int refund) {

        public static Costs of(int cost, double upstreamLength) {
            var refund = Math.min(1, cost);
            var upstream = Math.min(BeltCost.of(upstreamLength), cost - refund);
            return new Costs(upstream, cost - refund - upstream, refund);
        }
    }

    /**
     * Moves every entry at or past the cut off {@code belt}, which then holds only the upstream
     * half, onto the splitter's {@code half} or the empty {@code downstream} belt. What the half
     * cannot hold goes to the downstream belt's head.
     *
     * @return what fits nowhere, for the player who placed the splitter
     */
    public static <T> List<T> cut(BeltContents<T> belt, double at, double upstreamLength, Splitter.Half<T> half,
                                  BeltContents<T> downstream, double downstreamLength) {
        var onHalf = new ArrayList<T>();
        var past = new ArrayList<BeltContents.Entry<T>>();
        for (var entry : belt.takeFrom(at - BeltContents.SPACING / 2)) {
            var place = locate(entry.position(), at);
            if (place.section() == Section.DOWNSTREAM) {
                past.add(entry);
            } else if (place.position() + BeltContents.SPACING / 2 < Splitter.MIDLINE) {
                half.entering().restore(entry.payload(), place.position());
            } else {
                half.leaving().restore(entry.payload(), place.position() - Splitter.MIDLINE);
            }
        }
        onHalf.addAll(half.entering().fit(Splitter.MIDLINE));
        onHalf.addAll(half.leaving().fit(Splitter.MIDLINE));

        for (var payload : onHalf) downstream.restore(payload, 0);
        for (var entry : past) downstream.restore(entry.payload(), entry.position() - at - 1);
        var overflow = new ArrayList<>(downstream.fit(downstreamLength));
        overflow.addAll(belt.fit(upstreamLength));
        return overflow;
    }
}
