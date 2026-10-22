// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks;

import java.util.function.Consumer;

import net.minecraft.core.BlockPos;

/**
 * Hears each message a feeder sends its watchers about its arm, whether or not anyone watches it.
 * Nobody listens unless something sets a listener, and then the cost of each message is a null check.
 */
public final class FeederArmsSent {

    private static Consumer<BlockPos> listener;

    private FeederArmsSent() {
    }

    /** Hands the position of each feeder sending from now on to {@code listener}, in place of any listener before it. */
    public static void listen(Consumer<BlockPos> listener) {
        FeederArmsSent.listener = listener;
    }

    /** Called by a feeder at {@code pos} as it sends its arm. */
    public static void sent(BlockPos pos) {
        Consumer<BlockPos> heard = listener;
        if (heard != null) heard.accept(pos);
    }
}
