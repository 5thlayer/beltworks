// SPDX-FileCopyrightText: Rearth
// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: CC-BY-4.0 AND MIT

package io.github._5thlayer.beltworks.neoforge;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import io.github._5thlayer.beltworks.BlockEntitiesContent;
import io.github._5thlayer.beltworks.Beltworks;
import io.github._5thlayer.beltworks.blocks.BeltEndBlockEntity;
import io.github._5thlayer.beltworks.model.BeltContents;

/** What one of the block at {@code belt}'s belts gained and lost since the last one sent (#351). */
public record BeltChangesPayload(BlockPos belt, BeltEndBlockEntity.Track track, BeltContents.Changes<ItemStack> changes)
  implements CustomPacketPayload {

    public static final Type<BeltChangesPayload> TYPE = new Type<>(Beltworks.id("belt_changes"));

    // A float is finer than a pixel on any belt a player can lay.
    private static final StreamCodec<RegistryFriendlyByteBuf, BeltContents.Added<ItemStack>> ADDED_CODEC = StreamCodec.composite(
      ByteBufCodecs.VAR_INT, BeltContents.Added::id,
      ItemStack.STREAM_CODEC, BeltContents.Added::payload,
      ByteBufCodecs.FLOAT, added -> (float) added.position(),
      ByteBufCodecs.BOOL, BeltContents.Added::side,
      (id, payload, position, side) -> new BeltContents.Added<>(id, payload, position, side));

    private static final StreamCodec<RegistryFriendlyByteBuf, BeltContents.Changes<ItemStack>> CHANGES_CODEC = StreamCodec.composite(
      ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), BeltContents.Changes::removed,
      ADDED_CODEC.apply(ByteBufCodecs.list()), BeltContents.Changes::added,
      BeltContents.Changes::new);

    public static final StreamCodec<RegistryFriendlyByteBuf, BeltChangesPayload> STREAM_CODEC = StreamCodec.composite(
      BlockPos.STREAM_CODEC, BeltChangesPayload::belt,
      ByteBufCodecs.idMapper(id -> BeltEndBlockEntity.Track.values()[id], Enum::ordinal), BeltChangesPayload::track,
      CHANGES_CODEC, BeltChangesPayload::changes,
      BeltChangesPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(BeltChangesPayload payload, IPayloadContext context) {
        var level = context.player().level();
        if (!level.hasChunkAt(payload.belt)) return;
        level.getBlockEntity(payload.belt, BlockEntitiesContent.BELT_END.get())
          .ifPresent(belt -> belt.applyChanges(payload.track, payload.changes));
    }
}
