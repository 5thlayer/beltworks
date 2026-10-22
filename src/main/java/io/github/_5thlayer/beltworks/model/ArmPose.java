// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.model;

/**
 * Where a feeder's hand is, seen from above: the way its arm points from the feeder's centre, as a
 * unit {@code x} and {@code z}, and how many blocks it reaches. As Factorio's inserter does, the arm
 * turns and stretches at once, each at its own speed, so one may finish before the other. Half a
 * turn passes over the feeder's left, a quarter turn the short way, and the swing back retraces it.
 */
public record ArmPose(double x, double z, double reach) {

    /** The pose of an arm {@code elapsed} Factorio ticks, maybe fractional, into {@code phase}. */
    public static ArmPose of(FeederArms arms, LineScan.Travel facing, InserterSwing inserter, ArmSwing.Phase phase, double elapsed) {
        var turning = arms.revolutions() / inserter.rotationSpeed();
        var stretching = arms.stretch() / inserter.extensionSpeed();
        var turned = switch (phase) {
            case AT_HEAD -> 0;
            case TO_TAIL -> done(elapsed, turning);
            case AT_TAIL -> 1;
            case TO_HEAD -> 1 - done(elapsed, turning);
        };
        var stretched = switch (phase) {
            case AT_HEAD -> 0;
            case TO_TAIL -> done(elapsed, stretching);
            case AT_TAIL -> 1;
            case TO_HEAD -> 1 - done(elapsed, stretching);
        };
        var head = TileShape.back(facing);
        // Clockwise seen from above, Minecraft's z growing southward: a left turn is a quarter clockwise from the head.
        var swing = switch (arms.tailTurn()) {
            case STRAIGHT -> Math.PI;
            case LEFT -> Math.PI / 2;
            case RIGHT -> -Math.PI / 2;
        } * turned;
        var cos = Math.cos(swing);
        var sin = Math.sin(swing);
        return new ArmPose(head.x() * cos - head.z() * sin, head.x() * sin + head.z() * cos,
          arms.headReach() + (arms.tailReach() - arms.headReach()) * stretched);
    }

    // The share of a motion lasting {@code ticks} done after {@code elapsed}; one that takes no time is done.
    private static double done(double elapsed, double ticks) {
        return ticks <= 0 ? 1 : Math.min(1, Math.max(0, elapsed / ticks));
    }
}
