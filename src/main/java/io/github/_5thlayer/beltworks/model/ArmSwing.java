// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.model;

/**
 * Where a feeder's arm is in its cycle: over the head, swinging to the tail, over the tail or
 * swinging back (ADR 0018). It swings in Factorio's ticks, three to a Minecraft tick, and what a
 * half-swing overruns its last tick by carries into the next, so the arm keeps Factorio's pace
 * between whole ticks. Over an end it waits for its feeder to grab or drop, and banks nothing.
 */
public final class ArmSwing {

    public enum Phase {
        AT_HEAD,
        TO_TAIL,
        AT_TAIL,
        TO_HEAD
    }

    private Phase phase = Phase.AT_HEAD;
    // Factorio ticks into this half-swing, or, over an end it has just reached, past it.
    private int elapsed;

    /** An arm in a saved {@code phase}; one it does not know rests over the head. */
    public static ArmSwing load(String phase, int elapsed) {
        for (var known : Phase.values()) {
            if (known.name().equals(phase)) return at(known, elapsed);
        }
        return new ArmSwing();
    }

    /** An arm {@code elapsed} Factorio ticks into {@code phase}. */
    public static ArmSwing at(Phase phase, int elapsed) {
        var arm = new ArmSwing();
        arm.phase = phase;
        arm.elapsed = Math.max(0, elapsed);
        return arm;
    }

    /** One Minecraft tick of a half-swing lasting {@code halfSwingTicks} of Factorio's. */
    public void tick(int halfSwingTicks) {
        if (phase == Phase.AT_HEAD || phase == Phase.AT_TAIL) {
            elapsed = 0;
            return;
        }
        elapsed += InserterSwing.FACTORIO_TICKS_PER_TICK;
        if (elapsed < halfSwingTicks) return;
        elapsed -= halfSwingTicks;
        phase = phase == Phase.TO_TAIL ? Phase.AT_TAIL : Phase.AT_HEAD;
    }

    /** The hand has grabbed: the arm leaves the head. */
    public void swingToTail() {
        phase = Phase.TO_TAIL;
    }

    /** The hand has dropped: the arm leaves the tail. */
    public void swingToHead() {
        phase = Phase.TO_HEAD;
    }

    public Phase phase() {
        return phase;
    }

    public int elapsed() {
        return elapsed;
    }
}
