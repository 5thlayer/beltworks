// SPDX-FileCopyrightText: Rearth
// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: CC-BY-4.0 AND MIT

package io.github._5thlayer.beltworks.items;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * What a click with the tile item and a stored start would lay (PlanetaryFactory #393): each tile
 * of the stretch and what happens there, what it charges and hands back, or why it lays nothing.
 * The click executes it and the preview draws it (ADR 0006).
 *
 * @param cost     held-tier tiles taken from the player
 * @param returned the replaced tiles' items handed back
 */
public record StretchPlan(List<Tile> tiles, int cost, List<ItemStack> returned, @Nullable Refusal refusal) {

    public enum Action {
        PLACE,
        /** A tile of the held tier facing another way, turned to the stretch. */
        TURN,
        /** A tile of another tier, Fast Replaced (the Pack's ADR-0082). */
        REPLACE,
        /** A wedge under a middle or top over air, which costs nothing (#420). */
        WEDGE
    }

    public record Tile(BlockPos pos, BlockState state, Action action) {
    }

    /**
     * Listed in the order the plan asks them; the first met is the one the player reads. The Mod's
     * refusals in the Groundworks library's plans too, a splitter's included (ADR 0010).
     */
    public enum Reason implements io.github._5thlayer.groundworks.Refusal {
        BEHIND_LOOK("message.beltworks.stretch_behind"),
        BLOCKED("message.beltworks.stretch_blocked"),
        UNEVEN("message.beltworks.stretch_uneven"),
        SLOPE_TURNS("message.beltworks.slope_turns"),
        NO_ROOM_TO_CROSS("message.beltworks.stretch_no_room_to_cross"),
        WEDGE_BLOCKED("message.beltworks.wedge_blocked"),
        NOT_ENOUGH_TILES("message.beltworks.stretch_not_enough"),
        NO_ROOM_TO_RETURN("message.beltworks.stretch_no_room");

        private final String messageKey;

        Reason(String messageKey) {
            this.messageKey = messageKey;
        }

        public String messageKey() {
            return messageKey;
        }
    }

    public record Refusal(Reason reason, List<Object> args) {

        public static Refusal of(Reason reason, Object... args) {
            return new Refusal(reason, List.of(args));
        }

        public Component message() {
            return Component.translatable(reason.messageKey(), args.toArray());
        }
    }

    public StretchPlan {
        tiles = List.copyOf(tiles);
        returned = List.copyOf(returned);
    }

    public boolean refused() {
        return refusal != null;
    }
}
