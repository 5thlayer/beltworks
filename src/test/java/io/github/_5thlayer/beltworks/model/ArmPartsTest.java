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

    private static List<ArmParts.Part> tips(boolean holding) {
        return ArmParts.of(SOUTH, holding).stream().filter(part -> part.kind() == ArmParts.Kind.TIP).toList();
    }

    // How far in from the hand's middle, along its side (x here), a tip's inner end comes.
    private static double inner(ArmParts.Part tip) {
        return Math.min(Math.abs(tip.from().x() - 0.5), Math.abs(tip.to().x() - 0.5));
    }

    @Test
    void aPincerHasTwoJawsEachWithATip() {
        var parts = ArmParts.of(SOUTH, true);

        assertEquals(2, parts.stream().filter(part -> part.kind() == ArmParts.Kind.JAW).count());
        assertEquals(2, tips(true).size());
    }

    // Its tips meet the sides of a held block, which hangs 0.4 of a block wide.
    @Test
    void aHandHoldingItemsClosesItsPincerOnThem() {
        for (var tip : tips(true)) {
            assertTrue(inner(tip) < ArmParts.HELD_BLOCK_SCALE / 2, "the tip reaches in under the held block");
        }
    }

    @Test
    void anEmptyHandOpensItsPincerWiderThanWhatItHolds() {
        for (var tip : tips(false)) {
            assertTrue(inner(tip) > ArmParts.HELD_BLOCK_SCALE / 2 + 1.5 * PX, "the tip stands clear of a held block's side");
        }
    }

    // What the hand holds hangs between its jaws, below the crossbar, and the tips close on its
    // lower half, still clear of the belt beneath.
    @Test
    void theHeldItemsHangBetweenTheJaws() {
        var held = ArmParts.held(SOUTH);
        var parts = ArmParts.of(SOUTH, true);
        var crossbar = parts.stream().filter(part -> part.kind() == ArmParts.Kind.CROSSBAR).findFirst().orElseThrow();

        assertTrue(held.y() < crossbar.from().y(), "below the crossbar");
        for (var tip : tips(true)) {
            assertTrue(tip.from().y() < held.y(), "a tip on its lower half");
            assertTrue(tip.from().y() > BELT, "a tip above the belt");
        }
    }

    // The upper arm rises from the hub and the forearm comes down to the wrist over the spot reached.
    @Test
    void theArmRunsFromTheHubToTheWristOverTheSpotItReaches() {
        var parts = ArmParts.of(SOUTH, false);
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
