// SPDX-FileCopyrightText: Rearth
// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: CC-BY-4.0 AND MIT

package rearth.belts.neoforge;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import rearth.belts.BlockEntitiesContent;
import rearth.belts.Belts;

/**
 * A player's hand on the splitter half at {@code belt}, at a fraction of its block of belt, or on
 * the belt tile there, resent each tick the use button is held; a negative progress lets go (#350).
 */
public record BeltHandPayload(BlockPos belt, double progress) implements CustomPacketPayload {

    public static final Type<BeltHandPayload> TYPE = new Type<>(Belts.id("belt_hand"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BeltHandPayload> STREAM_CODEC = StreamCodec.composite(
      BlockPos.STREAM_CODEC, BeltHandPayload::belt,
      ByteBufCodecs.DOUBLE, BeltHandPayload::progress,
      BeltHandPayload::new);

    public static BeltHandPayload release(BlockPos belt) {
        return new BeltHandPayload(belt, -1);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(BeltHandPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        var level = player.level();
        // Checked first, so a packet cannot make the server load a chunk.
        if (!level.hasChunkAt(payload.belt)) return;
        level.getBlockEntity(payload.belt, BlockEntitiesContent.BELT_TILE.get()).ifPresent(tile -> {
            if (payload.progress < 0) {
                tile.releaseHand(player);
            } else {
                tile.holdHand(player);
            }
        });
        level.getBlockEntity(payload.belt, BlockEntitiesContent.CHUTE_BLOCK.get()).ifPresent(belt -> {
            if (payload.progress < 0) {
                belt.releaseHand(player);
            } else {
                belt.holdHand(player, payload.progress);
            }
        });
    }
}
