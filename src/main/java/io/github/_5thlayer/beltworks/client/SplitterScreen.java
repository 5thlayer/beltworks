// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.client;

import java.util.function.Supplier;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import io.github._5thlayer.beltworks.blocks.SplitterMenu;
import io.github._5thlayer.beltworks.model.Splitter;

/**
 * A splitter's screen, in Factorio's layout and vanilla's colours: the settings on top, the player's
 * inventory below. Each settings row names a setting, with a checkbox turning it on and a
 * Left/Right switch choosing its side. Input priority (#21) is the row above output priority (#20),
 * which has the filter's ghost slot (#22) at its end.
 *
 * <p>It holds no state of its own: every setting is read from the {@link SplitterMenu}, which the
 * server keeps true, and every change is a menu button or a click on the ghost slot, which the
 * server answers. Widgets are rebuilt when the menu's state changes, so a checkbox the player
 * pressed shows what the server did.
 *
 * <p>Public so the recipe viewer compat, which drops items on the filter slot, can name it.
 */
public final class SplitterScreen extends AbstractContainerScreen<SplitterMenu> {

    private static final int WIDTH = 232;
    private static final int ROW_HEIGHT = 20;
    private static final int ROWS = 2;
    private static final int TITLE_HEIGHT = 22;
    private static final int MARGIN = 6;
    private static final int SETTINGS_HEIGHT = TITLE_HEIGHT + ROWS * ROW_HEIGHT + 2 * MARGIN;
    private static final int HEIGHT = 178;
    private static final int INVENTORY_X = 35;
    // Vanilla's container colours: a light panel lit from the top left, slots sunk into it.
    private static final int PANEL = 0xFFC6C6C6;
    private static final int LIGHT = 0xFFFFFFFF;
    private static final int SHADE = 0xFF555555;
    private static final int SLOT = 0xFF8B8B8B;
    private static final int SLOT_SHADE = 0xFF373737;
    private static final int EDGE = 0xFF000000;
    private static final int LABEL = 0xFF404040;
    private static final int DIM = 0xFF8B8B8B;
    private static final int CHECKBOX = 17;

    private final Row input = new Row("screen.beltworks.splitter.input_priority", "screen.beltworks.splitter.input_side",
      menu::inputPriority, menu::inputSide, SplitterMenu.INPUT_TOGGLE, SplitterMenu.INPUT_FLIP);
    private final Row output = new Row("screen.beltworks.splitter.output_priority", "screen.beltworks.splitter.side",
      menu::outputPriority, menu::outputSide, SplitterMenu.OUTPUT_TOGGLE, SplitterMenu.OUTPUT_FLIP);
    // What the widgets were built from.
    private int built;

