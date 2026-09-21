package rearth.belts.neoforge.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jetbrains.annotations.Nullable;
import rearth.belts.collision.BeltCollisionRegistry;
import rearth.belts.neoforge.BeltHandPayload;

/** Holding the use button on a belt's curve sends where the ray meets it, every tick (#350). */
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

    /** The belt under the crosshair within reach, unless a block or an entity is nearer. */
    private static BeltCollisionRegistry.@Nullable BeltHit aim(Minecraft minecraft) {
        var player = minecraft.player;
        if (player == null || minecraft.level == null) return null;
        var eye = player.getEyePosition();
        var reach = eye.add(player.getViewVector(1).scale(player.blockInteractionRange()));
        var hit = BeltCollisionRegistry.raycast(minecraft.level, eye, reach);
        if (hit == null) return null;
        var other = minecraft.hitResult;
        if (other != null && other.getType() != HitResult.Type.MISS
              && eye.distanceTo(other.getLocation()) < hit.distance()) return null;
        return hit;
    }
}
