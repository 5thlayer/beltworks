package rearth.belts;

import rearth.belts.blocks.BeltTileBlock;
import rearth.belts.blocks.BeltWedgeBlock;
import rearth.belts.blocks.ChuteBlock;
import rearth.belts.blocks.SplitterBlock;
import rearth.belts.model.BeltTier;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;

public class BlockContent {
    
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Belts.MOD_ID, Registries.BLOCK);
    
    public static final RegistrySupplier<Block> CHUTE_BLOCK = loader(BeltTier.BELT);
    public static final RegistrySupplier<Block> IMPROVED_CHUTE_BLOCK = loader(BeltTier.IMPROVED);
    public static final RegistrySupplier<Block> EXPRESS_CHUTE_BLOCK = loader(BeltTier.EXPRESS);
    public static final RegistrySupplier<Block> TURBO_CHUTE_BLOCK = loader(BeltTier.TURBO);
    public static final RegistrySupplier<Block> BELT_TILE = tile(BeltTier.BELT);
    public static final RegistrySupplier<Block> IMPROVED_BELT_TILE = tile(BeltTier.IMPROVED);
    public static final RegistrySupplier<Block> EXPRESS_BELT_TILE = tile(BeltTier.EXPRESS);
    public static final RegistrySupplier<Block> TURBO_BELT_TILE = tile(BeltTier.TURBO);
    public static final RegistrySupplier<Block> BELT_WEDGE = BLOCKS.register("belt_wedge", () -> new BeltWedgeBlock(
      BlockBehaviour.Properties.ofLegacyCopy(Blocks.GLASS).sound(SoundType.POINTED_DRIPSTONE).noOcclusion().pushReaction(PushReaction.BLOCK).setId(ResourceKey.create(Registries.BLOCK, Belts.id("belt_wedge")))));
    public static final RegistrySupplier<Block> SPLITTER_BLOCK = splitter(BeltTier.BELT);
    public static final RegistrySupplier<Block> IMPROVED_SPLITTER_BLOCK = splitter(BeltTier.IMPROVED);
    public static final RegistrySupplier<Block> EXPRESS_SPLITTER_BLOCK = splitter(BeltTier.EXPRESS);
    public static final RegistrySupplier<Block> TURBO_SPLITTER_BLOCK = splitter(BeltTier.TURBO);

    public static Block loaderFor(BeltTier tier) {
        return switch (tier) {
            case BELT -> CHUTE_BLOCK.get();
            case IMPROVED -> IMPROVED_CHUTE_BLOCK.get();
            case EXPRESS -> EXPRESS_CHUTE_BLOCK.get();
            case TURBO -> TURBO_CHUTE_BLOCK.get();
        };
    }

    public static Block tileFor(BeltTier tier) {
        return switch (tier) {
            case BELT -> BELT_TILE.get();
            case IMPROVED -> IMPROVED_BELT_TILE.get();
            case EXPRESS -> EXPRESS_BELT_TILE.get();
            case TURBO -> TURBO_BELT_TILE.get();
        };
    }

    public static Block splitterFor(BeltTier tier) {
        return switch (tier) {
            case BELT -> SPLITTER_BLOCK.get();
            case IMPROVED -> IMPROVED_SPLITTER_BLOCK.get();
            case EXPRESS -> EXPRESS_SPLITTER_BLOCK.get();
            case TURBO -> TURBO_SPLITTER_BLOCK.get();
        };
    }

    private static RegistrySupplier<Block> tile(BeltTier tier) {
        return BLOCKS.register(tier.tile(), () -> new BeltTileBlock(
          BlockBehaviour.Properties.ofLegacyCopy(Blocks.GLASS).sound(SoundType.POINTED_DRIPSTONE).noOcclusion().setId(ResourceKey.create(Registries.BLOCK, Belts.id(tier.tile()))), tier));
    }

    private static RegistrySupplier<Block> splitter(BeltTier tier) {
        return BLOCKS.register(tier.splitter(), () -> new SplitterBlock(
          BlockBehaviour.Properties.ofLegacyCopy(Blocks.GLASS).sound(SoundType.POINTED_DRIPSTONE).noOcclusion().setId(ResourceKey.create(Registries.BLOCK, Belts.id(tier.splitter()))), tier));
    }

    private static RegistrySupplier<Block> loader(BeltTier tier) {
        return BLOCKS.register(tier.loader(), () -> new ChuteBlock(
          BlockBehaviour.Properties.ofLegacyCopy(Blocks.GLASS).sound(SoundType.POINTED_DRIPSTONE).noOcclusion().setId(ResourceKey.create(Registries.BLOCK, Belts.id(tier.loader()))), tier));
    }

}
