package rearth.belts;

import rearth.belts.items.BeltItem;
import rearth.belts.items.BeltTileItem;
import rearth.belts.items.SplitterItem;
import rearth.belts.items.TooltipBlockItem;
import rearth.belts.model.BeltTier;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

public class ItemContent {
    
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Belts.MOD_ID, Registries.ITEM);
    
    public static final RegistrySupplier<Item> CHUTE = loader(BeltTier.BELT, BlockContent.CHUTE_BLOCK);
    public static final RegistrySupplier<Item> IMPROVED_CHUTE = loader(BeltTier.IMPROVED, BlockContent.IMPROVED_CHUTE_BLOCK);
    public static final RegistrySupplier<Item> EXPRESS_CHUTE = loader(BeltTier.EXPRESS, BlockContent.EXPRESS_CHUTE_BLOCK);
    public static final RegistrySupplier<Item> TURBO_CHUTE = loader(BeltTier.TURBO, BlockContent.TURBO_CHUTE_BLOCK);
    public static final RegistrySupplier<Item> BELT_TILE = tile(BeltTier.BELT, BlockContent.BELT_TILE);
    public static final RegistrySupplier<Item> IMPROVED_BELT_TILE = tile(BeltTier.IMPROVED, BlockContent.IMPROVED_BELT_TILE);
    public static final RegistrySupplier<Item> EXPRESS_BELT_TILE = tile(BeltTier.EXPRESS, BlockContent.EXPRESS_BELT_TILE);
    public static final RegistrySupplier<Item> TURBO_BELT_TILE = tile(BeltTier.TURBO, BlockContent.TURBO_BELT_TILE);
    public static final RegistrySupplier<Item> SPLITTER = splitter(BeltTier.BELT, BlockContent.SPLITTER_BLOCK);
    public static final RegistrySupplier<Item> IMPROVED_SPLITTER = splitter(BeltTier.IMPROVED, BlockContent.IMPROVED_SPLITTER_BLOCK);
    public static final RegistrySupplier<Item> EXPRESS_SPLITTER = splitter(BeltTier.EXPRESS, BlockContent.EXPRESS_SPLITTER_BLOCK);
    public static final RegistrySupplier<Item> TURBO_SPLITTER = splitter(BeltTier.TURBO, BlockContent.TURBO_SPLITTER_BLOCK);
    // In the creative tab for testing placement; it has no recipe, since the belt item places supports (#366).
    public static final RegistrySupplier<Item> CONVEYOR_SUPPORT = ITEMS.register("conveyor_support", () -> new TooltipBlockItem(BlockContent.CONVEYOR_SUPPORT_BLOCK.get(), properties("conveyor_support")));
    public static final RegistrySupplier<Item> BELT = belt(BeltTier.BELT);
    public static final RegistrySupplier<Item> IMPROVED_BELT = belt(BeltTier.IMPROVED);
    public static final RegistrySupplier<Item> EXPRESS_BELT = belt(BeltTier.EXPRESS);
    public static final RegistrySupplier<Item> TURBO_BELT = belt(BeltTier.TURBO);

    public static Item beltFor(BeltTier tier) {
        return switch (tier) {
            case BELT -> BELT.get();
            case IMPROVED -> IMPROVED_BELT.get();
            case EXPRESS -> EXPRESS_BELT.get();
            case TURBO -> TURBO_BELT.get();
        };
    }

    public static Item tileFor(BeltTier tier) {
        return switch (tier) {
            case BELT -> BELT_TILE.get();
            case IMPROVED -> IMPROVED_BELT_TILE.get();
            case EXPRESS -> EXPRESS_BELT_TILE.get();
            case TURBO -> TURBO_BELT_TILE.get();
        };
    }

    private static RegistrySupplier<Item> tile(BeltTier tier, RegistrySupplier<Block> block) {
        return ITEMS.register(tier.tile(), () -> new BeltTileItem(block.get(), properties(tier.tile())));
    }

    private static RegistrySupplier<Item> loader(BeltTier tier, RegistrySupplier<Block> block) {
        return ITEMS.register(tier.loader(), () -> new TooltipBlockItem(block.get(), properties(tier.loader())));
    }

    private static RegistrySupplier<Item> splitter(BeltTier tier, RegistrySupplier<Block> block) {
        return ITEMS.register(tier.splitter(), () -> new SplitterItem(block.get(), properties(tier.splitter())));
    }

    private static RegistrySupplier<Item> belt(BeltTier tier) {
        return ITEMS.register(tier.beltItem(), () -> new BeltItem(properties(tier.beltItem()), tier));
    }

    private static Item.Properties properties(String path) {
        return new Item.Properties().arch$tab(ItemGroupContent.BELTS_GROUP).setId(ResourceKey.create(Registries.ITEM, Belts.id(path)));
    }
    
}
