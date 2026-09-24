// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.items;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import io.github._5thlayer.beltworks.model.Dismantle;

import java.util.List;

/**
 * What a sneak-click with a stored start would take up (PlanetaryFactory #404): the tiles of the
 * span, start first, the wedges under them, or why it takes up nothing. The click executes it and
 * the preview draws it.
 */
public record DismantlePlan(List<BlockPos> tiles, List<BlockPos> wedges, Dismantle.@Nullable Refusal refusal) {

    public DismantlePlan {
        tiles = List.copyOf(tiles);
        wedges = List.copyOf(wedges);
    }

    public boolean refused() {
        return refusal != null;
    }

    public Component message() {
        return Component.translatable("message.beltworks.dismantle_off_line");
    }
}
