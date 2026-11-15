// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/** What the player's crosshair rests on, for the marks a placed block shows only while looked at. */
public final class Crosshair {

    private Crosshair() {
    }

    /** Whether the crosshair rests on one of {@code positions}, with the HUD showing. */
    public static boolean on(BlockPos... positions) {
        var minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui) return false;
        if (!(minecraft.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return false;
        for (var pos : positions) if (hit.getBlockPos().equals(pos)) return true;
        return false;
    }
}
