package rearth.belts.model;

import org.junit.jupiter.api.Test;
import rearth.belts.model.BeltPath.Anchor;
import rearth.belts.model.BeltPath.Bound;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The four bounds a span keeps, each just inside and just past (PlanetaryFactory ADR-0078).
 *
 * <p>Every belt here starts at a loader at the origin facing east, so its curve starts at x = 0.
 * A loader ending a belt faces back along it, and its curve ends on its far face: an end loader
 * at x = n facing west ends the curve at x = n + 1.
 */
class BeltPathTest {

    private static final Anchor START = new Anchor(0, 0, 0, 1, 0);

    private static Anchor westFacing(int x, int y, int z) {
        return new Anchor(x, y, z, -1, 0);
    }

    private static Optional<Bound> bound(BeltPath path) {
        return path.refusal().map(BeltPath.Refusal::bound);
    }

    @Test
    void aStraightLevelBeltIsItsOwnLength() {
        var path = BeltPath.of(START, List.of(), westFacing(31, 0, 0));
        assertEquals(32, path.length(), 1e-9);
        assertEquals(Optional.empty(), path.refusal());
    }

    @Test
    void aSpanReachesThirtyTwoBlocksAndNoFurther() {
        assertEquals(Optional.empty(), bound(BeltPath.of(START, List.of(), westFacing(32, 0, 0))));
        assertEquals(Optional.of(Bound.SPAN_TOO_LONG), bound(BeltPath.of(START, List.of(), westFacing(33, 0, 0))));
    }

    @Test
    void reachIsTheLargerOfTheTwoHorizontalDistances() {
        var northFacing = new Anchor(32, 0, 20, 0, -1);
        assertEquals(Optional.empty(), bound(BeltPath.of(START, List.of(), northFacing)));
    }

    @Test
    void aBeltEndingOnASupportEndsAtItsCentre() {
        var path = BeltPath.of(START, List.of(), Anchor.support(8, 0, 0, -1, 0));
        assertEquals(8.5, path.length(), 1e-9);
        var end = path.nodes().getLast();
        assertEquals(8.5, end.x(), 1e-9);
        assertEquals(1, end.tangentX(), 1e-9);
    }

    @Test
    void aBeltStartingOnASupportStartsAtItsCentreAndLeavesTheWayItFaces() {
        var path = BeltPath.of(Anchor.support(0, 0, 0, 1, 0), List.of(), Anchor.support(8, 0, 0, -1, 0));
        assertEquals(8, path.length(), 1e-9);
        var start = path.nodes().getFirst();
        assertEquals(0.5, start.x(), 1e-9);
        assertEquals(1, start.tangentX(), 1e-9);
    }

    @Test
    void aSupportSplitsASixtyFourBlockBeltIntoTwoSpansThatFit() {
        var path = BeltPath.of(START, List.of(new Anchor(32, 0, 0, 1, 0)), westFacing(63, 0, 0));
        assertEquals(Optional.empty(), path.refusal());
        assertEquals(64, path.length(), 1e-9);
        assertEquals(2, path.spanLengths().length);
    }

    @Test
    void aStraightClimbOfOneBlockOverFourIsAccepted() {
        assertEquals(Optional.empty(), bound(BeltPath.of(START, List.of(), westFacing(3, 1, 0))));
    }

    @Test
    void aStraightClimbOfOneBlockOverThreeIsTooSteep() {
        assertEquals(Optional.of(Bound.TOO_STEEP), bound(BeltPath.of(START, List.of(), westFacing(2, 1, 0))));
    }

    // Twice the line's slope would pass at 33.7°; the drawn curve reaches 35.4°, because upstream
    // scales its tangents from the curve's length, which is longer than its horizontal run.
    @Test
    void theSlopeIsReadAtTheCurvesSteepestPointNotOnTheLineBetweenItsEnds() {
        var path = BeltPath.of(START, List.of(), westFacing(2, 1, 0));
        assertTrue(2.0 * 1 / 3 < BeltPath.MAX_SLOPE);
        assertTrue(path.steepestSlope(0) > BeltPath.MAX_SLOPE);
    }

    @Test
    void aLongClimbsSteepestPointApproachesTwiceItsLinesSlope() {
        var path = BeltPath.of(START, List.of(), westFacing(199, 4, 0));
        assertEquals(2.0 * 4 / 200, path.steepestSlope(0), 0.002);
    }

    @Test
    void aClimbThatAlsoMovesSidewaysIsRefused() {
        assertEquals(Optional.of(Bound.TURNS_WHILE_CLIMBING), bound(BeltPath.of(START, List.of(), westFacing(8, 1, 1))));
    }

    @Test
    void aClimbThatAlsoTurnsIsRefused() {
        var northFacing = new Anchor(8, 1, 8, 0, -1);
        assertEquals(Optional.of(Bound.TURNS_WHILE_CLIMBING), bound(BeltPath.of(START, List.of(), northFacing)));
    }

    @Test
    void aClimbThatDoublesBackIsRefused() {
        assertEquals(Optional.of(Bound.TURNS_WHILE_CLIMBING), bound(BeltPath.of(START, List.of(), westFacing(-8, 1, 0))));
    }

    @Test
    void aQuarterTurnOverTwoAndAHalfBlocksIsAccepted() {
        var northFacing = new Anchor(2, 0, 2, 0, -1);
        var path = BeltPath.of(START, List.of(), northFacing);
        assertEquals(Optional.empty(), path.refusal());
        assertTrue(path.tightestRadius(0) >= BeltPath.MIN_RADIUS);
    }

    @Test
    void aQuarterTurnOverOneAndAHalfBlocksIsTooTight() {
        var northFacing = new Anchor(1, 0, 1, 0, -1);
        assertEquals(Optional.of(Bound.TURN_TOO_TIGHT), bound(BeltPath.of(START, List.of(), northFacing)));
    }

    @Test
    void aSidestepOfOneBlockNeedsThreeOfRun() {
        assertEquals(Optional.empty(), bound(BeltPath.of(START, List.of(), westFacing(2, 0, 1))));
        assertEquals(Optional.of(Bound.TURN_TOO_TIGHT), bound(BeltPath.of(START, List.of(), westFacing(1, 0, 1))));
    }

    @Test
    void theRefusalNamesTheFirstSpanThatBreaksABound() {
        var support = new Anchor(10, 0, 0, 1, 0);
        var path = BeltPath.of(START, List.of(support), westFacing(12, 1, 0));
        assertEquals(Optional.of(new BeltPath.Refusal(Bound.TOO_STEEP, 1)), path.refusal());
    }

    @Test
    void aSupportFacesAlongTheBeltWhicheverWayItWasPlaced() {
        var backwards = new Anchor(10, 0, 0, -1, 0);
        var path = BeltPath.of(START, List.of(backwards), westFacing(20, 0, 0));
        assertEquals(1, path.nodes().get(1).tangentX(), 1e-9);
        assertEquals(Optional.empty(), path.refusal());
    }

    @Test
    void aPathWithNoEndStopsAtItsLastSupport() {
        var support = new Anchor(10, 0, 0, 1, 0);
        var path = BeltPath.of(START, List.of(support), null);
        assertEquals(2, path.nodes().size());
        assertEquals(10.5, path.nodes().getLast().x(), 1e-9);
        assertEquals(Optional.empty(), path.refusal());
    }

    @Test
    void aPathWithNoEndIsJudgedOnTheSpansItHas() {
        var support = new Anchor(2, 1, 0, 1, 0);
        assertEquals(Optional.of(Bound.TOO_STEEP), bound(BeltPath.of(START, List.of(support), null)));
    }
}
