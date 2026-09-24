// SPDX-FileCopyrightText: Rearth
// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: CC-BY-4.0 AND MIT

package rearth.belts;

import rearth.belts.blocks.BeltTileBlock;
import rearth.belts.blocks.BeltWedgeBlock;
import rearth.belts.blocks.ChuteBlock;
import rearth.belts.blocks.SplitterBlock;
import rearth.belts.model.BeltTier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class BlockContent {
    
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Registries.BLOCK, Belts.MOD_ID);
    
    public static final DeferredHolder<Block, Block> CHUTE_BLOCK = loader(BeltTier.BELT);
    public static final DeferredHolder<Block, Block> IMPROVED_CHUTE_BLOCK = loader(BeltTier.IMPROVED);
    public static final DeferredHolder<Block, Block> EXPRESS_CHUTE_BLOCK = loader(BeltTier.EXPRESS);
    public static final DeferredHolder<Block, Block> TURBO_CHUTE_BLOCK = loader(BeltTier.TURBO);
    public static final DeferredHolder<Block, Block> BELT_TILE = tile(BeltTier.BELT);
    public static final DeferredHolder<Block, Block> IMPROVED_BELT_TILE = tile(BeltTier.IMPROVED);
    public static final DeferredHolder<Block, Block> EXPRESS_BELT_TILE = tile(BeltTier.EXPRESS);
    public static final DeferredHolder<Block, Block> TURBO_BELT_TILE = tile(BeltTier.TURBO);
    public static final DeferredHolder<Block, Block> BELT_WEDGE = BLOCKS.register("belt_wedge", () -> new BeltWedgeBlock(
      BlockBehaviour.Properties.ofLegacyCopy(Blocks.GLASS).sound(SoundType.POINTED_DRIPSTONE).noOcclusion().pushReaction(PushReaction.BLOCK).setId(ResourceKey.create(Registries.BLOCK, Belts.id("belt_wedge")))));
    public static final DeferredHolder<Block, Block> SPLITTER_BLOCK = splitter(BeltTier.BELT);
    public static final DeferredHolder<Block, Block> IMPROVED_SPLITTER_BLOCK = splitter(BeltTier.IMPROVED);
    public static final DeferredHolder<Block, Block> EXPRESS_SPLITTER_BLOCK = splitter(BeltTier.EXPRESS);
    public static final DeferredHolder<Block, Block> TURBO_SPLITTER_BLOCK = splitter(BeltTier.TURBO);

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

    private static DeferredHolder<Block, Block> tile(BeltTier tier) {
        return BLOCKS.register(tier.tile(), () -> new BeltTileBlock(
          BlockBehaviour.Properties.ofLegacyCopy(Blocks.GLASS).sound(SoundType.POINTED_DRIPSTONE).noOcclusion().setId(ResourceKey.create(Registries.BLOCK, Belts.id(tier.tile()))), tier));
    }

    private static DeferredHolder<Block, Block> splitter(BeltTier tier) {
        return BLOCKS.register(tier.splitter(), () -> new SplitterBlock(
          BlockBehaviour.Properties.ofLegacyCopy(Blocks.GLASS).sound(SoundType.POINTED_DRIPSTONE).noOcclusion().setId(ResourceKey.create(Registries.BLOCK, Belts.id(tier.splitter()))), tier));
    }

    private static DeferredHolder<Block, Block> loader(BeltTier tier) {
        return BLOCKS.register(tier.loader(), () -> new ChuteBlock(
          BlockBehaviour.Properties.ofLegacyCopy(Blocks.GLASS).sound(SoundType.POINTED_DRIPSTONE).noOcclusion().setId(ResourceKey.create(Registries.BLOCK, Belts.id(tier.loader()))), tier));
    }

}
