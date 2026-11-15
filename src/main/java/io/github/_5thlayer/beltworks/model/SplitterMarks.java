// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.model;

import java.util.ArrayList;
import java.util.List;

/**
 * The marks on one splitter half for the priorities that prefer it, as Factorio marks a splitter's
 * (GLOSSARY.md, Output priority): a triangle pointing the way items travel, at the half's back edge
 * for input priority and at its front edge for output priority, counted in blocks from the half's corner.
 */
public final class SplitterMarks {

    /** The triangle's base across, smaller than a feeder's ring so the half's items still show. */
    static final double SIZE = 6 / 16d;
    /** How far from the half's centre each triangle sits, back or ahead. */
    static final double OFFSET = 5 / 16d;

    private SplitterMarks() {
    }

    /** The marks of the half on side {@code half}, facing {@code facing}, its belt's surface standing {@code top} high. */
    public static List<FeederMarks.Mark> of(Splitter.Priority half, Splitter.Priority input, Splitter.Priority output,
                                            LineScan.Travel facing, double top) {
        var marks = new ArrayList<FeederMarks.Mark>(2);
        if (input == half) marks.add(mark(facing, -OFFSET, top));
        if (output == half) marks.add(mark(facing, OFFSET, top));
        return marks;
    }

    private static FeederMarks.Mark mark(LineScan.Travel facing, double along, double top) {
        return new FeederMarks.Mark(FeederMarks.Kind.TRIANGLE, 0.5 + facing.x() * along, top, 0.5 + facing.z() * along, facing, SIZE);
    }
}
