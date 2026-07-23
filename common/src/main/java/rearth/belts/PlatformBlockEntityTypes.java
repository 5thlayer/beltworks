package rearth.belts;

import dev.architectury.injectables.annotations.ExpectPlatform;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

public final class PlatformBlockEntityTypes {

    private PlatformBlockEntityTypes() {
    }

    @ExpectPlatform
    public static <T extends BlockEntity> BlockEntityType<T> create(
            BlockEntityType.BlockEntitySupplier<T> factory,
            Block... validBlocks
    ) {
        throw new AssertionError();
    }
}
