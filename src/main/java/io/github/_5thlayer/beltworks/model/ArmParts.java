// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.model;

import java.util.ArrayList;
import java.util.List;

/**
 * The parts a feeder's arm is drawn as, in feeder-block units from its floor's corner: a plate
 * turning on the base under a hub, an upper arm and a forearm of two equal segments in the tier's
 * colour, folding or stretching so the hand hangs over the spot the arm reaches, a joint at the
 * shoulder, elbow and wrist, and a hand shaped as a pincer, closed on what it holds and open when
 * empty (GLOSSARY.md, Arm and Hand).
 */
public final class ArmParts {

    private static final double PX = 1 / 16d;
    // The plate turns on the base's top, and the upper arm rises from the hub on it.
    private static final double BASE_TOP = 6 * PX;
    private static final double PLATE_TOP = 7.5 * PX;
    private static final double PLATE_HALF_WIDTH = 4.5 * PX;
    private static final double HUB_HEIGHT = 1.5 * PX;
    private static final double HUB_HALF_WIDTH = 2.5 * PX;
    // The hand comes down so what it holds sits just above the top of what it is over.
    private static final double HELD_ABOVE_TOP = 0.12;
    private static final double HAND_LENGTH = 4 * PX;
    private static final double HELD_BELOW_HAND = 0.12;
    /** The scale a held block's model is drawn at. */
    public static final double HELD_BLOCK_SCALE = 0.4;
    /** The scale any other held item's model is drawn at. */
    public static final double HELD_ITEM_SCALE = 0.35;
    // Vanilla draws a block's model fixed at half size, and a flat item's at its full size.
    private static final double FIXED_BLOCK_SIZE = 0.5;
    /** The highest the elbow rises above the feeder, folded over a spot a block away. */
    public static final double ELBOW_RISE = 2;
    // Two equal segments reach three blocks out with the wrist over a full block, and fold up nearer in.
    private static final double SEGMENT_LENGTH = 1.6;
    private static final double SEGMENT_HALF_WIDTH = PX;
    // A joint is an axle across the arm, 4 px along it.
    private static final double JOINT_HALF_LENGTH = 2 * PX;
    private static final double JOINT_HALF_WIDTH = 1.5 * PX;
    // The pincer, below the wrist and out to its side: a block on the wrist, a crossbar under it,
    // and a jaw hanging from each of the crossbar's ends to a tip that turns inward.
    private static final double WRIST_TOP = -PX;
    private static final double WRIST_BOTTOM = 2.5 * PX;
    private static final double WRIST_HALF_WIDTH = 1.5 * PX;
    private static final double CROSSBAR = 3 * PX;
    private static final double CROSSBAR_HALF_LENGTH = 4.5 * PX;
    private static final double JAW_TOP = 3.5 * PX;
    private static final double JAW_OUT = 4 * PX;
    private static final double JAW_HALF_WIDTH = 0.9 * PX;
    private static final double JAW_HALF_DEPTH = 0.5 * PX;
    private static final double TIP_LENGTH = 1.2 * PX;
    // Closed, the tips meet the sides of what the hand holds, on its lower half; open, they stand
    // clear of anything it could hold.
    private static final double CLOSED_TIP_DOWN = 7 * PX;
    private static final Jaws OPEN = new Jaws(6.5 * PX, 5.2 * PX);

    /** Which part of the arm; the upper arm and the forearm take the tier's colour, the rest the plate's slate. */
    public enum Kind {
        PLATE, HUB, UPPER_ARM, FOREARM, JOINT, WRIST, CROSSBAR, JAW, TIP;

        public boolean tierColoured() {
            return this == UPPER_ARM || this == FOREARM;
        }
    }

    /** The block a part is lit by: the feeder's own, the one above it, or the one the wrist is in. */
    public enum Place { FEEDER, ABOVE, WRIST }

    /** A point, or a direction, in feeder-block units. */
    public record Point(double x, double y, double z) {

        public Point add(Point other) {
            return new Point(x + other.x, y + other.y, z + other.z);
        }

        public Point scale(double by) {
            return new Point(x * by, y * by, z * by);
        }

        Point up(double by) {
            return new Point(x, y + by, z);
        }
    }

    /**
     * A part as a square-ended beam from {@code from} to {@code to}, {@code halfWidth} either way
     * along {@code side}, a horizontal unit square to its length, and {@code halfDepth} either way
     * across both.
     */
    public record Part(Kind kind, Point from, Point to, double halfWidth, double halfDepth, Point side, Place place) {
    }

    /**
     * A stretch of a face along its length, {@code start} to {@code end} blocks from its beginning,
     * drawn from {@code v} blocks into its texture.
     */
    public record Piece(double start, double end, double v) {
    }

    // A tip {@code down} below the wrist, its inner end {@code in} out from the hand's middle.
    private record Jaws(double down, double in) {
    }

    private ArmParts() {
    }

