// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.blocks;

import java.util.OptionalDouble;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import io.github._5thlayer.beltworks.BlockEntitiesContent;
import io.github._5thlayer.beltworks.mixin.ItemEntityAccessor;

/**
 * An item entity on a belt tile, however it got there (#92): it joins the tile's line at the point
 * under it while the line has room there, one item at a time, and while it has none it rests on the
 * line's items and is carried as far as they move. It never looks at another tile. An item nobody may
 * pick up is left alone, as joining a line is a pickup: it rides the tile as it always has.
 */
public final class BeltLanding {

    // ItemEntity.INFINITE_PICKUP_DELAY: set by setNeverPickUp, as /give's fake item and display items are.
    private static final int NEVER_PICKED_UP = 32767;

    private BeltLanding() {
    }

    /**
     * Puts as much of the entity's stack as the tile's line has room for into the line, at the point
     * under the entity, and leaves the rest where it is.
     *
     * @param tileStep how far a tile with no line under it carries an item in a tick, in blocks
     * @return empty when the whole stack joined the line and the entity is gone, else how far, in
     *   blocks, to carry what is left this tick: as far as the line's items under it moved
     */
    public static OptionalDouble land(Level level, BlockPos pos, ItemEntity item, double tileStep) {
        if (!(level.getBlockEntity(pos, BlockEntitiesContent.BELT_TILE.get()).orElse(null) instanceof BeltTileBlockEntity tile)) {
            return OptionalDouble.of(tileStep);
        }
        var stack = item.getItem();
        // Else /give's fake item, spawned beside the stack it put in the inventory, would join a line too.
        if (stack.isEmpty() || ((ItemEntityAccessor) item).beltworks$pickupDelay() == NEVER_PICKED_UP) return OptionalDouble.of(tileStep);

        var centre = pos.getCenter();
        var offset = tile.shape().project(item.getX() - centre.x, item.getZ() - centre.z, BeltTileBlock.travel(tile.travel()));
        // One item per insert, and an item only leaves the entity once the line holds it.
        var remaining = stack.getCount();
        while (remaining > 0 && tile.dropOnto(stack.copyWithCount(1), offset)) remaining--;
        if (remaining == 0) {
            item.discard();
            return OptionalDouble.empty();
        }
        if (remaining != stack.getCount()) item.setItem(stack.copyWithCount(remaining));

        // A tile no line holds yet has no items to rest on, and carries as it always has.
        var moved = tile.movedAt(offset);
        return OptionalDouble.of(Double.isNaN(moved) ? tileStep : moved);
    }
}
