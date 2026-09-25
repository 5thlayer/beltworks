// SPDX-FileCopyrightText: Rearth
// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: CC-BY-4.0 AND MIT

package io.github._5thlayer.beltworks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

public class ComponentContent {
    
    public static final DeferredRegister<DataComponentType<?>> COMPONENTS = DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, Beltworks.MOD_ID);
    
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<BlockPos>> BELT_START = COMPONENTS.register("belt_start",
      () -> DataComponentType.<BlockPos>builder().persistent(BlockPos.CODEC).networkSynchronized(BlockPos.STREAM_CODEC).build());
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Direction>> BELT_DIR = COMPONENTS.register("belt_start_dir",
      () -> DataComponentType.<Direction>builder().persistent(Direction.CODEC).networkSynchronized(Direction.STREAM_CODEC).build());
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<List<BlockPos>>> STRETCH_CORNERS = COMPONENTS.register("stretch_corners",
      () -> DataComponentType.<List<BlockPos>>builder().persistent(BlockPos.CODEC.listOf()).networkSynchronized(BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list())).build());
    
}