    /**
     * A feeder's arm in {@code pose}, its pincer closed on what its hand holds, {@code grip} blocks
     * wide as {@link #heldWidth} gives, or open when {@code grip} is 0, the hand empty.
     */
    public static List<Part> of(ArmPose pose, double grip) {
        var forward = new Point(pose.x(), 0, pose.z());
        var side = new Point(-pose.z(), 0, pose.x());
        var middle = new Point(0.5, BASE_TOP, 0.5);
        var hub = middle.up(PLATE_TOP - BASE_TOP);
        var shoulder = hub.up(HUB_HEIGHT);
        var wrist = wrist(pose);
        var elbow = elbow(shoulder, wrist, pose);

        var parts = new ArrayList<Part>();
        parts.add(new Part(Kind.PLATE, middle, hub, PLATE_HALF_WIDTH, PLATE_HALF_WIDTH, side, Place.FEEDER));
        parts.add(new Part(Kind.HUB, hub, shoulder, HUB_HALF_WIDTH, HUB_HALF_WIDTH, side, Place.FEEDER));
        parts.add(new Part(Kind.UPPER_ARM, shoulder, elbow, SEGMENT_HALF_WIDTH, SEGMENT_HALF_WIDTH, side, Place.ABOVE));
        parts.add(new Part(Kind.FOREARM, elbow, wrist, SEGMENT_HALF_WIDTH, SEGMENT_HALF_WIDTH, side, Place.WRIST));
        parts.add(joint(shoulder, side, forward, Place.FEEDER));
        parts.add(joint(elbow, side, forward, Place.ABOVE));
        parts.add(joint(wrist, side, forward, Place.WRIST));

        // The pincer, its jaws opening along the arm's side.
        var hand = new Hand(wrist, side);
        parts.add(new Part(Kind.WRIST, hand.at(WRIST_TOP, 0), hand.at(WRIST_BOTTOM, 0), WRIST_HALF_WIDTH, WRIST_HALF_WIDTH, side, Place.WRIST));
        parts.add(new Part(Kind.CROSSBAR, hand.at(CROSSBAR, -CROSSBAR_HALF_LENGTH), hand.at(CROSSBAR, CROSSBAR_HALF_LENGTH), PX,
          0.5 * PX, forward, Place.WRIST));
        var jaws = grip > 0 ? new Jaws(CLOSED_TIP_DOWN, grip / 2) : OPEN;
        for (var out : new int[] {-1, 1}) {
            var tip = hand.at(jaws.down, out * (jaws.in + TIP_LENGTH));
            parts.add(new Part(Kind.JAW, hand.at(JAW_TOP, out * JAW_OUT), tip, JAW_HALF_WIDTH, JAW_HALF_DEPTH, forward, Place.WRIST));
            parts.add(new Part(Kind.TIP, tip, hand.at(jaws.down, out * jaws.in), JAW_HALF_WIDTH, JAW_HALF_DEPTH, forward, Place.WRIST));
        }
        return parts;
    }

    /** How wide what the hand holds is drawn, in blocks, a {@code block} or any other item. */
    public static double heldWidth(boolean block) {
        return block ? HELD_BLOCK_SCALE * FIXED_BLOCK_SIZE : HELD_ITEM_SCALE;
    }

    /** Where the middle of what the hand holds hangs, just above the top of what it is over. */
    public static Point held(ArmPose pose) {
        return wrist(pose).up(-HAND_LENGTH - HELD_BELOW_HAND);
    }

    /** The wrist: over the spot the arm reaches, the hand above the top there. */
    public static Point wrist(ArmPose pose) {
        return new Point(0.5 + pose.x() * pose.reach(), pose.top() + HELD_ABOVE_TOP + HELD_BELOW_HAND + HAND_LENGTH,
          0.5 + pose.z() * pose.reach());
    }

    /**
     * A face {@code length} blocks long, a block of its texture at a time, as an atlas sprite
     * can't wrap; one shorter than a block takes the middle of its texture.
     */
    public static List<Piece> pieces(double length) {
        if (length < 1) return List.of(new Piece(0, length, (1 - length) / 2));
        var pieces = new ArrayList<Piece>();
        for (double start = 0; start < length - 1e-9; start++) {
            pieces.add(new Piece(start, Math.min(length, start + 1), 0));
        }
        return pieces;
    }

    // Where two equal segments from the shoulder to the wrist meet, above the line between them.
    private static Point elbow(Point shoulder, Point wrist, ArmPose pose) {
        var out = pose.reach();
        var up = wrist.y() - shoulder.y();
        var apart = Math.sqrt(out * out + up * up);
        var rise = Math.sqrt(Math.max(0, SEGMENT_LENGTH * SEGMENT_LENGTH - apart * apart / 4));
        // Half the way out, then square to the shoulder-to-wrist line, upward.
        var along = out / 2 - up / apart * rise;
        var height = up / 2 + out / apart * rise;
        return shoulder.add(new Point(pose.x() * along, height, pose.z() * along));
    }

    // An axle across the arm at {@code at}.
    private static Part joint(Point at, Point side, Point forward, Place place) {
        return new Part(Kind.JOINT, at.add(side.scale(-JOINT_HALF_LENGTH)), at.add(side.scale(JOINT_HALF_LENGTH)), JOINT_HALF_WIDTH,
          JOINT_HALF_WIDTH, forward, place);
    }

    // Points on the pincer, {@code down} below the wrist and {@code out} along its side.
    private record Hand(Point wrist, Point side) {

        Point at(double down, double out) {
            return wrist.up(-down).add(side.scale(out));
        }
    }
}
