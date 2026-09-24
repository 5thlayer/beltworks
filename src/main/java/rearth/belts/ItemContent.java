// SPDX-FileCopyrightText: Rearth
// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: CC-BY-4.0 AND MIT

package rearth.belts;

import rearth.belts.items.BeltTileItem;
import rearth.belts.items.SplitterItem;
import rearth.belts.items.TooltipBlockItem;
import rearth.belts.model.BeltTier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ItemContent {
    
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, Belts.MOD_ID);
    
    public static final DeferredHolder<Item, Item> CHUTE = loader(BeltTier.BELT, BlockContent.CHUTE_BLOCK);
    public static final DeferredHolder<Item, Item> IMPROVED_CHUTE = loader(BeltTier.IMPROVED, BlockContent.IMPROVED_CHUTE_BLOCK);
    public static final DeferredHolder<Item, Item> EXPRESS_CHUTE = loader(BeltTier.EXPRESS, BlockContent.EXPRESS_CHUTE_BLOCK);
    public static final DeferredHolder<Item, Item> TURBO_CHUTE = loader(BeltTier.TURBO, BlockContent.TURBO_CHUTE_BLOCK);
    public static final DeferredHolder<Item, Item> BELT_TILE = tile(BeltTier.BELT, BlockContent.BELT_TILE);
    public static final DeferredHolder<Item, Item> IMPROVED_BELT_TILE = tile(BeltTier.IMPROVED, BlockContent.IMPROVED_BELT_TILE);
    public static final DeferredHolder<Item, Item> EXPRESS_BELT_TILE = tile(BeltTier.EXPRESS, BlockContent.EXPRESS_BELT_TILE);
    public static final DeferredHolder<Item, Item> TURBO_BELT_TILE = tile(BeltTier.TURBO, BlockContent.TURBO_BELT_TILE);
    public static final DeferredHolder<Item, Item> SPLITTER = splitter(BeltTier.BELT, BlockContent.SPLITTER_BLOCK);
    public static final DeferredHolder<Item, Item> IMPROVED_SPLITTER = splitter(BeltTier.IMPROVED, BlockContent.IMPROVED_SPLITTER_BLOCK);
    public static final DeferredHolder<Item, Item> EXPRESS_SPLITTER = splitter(BeltTier.EXPRESS, BlockContent.EXPRESS_SPLITTER_BLOCK);
    public static final DeferredHolder<Item, Item> TURBO_SPLITTER = splitter(BeltTier.TURBO, BlockContent.TURBO_SPLITTER_BLOCK);

    public static Item tileFor(BeltTier tier) {
        return switch (tier) {
            case BELT -> BELT_TILE.get();
            case IMPROVED -> IMPROVED_BELT_TILE.get();
            case EXPRESS -> EXPRESS_BELT_TILE.get();
            case TURBO -> TURBO_BELT_TILE.get();
        };
    }

    private static DeferredHolder<Item, Item> tile(BeltTier tier, DeferredHolder<Block, Block> block) {
        return ITEMS.register(tier.tile(), () -> new BeltTileItem(block.get(), properties(tier.tile())));
    }

    private static DeferredHolder<Item, Item> loader(BeltTier tier, DeferredHolder<Block, Block> block) {
        return ITEMS.register(tier.loader(), () -> new TooltipBlockItem(block.get(), properties(tier.loader())));
    }

    private static DeferredHolder<Item, Item> splitter(BeltTier tier, DeferredHolder<Block, Block> block) {
        return ITEMS.register(tier.splitter(), () -> new SplitterItem(block.get(), properties(tier.splitter())));
    }

    private static Item.Properties properties(String path) {
        return new Item.Properties().setId(ResourceKey.create(Registries.ITEM, Belts.id(path)));
    }
    
}
