// SPDX-FileCopyrightText: Rearth
// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: CC-BY-4.0 AND MIT

package io.github._5thlayer.beltworks.neoforge.client;

import net.minecraft.world.phys.AABB;
import io.github._5thlayer.beltworks.blocks.BeltEndBlockEntity;
import io.github._5thlayer.beltworks.client.renderers.BeltEndRenderer;

public final class NeoForgeBeltEndRenderer extends BeltEndRenderer {

    @Override
    public AABB getRenderBoundingBox(BeltEndBlockEntity blockEntity) {
        return AABB.INFINITE;
    }
}
