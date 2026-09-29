// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.model;

import org.jetbrains.annotations.Nullable;

import java.util.function.BiPredicate;
import java.util.function.Predicate;

/**
 * One player's hold on a belt piece's hand: it lapses unless resent, dies with its player, and
 * once the inventory refuses an item stops taking until it is released (#350, #396). The latest
 * holder takes it over.
 */
public final class HeldHand<P> {

    // The client resends a hold every tick the button is down, so a hold it stopped sending lapses.
    public static final int LAPSE_TICKS = 5;

    private @Nullable Hold<P> hold;

    public void hold(P player, long now) {
        var taking = hold == null || hold.player != player || hold.taking;
        hold = new Hold<>(player, now + LAPSE_TICKS, taking);
    }

    public void release(P player) {
        if (hold != null && hold.player == player) hold = null;
    }

    /**
     * The hand taking at {@code point} now, or null where there is no live hold or it stopped
     * taking. {@code into} puts an item in the player's inventory and answers whether it did.
     */
    public <T> BeltContents.@Nullable Hand<T> hand(double point, long now, Predicate<P> alive, BiPredicate<P, T> into) {
        var held = hold;
        if (held == null) return null;
        if (now > held.until || !alive.test(held.player)) {
            hold = null;
            return null;
        }
        if (!held.taking) return null;
        return new BeltContents.Hand<>(point, item -> {
            if (into.test(held.player, item)) return true;
            hold = new Hold<>(held.player, held.until, false);
            return false;
        });
    }

    private record Hold<P>(P player, long until, boolean taking) {
    }
}
