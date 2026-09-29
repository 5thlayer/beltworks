// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.model;

/**
 * What a belt's end hands its items on to: the next belt's entry, or a piece with no positions of
 * its own, such as a loader, which takes an item or refuses it (#86). One offer shape, so a
 * sender hands on the same way whatever stands in front of it.
 */
@FunctionalInterface
public interface Outlet<T> {

    /**
     * Takes the item at the sender's end, answering whether it went.
     *
     * @param overshoot the sender's {@link BeltContents#overshoot}, read while the item is its end;
     *                  a receiver with no positions ignores it
     */
    boolean offer(T payload, double overshoot);

    /** A belt's entry, which places what it is offered at the overshoot, as a {@link Join} does. */
    static <T> Outlet<T> entry(Splitter.Handoff<T> entry) {
        return (payload, overshoot) -> Join.offer(payload, overshoot, entry);
    }
}
