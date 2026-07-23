package rearth.belts;

import dev.architectury.registry.CreativeTabRegistry;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

public class ItemGroupContent {
    
    public static final DeferredRegister<CreativeModeTab> GROUPS = DeferredRegister.create(Belts.MOD_ID, Registries.CREATIVE_MODE_TAB);
    
    public static final RegistrySupplier<CreativeModeTab> BELTS_GROUP = GROUPS.register("group", () -> CreativeTabRegistry.create(
      Component.translatable("itemgroup.belts.items"),
      () -> new ItemStack(ItemContent.BELT.get())
    ));
}
