// SPDX-FileCopyrightText: Rearth
// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: CC-BY-4.0 AND MIT

package io.github._5thlayer.beltworks.neoforge.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jetbrains.annotations.Nullable;
import io.github._5thlayer.beltworks.blocks.BeltTileBlock;
import io.github._5thlayer.beltworks.collision.BeltCollisionRegistry;
import io.github._5thlayer.beltworks.items.BeltTileItem;
import io.github._5thlayer.beltworks.items.Dismantling;
import io.github._5thlayer.beltworks.items.SplitterItem;
import io.github._5thlayer.beltworks.neoforge.BeltHandPayload;

/** Holding the use button on a belt sends where the ray meets it, every tick (#350). */
public final class BeltHandClient {

    private static @Nullable BlockPos held;

    private BeltHandClient() {
    }

    public static void tick(ClientTickEvent.Post event) {
        var minecraft = Minecraft.getInstance();
        var hit = minecraft.options.keyUse.isDown() && minecraft.screen == null
                    && minecraft.player != null && !minecraft.player.isUsingItem() ? aim(minecraft) : null;

        if (held != null && (hit == null || !held.equals(hit.source()))) {
            ClientPacketDistributor.sendToServer(BeltHandPayload.release(held));
            held = null;
        }
        if (hit == null) return;
        ClientPacketDistributor.sendToServer(new BeltHandPayload(hit.source(), hit.progress()));
        held = hit.source();
    }

    // Otherwise the same press would also place the held block or use the held item past the belt.
    public static void interact(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isUseItem() || aim(Minecraft.getInstance()) == null) return;
        event.setSwingHand(false);
        event.setCanceled(true);
    }

    /**
     * The belt under the crosshair within reach, unless a block or an entity is nearer. A held tile
     * or splitter is never a hand, since its click places one, and nor is an item that dismantles
     * when sneaking or with a start stored, since its click is a dismantle's (#404).
     */
    private static BeltCollisionRegistry.@Nullable BeltHit aim(Minecraft minecraft) {
        var player = minecraft.player;
        if (player == null || minecraft.level == null) return null;
        var held = player.getMainHandItem().getItem();
        if (held instanceof SplitterItem || held instanceof BeltTileItem) return null;
        var stack = player.getMainHandItem();
        if (Dismantling.dismantles(stack) && (player.isShiftKeyDown() || Dismantling.liveStart(minecraft.level, stack) != null)) return null;
        var eye = player.getEyePosition();
        // A tile is aimed as a whole block, since its surface is too thin to aim at reliably (#396).
        if (minecraft.hitResult instanceof BlockHitResult block && block.getType() == HitResult.Type.BLOCK
              && minecraft.level.getBlockState(block.getBlockPos()).getBlock() instanceof BeltTileBlock) {
            return new BeltCollisionRegistry.BeltHit(block.getBlockPos(), 1, eye.distanceTo(block.getLocation()));
        }
        var reach = eye.add(player.getViewVector(1).scale(player.blockInteractionRange()));
        var hit = BeltCollisionRegistry.raycast(minecraft.level, eye, reach);
        if (hit == null) return null;
        var other = minecraft.hitResult;
        if (other != null && other.getType() != HitResult.Type.MISS
              && eye.distanceTo(other.getLocation()) < hit.distance()) return null;
        return hit;
    }
}
