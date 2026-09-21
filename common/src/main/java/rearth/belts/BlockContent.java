package rearth.belts;

import rearth.belts.blocks.ChuteBlock;
import rearth.belts.blocks.ConveyorSupportBlock;
import rearth.belts.model.BeltTier;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

public class BlockContent {
    
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Belts.MOD_ID, Registries.BLOCK);
    
    public static final RegistrySupplier<Block> CHUTE_BLOCK = loader(BeltTier.BELT);
    public static final RegistrySupplier<Block> IMPROVED_CHUTE_BLOCK = loader(BeltTier.IMPROVED);
    public static final RegistrySupplier<Block> EXPRESS_CHUTE_BLOCK = loader(BeltTier.EXPRESS);
    public static final RegistrySupplier<Block> TURBO_CHUTE_BLOCK = loader(BeltTier.TURBO);
    public static final RegistrySupplier<Block> CONVEYOR_MODEL = BLOCKS.register("conveyor_model", () -> new Block(
      BlockBehaviour.Properties.ofLegacyCopy(Blocks.GLASS).noOcclusion().setId(ResourceKey.create(Registries.BLOCK, Belts.id("conveyor_model")))));
    public static final RegistrySupplier<Block> CONVEYOR_SUPPORT_BLOCK = BLOCKS.register("conveyor_support", () -> new ConveyorSupportBlock(
      BlockBehaviour.Properties.ofLegacyCopy(Blocks.GLASS).sound(SoundType.POINTED_DRIPSTONE).noOcclusion().setId(ResourceKey.create(Registries.BLOCK, Belts.id("conveyor_support")))));

    public static Block loaderFor(BeltTier tier) {
        return switch (tier) {
            case BELT -> CHUTE_BLOCK.get();
            case IMPROVED -> IMPROVED_CHUTE_BLOCK.get();
            case EXPRESS -> EXPRESS_CHUTE_BLOCK.get();
            case TURBO -> TURBO_CHUTE_BLOCK.get();
        };
    }

    private static RegistrySupplier<Block> loader(BeltTier tier) {
        return BLOCKS.register(tier.loader(), () -> new ChuteBlock(
          BlockBehaviour.Properties.ofLegacyCopy(Blocks.GLASS).sound(SoundType.POINTED_DRIPSTONE).noOcclusion().setId(ResourceKey.create(Registries.BLOCK, Belts.id(tier.loader()))), tier));
    }

}
