package rearth.belts.fabric;

import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
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
        return FabricBlockEntityTypeBuilder.create(factory::create, validBlocks).build();
    }
}
