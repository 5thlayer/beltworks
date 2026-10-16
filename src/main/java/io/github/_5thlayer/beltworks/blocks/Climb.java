// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.blocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.EventHooks;
import org.jetbrains.annotations.Nullable;
import io.github._5thlayer.beltworks.DataComponentsContent;
import io.github._5thlayer.beltworks.items.BeltRefusal;
import io.github._5thlayer.beltworks.items.BeltTileItem;

import java.util.Map;

/**
 * Climb (ADR 0017). As Head Reach does, a press takes the held item before the aimed block: a tile
 * in the main hand has the climb its next placement takes cycled down, level and up, which stays on
 * the stack until its last item is placed, and otherwise the tile under the crosshair has its climb
 * cycled in place, refused where the reshape it causes would be refused as a placement.
 */
public final class Climb {

    private Climb() {
    }

    /** The climb a held tile's next placement takes: level, unless a press has set another. */
    public static BeltTileBlock.ClimbState held(ItemStack stack) {
        return stack.getOrDefault(DataComponentsContent.HELD_CLIMB.get(), BeltTileBlock.ClimbState.LEVEL);
    }

    /**
     * A press of Climb, decided on the server. An aimed block the player could not reach is no aimed
     * block, checked from its position alone before anything reads it, since a modified client may
     * aim anywhere.
     */
    public static void press(Player player, @Nullable BlockPos aimed) {
        var stack = player.getMainHandItem();
        if (stack.getItem() instanceof BeltTileItem) {
            var climb = held(stack).next();
            // Level is no component, so a stack pressed back to it stacks again with one never pressed.
            if (climb == BeltTileBlock.ClimbState.LEVEL) stack.remove(DataComponentsContent.HELD_CLIMB.get());
            else stack.set(DataComponentsContent.HELD_CLIMB.get(), climb);
            player.sendOverlayMessage(message(climb));
            return;
        }
        if (aimed == null || !mayReach(player, aimed) || !mayChange(player, aimed)) return;
        var level = player.level();
        var state = level.getBlockState(aimed);
        if (!(state.getBlock() instanceof BeltTileBlock tile)) return;
        var set = state.setValue(BeltTileBlock.CLIMB, state.getValue(BeltTileBlock.CLIMB).next());
        var planned = Map.of(BeltTileBlock.spot(aimed), BeltTileBlock.laid(set));
        // A slope never turns, so a corner the climb would slope is refused as a placement sloping it is (ADR 0005).
        var formed = BeltTileBlock.formed(set, BeltTileBlock.spot(aimed), BeltTileBlock.around(level, planned));
        var refusal = state.getValue(BeltTileBlock.CORNER) != BeltTileBlock.Shape.STRAIGHT && formed.getValue(BeltTileBlock.PITCH) != BeltTileBlock.PitchState.LEVEL
                ? BeltRefusal.SLOPE_TURNS : BeltTileBlock.reshape(level, planned, tile).refusal();
        if (refusal != null) {
            player.sendOverlayMessage(Component.translatable(refusal.messageKey()));
            return;
        }
        level.setBlock(aimed, set, Block.UPDATE_ALL);
        player.sendOverlayMessage(message(set.getValue(BeltTileBlock.CLIMB)));
    }

    private static Component message(BeltTileBlock.ClimbState climb) {
        return Component.translatable("message.beltworks.climb", Component.translatable("message.beltworks.climb." + climb.getSerializedName()));
    }

    private static boolean mayReach(Player player, BlockPos pos) {
        var level = player.level();
        return player.mayBuild() && level.isLoaded(pos) && player.isWithinBlockInteractionRange(pos, 1.0)
                && level.mayInteract(player, pos);
    }

    // The claim guard Rotate in Place asks too: NeoForge's place event at the tile, not cancelled.
    private static boolean mayChange(Player player, BlockPos pos) {
        var level = player.level();
        return !EventHooks.onBlockPlace(player, BlockSnapshot.create(level.dimension(), level, pos), Direction.UP);
    }
}
