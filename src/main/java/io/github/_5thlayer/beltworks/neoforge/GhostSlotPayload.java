// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.neoforge;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import io.github._5thlayer.beltworks.Beltworks;
import io.github._5thlayer.beltworks.blocks.GhostSlotMenu;

/**
 * An item a recipe viewer dropped on the ghost slot of the menu {@code containerId}: a splitter's
 * filter or a creative crate's item (#22, #125). A click on the slot needs no packet of ours: the
 * server reads the stack it holds on the cursor. A drop from a viewer's list has no cursor stack to
 * read, so the client names the item. It is only ever a template, copied at one, and it takes the
 * same path as a click.
 */
public record GhostSlotPayload(int containerId, ItemStack stack) implements CustomPacketPayload {

    public static final Type<GhostSlotPayload> TYPE = new Type<>(Beltworks.id("ghost_slot"));

    public static final StreamCodec<RegistryFriendlyByteBuf, GhostSlotPayload> STREAM_CODEC = StreamCodec.composite(
      ByteBufCodecs.VAR_INT, GhostSlotPayload::containerId,
      ItemStack.OPTIONAL_STREAM_CODEC, GhostSlotPayload::stack,
      GhostSlotPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(GhostSlotPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) payload.receive(player);
    }

    /**
     * Sets the ghost slot of the menu {@code player} has open, if it is the menu named and still
     * valid: a packet cannot reach any other splitter or crate. A spectator sets nothing.
     */
    public void receive(Player player) {
        if (player.isSpectator()) return;
        var menu = player.containerMenu;
        if (menu instanceof GhostSlotMenu ghost && menu.containerId == containerId && menu.stillValid(player)) {
            ghost.setGhostItem(stack);
        }
    }
}
