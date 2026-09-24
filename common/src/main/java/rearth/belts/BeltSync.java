// SPDX-FileCopyrightText: Rearth
// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: CC-BY-4.0 AND MIT

package rearth.belts;

import dev.architectury.injectables.annotations.ExpectPlatform;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.item.ItemStack;
import rearth.belts.blocks.ChuteBlockEntity;
import rearth.belts.model.BeltContents;

import java.util.Collection;

public final class BeltSync {

    private BeltSync() {
    }

    /** Sends what one of the block at {@code belt}'s belts gained and lost to every player who sees it. */
    @ExpectPlatform
    public static void send(ServerLevel level, BlockPos belt, ChuteBlockEntity.Track track, BeltContents.Changes<ItemStack> changes) {
        throw new AssertionError();
    }

    /** Sends a line of tiles' update to every player who sees any chunk the line crosses (#395). */
    @ExpectPlatform
    public static void sendLine(ServerLevel level, Collection<ChunkPos> chunks, TileLineUpdate update) {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static void sendLine(ServerPlayer player, TileLineUpdate update) {
        throw new AssertionError();
    }
}
