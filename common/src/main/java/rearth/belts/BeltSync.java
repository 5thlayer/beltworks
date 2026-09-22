package rearth.belts;

import dev.architectury.injectables.annotations.ExpectPlatform;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import rearth.belts.model.BeltContents;

public final class BeltSync {

    private BeltSync() {
    }

    /** Sends what the belt starting at {@code belt} gained and lost to every player who sees it. */
    @ExpectPlatform
    public static void send(ServerLevel level, BlockPos belt, BeltContents.Changes<ItemStack> changes) {
        throw new AssertionError();
    }
}
