// SPDX-FileCopyrightText: Rearth
// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: CC-BY-4.0 AND MIT

package io.github._5thlayer.beltworks;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import io.github._5thlayer.beltworks.blocks.BeltEndBlockEntity;
import io.github._5thlayer.beltworks.model.BeltContents;
import io.github._5thlayer.beltworks.neoforge.BeltChangesPayload;
import io.github._5thlayer.beltworks.neoforge.BeltLinePayload;

import java.util.Collection;
import java.util.LinkedHashSet;

public final class BeltSync {

    private BeltSync() {
    }

    /** Sends what one of the block at {@code belt}'s belts gained and lost to every player who sees it. */
    public static void send(ServerLevel level, BlockPos belt, BeltEndBlockEntity.Track track, BeltContents.Changes<ItemStack> changes) {
        PacketDistributor.sendToPlayersTrackingChunk(level, ChunkPos.containing(belt), new BeltChangesPayload(belt, track, changes));
    }

    /** Sends a line of tiles' update to every player who sees any chunk the line crosses (#395). */
    public static void sendLine(ServerLevel level, Collection<ChunkPos> chunks, TileLineUpdate update) {
        // Once per player, however many of the line's chunks they see.
        var players = new LinkedHashSet<ServerPlayer>();
        for (var chunk : chunks) players.addAll(level.getChunkSource().chunkMap.getPlayers(chunk, false));
        if (players.isEmpty()) return;
        var payload = new BeltLinePayload(update);
        for (var player : players) PacketDistributor.sendToPlayer(player, payload);
    }

    public static void sendLine(ServerPlayer player, TileLineUpdate update) {
        PacketDistributor.sendToPlayer(player, new BeltLinePayload(update));
    }
}
