package rearth.belts.items;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import rearth.belts.model.BeltPath;
import rearth.belts.model.BeltTier;

import java.util.List;

/**
 * What one click of the belt item would do at an aimed spot. The click executes it and the preview
 * draws it, so the two cannot drift (PlanetaryFactory ADR-0069, #372).
 *
 * @param ends     for a start click, the one end the click stores; for a belt, its start then its end;
 *                 for a mid-belt support, the stored start
 * @param startDir the direction a start click stores, or null for any other click
 * @param supports every mid-belt support the belt would have, a sneak-click's new one last
 * @param path     the curve, or null before a start is stored or when the belt would end where it starts
 */
public record BeltPlan(Click click, BeltTier tier, List<End> ends, @Nullable Direction startDir, List<PlannedSupport> supports, @Nullable BeltPath path,
                       int beltCost, @Nullable Refusal refusal) {

    public enum Click {
        START, MIDPOINT, BELT
    }

    public enum Action {
        PLACE_LOADER, PLACE_SUPPORT, TURN_SUPPORT, USE
    }

    /**
     * @param pos    the block clicked or placed; for a start click, the position the item stores
     * @param state  the block the click leaves here, or null where it leaves the block alone
     * @param loader the tier of a loader the click places
     * @param arrow  the direction a start click fixes, or null where the belt's other point decides it
     */
    public record End(BlockPos pos, Action action, @Nullable BlockState state,
                      @Nullable BeltTier loader, @Nullable Direction arrow) {
    }

    public record Refusal(String key, List<Object> args) {

        public static Refusal of(String key, Object... args) {
            return new Refusal(key, List.of(args));
        }

        public Component message() {
            return Component.translatable(key, args.toArray());
        }
    }

    public boolean refused() {
        return refusal != null;
    }
}
