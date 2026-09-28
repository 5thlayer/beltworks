// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.neoforge;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import io.github._5thlayer.beltworks.Beltworks;
import io.github._5thlayer.beltworks.blocks.FeederBlockEntity;

/** The feeder at {@code pos} took {@code item} with its head, sent to the players who see it so they draw it sucked in. */
public record FeederSuckedPayload(BlockPos pos, ItemStack item) implements CustomPacketPayload {

    public static final Type<FeederSuckedPayload> TYPE = new Type<>(Beltworks.id("feeder_sucked"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FeederSuckedPayload> STREAM_CODEC = StreamCodec.composite(
      BlockPos.STREAM_CODEC, FeederSuckedPayload::pos,
      ItemStack.STREAM_CODEC, FeederSuckedPayload::item,
      FeederSuckedPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(FeederSuckedPayload payload, IPayloadContext context) {
        if (context.player().level().getBlockEntity(payload.pos) instanceof FeederBlockEntity feeder) feeder.sucked(payload.item);
    }
}
