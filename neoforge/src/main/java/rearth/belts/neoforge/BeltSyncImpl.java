package rearth.belts.neoforge;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.network.PacketDistributor;
import rearth.belts.TileLineUpdate;
import rearth.belts.blocks.ChuteBlockEntity;
import rearth.belts.model.BeltContents;

import java.util.Collection;
import java.util.LinkedHashSet;

public final class BeltSyncImpl {

    private BeltSyncImpl() {
    }

    public static void send(ServerLevel level, BlockPos belt, ChuteBlockEntity.Track track, BeltContents.Changes<ItemStack> changes) {
        PacketDistributor.sendToPlayersTrackingChunk(level, ChunkPos.containing(belt), new BeltChangesPayload(belt, track, changes));
    }

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
