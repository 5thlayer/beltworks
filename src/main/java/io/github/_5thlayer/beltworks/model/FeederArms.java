// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.model;

/**
 * A feeder's two arms: how far its head and its tail reach, one to three blocks each, and which way
 * its tail turns. The head reaches behind the feeder's facing and the tail ahead of it, turned left
 * or right as a tile turns, both on the feeder's own level and counted from the feeder's block. An
 * arm passes over whatever stands between (CONTEXT.md, Arm).
 */
public record FeederArms(int headReach, int tailReach, Turn tailTurn) {

    public static final int MIN_REACH = 1;
    public static final int MAX_REACH = 3;
    /** What a feeder placed before arms had reach: its head and tail on the blocks either side. */
    public static final FeederArms ADJACENT = new FeederArms(MIN_REACH, MIN_REACH, Turn.STRAIGHT);

    /** One of a feeder's two arms, as the Head Reach and Tail Reach keys name them. */
    public enum Arm {
        HEAD,
        TAIL
    }

    /** Which way the tail points, seen from above with the feeder's facing ahead. */
    public enum Turn {
        STRAIGHT,
        LEFT,
        RIGHT;

        LineScan.Travel of(LineScan.Travel facing) {
            return switch (this) {
                case STRAIGHT -> facing;
                case LEFT -> TileShape.left(facing);
                case RIGHT -> TileShape.right(facing);
            };
        }
    }

    public FeederArms {
        if (!reaches(headReach) || !reaches(tailReach)) {
            throw new IllegalArgumentException("a reach is 1 to 3 blocks, not " + headReach + " and " + tailReach);
        }
    }

    public static boolean reaches(int blocks) {
        return blocks >= MIN_REACH && blocks <= MAX_REACH;
    }

    /** A block an arm reaches, and the way the arm points to it, whose opposite is the face it reaches through. */
    public record Target(LineScan.Spot spot, LineScan.Travel pointing) {
    }

    public Target head(LineScan.Spot feeder, LineScan.Travel facing) {
        var pointing = TileShape.back(facing);
        return new Target(feeder.step(pointing, headReach), pointing);
    }

    public Target tail(LineScan.Spot feeder, LineScan.Travel facing) {
        var pointing = tailTurn.of(facing);
        return new Target(feeder.step(pointing, tailReach), pointing);
    }

    public int reach(Arm arm) {
        return arm == Arm.HEAD ? headReach : tailReach;
    }

    /** These arms with {@code arm} a block longer, from the longest back to the shortest, as a press of its key leaves them. */
    public FeederArms lengthened(Arm arm) {
        var blocks = reach(arm) % MAX_REACH + MIN_REACH;
        return arm == Arm.HEAD ? withHeadReach(blocks) : withTailReach(blocks);
    }

    public FeederArms withHeadReach(int blocks) {
        return new FeederArms(blocks, tailReach, tailTurn);
    }

    public FeederArms withTailReach(int blocks) {
        return new FeederArms(headReach, blocks, tailTurn);
    }

    public FeederArms withTailTurn(Turn turn) {
        return new FeederArms(headReach, tailReach, turn);
    }
}
