package rearth.belts;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import rearth.belts.model.BeltContents;
import rearth.belts.model.BeltTier;

import java.util.List;

/**
 * What a client is told of a line of tiles, addressed by its head (PlanetaryFactory #395): the
 * whole line when it is built or first seen, then only what it gains and loses, and no tiers once
 * the head stops holding it.
 *
 * @param reset whether {@code changes} holds every entry of a line the client should start afresh
 */
public record TileLineUpdate(BlockPos head, Direction travel, List<BeltTier> tiers, boolean reset,
                             BeltContents.Changes<ItemStack> changes) {

    public static TileLineUpdate gone(BlockPos head, Direction travel) {
        return new TileLineUpdate(head, travel, List.of(), true, BeltContents.Changes.none());
    }

    public boolean isGone() {
        return tiers.isEmpty();
    }
}
