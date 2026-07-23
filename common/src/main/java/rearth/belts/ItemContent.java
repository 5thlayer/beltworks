package rearth.belts;

import rearth.belts.items.BeltItem;
import rearth.belts.items.TooltipBlockItem;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;

public class ItemContent {
    
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Belts.MOD_ID, Registries.ITEM);
    
    public static final RegistrySupplier<Item> CHUTE = ITEMS.register("chute", () -> new TooltipBlockItem(BlockContent.CHUTE_BLOCK.get(), properties("chute")));
    public static final RegistrySupplier<Item> CONVEYOR_SUPPORT = ITEMS.register("conveyor_support", () -> new TooltipBlockItem(BlockContent.CONVEYOR_SUPPORT_BLOCK.get(), properties("conveyor_support")));
    public static final RegistrySupplier<Item> BELT = ITEMS.register("belt", () -> new BeltItem(properties("belt")));

    private static Item.Properties properties(String path) {
        return new Item.Properties().arch$tab(ItemGroupContent.BELTS_GROUP).setId(ResourceKey.create(Registries.ITEM, Belts.id(path)));
    }
    
}
