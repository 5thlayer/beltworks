package rearth.belts.neoforge;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

public final class PlatformBlockEntityTypesImpl {

    private PlatformBlockEntityTypesImpl() {
    }

    public static <T extends BlockEntity> BlockEntityType<T> create(
            BlockEntityType.BlockEntitySupplier<T> factory,
            Block... validBlocks
    ) {
        return new BlockEntityType<>(factory, validBlocks);
    }
}
