// SPDX-FileCopyrightText: Rearth
// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: CC-BY-4.0 AND MIT

package rearth.belts.neoforge.client;

import net.minecraft.world.phys.AABB;
import rearth.belts.blocks.ChuteBlockEntity;
import rearth.belts.client.renderers.ChuteBeltRenderer;

public final class NeoForgeChuteBeltRenderer extends ChuteBeltRenderer {

    @Override
    public AABB getRenderBoundingBox(ChuteBlockEntity blockEntity) {
        return AABB.INFINITE;
    }
}
