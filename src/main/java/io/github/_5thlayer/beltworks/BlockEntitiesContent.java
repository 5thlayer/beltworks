// SPDX-FileCopyrightText: Rearth
// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: CC-BY-4.0 AND MIT

package io.github._5thlayer.beltworks;

import io.github._5thlayer.beltworks.blocks.BeltTileBlockEntity;
import io.github._5thlayer.beltworks.blocks.BeltEndBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class BlockEntitiesContent {
    
    public static final DeferredRegister<BlockEntityType<?>> TYPES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Beltworks.MOD_ID);
    
    
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BeltEndBlockEntity>> BELT_END = TYPES.register(
            "belt_end",
            () -> new BlockEntityType<>(BeltEndBlockEntity::new, BlockContent.LOADER_BLOCK.get(),
                    BlockContent.IMPROVED_LOADER_BLOCK.get(), BlockContent.EXPRESS_LOADER_BLOCK.get(), BlockContent.TURBO_LOADER_BLOCK.get(),
                    BlockContent.SPLITTER_BLOCK.get(), BlockContent.IMPROVED_SPLITTER_BLOCK.get(),
                    BlockContent.EXPRESS_SPLITTER_BLOCK.get(), BlockContent.TURBO_SPLITTER_BLOCK.get())
    );

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BeltTileBlockEntity>> BELT_TILE = TYPES.register(
            "belt_tile",
            () -> new BlockEntityType<>(BeltTileBlockEntity::new, BlockContent.BELT_TILE.get(),
                    BlockContent.IMPROVED_BELT_TILE.get(), BlockContent.EXPRESS_BELT_TILE.get(),
                    BlockContent.TURBO_BELT_TILE.get())
    );

}
