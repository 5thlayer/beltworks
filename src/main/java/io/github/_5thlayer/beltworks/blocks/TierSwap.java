// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.blocks;

import net.minecraft.core.BlockPos;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * A tier swap's handoff (GLOSSARY.md): a block put in the place of one of the same kind and another
 * tier keeps what the old one held, whoever called {@code setBlock}. The chunk makes the new block's
 * entity right after removing the old one's, within the one {@code setBlock}, so the old one parks
 * what it held here as it is removed, and the new one takes it as it joins the level.
 */
final class TierSwap {

    private record Parcel(Level level, BlockPos pos, long gameTime, Consumer<BlockEntity> deliver, Runnable spill) {
    }

    // Parked and taken within the one setBlock, on the server thread. A splitter half's parcel waits
    // while its partner, swapped from within the half's removal, parks and takes its own.
    private static final List<Parcel> PARKED = new ArrayList<>();

    private TierSwap() {
    }

    /**
     * Whether {@code now}, put in the place of {@code was}, is a block of the same kind and another
     * tier. A kind is a block class with one block per tier: a tile, a loader, a splitter half or a
     * feeder.
     */
    static boolean swaps(BlockState was, BlockState now) {
        return now.getBlock() != was.getBlock() && now.getBlock().getClass() == was.getBlock().getClass();
    }

    /**
     * Parks everything {@code from} saves for the block entity replacing it, which loads it as it
     * would a save: what it cannot hold, such as energy past its tier's buffer, it drops as a load
     * does. {@code spill} runs instead if no block entity takes it.
     */
    static void parkSaved(BlockEntity from, BlockPos pos, Runnable spill) {
        var level = from.getLevel();
        var registries = level.registryAccess();
        var saved = from.saveCustomOnly(registries);
        park(level, pos, BlockEntity.class, to -> to.loadCustomOnly(TagValueInput.create(ProblemReporter.DISCARDING, registries, saved)), spill);
    }

    /** Parks what a block entity of {@code type} replacing the one at {@code pos} is handed; {@code spill} runs if none takes it. */
    static <T extends BlockEntity> void park(Level level, BlockPos pos, Class<T> type, Consumer<T> deliver, Runnable spill) {
        if (level.isClientSide()) return;
        // A parcel from an earlier tick, or one at this place, was never taken: a setBlock that made
        // no block entity, or none of its kind, swapped its block.
        var stale = new ArrayList<Parcel>();
        PARKED.removeIf(parcel -> {
            var left = parcel.level().getGameTime() != parcel.gameTime() || parcel.level() == level && parcel.pos().equals(pos);
            if (left) stale.add(parcel);
            return left;
        });
        PARKED.add(new Parcel(level, pos.immutable(), level.getGameTime(), to -> {
            if (type.isInstance(to)) deliver.accept(type.cast(to));
            else spill.run();
        }, spill));
        for (var parcel : stale) parcel.spill().run();
    }

    /** Hands {@code to}, as it joins its level, what the block entity it replaced parked, if any, and answers whether it did. */
    static boolean take(BlockEntity to) {
        var level = to.getLevel();
        if (level == null || level.isClientSide() || PARKED.isEmpty()) return false;
        for (var parcels = PARKED.iterator(); parcels.hasNext(); ) {
            var parcel = parcels.next();
            if (parcel.level() != level || !parcel.pos().equals(to.getBlockPos())) continue;
            parcels.remove();
            parcel.deliver().accept(to);
            return true;
        }
        return false;
    }
}
