// SPDX-FileCopyrightText: Rearth
// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: CC-BY-4.0 AND MIT

package io.github._5thlayer.beltworks.blocks;

import java.util.ArrayList;
import java.util.List;

import io.github._5thlayer.groundworks.DismantleFamily;
import io.github._5thlayer.groundworks.DismantleSpan;
import io.github._5thlayer.groundworks.Refusal;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jetbrains.annotations.Nullable;
import io.github._5thlayer.beltworks.BlockEntitiesContent;
import io.github._5thlayer.beltworks.model.Dismantle;
import io.github._5thlayer.beltworks.model.LineScan;

/**
 * The belt family of the Groundworks library's Dismantle (ADR 0011): a span of one transport line,
 * by the {@link Dismantle} rule over the line's scan, taking its tiles and every item they carry.
 * Groundworks runs the gesture, the stored start, the removal and the preview.
 *
 * <p>A span takes tiles only. A tile's removal takes its wedge (#420), so the wedges are drawn and
 * never taken, and the tile's loot table gives the tile back. Loaders and splitters are no tiles,
 * so a span never takes one.
 */
public final class BeltFamily implements DismantleFamily {

    /** The one belt family, registered with Groundworks and asked which tools it takes. */
    public static final BeltFamily INSTANCE = new BeltFamily();

    public enum Reason implements Refusal {
        /** The end is not a tile of the start's line. */
        OFF_LINE
    }

    @Override
    public boolean claims(BlockState state) {
        return state.getBlock() instanceof BeltTileBlock;
    }

    /** A click on a wedge names the tile it stands under. */
    @Override
    public BlockPos names(BlockGetter level, BlockPos clicked) {
        return level.getBlockState(clicked).getBlock() instanceof BeltWedgeBlock
                 && level.getBlockState(clicked.above()).getBlock() instanceof BeltTileBlock ? clicked.above() : clicked;
    }

    @Override
    public DismantleSpan span(Level level, BlockPos start, BlockPos end) {
        var span = Dismantle.span(spot(start), spot(end), spot -> piece(level, spot));
        if (span.refusal() != null) return DismantleSpan.refused(Reason.OFF_LINE);
        var tiles = new ArrayList<BlockPos>();
        var wedges = new ArrayList<BlockPos>();
        for (var spot : span.spots()) {
            var pos = new BlockPos(spot.x(), spot.y(), spot.z());
            tiles.add(pos);
            if (level.getBlockState(pos.below()).getBlock() instanceof BeltWedgeBlock) wedges.add(pos.below());
        }
        return DismantleSpan.taking(tiles, wedges);
    }

    @Override
    public Component message(Refusal refusal) {
        return Component.translatable("message.beltworks.dismantle_off_line");
    }

    /** The same tile block, facing the same way: a turned tile is no longer the start. */
    @Override
    public boolean isSameStart(BlockState stored, BlockState now) {
        return stored.getBlock() == now.getBlock()
                 && stored.getValue(BlockStateProperties.HORIZONTAL_FACING) == now.getValue(BlockStateProperties.HORIZONTAL_FACING);
    }

    // Every tile lets go of its items before any is removed, so the first removal's rebuild cannot
    // hand a spanned tile's share to a tile outside the span.
    @Override
    public List<ItemStack> beforeTaking(Level level, List<BlockPos> taken) {
        var handed = new ArrayList<ItemStack>();
        for (var pos : taken) {
            level.getBlockEntity(pos, BlockEntitiesContent.BELT_TILE.get())
              .ifPresent(tile -> tile.takeCarried().forEach(share -> handed.add(share.payload())));
        }
        return handed;
    }

    private static LineScan.@Nullable Piece piece(Level level, LineScan.Spot spot) {
        var pos = new BlockPos(spot.x(), spot.y(), spot.z());
        // A span stops at a chunk's edge rather than loading the next one.
        if (!level.isLoaded(pos)) return null;
        var state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof BeltTileBlock)) return null;
        var travel = BeltTileBlock.travel(state.getValue(BlockStateProperties.HORIZONTAL_FACING));
        return new LineScan.Piece(travel, state.getValue(BeltTileBlock.CORNER).model().entry(travel), state.getValue(BeltTileBlock.PITCH).model());
    }

    private static LineScan.Spot spot(BlockPos pos) {
        return new LineScan.Spot(pos.getX(), pos.getY(), pos.getZ());
    }
}
