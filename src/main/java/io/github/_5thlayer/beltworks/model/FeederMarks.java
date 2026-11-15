// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.model;

/**
 * The marks on a feeder's ends, as Factorio marks an inserter's (GLOSSARY.md, Feeder): a ring on
 * its head and a chevron on its tail, each lying on the top of what that end holds, counted in
 * blocks from the feeder block's corner.
 */
public record FeederMarks(WorldMark head, WorldMark tail) {

    /** The ring's diameter and the chevron's width alike: a tile's belt across. */
    private static final double SIZE = 10 / 16d;

    /** The marks of a feeder facing {@code facing}, its head's end standing {@code headTop} high and its tail's {@code tailTop}. */
    public static FeederMarks of(FeederArms arms, LineScan.Travel facing, double headTop, double tailTop) {
        var feeder = new LineScan.Spot(0, 0, 0);
        return new FeederMarks(mark(WorldMark.Kind.RING, arms.head(feeder, facing), headTop), mark(WorldMark.Kind.CHEVRON, arms.tail(feeder, facing), tailTop));
    }

    private static WorldMark mark(WorldMark.Kind kind, FeederArms.Target target, double top) {
        return new WorldMark(kind, target.spot().x() + 0.5, top, target.spot().z() + 0.5, target.pointing(), SIZE);
    }
}
