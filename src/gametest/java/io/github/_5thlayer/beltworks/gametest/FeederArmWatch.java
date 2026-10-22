// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.gametest;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.BlockPos;

/** Counts the messages about its arm each feeder a GameTest watches sends, as {@link BlockUpdateWatch} counts block updates. */
public final class FeederArmWatch {

    private static final Map<BlockPos, Integer> COUNTS = new ConcurrentHashMap<>();

    private FeederArmWatch() {
    }

    static void watch(BlockPos pos) {
        COUNTS.put(pos.immutable(), 0);
    }

    /** Stops watching the position, returning the messages sent for it since {@link #watch}. */
    static int stop(BlockPos pos) {
        Integer count = COUNTS.remove(pos);
        return count == null ? 0 : count;
    }

    /** Hears every feeder's arm sent, from {@link BeltGameTests#register} on. */
    static void saw(BlockPos pos) {
        if (!COUNTS.isEmpty()) COUNTS.computeIfPresent(pos, (watched, count) -> count + 1);
    }
}
