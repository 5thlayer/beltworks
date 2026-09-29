// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeldHandTest {

    private final List<String> taken = new ArrayList<>();

    private boolean into(String player, String item) {
        taken.add(player + ":" + item);
        return true;
    }

    private static boolean full(String player, String item) {
        return false;
    }

    @Test
    void aHoldTakesAtThePointItIsAskedAt() {
        var held = new HeldHand<String>();
        held.hold("alex", 0);

        var hand = held.hand(0.25, 0, player -> true, this::into);

        assertNotNull(hand);
        assertEquals(0.25, hand.point());
        assertTrue(hand.taker().test("stone"));
        assertEquals(List.of("alex:stone"), taken);
    }

    @Test
    void noHoldNoHand() {
        assertNull(new HeldHand<String>().hand(0, 0, player -> true, this::into));
    }

    @Test
    void aHoldLapsesWhenItIsNotResent() {
        var held = new HeldHand<String>();
        held.hold("alex", 0);

        assertNotNull(held.hand(0, HeldHand.LAPSE_TICKS, player -> true, this::into));
        assertNull(held.hand(0, HeldHand.LAPSE_TICKS + 1, player -> true, this::into));
    }

    @Test
    void aHoldDiesWithItsPlayer() {
        var held = new HeldHand<String>();
        held.hold("alex", 0);

        assertNull(held.hand(0, 0, player -> false, this::into));
        assertNull(held.hand(0, 0, player -> true, this::into));
    }

    @Test
    void aFullInventoryStopsTakingUntilReleased() {
        var held = new HeldHand<String>();
        held.hold("alex", 0);

        assertFalse(held.hand(0, 0, player -> true, HeldHandTest::full).taker().test("stone"));
        held.hold("alex", 1);
        assertNull(held.hand(0, 1, player -> true, this::into));

        held.release("alex");
        held.hold("alex", 2);
        assertNotNull(held.hand(0, 2, player -> true, this::into));
    }

    @Test
    void anotherPlayerTakesOverAndTakesAgain() {
        var held = new HeldHand<String>();
        held.hold("alex", 0);
        held.hand(0, 0, player -> true, HeldHandTest::full).taker().test("stone");

        held.hold("sam", 1);
        held.hand(0, 1, player -> true, this::into).taker().test("stone");

        assertEquals(List.of("sam:stone"), taken);
    }

    @Test
    void onlyTheHolderReleases() {
        var held = new HeldHand<String>();
        held.hold("alex", 0);

        held.release("sam");
        assertNotNull(held.hand(0, 0, player -> true, this::into));
        held.release("alex");
        assertNull(held.hand(0, 0, player -> true, this::into));
    }
}
