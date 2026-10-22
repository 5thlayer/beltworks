// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.neoforge;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import io.github._5thlayer.beltworks.Beltworks;
import io.github._5thlayer.beltworks.blocks.FeederBlockEntity;
import io.github._5thlayer.beltworks.model.ArmSwing;

/**
 * The feeder at {@code pos} started a half-swing or its hand changed: what the hand holds, as its
 * watchers draw it, and where the arm is, {@code elapsed} Factorio ticks into {@code phase}. Sent to
 * the players who see it, who swing the arm on from there on their own.
 */
public record FeederArmPayload(BlockPos pos, ItemStack hand, ArmSwing.Phase phase, int elapsed) implements CustomPacketPayload {

    public static final Type<FeederArmPayload> TYPE = new Type<>(Beltworks.id("feeder_arm"));

    private static final StreamCodec<RegistryFriendlyByteBuf, ArmSwing.Phase> PHASE_CODEC =
      ByteBufCodecs.VAR_INT.map(ordinal -> ArmSwing.Phase.values()[ordinal], ArmSwing.Phase::ordinal).cast();

    public static final StreamCodec<RegistryFriendlyByteBuf, FeederArmPayload> STREAM_CODEC = StreamCodec.composite(
      BlockPos.STREAM_CODEC, FeederArmPayload::pos,
      ItemStack.OPTIONAL_STREAM_CODEC, FeederArmPayload::hand,
      PHASE_CODEC, FeederArmPayload::phase,
      ByteBufCodecs.VAR_INT, FeederArmPayload::elapsed,
      FeederArmPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(FeederArmPayload payload, IPayloadContext context) {
        if (context.player().level().getBlockEntity(payload.pos) instanceof FeederBlockEntity feeder) {
            feeder.armMoved(payload.hand, payload.phase, payload.elapsed);
        }
    }
}
