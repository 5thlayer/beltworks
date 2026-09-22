package rearth.belts.neoforge;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.network.PacketDistributor;
import rearth.belts.model.BeltContents;

public final class BeltSyncImpl {

    private BeltSyncImpl() {
    }

    public static void send(ServerLevel level, BlockPos belt, BeltContents.Changes<ItemStack> changes) {
        PacketDistributor.sendToPlayersTrackingChunk(level, ChunkPos.containing(belt), new BeltChangesPayload(belt, changes));
    }
}
