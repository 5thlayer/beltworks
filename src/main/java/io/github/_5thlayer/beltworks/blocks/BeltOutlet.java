// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.blocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import io.github._5thlayer.beltworks.model.Outlet;
import io.github._5thlayer.beltworks.model.Pitch;
import io.github._5thlayer.beltworks.model.TileShape;

/**
 * The belt piece in front of a belt's end, found in the world, that the end hands its items on to:
 * a loader, a splitter half's back, a tile line's first tile or the side of a tile (#86). As
 * {@link FeederEnd} is across a line, this is along it.
 */
sealed interface BeltOutlet extends Outlet<ItemStack> {

    /**
     * What the tile line whose last tile runs {@code travel} into {@code pos} hands on to: a loader
     * facing back along it, a splitter half facing its way, or else the side of a straight, level
     * tile of another line. A corner's side is its entry, head-on is no feed (PlanetaryFactory
     * #409), and a slope takes no side-load (#417).
     */
    static @Nullable BeltOutlet pastLineEnd(Level level, BlockPos pos, Direction travel, BeltTileBlockEntity from) {
        if (!level.isLoaded(pos)) return null;
        return switch (level.getBlockEntity(pos)) {
            case BeltEndBlockEntity end when end.isSplitter() -> end.getOwnFacing() == travel ? new Half(end) : null;
            case BeltEndBlockEntity end -> end.getOwnFacing() == travel.getOpposite() ? new Loader(end) : null;
            case BeltTileBlockEntity fed when fed.shape() == TileShape.STRAIGHT && fed.pitch() == Pitch.LEVEL
                    && fed.travel().getAxis() != travel.getAxis() -> new SideLoad(fed, from);
            case null, default -> null;
        };
    }

    /**
     * What a splitter half facing {@code facing} into {@code pos} hands on to: a half facing its
     * way, or the first tile of a line there, entered its way. Nothing else yet (#89).
     */
    static @Nullable BeltOutlet aheadOfHalf(Level level, BlockPos pos, Direction facing) {
        if (!level.isLoaded(pos)) return null;
        return switch (level.getBlockEntity(pos)) {
            case BeltEndBlockEntity end when end.isSplitter() && end.getOwnFacing() == facing -> new Half(end);
            case BeltTileBlockEntity tile -> {
                var entry = tile.entryHandoff(facing);
                yield entry == null ? null : new FirstTile(tile, Outlet.entry(entry));
            }
            case null, default -> null;
        };
    }

    /** A loader, unloading into the inventory behind it at its own rate. */
    record Loader(BeltEndBlockEntity loader) implements BeltOutlet {

        @Override
        public boolean offer(ItemStack item, double overshoot) {
            return loader.acceptFromLine(item);
        }
    }

    /** A splitter half's back, the segment before its midline. */
    record Half(BeltEndBlockEntity half) implements BeltOutlet {

        @Override
        public boolean offer(ItemStack item, double overshoot) {
            return half.offerAtBack(item, overshoot);
        }
    }

    /** A tile line's first tile, as the belt behind it hands on. */
    record FirstTile(BeltTileBlockEntity tile, Outlet<ItemStack> entry) implements BeltOutlet {

        @Override
        public boolean offer(ItemStack item, double overshoot) {
            if (!entry.offer(item, overshoot)) return false;
            tile.lineChanged();
            return true;
        }
    }

    /** The side of another line's tile, which merges the item into a gap at that tile. */
    record SideLoad(BeltTileBlockEntity fed, BeltTileBlockEntity from) implements BeltOutlet {

        @Override
        public boolean offer(ItemStack item, double overshoot) {
            return fed.sideLoadFrom(from, item);
        }
    }
}
