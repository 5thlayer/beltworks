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
import io.github._5thlayer.beltworks.blocks.FeederReach;
import io.github._5thlayer.beltworks.model.FeederArms;

import java.util.Optional;

/**
 * Head Reach or Tail Reach was pressed, with the block under the crosshair if any. The client only
 * says what was pressed; the server decides what it lengthens.
 */
public record FeederReachPayload(FeederArms.Arm arm, Optional<BlockPos> aimed) implements CustomPacketPayload {

    public static final Type<FeederReachPayload> TYPE = new Type<>(Beltworks.id("feeder_reach"));

    public static final StreamCodec<ByteBuf, FeederReachPayload> STREAM_CODEC = StreamCodec.composite(
      ByteBufCodecs.BOOL.map(head -> head ? FeederArms.Arm.HEAD : FeederArms.Arm.TAIL, arm -> arm == FeederArms.Arm.HEAD),
      FeederReachPayload::arm,
      BlockPos.STREAM_CODEC.apply(ByteBufCodecs::optional), FeederReachPayload::aimed,
      FeederReachPayload::new);

    @Override
    public Type<FeederReachPayload> type() {
        return TYPE;
    }

    public static void handle(FeederReachPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) FeederReach.press(player, payload.aimed().orElse(null), payload.arm());
    }
}
