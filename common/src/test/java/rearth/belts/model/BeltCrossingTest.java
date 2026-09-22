package rearth.belts.model;

import org.junit.jupiter.api.Test;
import rearth.belts.model.BeltPath.Anchor;
import rearth.belts.model.BeltPath.Crossing;
import rearth.belts.model.BeltPath.CrossingRefusal;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where a splitter half placed on a block meets a belt, and the two belts the cut leaves
 * (PlanetaryFactory #361). Every belt starts at a loader at the origin facing east, so its curve
 * starts at x = 0 on z = 0.5.
 */
class BeltCrossingTest {

    private static final Anchor START = new Anchor(0, 0, 0, 1, 0);
    private static final int EAST = 1;

    private static Anchor westFacing(int x, int y, int z) {
        return new Anchor(x, y, z, -1, 0);
    }

    private static Optional<Crossing> eastFacingHalfAt(BeltPath path, int x, int y, int z) {
        return path.crossing(x, y, z, EAST, 0);
    }

    private static Optional<Crossing> refused(CrossingRefusal reason) {
        return Optional.of(new Crossing.Refused(reason));
    }

    @Test
    void aStraightBeltIsCutAtTheHalfsBackFace() {
        var path = BeltPath.of(START, List.of(), westFacing(9, 0, 0));
        var cut = assertInstanceOf(Crossing.Cut.class, eastFacingHalfAt(path, 4, 0, 0).orElseThrow());
        assertEquals(0, cut.span());
        assertEquals(4, cut.at(), 1e-9);
    }

    @Test
    void aBlockTheBeltDoesNotReachIsNoCrossing() {
        var path = BeltPath.of(START, List.of(), westFacing(9, 0, 0));
        assertEquals(Optional.empty(), eastFacingHalfAt(path, 4, 0, 1));
        assertEquals(Optional.empty(), eastFacingHalfAt(path, 4, 1, 0));
        assertEquals(Optional.empty(), eastFacingHalfAt(path, 12, 0, 0));
    }

    @Test
    void aBeltFlowingAgainstTheFacingIsRefused() {
        var path = BeltPath.of(START, List.of(), westFacing(9, 0, 0));
        assertEquals(refused(CrossingRefusal.AGAINST_FACING), path.crossing(4, 0, 0, -1, 0));
    }

    @Test
    void aBeltCrossingThroughASideIsRefused() {
        var path = BeltPath.of(START, List.of(), westFacing(9, 0, 0));
        assertEquals(refused(CrossingRefusal.THROUGH_SIDE), path.crossing(4, 0, 0, 0, -1));
        assertEquals(refused(CrossingRefusal.THROUGH_SIDE), path.crossing(4, 0, 0, 0, 1));
    }

    @Test
    void aBeltChangingLaneAcrossTheBlockCrossesAtAnAngle() {
        var path = BeltPath.of(START, List.of(), westFacing(6, 0, 1));
        assertEquals(refused(CrossingRefusal.AT_ANGLE), eastFacingHalfAt(path, 2, 0, 0));
    }

    @Test
    void aBeltTurningAcrossTheBlockIsRefused() {
        var southward = new Anchor(4, 0, 4, 0, -1);
        var path = BeltPath.of(START, List.of(), southward);
        assertEquals(refused(CrossingRefusal.CURVED), eastFacingHalfAt(path, 1, 0, 0));
    }

    @Test
    void aClimbingBeltIsRefused() {
        var path = BeltPath.of(START, List.of(), westFacing(9, 1, 0));
        assertEquals(refused(CrossingRefusal.CURVED), eastFacingHalfAt(path, 4, 0, 0));
    }

    @Test
    void aStraightBeltCutsWhereverTheHalfStandsAlongIt() {
        var path = BeltPath.of(START, List.of(), westFacing(9, 0, 0));
        for (int x = 0; x <= 9; x++) {
            var cut = assertInstanceOf(Crossing.Cut.class, eastFacingHalfAt(path, x, 0, 0).orElseThrow());
            assertEquals(x, cut.at(), 1e-9);
        }
    }

    @Test
    void theCutLeavesTwoBeltsOneBlockShortOfTheCutBelt() {
        var path = BeltPath.of(START, List.of(), westFacing(9, 0, 0));
        var cut = (Crossing.Cut) eastFacingHalfAt(path, 4, 0, 0).orElseThrow();
        var halves = path.cut(cut, 4, 0, 0, EAST, 0);
        assertEquals(4, halves.upstream().length(), 1e-9);
        assertEquals(5, halves.downstream().length(), 1e-9);
        assertEquals(Optional.empty(), halves.upstream().refusal());
        assertEquals(Optional.empty(), halves.downstream().refusal());
    }

    // Curves on both sides of a straight span; each keeps its own support and its curve.
    @Test
    void aBeltCurvedOnBothSidesOfTheCutKeepsEachCurve() {
        var before = new Anchor(6, 0, 2, 1, 0);
        var after = new Anchor(14, 0, 2, 1, 0);
        var path = BeltPath.of(START, List.of(before, after), westFacing(20, 0, 0));
        assertEquals(Optional.empty(), path.refusal());
        var spans = path.spanLengths();

        var cut = assertInstanceOf(Crossing.Cut.class, eastFacingHalfAt(path, 10, 0, 2).orElseThrow());
        assertEquals(1, cut.span());
        assertEquals(spans[0] + 3.5, cut.at(), 1e-9);

        var halves = path.cut(cut, 10, 0, 2, EAST, 0);
        assertEquals(cut.at(), halves.upstream().length(), 1e-9);
        assertEquals(path.length(), halves.upstream().length() + 1 + halves.downstream().length(), 1e-9);
        assertEquals(spans[0], halves.upstream().spanLengths()[0], 1e-9);
        assertEquals(spans[2], halves.downstream().spanLengths()[1], 1e-9);
        assertEquals(1, halves.downstream().nodes().get(1).tangentX(), 1e-9);
    }

    @Test
    void theCurvedStretchesEitherSideOfTheStraightSpanAreNotCut() {
        var before = new Anchor(6, 0, 2, 1, 0);
        var after = new Anchor(14, 0, 2, 1, 0);
        var path = BeltPath.of(START, List.of(before, after), westFacing(20, 0, 0));
        assertEquals(refused(CrossingRefusal.AT_ANGLE), eastFacingHalfAt(path, 3, 0, 1));
    }

    // A lane change whose centreline stays out of the block but whose width reaches into it.
    @Test
    void aBeltWhoseWidthOverlapsTheBlockIsNotCut() {
        var path = BeltPath.of(START, List.of(), westFacing(12, 0, 1));
        var overlapped = -1;
        for (int x = 0; x <= 12 && overlapped < 0; x++) {
            var inBlock = path.crossing(x, 0, 1, EAST, 0);
            if (inBlock.equals(refused(CrossingRefusal.NOT_CUT))) overlapped = x;
        }
        assertTrue(overlapped >= 0, "no block beside the lane change reads as merely overlapped");
    }

    @Test
    void aBeltEndingOnTheBlocksBackFaceDoesNotReachIntoIt() {
        var path = BeltPath.of(START, List.of(), westFacing(3, 0, 0));
        assertEquals(Optional.empty(), eastFacingHalfAt(path, 4, 0, 0));
    }
}
