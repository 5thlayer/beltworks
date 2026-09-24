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
 * the head stops holding it. The tiles are found from the head by each one's travel and rise, since a
 * line turns at its corners (#391) and climbs at its slopes (#417).
 *
 * @param travels each tile's travel, head first, on a reset only; a change names no tiles
 * @param rises how many blocks up the tile after each one is, beside {@code travels}
 * @param reset whether {@code changes} holds every entry of a line the client should start afresh
 */
public record TileLineUpdate(BlockPos head, List<Direction> travels, List<Integer> rises, List<BeltTier> tiers,
                             boolean ring, boolean reset, BeltContents.Changes<ItemStack> changes) {

    public static TileLineUpdate gone(BlockPos head) {
        return new TileLineUpdate(head, List.of(), List.of(), List.of(), false, true, BeltContents.Changes.none());
    }

    public boolean isGone() {
        return tiers.isEmpty();
    }
}
