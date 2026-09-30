// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.blocks;

import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetPlayerInventoryPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import io.github._5thlayer.beltworks.BlockEntitiesContent;

/**
 * Factorio's drop onto a belt, by vanilla's Q (#91): a player aiming at a belt tile within their
 * block interaction range puts one item from the selected slot into the tile's line at the aimed
 * point, and nothing is thrown. Everything else is vanilla's.
 */
public final class BeltDrop {

    /** Why a drop onto a tile with no gap is refused. */
    public static final String FULL = "message.beltworks.drop_full";

    private BeltDrop() {
    }

    /**
     * Drops one item from the selected slot onto the tile the player aims at, when they aim at one.
     * The aim is the player's own look, cast on the server, never a position the client sent.
     *
     * @return whether the drop was answered, by taking the item or by refusing it; false leaves
     *   the drop to vanilla, which throws it
     */
    public static boolean dropOnto(ServerPlayer player) {
        var stack = player.getInventory().getSelectedItem();
        if (stack.isEmpty() || !(player.level() instanceof ServerLevel level)) return false;

        var eye = player.getEyePosition();
        var look = player.getViewVector(1).scale(player.blockInteractionRange());
        var end = eye.add(look);
        var hit = level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        if (hit.getType() != HitResult.Type.BLOCK) return false;
        if (level.getBlockEntity(hit.getBlockPos(), BlockEntitiesContent.BELT_TILE.get()).orElse(null) instanceof BeltTileBlockEntity tile) {
            return dropOnto(player, stack, tile, hit, eye, end);
        }
        return false;
    }

    private static boolean dropOnto(ServerPlayer player, ItemStack stack, BeltTileBlockEntity tile,
                                    BlockHitResult hit, Vec3 eye, Vec3 end) {
        // Something nearer than the tile is what is aimed at, and it is vanilla's.
        var box = player.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1);
        var nearer = ProjectileUtil.getEntityHitResult(player, eye, end, box, EntitySelector.CAN_BE_PICKED, eye.distanceToSqr(hit.getLocation()));
        if (nearer != null) return false;
        // A tile no line holds yet, just placed, is left to vanilla's throw.
        if (tile.line() == null) return false;

        var centre = tile.getBlockPos().getCenter();
        var offset = tile.shape().project(hit.getLocation().x - centre.x, hit.getLocation().z - centre.z, BeltTileBlock.travel(tile.travel()));
        var inventory = player.getInventory();
        if (tile.dropOnto(stack.copyWithCount(1), offset)) {
            inventory.removeFromSelected(false);
            // As vanilla's Q does: the client has already taken the item from its copy of the slot.
            var slot = inventory.getSelectedSlot();
            player.containerMenu.findSlot(inventory, slot).ifPresent(at -> player.containerMenu.setRemoteSlot(at, inventory.getSelectedItem()));
            if (player.getUseItem().isEmpty()) player.stopUsingItem();
            return true;
        }
        // Vanilla's client took the item from its copy before asking, so the slot is sent back as it is.
        player.connection.send(new ClientboundSetPlayerInventoryPacket(inventory.getSelectedSlot(), stack.copy()));
        player.sendSystemMessage(Component.translatable(FULL), true);
        return true;
    }
}
