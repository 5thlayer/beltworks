// SPDX-FileCopyrightText: Rearth
// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: CC-BY-4.0 AND MIT

package io.github._5thlayer.beltworks;

import io.github._5thlayer.beltworks.items.BeltTileItem;
import io.github._5thlayer.beltworks.items.SplitterItem;
import io.github._5thlayer.beltworks.items.TooltipBlockItem;
import io.github._5thlayer.beltworks.model.BeltTier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ItemContent {
    
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, Beltworks.MOD_ID);
    
    public static final DeferredHolder<Item, Item> LOADER = loader(BeltTier.BELT, BlockContent.LOADER_BLOCK);
    public static final DeferredHolder<Item, Item> IMPROVED_LOADER = loader(BeltTier.IMPROVED, BlockContent.IMPROVED_LOADER_BLOCK);
    public static final DeferredHolder<Item, Item> EXPRESS_LOADER = loader(BeltTier.EXPRESS, BlockContent.EXPRESS_LOADER_BLOCK);
    public static final DeferredHolder<Item, Item> TURBO_LOADER = loader(BeltTier.TURBO, BlockContent.TURBO_LOADER_BLOCK);
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
        return new Item.Properties().setId(ResourceKey.create(Registries.ITEM, Beltworks.id(path)));
    }
    
}
