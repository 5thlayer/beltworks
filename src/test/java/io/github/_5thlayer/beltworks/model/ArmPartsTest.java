// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.model;

import org.junit.jupiter.api.Test;

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
}
