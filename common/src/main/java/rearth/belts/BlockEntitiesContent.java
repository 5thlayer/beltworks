package rearth.belts;

import rearth.belts.blocks.ChuteBlockEntity;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;

public class BlockEntitiesContent {
    
    public static final DeferredRegister<BlockEntityType<?>> TYPES = DeferredRegister.create(Belts.MOD_ID, Registries.BLOCK_ENTITY_TYPE);
    
    
    public static final RegistrySupplier<BlockEntityType<ChuteBlockEntity>> CHUTE_BLOCK = TYPES.register(
            "chute",
            () -> PlatformBlockEntityTypes.create(ChuteBlockEntity::new, BlockContent.CHUTE_BLOCK.get())
    );
    
}
