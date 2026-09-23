package rearth.belts;

import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;

import java.util.List;

public class ComponentContent {
    
    public static final DeferredRegister<DataComponentType<?>> COMPONENTS = DeferredRegister.create(Belts.MOD_ID, Registries.DATA_COMPONENT_TYPE);
    
    public static final RegistrySupplier<DataComponentType<BlockPos>> BELT_START = COMPONENTS.register("belt_start",
      () -> DataComponentType.<BlockPos>builder().persistent(BlockPos.CODEC).networkSynchronized(BlockPos.STREAM_CODEC).build());
    public static final RegistrySupplier<DataComponentType<Direction>> BELT_DIR = COMPONENTS.register("belt_start_dir",
      () -> DataComponentType.<Direction>builder().persistent(Direction.CODEC).networkSynchronized(Direction.STREAM_CODEC).build());
    public static final RegistrySupplier<DataComponentType<List<BlockPos>>> STRETCH_CORNERS = COMPONENTS.register("stretch_corners",
      () -> DataComponentType.<List<BlockPos>>builder().persistent(BlockPos.CODEC.listOf()).networkSynchronized(BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list())).build());
    
}
