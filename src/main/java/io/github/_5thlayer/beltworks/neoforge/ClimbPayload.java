// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.neoforge;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import io.github._5thlayer.beltworks.Beltworks;
import io.github._5thlayer.beltworks.blocks.Climb;

import java.util.Optional;

/**
 * Climb was pressed, with the block under the crosshair if any. The client only says it was
 * pressed; the server decides what it sets.
 */
public record ClimbPayload(Optional<BlockPos> aimed) implements CustomPacketPayload {

    public static final Type<ClimbPayload> TYPE = new Type<>(Beltworks.id("climb"));

    public static final StreamCodec<ByteBuf, ClimbPayload> STREAM_CODEC = StreamCodec.composite(
      BlockPos.STREAM_CODEC.apply(ByteBufCodecs::optional), ClimbPayload::aimed,
      ClimbPayload::new);

    @Override
    public Type<ClimbPayload> type() {
        return TYPE;
    }

    public static void handle(ClimbPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) Climb.press(player, payload.aimed().orElse(null));
    }
}
