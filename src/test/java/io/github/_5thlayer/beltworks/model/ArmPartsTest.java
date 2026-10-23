// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArmPartsTest {

    private static final double PX = 1 / 16d;
    private static final double BELT = 6 * PX;
    // Reaching a block south, over a belt.
    private static final ArmPose SOUTH = new ArmPose(0, 1, 1, BELT);

    private static final double BLOCK = ArmParts.heldWidth(true);
    private static final double ITEM = ArmParts.heldWidth(false);

    private static List<ArmParts.Part> tips(double grip) {
        return ArmParts.of(SOUTH, grip).stream().filter(part -> part.kind() == ArmParts.Kind.TIP).toList();
    }

    // How far in from the hand's middle, along its side (x here), a tip's inner end comes.
    private static double inner(ArmParts.Part tip) {
        return Math.min(Math.abs(tip.from().x() - 0.5), Math.abs(tip.to().x() - 0.5));
    }

    @Test
    void aPincerHasTwoJawsEachWithATip() {
        var parts = ArmParts.of(SOUTH, BLOCK);

        assertEquals(2, parts.stream().filter(part -> part.kind() == ArmParts.Kind.JAW).count());
        assertEquals(2, tips(BLOCK).size());
    }

    // A block is drawn at half the size a flat item is, so the pincer closes further on it.
    @Test
    void aHandHoldingItemsClosesItsPincerOnTheirSides() {
        for (var tip : tips(BLOCK)) assertEquals(BLOCK / 2, inner(tip), 1e-9);
        for (var tip : tips(ITEM)) assertEquals(ITEM / 2, inner(tip), 1e-9);
    }

    @Test
    void anEmptyHandOpensItsPincerWiderThanAnythingItHolds() {
        for (var tip : tips(0)) {
            assertTrue(inner(tip) > Math.max(BLOCK, ITEM) / 2 + 1.5 * PX, "the tip stands clear of a held item's side");
        }
    }

    // What the hand holds hangs between its jaws, below the crossbar, and the tips close on its
    // lower half, still clear of the belt beneath.
    @Test
    void theHeldItemsHangBetweenTheJaws() {
        var held = ArmParts.held(SOUTH);
        var parts = ArmParts.of(SOUTH, BLOCK);
        var crossbar = parts.stream().filter(part -> part.kind() == ArmParts.Kind.CROSSBAR).findFirst().orElseThrow();

        assertTrue(held.y() < crossbar.from().y(), "below the crossbar");
        for (var tip : tips(BLOCK)) {
            assertTrue(tip.from().y() < held.y(), "a tip on its lower half");
            assertTrue(tip.from().y() > BELT, "a tip above the belt");
        }
    }

    // The upper arm rises from the hub and the forearm comes down to the wrist over the spot reached.
    @Test
    void theArmRunsFromTheHubToTheWristOverTheSpotItReaches() {
        var parts = ArmParts.of(SOUTH, 0);
        var hub = parts.stream().filter(part -> part.kind() == ArmParts.Kind.HUB).findFirst().orElseThrow();
        var upper = parts.stream().filter(part -> part.kind() == ArmParts.Kind.UPPER_ARM).findFirst().orElseThrow();
        var forearm = parts.stream().filter(part -> part.kind() == ArmParts.Kind.FOREARM).findFirst().orElseThrow();

        assertEquals(hub.to(), upper.from());
        assertEquals(upper.to(), forearm.from());
        assertEquals(0.5, forearm.to().x(), 1e-9);
        assertEquals(1.5, forearm.to().z(), 1e-9);
    }

    // An atlas sprite can't wrap, so a face is drawn a block of its texture at a time.
    @Test
    void aFaceIsCutIntoPiecesNoLongerThanABlock() {
        assertEquals(List.of(new ArmParts.Piece(0, 1, 0), new ArmParts.Piece(1, 1.6, 0)), ArmParts.pieces(1.6));
        assertEquals(List.of(new ArmParts.Piece(0, 1, 0), new ArmParts.Piece(1, 2, 0)), ArmParts.pieces(2));
    }

    // One shorter than a block takes the middle of its texture.
    @Test
    void aShortFaceTakesTheMiddleOfItsTexture() {
        assertEquals(List.of(new ArmParts.Piece(0, 0.25, 0.375)), ArmParts.pieces(0.25));
    }

    // The corners of a part's beam.
    private static List<ArmParts.Point> corners(ArmParts.Part part) {
        var length = new ArmParts.Point(part.to().x() - part.from().x(), part.to().y() - part.from().y(), part.to().z() - part.from().z());
        var side = part.side();
        var across = new ArmParts.Point(side.y() * length.z() - side.z() * length.y(), side.z() * length.x() - side.x() * length.z(),
          side.x() * length.y() - side.y() * length.x());
        var norm = Math.sqrt(across.x() * across.x() + across.y() * across.y() + across.z() * across.z());
        across = across.scale(part.halfDepth() / norm);
        var corners = new ArrayList<ArmParts.Point>();
        for (var end : List.of(part.from(), part.to())) {
            for (var a : new int[] {-1, 1}) {
                for (var b : new int[] {-1, 1}) {
                    corners.add(end.add(side.scale(a * part.halfWidth())).add(across.scale(b)));
                }
            }
        }
        return corners;
    }

    // The least and greatest of a part's corners along each axis: {min x, max x, min y, max y, min z, max z}.
    private static double[] bounds(ArmParts.Part part) {
        var corners = corners(part);
        var bounds = new double[6];
        for (var axis = 0; axis < 3; axis++) {
            final var at = axis;
            var values = corners.stream().mapToDouble(c -> at == 0 ? c.x() : at == 1 ? c.y() : c.z()).toArray();
            bounds[2 * axis] = Arrays.stream(values).min().orElseThrow();
            bounds[2 * axis + 1] = Arrays.stream(values).max().orElseThrow();
        }
        return bounds;
    }

    private static final double EPSILON = 1e-9;

    // Whether two parts' bounding boxes meet or cross; the parts are small, so the box stands in for them.
    private static boolean touch(double[] a, double[] b) {
        for (var axis = 0; axis < 3; axis++) {
            if (a[2 * axis] > b[2 * axis + 1] + EPSILON || b[2 * axis] > a[2 * axis + 1] + EPSILON) return false;
        }
        return true;
    }

    // Whether any face of one box lies in the plane of any face of the other, on the same axis.
    private static boolean sharesAFacePlane(double[] a, double[] b) {
        for (var axis = 0; axis < 3; axis++) {
            for (var mine = 0; mine < 2; mine++) {
                for (var theirs = 0; theirs < 2; theirs++) {
                    if (Math.abs(a[2 * axis + mine] - b[2 * axis + theirs]) <= EPSILON) return true;
                }
            }
        }
        return false;
    }

    // Faces in one plane where two parts meet or cross flicker, as the renderer can't tell which is in front.
    @Test
    void noTwoPartsOfTheHandAndItsBaseShareAFacePlaneWhereTheyMeet() {
        for (var grip : new double[] {0, BLOCK, ITEM}) {
            var pincer = ArmParts.of(SOUTH, grip).stream().filter(part -> switch (part.kind()) {
                case PLATE, HUB, WRIST, CROSSBAR, JAW, TIP -> true;
                // The shoulder's and the wrist's, the elbow's being clear of the rest.
                case JOINT -> part.place() != ArmParts.Place.ABOVE;
                default -> false;
            }).toList();
            for (var i = 0; i < pincer.size(); i++) {
                for (var j = i + 1; j < pincer.size(); j++) {
                    var a = bounds(pincer.get(i));
                    var b = bounds(pincer.get(j));
                    assertTrue(!touch(a, b) || !sharesAFacePlane(a, b), pincer.get(i).kind() + " and " + pincer.get(j).kind() + " share a face plane");
                }
            }
        }
    }
}
