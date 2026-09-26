// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.blocks;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import io.github._5thlayer.beltworks.model.Support;

/** The support a belt piece shows (ADR 0012), whatever the piece. */
public final class Supports {

    /** A support and the position whose block space its legs are in. */
    public record Shown(BlockPos pos, Support support) {
    }

    private Supports() {
    }

    /**
     * The support shown with the belt piece at {@code pos}, or null where it shows none. A splitter
     * shows one for both halves, from its left half.
     */
    public static @Nullable Shown at(BlockGetter level, BlockPos pos, Support.Setting setting) {
        var state = level.getBlockState(pos);
        if (state.getBlock() instanceof SplitterBlock && state.getValue(SplitterBlock.SIDE) == SplitterBlock.Side.RIGHT) {
            pos = SplitterBlock.partner(pos, state);
            state = level.getBlockState(pos);
        }
        var support = of(level, pos, state, setting);
        return support == null ? null : new Shown(pos, support);
    }

    static @Nullable Support of(BlockGetter level, BlockPos pos, BlockState state, Support.Setting setting) {
        if (state.getBlock() instanceof BeltTileBlock) return BeltTileBlock.support(level, pos, state, setting);
        if (state.getBlock() instanceof BeltEndBlock end) return end.support(level, pos, state, setting);
        return null;
    }
}
