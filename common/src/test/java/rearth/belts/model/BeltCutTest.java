package rearth.belts.model;

import org.junit.jupiter.api.Test;
import rearth.belts.model.BeltCut.Section;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A belt cut by a splitter half placed across it (PlanetaryFactory #361): each entry stays on the
 * stretch it was on, the half keeps what it covers, and the two stored costs and the refund add
 * up to what the belt cost.
 */
class BeltCutTest {

    private static final double S = BeltContents.SPACING;

    /** A belt of this length packed full, entry i named "i" at i spacings from the start plus {@code offset}. */
    private static BeltContents<String> full(double length, double offset) {
        var belt = new BeltContents<String>();
        for (int i = 0; offset + i * S <= length - S + 1e-9; i++) belt.restore(Integer.toString(i), offset + i * S);
        return belt;
    }

    private static List<Double> positions(BeltContents<String> belt) {
        return belt.entries().stream().map(BeltContents.Entry::position).toList();
    }

    private static List<String> payloads(BeltContents<String> belt) {
        return belt.entries().stream().map(BeltContents.Entry::payload).toList();
    }

    private static List<Double> spaced(int count, double from) {
        var positions = new ArrayList<Double>();
        for (int i = 0; i < count; i++) positions.add(from + i * S);
        return positions;
    }

    @Test
    void anEntryIsPlacedByItsCentre() {
        assertEquals(new BeltCut.Place(Section.UPSTREAM, 3.9), BeltCut.locate(3.9, 4));
        assertEquals(Section.SPLITTER, BeltCut.locate(3.95, 4).section());
        assertEquals(-0.05, BeltCut.locate(3.95, 4).position(), 1e-9);
        assertEquals(Section.SPLITTER, BeltCut.locate(4.9, 4).section());
        assertEquals(Section.DOWNSTREAM, BeltCut.locate(4.95, 4).section());
        assertEquals(-0.05, BeltCut.locate(4.95, 4).position(), 1e-9);
    }

    @Test
    void aFullNineBlockBeltCutAtFourKeepsEveryEntryWhereItWas() {
        var belt = full(9, 0);
        var half = new Splitter.Half<String>();
        var downstream = new BeltContents<String>();

        var overflow = BeltCut.cut(belt, 4, 4, half, downstream, 4);

        assertEquals(List.of(), overflow);
        assertEquals(spaced(32, 0), positions(belt));
        assertEquals("31", payloads(belt).getLast());
        assertEquals(spaced(4, 0), positions(half.entering()));
        assertEquals(List.of("32", "33", "34", "35"), payloads(half.entering()));
        assertEquals(spaced(4, 0), positions(half.leaving()));
        assertEquals(List.of("36", "37", "38", "39"), payloads(half.leaving()));
        assertEquals(spaced(32, 0), positions(downstream));
        assertEquals("40", payloads(downstream).getFirst());
    }

    @Test
    void entriesStraddlingASegmentsStartArePackedOntoIt() {
        var belt = full(9, S / 2);
        var half = new Splitter.Half<String>();
        var downstream = new BeltContents<String>();

        var overflow = BeltCut.cut(belt, 4, 4, half, downstream, 4);

        assertEquals(List.of(), overflow);
        assertEquals(spaced(4, 0), positions(half.entering()));
        assertEquals(spaced(4, 0), positions(half.leaving()));
        assertEquals(0, positions(downstream).getFirst(), 1e-9);
        assertEquals(71, belt.size() + half.size() + downstream.size());
    }

    @Test
    void whatTheDownstreamBeltCannotHoldIsHandedBackAndNothingIsLostOrCreated() {
        var belt = full(9, 0);
        var half = new Splitter.Half<String>();
        var downstream = new BeltContents<String>();

        var overflow = BeltCut.cut(belt, 4, 4, half, downstream, 1);

        assertEquals(8, downstream.size());
        assertEquals(24, overflow.size());
        var all = new ArrayList<String>(payloads(belt));
        all.addAll(half.payloads());
        all.addAll(payloads(downstream));
        all.addAll(overflow);
        all.sort(null);
        var expected = new ArrayList<String>();
        for (int i = 0; i < 72; i++) expected.add(Integer.toString(i));
        expected.sort(null);
        assertEquals(expected, all);
    }

    @Test
    void theCutBeltReportsTheEntriesItLostSoAClientDropsThem() {
        var belt = full(9, 0);
        belt.drainChanges();

        BeltCut.cut(belt, 4, 4, new Splitter.Half<>(), new BeltContents<>(), 4);

        var changes = belt.drainChanges();
        assertEquals(40, changes.removed().size());
        assertTrue(changes.added().isEmpty());
    }

    @Test
    void aStraightBeltsCostIsSplitByLengthAndOneBeltIsRefunded() {
        assertEquals(new BeltCut.Costs(4, 4, 1), BeltCut.Costs.of(9, 4));
        assertEquals(new BeltCut.Costs(4, 0, 1), BeltCut.Costs.of(5, 4));
    }

    @Test
    void theDownstreamHalfTakesTheRounding() {
        var costs = BeltCut.Costs.of(12, 4.3);
        assertEquals(new BeltCut.Costs(5, 6, 1), costs);
    }

    @Test
    void aBeltThatCostNothingRefundsNothing() {
        assertEquals(new BeltCut.Costs(0, 0, 0), BeltCut.Costs.of(0, 4));
    }

    @Test
    void aBeltCurvedOnBothSidesKeepsItsCostWholeAcrossTheCut() {
        var start = new BeltPath.Anchor(0, 0, 0, 1, 0);
        var path = BeltPath.of(start, List.of(new BeltPath.Anchor(6, 0, 2, 1, 0), new BeltPath.Anchor(14, 0, 2, 1, 0)),
          new BeltPath.Anchor(20, 0, 0, -1, 0));
        var cut = (BeltPath.Crossing.Cut) path.crossing(10, 0, 2, 1, 0).orElseThrow();
        var halves = path.cut(cut, 10, 0, 2, 1, 0);
        var cost = BeltCost.of(path.length());

        var costs = BeltCut.Costs.of(cost, halves.upstream().length());

        assertEquals(BeltCost.of(halves.upstream().length()), costs.upstream());
        assertEquals(1, costs.refund());
        assertEquals(cost, costs.upstream() + costs.downstream() + costs.refund());
    }
}
