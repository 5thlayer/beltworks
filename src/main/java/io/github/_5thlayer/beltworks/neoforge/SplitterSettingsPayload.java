// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.neoforge;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ByIdMap;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import io.github._5thlayer.beltworks.Beltworks;
import io.github._5thlayer.beltworks.BlockEntitiesContent;
import io.github._5thlayer.beltworks.model.Splitter;

/** The output priority a player set on the splitter one half of which is at {@code half}, from its screen (#20). */
public record SplitterSettingsPayload(BlockPos half, Splitter.Priority outputPriority) implements CustomPacketPayload {

    public static final Type<SplitterSettingsPayload> TYPE = new Type<>(Beltworks.id("splitter_settings"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SplitterSettingsPayload> STREAM_CODEC = StreamCodec.composite(
      BlockPos.STREAM_CODEC, SplitterSettingsPayload::half,
      ByteBufCodecs.idMapper(ByIdMap.continuous(Splitter.Priority::ordinal, Splitter.Priority.values(), ByIdMap.OutOfBoundsStrategy.ZERO), Splitter.Priority::ordinal), SplitterSettingsPayload::outputPriority,
      SplitterSettingsPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SplitterSettingsPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        var level = player.level();
        // Checked first, so a packet cannot make the server load a chunk.
        if (!level.hasChunkAt(payload.half) || !player.isWithinBlockInteractionRange(payload.half, 1)) return;
        level.getBlockEntity(payload.half, BlockEntitiesContent.BELT_END.get())
          .ifPresent(half -> half.setOutputPriority(payload.outputPriority));
    }
}
