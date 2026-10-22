// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.model;

/**
 * Where a feeder's hand is, seen from above: the way its arm points from the feeder's centre, as a
 * unit {@code x} and {@code z}, how many blocks it reaches, and the {@code top} it comes down to,
 * in blocks above the feeder's floor. As Factorio's inserter does, the arm turns and stretches at
 * once, each at its own speed, so one may finish before the other. Half a turn passes over the
 * feeder's left, a quarter turn the short way, and the swing back retraces it. The hand comes down
 * to the top of what each end holds, a belt's surface or a chest's lid, and shifts between the two
 * as the arm turns.
 */
public record ArmPose(double x, double z, double reach, double top) {

    /**
     * The pose of an arm {@code elapsed} Factorio ticks, maybe fractional, into {@code phase}, its
     * head's end standing {@code headTop} high and its tail's {@code tailTop}.
     */
    public static ArmPose of(FeederArms arms, LineScan.Travel facing, InserterSwing inserter, ArmSwing.Phase phase, double elapsed,
                             double headTop, double tailTop) {
        var turning = arms.revolutions() / inserter.rotationSpeed();
        var stretching = arms.stretch() / inserter.extensionSpeed();
        var turned = towardTail(phase, elapsed, turning);
        var stretched = towardTail(phase, elapsed, stretching);
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
          arms.headReach() + (arms.tailReach() - arms.headReach()) * stretched, headTop + (tailTop - headTop) * turned);
    }

    // How far towards the tail a motion lasting {@code ticks} each way is, {@code elapsed} into {@code phase}.
    private static double towardTail(ArmSwing.Phase phase, double elapsed, double ticks) {
        // One that takes no time is done at once.
        var done = ticks <= 0 ? 1 : Math.min(1, Math.max(0, elapsed / ticks));
        return switch (phase) {
            case AT_HEAD -> 0;
            case TO_TAIL -> done;
            case AT_TAIL -> 1;
            case TO_HEAD -> 1 - done;
        };
    }
}
