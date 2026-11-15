// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.model;

/**
 * A flat mark drawn in the world over a block's top, as Factorio marks an inserter's ends or a
 * splitter's priorities: centred at {@code x}, {@code y} and {@code z}, counted in blocks from the
 * marked block's corner, {@code size} blocks across, pointing {@code pointing}.
 */
public record WorldMark(Kind kind, double x, double y, double z, LineScan.Travel pointing, double size) {

    public enum Kind {
        /** A feeder's head. */
        RING,
        /** A feeder's tail. */
        CHEVRON,
        /** A splitter's priority. */
        TRIANGLE
    }
}
