// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks;

import java.util.function.Consumer;

import net.minecraft.core.BlockPos;

/**
 * Hears each block update a server level sends to its clients, which nothing else observes on a
 * server. Nobody listens unless something sets a listener, and then the cost of each update is a
 * null check.
 */
public final class BlockUpdatesSent {

    private static Consumer<BlockPos> listener;

    private BlockUpdatesSent() {
    }

    /** Hands each block update sent from now on to {@code listener}, in place of any listener before it. */
    public static void listen(Consumer<BlockPos> listener) {
        BlockUpdatesSent.listener = listener;
    }

    /** Called by {@code ServerLevelUpdateMixin} as a server level sends the update at {@code pos}. */
    public static void sent(BlockPos pos) {
        Consumer<BlockPos> heard = listener;
        if (heard != null) heard.accept(pos);
    }
}
