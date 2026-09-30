// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.neoforge;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import io.github._5thlayer.beltworks.Beltworks;
import io.github._5thlayer.beltworks.blocks.SplitterMenu;

/**
 * An item a recipe viewer dropped on the filter slot of the splitter menu {@code containerId} (#22).
 * A click on the slot needs no packet of ours: the server reads the stack it holds on the cursor.
 * A drop from a viewer's list has no cursor stack to read, so the client names the item. It is only
 * ever a template to match against, copied at one, and it takes the same path as a click.
 */
public record SplitterFilterPayload(int containerId, ItemStack filter) implements CustomPacketPayload {

    public static final Type<SplitterFilterPayload> TYPE = new Type<>(Beltworks.id("splitter_filter"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SplitterFilterPayload> STREAM_CODEC = StreamCodec.composite(
      ByteBufCodecs.VAR_INT, SplitterFilterPayload::containerId,
      ItemStack.OPTIONAL_STREAM_CODEC, SplitterFilterPayload::filter,
      SplitterFilterPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SplitterFilterPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || player.isSpectator()) return;
        // Only the menu the player has open, and only while it is still valid: a packet cannot reach any other splitter.
        if (player.containerMenu instanceof SplitterMenu menu && menu.containerId == payload.containerId && menu.stillValid(player)) {
            menu.setFilter(payload.filter);
        }
    }
}
