package rearth.belts.neoforge.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jetbrains.annotations.Nullable;
import rearth.belts.blocks.BeltTileBlock;
import rearth.belts.collision.BeltCollisionRegistry;
import rearth.belts.items.BeltTileItem;
import rearth.belts.items.SplitterItem;
import rearth.belts.neoforge.BeltHandPayload;

/** Holding the use button on a belt sends where the ray meets it, every tick (#350). */
final class BeltHandClient {

    private static @Nullable BlockPos held;

    private BeltHandClient() {
    }

    static void tick(ClientTickEvent.Post event) {
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
    static void interact(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isUseItem() || aim(Minecraft.getInstance()) == null) return;
        event.setSwingHand(false);
        event.setCanceled(true);
    }

    /**
     * The belt under the crosshair within reach, unless a block or an entity is nearer. A held tile
     * or splitter is never a hand, since its click places one.
     */
    private static BeltCollisionRegistry.@Nullable BeltHit aim(Minecraft minecraft) {
        var player = minecraft.player;
        if (player == null || minecraft.level == null) return null;
        var held = player.getMainHandItem().getItem();
        if (held instanceof SplitterItem || held instanceof BeltTileItem) return null;
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
