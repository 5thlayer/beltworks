// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.inventory.AbstractContainerMenu;

/**
 * Vanilla's container window, drawn in code for the splitter's and the creative crate's screens: a
 * light panel lit from the top left, with its slots sunk into it, in vanilla's own colours.
 */
final class ContainerPanel {

    static final int PANEL = 0xFFC6C6C6;
    static final int LIGHT = 0xFFFFFFFF;
    static final int SHADE = 0xFF555555;
    static final int SLOT = 0xFF8B8B8B;
    static final int SLOT_SHADE = 0xFF373737;
    static final int EDGE = 0xFF000000;
    static final int LABEL = 0xFF404040;

    private ContainerPanel() {
    }

    /**
     * A window with every one of the menu's slots, at {@code left} and {@code top} on the screen and
     * {@code width} by {@code height}.
     */
    static void draw(GuiGraphicsExtractor graphics, AbstractContainerMenu menu, int left, int top, int width, int height) {
        panel(graphics, left, top, width, height);
        for (var slot : menu.slots) slot(graphics, left + slot.x, top + slot.y);
    }

    /**
     * A vanilla window, pixel for pixel as the inventory texture draws one: a black outline rounded
     * at its corners, two pixels lit on the top and left, two shaded on the bottom and right.
     */
    private static void panel(GuiGraphicsExtractor graphics, int x0, int y0, int w, int h) {
        fill(graphics, x0, y0, 3, 3, w - 3, h - 3, PANEL);
        fill(graphics, x0, y0, w - 3, 2, w - 2, 3, PANEL);
        fill(graphics, x0, y0, 2, h - 3, 3, h - 2, PANEL);
        fill(graphics, x0, y0, 2, 1, w - 3, 2, LIGHT);
        fill(graphics, x0, y0, 1, 2, w - 3, 3, LIGHT);
        fill(graphics, x0, y0, 1, 3, 4, 4, LIGHT);
        fill(graphics, x0, y0, 1, 4, 3, h - 3, LIGHT);
        fill(graphics, x0, y0, w - 3, 3, w - 1, h - 3, SHADE);
        fill(graphics, x0, y0, w - 4, h - 4, w - 3, h - 3, SHADE);
        fill(graphics, x0, y0, 3, h - 3, w - 1, h - 2, SHADE);
        fill(graphics, x0, y0, 3, h - 2, w - 2, h - 1, SHADE);
        fill(graphics, x0, y0, 2, 0, w - 3, 1, EDGE);
        fill(graphics, x0, y0, 1, 1, 2, 2, EDGE);
        fill(graphics, x0, y0, 0, 2, 1, h - 3, EDGE);
        fill(graphics, x0, y0, 1, h - 3, 2, h - 2, EDGE);
        fill(graphics, x0, y0, 2, h - 2, 3, h - 1, EDGE);
        fill(graphics, x0, y0, 3, h - 1, w - 2, h, EDGE);
        fill(graphics, x0, y0, w - 3, 1, w - 2, 2, EDGE);
        fill(graphics, x0, y0, w - 2, 2, w - 1, 3, EDGE);
        fill(graphics, x0, y0, w - 1, 3, w, h - 2, EDGE);
        fill(graphics, x0, y0, w - 2, h - 2, w - 1, h - 1, EDGE);
    }

    /** A slot whose item sits at {@code x} and {@code y}: shaded on the top and left, lit on the bottom and right, its other two corners the slot's own grey. */
    private static void slot(GuiGraphicsExtractor graphics, int x, int y) {
        graphics.fill(x - 1, y - 1, x + 17, y + 17, SLOT_SHADE);
        graphics.fill(x, y, x + 17, y + 17, LIGHT);
        graphics.fill(x, y, x + 16, y + 16, SLOT);
        graphics.fill(x + 16, y - 1, x + 17, y, SLOT);
        graphics.fill(x - 1, y + 16, x, y + 17, SLOT);
    }

    private static void fill(GuiGraphicsExtractor graphics, int x, int y, int left, int top, int right, int bottom, int colour) {
        graphics.fill(x + left, y + top, x + right, y + bottom, colour);
    }
}
