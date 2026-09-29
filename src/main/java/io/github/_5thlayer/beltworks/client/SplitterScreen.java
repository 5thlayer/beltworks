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
import io.github._5thlayer.beltworks.neoforge.SplitterSettingsPayload;

/**
 * A splitter's settings, in Factorio's layout: a titled panel whose rows each name a setting, a
 * checkbox turning it on and a Left/Right switch choosing its side. For now it holds only the
 * output-priority row (#20). It opens on what the splitter's update tag brought, and writes only
 * through {@link SplitterSettingsPayload}.
 */
final class SplitterScreen extends Screen {

    private static final int WIDTH = 220;
    private static final int HEIGHT = 58;
    private static final int PANEL = 0xFF313031;
    private static final int INSET = 0xFF242324;
    private static final int EDGE = 0xFF000000;
    private static final int TITLE = 0xFFFFE6C0;
    private static final int LABEL = 0xFFFFFFFF;

    private final BlockPos half;
    private Splitter.Priority priority;
    // The side the switch shows while the checkbox is off, kept so turning it on again restores it.
    private Splitter.Priority side;
    private int left;
    private int top;

    SplitterScreen(BlockPos half, Splitter.Priority priority) {
        super(Component.translatable("screen.beltworks.splitter"));
        this.half = half;
        this.priority = priority;
        side = priority == Splitter.Priority.NONE ? Splitter.Priority.LEFT : priority;
    }

    static void open(BlockPos half) {
        var minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;
        minecraft.level.getBlockEntity(half, BlockEntitiesContent.BELT_END.get())
          .ifPresent(entity -> minecraft.setScreen(new SplitterScreen(half, entity.splitterSettings().outputPriority())));
    }

    @Override
    protected void init() {
        left = (width - WIDTH) / 2;
        top = (height - HEIGHT) / 2;
        var row = top + 28;
        var sideSwitch = new SideSwitch(left + WIDTH - 88, row + 4);
        sideSwitch.active = priority != Splitter.Priority.NONE;
        addRenderableWidget(Checkbox.builder(Component.translatable("screen.beltworks.splitter.output_priority"), font)
          .pos(left + 10, row)
          .selected(priority != Splitter.Priority.NONE)
          .onValueChange((box, on) -> {
              sideSwitch.active = on;
              set(on ? side : Splitter.Priority.NONE);
          })
          .build());
        addRenderableWidget(sideSwitch);
    }

    private void set(Splitter.Priority priority) {
        this.priority = priority;
        if (priority != Splitter.Priority.NONE) side = priority;
        ClientPacketDistributor.sendToServer(new SplitterSettingsPayload(half, priority));
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
        graphics.fill(left + 6, top + 22, left + WIDTH - 6, top + HEIGHT - 6, INSET);
        graphics.text(font, title, left + 8, top + 8, TITLE, false);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        var y = top + 34;
        var dim = priority == Splitter.Priority.NONE ? 0xFF808080 : LABEL;
        graphics.text(font, Component.translatable("screen.beltworks.splitter.left"), left + WIDTH - 88 - 4 - font.width(Component.translatable("screen.beltworks.splitter.left")), y, dim, false);
        graphics.text(font, Component.translatable("screen.beltworks.splitter.right"), left + WIDTH - 88 + SideSwitch.WIDTH + 4, y, dim, false);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** Factorio's two-way switch: a track with its knob at the left or the right end. */
    private final class SideSwitch extends AbstractButton {

        static final int WIDTH = 24;
        static final int HEIGHT = 12;

        SideSwitch(int x, int y) {
            super(x, y, WIDTH, HEIGHT, Component.translatable("screen.beltworks.splitter.side"));
        }

        @Override
        public void onPress(InputWithModifiers input) {
            set(side == Splitter.Priority.LEFT ? Splitter.Priority.RIGHT : Splitter.Priority.LEFT);
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            var x = getX();
            var y = getY();
            graphics.fill(x, y + 3, x + WIDTH, y + HEIGHT - 3, EDGE);
            var knob = side == Splitter.Priority.LEFT ? x : x + WIDTH - HEIGHT;
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
