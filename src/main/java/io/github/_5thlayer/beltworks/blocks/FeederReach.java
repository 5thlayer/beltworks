// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.blocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.EventHooks;
import org.jetbrains.annotations.Nullable;
import io.github._5thlayer.beltworks.DataComponentsContent;
import io.github._5thlayer.beltworks.model.FeederArms;

/**
 * Head Reach and Tail Reach (ADR 0013). As Rotate does, a press takes the held item before the
 * aimed block: a feeder in the main hand has the reach its next placement takes lengthened, which
 * stays on the stack until its last item is placed, and otherwise the feeder under the crosshair
 * has that arm lengthened in place.
 */
public final class FeederReach {

    private FeederReach() {
    }

    /** The reach a held feeder's next placement takes: the blocks either side, unless a press has lengthened it. */
    public static FeederArms held(ItemStack stack) {
        return stack.getOrDefault(DataComponentsContent.HELD_REACH.get(), FeederArms.ADJACENT);
    }

    public static boolean isFeeder(ItemStack stack) {
        return stack.getItem() instanceof BlockItem item && item.getBlock() instanceof FeederBlock;
    }

    /**
     * A press of {@code arm}'s key, decided on the server. An aimed block the player could not reach
     * is no aimed block, checked from its position alone before anything reads it, since a modified
     * client may aim anywhere.
     */
    public static void press(Player player, @Nullable BlockPos aimed, FeederArms.Arm arm) {
        var stack = player.getMainHandItem();
        if (isFeeder(stack)) {
            setHeld(stack, held(stack).lengthened(arm));
            return;
        }
        if (aimed == null || !mayReach(player, aimed)) return;
        if (!(player.level().getBlockEntity(aimed) instanceof FeederBlockEntity feeder) || !mayChange(player, aimed)) return;
        var arms = feeder.arms().lengthened(arm);
        feeder.setArms(arms);
        // Told as well as drawn, since a reach of three can end out of sight.
        player.sendOverlayMessage(Component.translatable(
                arm == FeederArms.Arm.HEAD ? "message.beltworks.head_reach" : "message.beltworks.tail_reach", arms.reach(arm)));
    }

    // The blocks either side are no component, so a stack pressed back to them stacks again with one never pressed.
    private static void setHeld(ItemStack stack, FeederArms arms) {
        if (arms.headReach() == FeederArms.MIN_REACH && arms.tailReach() == FeederArms.MIN_REACH) {
            stack.remove(DataComponentsContent.HELD_REACH.get());
        } else {
            stack.set(DataComponentsContent.HELD_REACH.get(), arms);
        }
    }

    private static boolean mayReach(Player player, BlockPos pos) {
        var level = player.level();
        return player.mayBuild() && level.isLoaded(pos) && player.isWithinBlockInteractionRange(pos, 1.0)
                && level.mayInteract(player, pos);
    }

    // The claim guard Rotate in Place asks too: NeoForge's place event at the feeder, not cancelled.
    private static boolean mayChange(Player player, BlockPos pos) {
        var level = player.level();
        return !EventHooks.onBlockPlace(player, BlockSnapshot.create(level.dimension(), level, pos), Direction.UP);
    }
}