    public SplitterScreen(SplitterMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, WIDTH, HEIGHT);
    }

    @Override
    protected void init() {
        super.init();
        built = state();
        input.add(0);
        output.add(1);
    }

    private int state() {
        return ((menu.inputPriority().ordinal() * 3 + menu.inputSide().ordinal()) * 3 + menu.outputPriority().ordinal()) * 3
                 + menu.outputSide().ordinal();
    }

    @Override
    protected void containerTick() {
        // The server's answer to a press, or another player's change, or the first sync after opening.
        if (state() != built) rebuildWidgets();
    }

    private void press(int button) {
        if (minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        panel(graphics, leftPos, topPos, leftPos + WIDTH, topPos + HEIGHT);
        graphics.text(font, title, leftPos + 8, topPos + 8, LABEL, false);
        graphics.text(font, playerInventoryTitle, leftPos + INVENTORY_X, topPos + SETTINGS_HEIGHT + 4, LABEL, false);
        for (var slot : menu.slots) {
            var x = leftPos + slot.x;
            var y = topPos + slot.y;
            // Shaded on the top and left, lit on the bottom and right, its other two corners the slot's own grey.
            graphics.fill(x - 1, y - 1, x + 17, y + 17, SLOT_SHADE);
            graphics.fill(x, y, x + 17, y + 17, LIGHT);
            graphics.fill(x, y, x + 16, y + 16, SLOT);
            graphics.fill(x + 16, y - 1, x + 17, y, SLOT);
            graphics.fill(x - 1, y + 16, x, y + 17, SLOT);
        }
        input.label(graphics, 0);
        output.label(graphics, 1);
    }

    /**
     * A vanilla window, pixel for pixel as the inventory texture draws one: a black outline rounded
     * at its corners, two pixels lit on the top and left, two shaded on the bottom and right.
     */
    private static void panel(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1) {
        var w = x1 - x0;
        var h = y1 - y0;
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

    private static void fill(GuiGraphicsExtractor graphics, int x, int y, int left, int top, int right, int bottom, int colour) {
        graphics.fill(x + left, y + top, x + right, y + bottom, colour);
    }

    // The labels are drawn with the background, in colours that read on it.
    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        var slot = hoveredSlot;
        if (slot != null && slot.index == SplitterMenu.FILTER_SLOT && !slot.hasItem() && menu.getCarried().isEmpty()) {
            graphics.setTooltipForNextFrame(font, Component.translatable("screen.beltworks.splitter.filter"), mouseX, mouseY);
        }
    }

    /** One setting: a checkbox turning a priority on and a Left/Right switch choosing its side. */
    private final class Row {

        private static final int SWITCH_X = 142;

        private final String label;
        private final String switchLabel;
        private final Supplier<Splitter.Priority> priority;
        private final Supplier<Splitter.Priority> side;
        private final int toggle;
        private final int flip;

        Row(String label, String switchLabel, Supplier<Splitter.Priority> priority, Supplier<Splitter.Priority> side, int toggle, int flip) {
            this.label = label;
            this.switchLabel = switchLabel;
            this.priority = priority;
            this.side = side;
            this.toggle = toggle;
            this.flip = flip;
        }

        private boolean on() {
            return priority.get() != Splitter.Priority.NONE;
        }

        private int y(int index) {
            return topPos + TITLE_HEIGHT + MARGIN + index * ROW_HEIGHT;
        }

        void add(int index) {
            var y = y(index);
            var sideSwitch = new SideSwitch(this, leftPos + SWITCH_X, y + 4, Component.translatable(switchLabel));
            sideSwitch.active = on();
            // Its label is drawn with the background, as a checkbox's own is white and shadowed.
            addRenderableWidget(Checkbox.builder(Component.empty(), font)
              .pos(leftPos + 10, y)
              .selected(on())
              .onValueChange((box, selected) -> press(toggle))
              .build());
            addRenderableWidget(sideSwitch);
        }

        void label(GuiGraphicsExtractor graphics, int index) {
            var y = y(index) + 6;
            var colour = on() ? LABEL : DIM;
            graphics.text(font, Component.translatable(label), leftPos + 10 + CHECKBOX + 4, y, LABEL, false);
            var leftText = Component.translatable("screen.beltworks.splitter.left");
            graphics.text(font, leftText, leftPos + SWITCH_X - 4 - font.width(leftText), y, colour, false);
            graphics.text(font, Component.translatable("screen.beltworks.splitter.right"), leftPos + SWITCH_X + SideSwitch.WIDTH + 4, y, colour, false);
        }
    }

    /** Factorio's two-way switch: a track with its knob at the left or the right end. */
    private final class SideSwitch extends AbstractButton {

        static final int WIDTH = 24;
        static final int HEIGHT = 12;

        private final Row row;

        SideSwitch(Row row, int x, int y, Component name) {
            super(x, y, WIDTH, HEIGHT, name);
            this.row = row;
        }

        @Override
        public void onPress(InputWithModifiers input) {
            press(row.flip);
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            var x = getX();
            var y = getY();
            graphics.fill(x, y + 3, x + WIDTH, y + HEIGHT - 3, SLOT_SHADE);
            var knob = row.side.get() == Splitter.Priority.LEFT ? x : x + WIDTH - HEIGHT;
            var colour = !active ? DIM : isHoveredOrFocused() ? LIGHT : PANEL;
            graphics.fill(knob, y, knob + HEIGHT, y + HEIGHT, EDGE);
            graphics.fill(knob + 1, y + 1, knob + HEIGHT - 1, y + HEIGHT - 1, LIGHT);
            graphics.fill(knob + 2, y + 2, knob + HEIGHT - 1, y + HEIGHT - 1, SHADE);
            graphics.fill(knob + 2, y + 2, knob + HEIGHT - 2, y + HEIGHT - 2, colour);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }
}
