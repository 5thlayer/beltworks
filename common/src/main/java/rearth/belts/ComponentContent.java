package rearth.belts;

import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import rearth.belts.items.PlannedSupport;

import java.util.List;

public class ComponentContent {
    
    public static final DeferredRegister<DataComponentType<?>> COMPONENTS = DeferredRegister.create(Belts.MOD_ID, Registries.DATA_COMPONENT_TYPE);
    
    public static final RegistrySupplier<DataComponentType<BlockPos>> BELT_START = COMPONENTS.register("belt_start",
      () -> DataComponentType.<BlockPos>builder().persistent(BlockPos.CODEC).networkSynchronized(BlockPos.STREAM_CODEC).build());
    public static final RegistrySupplier<DataComponentType<Direction>> BELT_DIR = COMPONENTS.register("belt_start_dir",
      () -> DataComponentType.<Direction>builder().persistent(Direction.CODEC).networkSynchronized(Direction.STREAM_CODEC).build());
    
    public static final RegistrySupplier<DataComponentType<List<PlannedSupport>>> MIDPOINTS = COMPONENTS.register("belt_midpoints",
      () -> DataComponentType.<List<PlannedSupport>>builder().persistent(PlannedSupport.CODEC.listOf()).networkSynchronized(PlannedSupport.STREAM_CODEC.apply(ByteBufCodecs.list())).build());
    
}
