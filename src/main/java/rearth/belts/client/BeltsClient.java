// SPDX-FileCopyrightText: Rearth
// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: CC-BY-4.0 AND MIT

package rearth.belts.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import rearth.belts.collision.BeltCollisionRegistry;

public final class BeltsClient {
    
    /** Runs at the start of each client level tick, before its entities move. */
    public static void tick(ClientLevel level) {
        var player = Minecraft.getInstance().player;
        if (player != null) BeltCollisionRegistry.moveLocalPlayer(level, player);
        TileLines.tick(level);
    }
    
}
