package rearth.belts.neoforge;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import rearth.belts.Belts;
import rearth.belts.TileLineUpdate;
import rearth.belts.client.TileLines;
import rearth.belts.model.BeltContents;
import rearth.belts.model.BeltTier;

/** A line of tiles' {@link TileLineUpdate}, sent to the players who see it (#395). */
public record BeltLinePayload(TileLineUpdate update) implements CustomPacketPayload {

    public static final Type<BeltLinePayload> TYPE = new Type<>(Belts.id("belt_line"));

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

    private static final StreamCodec<RegistryFriendlyByteBuf, TileLineUpdate> UPDATE_CODEC = StreamCodec.composite(
      BlockPos.STREAM_CODEC, TileLineUpdate::head,
      Direction.STREAM_CODEC.apply(ByteBufCodecs.list()), TileLineUpdate::travels,
      ByteBufCodecs.VAR_INT.map(BeltTier::of, BeltTier::number).apply(ByteBufCodecs.list()), TileLineUpdate::tiers,
      ByteBufCodecs.BOOL, TileLineUpdate::ring,
      ByteBufCodecs.BOOL, TileLineUpdate::reset,
      CHANGES_CODEC, TileLineUpdate::changes,
      TileLineUpdate::new);

    public static final StreamCodec<RegistryFriendlyByteBuf, BeltLinePayload> STREAM_CODEC =
      UPDATE_CODEC.map(BeltLinePayload::new, BeltLinePayload::update);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(BeltLinePayload payload, IPayloadContext context) {
        TileLines.accept(context.player().level(), payload.update);
    }
}
