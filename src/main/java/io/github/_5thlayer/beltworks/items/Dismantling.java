// SPDX-FileCopyrightText: Rearth
// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: CC-BY-4.0 AND MIT

package io.github._5thlayer.beltworks.items;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jetbrains.annotations.Nullable;
import io.github._5thlayer.beltworks.BlockEntitiesContent;
import io.github._5thlayer.beltworks.Beltworks;
import io.github._5thlayer.beltworks.ComponentContent;
import io.github._5thlayer.beltworks.blocks.BeltTileBlock;
import io.github._5thlayer.beltworks.blocks.BeltWedgeBlock;
import io.github._5thlayer.beltworks.model.Dismantle;
import io.github._5thlayer.beltworks.model.LineScan;

import java.util.ArrayList;
import java.util.List;

/**
 * Taking up a line's tiles with an item in {@link #DISMANTLES_BELTS} (PlanetaryFactory #404): a
 * sneak-click stores the start on the held stack, and a click takes up the {@link Dismantle} span
 * to the aimed tile. A sneak-click with a start stored moves it. A start whose tile is gone or
 * turned is no start, so a click passes on as it would; a sneak-use in the air clears it.
 */
public final class Dismantling {

    /** The items the gesture belongs to; the fork names none of them. */
    public static final TagKey<Item> DISMANTLES_BELTS = TagKey.create(Registries.ITEM, Beltworks.id("dismantles_belts"));

    private Dismantling() {
    }

    public static boolean dismantles(ItemStack stack) {
        return stack.is(DISMANTLES_BELTS);
    }

    /** The stored start, or null when none is stored or its tile is gone or turned since. */
    public static @Nullable BlockPos liveStart(BlockGetter level, ItemStack held) {
        var start = held.get(ComponentContent.DISMANTLE_START.get());
        var facing = held.get(ComponentContent.DISMANTLE_FACING.get());
        if (start == null || facing == null) return null;
        var state = level.getBlockState(start);
        return state.getBlock() instanceof BeltTileBlock && state.getValue(BlockStateProperties.HORIZONTAL_FACING) == facing ? start : null;
    }

    /** The tile a click on {@code pos} names: the tile itself, or the tile a wedge stands under. */
    public static BlockPos aimedTile(BlockGetter level, BlockPos pos) {
        return level.getBlockState(pos).getBlock() instanceof BeltWedgeBlock
                 && level.getBlockState(pos.above()).getBlock() instanceof BeltTileBlock ? pos.above() : pos;
    }

    /** What a click at {@code aimed} would take up, or null when {@code held} has no live start. */
    public static @Nullable DismantlePlan plan(Level level, ItemStack held, BlockPos aimed) {
        if (!dismantles(held)) return null;
        var start = liveStart(level, held);
        if (start == null) return null;

        var span = Dismantle.span(spot(start), spot(aimedTile(level, aimed)), spot -> piece(level, spot));
        if (span.refusal() != null) return new DismantlePlan(List.of(), List.of(), span.refusal());
        var tiles = new ArrayList<BlockPos>();
        var wedges = new ArrayList<BlockPos>();
        for (var spot : span.spots()) {
            var pos = new BlockPos(spot.x(), spot.y(), spot.z());
            tiles.add(pos);
            if (level.getBlockState(pos.below()).getBlock() instanceof BeltWedgeBlock) wedges.add(pos.below());
        }
        return new DismantlePlan(tiles, wedges, null);
    }

    /**
     * A click on a block, or PASS where the gesture has nothing to say and the click goes on as it
     * would. With a live start every click on a block is the dismantle's, as the preview shows the
     * dismantle whatever the aim.
     */
    public static InteractionResult useOn(Player player, InteractionHand hand, BlockPos pos) {
        var held = player.getItemInHand(hand);
        if (!dismantles(held)) return InteractionResult.PASS;
        var level = player.level();
        var aimed = aimedTile(level, pos);
        if (player.isShiftKeyDown()) {
            if (!(level.getBlockState(aimed).getBlock() instanceof BeltTileBlock)) return InteractionResult.PASS;
            if (!level.isClientSide()) {
                held.set(ComponentContent.DISMANTLE_START.get(), aimed.immutable());
                held.set(ComponentContent.DISMANTLE_FACING.get(), level.getBlockState(aimed).getValue(BlockStateProperties.HORIZONTAL_FACING));
                tell(player, Component.translatable("message.beltworks.dismantle_started"));
            }
            return InteractionResult.SUCCESS;
        }
        var plan = plan(level, held, aimed);
        if (plan == null) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (plan.refused()) {
            tell(player, plan.message());
            return InteractionResult.SUCCESS;
        }
        execute(plan, level, held, player);
        return InteractionResult.SUCCESS;
    }

    /** A use in the air: a sneak clears the stored start. */
    public static InteractionResult use(Player player, InteractionHand hand) {
        var held = player.getItemInHand(hand);
        if (!player.isShiftKeyDown() || !dismantles(held) || !held.has(ComponentContent.DISMANTLE_START.get())) return InteractionResult.PASS;
        if (!player.level().isClientSide()) {
            clearStart(held);
            tell(player, Component.translatable("message.beltworks.dismantle_cleared"));
        }
        return InteractionResult.SUCCESS;
    }

    // Every tile lets go of its items before any is removed, so the first removal's rebuild cannot
    // hand a spanned tile's share to a tile outside the span.
    private static void execute(DismantlePlan plan, Level level, ItemStack held, Player player) {
        clearStart(held);
        var handed = new ArrayList<ItemStack>();
        for (var pos : plan.tiles()) {
            level.getBlockEntity(pos, BlockEntitiesContent.BELT_TILE.get())
              .ifPresent(tile -> tile.takeCarried().forEach(share -> handed.add(share.payload())));
        }
        for (var pos : plan.tiles()) {
            handed.add(new ItemStack(level.getBlockState(pos).getBlock().asItem()));
            // The tile's removal takes its wedge (#420).
            level.destroyBlock(pos, false, player);
        }
        if (player.hasInfiniteMaterials()) return;
        for (var stack : handed) {
            if (stack.isEmpty()) continue;
            if (!player.getInventory().add(stack) && !stack.isEmpty()) player.drop(stack, false);
        }
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

    private static void clearStart(ItemStack held) {
        held.remove(ComponentContent.DISMANTLE_START.get());
        held.remove(ComponentContent.DISMANTLE_FACING.get());
    }

    private static void tell(Player player, Component message) {
        if (player instanceof ServerPlayer server) server.sendSystemMessage(message, true);
    }

    private static LineScan.Spot spot(BlockPos pos) {
        return new LineScan.Spot(pos.getX(), pos.getY(), pos.getZ());
    }
}
