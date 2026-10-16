// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import io.github._5thlayer.beltworks.Beltworks;
import io.github._5thlayer.beltworks.blocks.BeltTileBlock;
import io.github._5thlayer.beltworks.blocks.Climb;
import io.github._5thlayer.beltworks.items.BeltTileItem;

/**
 * A held tile's climb, a line above the action bar whenever it isn't level, so a stack left set
 * to climb is never laid unseen (ADR 0017). A line of its own, so it shows beside Groundworks'
 * height on the action bar rather than taking its place.
 */
final class ClimbHud {

    // The action bar's line, and one line above it.
    private static final int ACTION_BAR = 68;
    private static final int LINE = 12;
    private static final int COLOR = 0xFFFFFFFF;

    private ClimbHud() {
    }

    static void onRegisterLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.OVERLAY_MESSAGE, Beltworks.id("climb"), ClimbHud::render);
    }

    private static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        var minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui) return;
        var stack = minecraft.player.getMainHandItem();
        if (!(stack.getItem() instanceof BeltTileItem)) return;
        var climb = Climb.held(stack);
        if (climb == BeltTileBlock.ClimbState.LEVEL) return;
        var text = Component.translatable("message.beltworks.climb", Component.translatable("message.beltworks.climb." + climb.getSerializedName()));
        graphics.centeredText(minecraft.font, text, graphics.guiWidth() / 2, graphics.guiHeight() - ACTION_BAR - LINE - 4, COLOR);
    }
}
