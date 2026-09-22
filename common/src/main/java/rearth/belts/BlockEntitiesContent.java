package rearth.belts;

import rearth.belts.blocks.BeltTileBlockEntity;
import rearth.belts.blocks.ChuteBlockEntity;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;

public class BlockEntitiesContent {
    
    public static final DeferredRegister<BlockEntityType<?>> TYPES = DeferredRegister.create(Belts.MOD_ID, Registries.BLOCK_ENTITY_TYPE);
    
    
    public static final RegistrySupplier<BlockEntityType<ChuteBlockEntity>> CHUTE_BLOCK = TYPES.register(
            "chute",
            () -> PlatformBlockEntityTypes.create(ChuteBlockEntity::new, BlockContent.CHUTE_BLOCK.get(),
                    BlockContent.IMPROVED_CHUTE_BLOCK.get(), BlockContent.EXPRESS_CHUTE_BLOCK.get(), BlockContent.TURBO_CHUTE_BLOCK.get(),
                    BlockContent.SPLITTER_BLOCK.get(), BlockContent.IMPROVED_SPLITTER_BLOCK.get(),
                    BlockContent.EXPRESS_SPLITTER_BLOCK.get(), BlockContent.TURBO_SPLITTER_BLOCK.get(),
                    BlockContent.CONVEYOR_SUPPORT_BLOCK.get())
    );

    public static final RegistrySupplier<BlockEntityType<BeltTileBlockEntity>> BELT_TILE = TYPES.register(
            "belt_tile",
            () -> PlatformBlockEntityTypes.create(BeltTileBlockEntity::new, BlockContent.BELT_TILE.get(),
                    BlockContent.IMPROVED_BELT_TILE.get(), BlockContent.EXPRESS_BELT_TILE.get(),
                    BlockContent.TURBO_BELT_TILE.get())
    );

}
