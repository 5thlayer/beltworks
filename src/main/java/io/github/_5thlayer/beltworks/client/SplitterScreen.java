// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import io.github._5thlayer.beltworks.BlockEntitiesContent;
import io.github._5thlayer.beltworks.model.Splitter;
import io.github._5thlayer.beltworks.model.SplitterSettings;
import io.github._5thlayer.beltworks.neoforge.SplitterSettingsPayload;

/**
 * A splitter's settings, in Factorio's layout: a titled panel whose rows each name a setting, a
 * checkbox turning it on and a Left/Right switch choosing its side. Input priority (#21) is the row above
 * output priority (#20). It opens on what the splitter's update tag brought, and writes only
 * through {@link SplitterSettingsPayload}.
 */
final class SplitterScreen extends Screen {

    private static final int WIDTH = 220;
    private static final int ROW_HEIGHT = 20;
    private static final int ROWS = 2;
    private static final int TITLE_HEIGHT = 22;
    private static final int MARGIN = 6;
    private static final int HEIGHT = TITLE_HEIGHT + ROWS * ROW_HEIGHT + 2 * MARGIN;
    private static final int PANEL = 0xFF313031;
    private static final int INSET = 0xFF242324;
    private static final int EDGE = 0xFF000000;
    private static final int TITLE = 0xFFFFE6C0;
    private static final int LABEL = 0xFFFFFFFF;

    private final BlockPos half;
    private final Row input;
    private final Row output;
    private int left;
    private int top;

    SplitterScreen(BlockPos half, SplitterSettings settings) {
        super(Component.translatable("screen.beltworks.splitter"));
        this.half = half;
        input = new Row("screen.beltworks.splitter.input_priority", "screen.beltworks.splitter.input_side", settings.inputPriority());
        output = new Row("screen.beltworks.splitter.output_priority", "screen.beltworks.splitter.side", settings.outputPriority());
    }

    static void open(BlockPos half) {
        var minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;
        minecraft.level.getBlockEntity(half, BlockEntitiesContent.BELT_END.get())
          .ifPresent(entity -> minecraft.setScreen(new SplitterScreen(half, entity.splitterSettings())));
    }

    @Override
    protected void init() {
        left = (width - WIDTH) / 2;
        top = (height - HEIGHT) / 2;
        input.add(0);
        output.add(1);
    }

    // Every change sends the whole record, so setting one priority never wipes the other.
    private void send() {
        ClientPacketDistributor.sendToServer(new SplitterSettingsPayload(half, input.priority, output.priority));
    }

    // Closes once the splitter is gone or out of reach.
    @Override
    public void tick() {
        var player = minecraft.player;
        if (player == null || minecraft.level == null
              || minecraft.level.getBlockEntity(half, BlockEntitiesContent.BELT_END.get()).isEmpty()
              || !player.isWithinBlockInteractionRange(half, 1)) onClose();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(left - 1, top - 1, left + WIDTH + 1, top + HEIGHT + 1, EDGE);
        graphics.fill(left, top, left + WIDTH, top + HEIGHT, PANEL);
        graphics.fill(left + MARGIN, top + TITLE_HEIGHT, left + WIDTH - MARGIN, top + HEIGHT - MARGIN, INSET);
        graphics.text(font, title, left + 8, top + 8, TITLE, false);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        input.label(graphics, 0);
        output.label(graphics, 1);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** One setting: a checkbox turning a priority on and a Left/Right switch choosing its side. */
    private final class Row {

        private static final int SWITCH_X = WIDTH - 88;

        private final String label;
        private final String switchLabel;
        private Splitter.Priority priority;
        // The side the switch shows while the checkbox is off, kept so turning it on again restores it.
        private Splitter.Priority side;

        Row(String label, String switchLabel, Splitter.Priority priority) {
            this.label = label;
            this.switchLabel = switchLabel;
            this.priority = priority;
            side = priority == Splitter.Priority.NONE ? Splitter.Priority.LEFT : priority;
        }

        private int y(int index) {
            return top + TITLE_HEIGHT + MARGIN + index * ROW_HEIGHT;
        }

        void add(int index) {
            var y = y(index);
            var sideSwitch = new SideSwitch(this, left + SWITCH_X, y + 4, Component.translatable(switchLabel));
            sideSwitch.active = priority != Splitter.Priority.NONE;
            addRenderableWidget(Checkbox.builder(Component.translatable(label), font)
              .pos(left + 10, y)
              .selected(priority != Splitter.Priority.NONE)
              .onValueChange((box, on) -> {
                  sideSwitch.active = on;
                  set(on ? side : Splitter.Priority.NONE);
              })
              .build());
            addRenderableWidget(sideSwitch);
        }

        void set(Splitter.Priority priority) {
            this.priority = priority;
            if (priority != Splitter.Priority.NONE) side = priority;
            send();
        }

        void label(GuiGraphicsExtractor graphics, int index) {
            var y = y(index) + 6;
            var dim = priority == Splitter.Priority.NONE ? 0xFF808080 : LABEL;
            var leftText = Component.translatable("screen.beltworks.splitter.left");
            graphics.text(font, leftText, left + SWITCH_X - 4 - font.width(leftText), y, dim, false);
            graphics.text(font, Component.translatable("screen.beltworks.splitter.right"), left + SWITCH_X + SideSwitch.WIDTH + 4, y, dim, false);
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
            row.set(row.side == Splitter.Priority.LEFT ? Splitter.Priority.RIGHT : Splitter.Priority.LEFT);
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            var x = getX();
            var y = getY();
            graphics.fill(x, y + 3, x + WIDTH, y + HEIGHT - 3, EDGE);
            var knob = row.side == Splitter.Priority.LEFT ? x : x + WIDTH - HEIGHT;
            var colour = !active ? 0xFF606060 : isHoveredOrFocused() ? 0xFFFFC060 : 0xFFE0E0E0;
            graphics.fill(knob, y, knob + HEIGHT, y + HEIGHT, EDGE);
            graphics.fill(knob + 1, y + 1, knob + HEIGHT - 1, y + HEIGHT - 1, colour);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }
}
