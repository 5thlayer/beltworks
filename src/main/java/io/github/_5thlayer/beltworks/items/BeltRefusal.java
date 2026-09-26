// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.items;

import io.github._5thlayer.groundworks.Refusal;

/**
 * The Mod's refusals in the Groundworks library's plans: a tile's, a stretch's legs' and a
 * splitter's (ADR 0010). Groundworks tells the player its own, such as a stretch behind the look or
 * one the inventory can't pay for.
 */
public enum BeltRefusal implements Refusal {
    BLOCKED("message.beltworks.stretch_blocked"),
    CROSSES_A_LINE("message.beltworks.stretch_crosses_a_line"),
    SLOPE_TURNS("message.beltworks.slope_turns"),
    WEDGE_BLOCKED("message.beltworks.wedge_blocked");

    private final String messageKey;

    BeltRefusal(String messageKey) {
        this.messageKey = messageKey;
    }

    public String messageKey() {
        return messageKey;
    }
}
