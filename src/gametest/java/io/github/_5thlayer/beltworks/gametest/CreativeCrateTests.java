// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.gametest;

import java.util.OptionalInt;
import java.util.UUID;
import java.util.function.Consumer;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;
import io.github._5thlayer.beltworks.BlockContent;
import io.github._5thlayer.beltworks.ItemContent;
import io.github._5thlayer.beltworks.blocks.BeltTileBlockEntity;
import io.github._5thlayer.beltworks.blocks.CreativeCrateBlockEntity;
import io.github._5thlayer.beltworks.blocks.CreativeCrateMenu;
import io.github._5thlayer.beltworks.model.BeltTier;

/**
 * A creative crate (5thlayer/beltworks#125) gives the one item it is set to without end, through
 * NeoForge's item handler, to a loader, a feeder or a hopper alike, and voids whatever is put into
 * it. Its menu sets it from the cursor or a shift-clicked stack and takes nothing; broken it loses
 * its item, and saved it keeps it. Every test's name starts {@code creativecrate}, so one glob runs them.
 */
final class CreativeCrateTests {

    private static final BlockPos CRATE = new BlockPos(2, 1, 3);
    private static final BlockPos STANDING = new BlockPos(2, 1, 1);

    // A crate or chest, a loader, three tiles running east, a loader, and a crate or chest.
    private static final BlockPos SOURCE = new BlockPos(2, 1, 3);
    private static final BlockPos FIRST_TILE = SOURCE.east(2);
    private static final int TILES = 3;
    private static final BlockPos TARGET = FIRST_TILE.east(TILES + 1);
    // A tile holds eight items, so a line of three backed up holds 24.
    private static final int BACKED_UP = 8 * TILES;
    // More than one stack, and more than the line holds: what a source that empties could not give.
    private static final int DELIVERED = 100;
    private static final int SUPPLY = 27 * 64;
    private static final int LINE_TICKS = 400;

    private static final int PULLS = 100;

    private CreativeCrateTests() {
    }

    static void register(BeltGameTests.Registrar tests) {
        var loadersUnpowered = tests.withLoaderPower(false);
        tests.test("creativecrate_set_gives_its_item_endlessly_at_its_max_stack_size", 1, CreativeCrateTests::givesEndlessly);
        tests.test("creativecrate_gives_exactly_the_stack_it_was_set_to_data_and_all", 1, CreativeCrateTests::givesWithData);
        tests.test("creativecrate_gives_an_unstackable_item_one_per_pull", 1, CreativeCrateTests::givesUnstackable);
        tests.test("creativecrate_voids_whatever_is_put_into_it_set_or_not", 1, CreativeCrateTests::voidsInserts);
        tests.test("creativecrate_unset_gives_nothing", 1, CreativeCrateTests::unsetGivesNothing);
        loadersUnpowered.test("creativecrate_loader_pulls_its_item_onto_a_belt_line", LINE_TICKS, CreativeCrateTests::loaderPulls);
        loadersUnpowered.test("creativecrate_line_ending_in_a_loader_into_a_crate_never_backs_up", LINE_TICKS,
          CreativeCrateTests::lineNeverBacksUp);
        tests.test("creativecrate_hopper_pulls_its_item", 200, CreativeCrateTests::hopperPulls);
        loadersUnpowered.test("creativecrate_feeder_head_takes_from_it", 200, CreativeCrateTests::feederTakes);
        tests.test("creativecrate_plain_use_opens_its_menu", 1, CreativeCrateTests::useOpensMenu);
        tests.test("creativecrate_menu_sets_and_clears_it_taking_nothing", 1, CreativeCrateTests::menuSets);
        tests.test("creativecrate_broken_drops_a_blank_crate_and_picks_blank", 20, CreativeCrateTests::breaksBlank);
        tests.test("creativecrate_keeps_its_item_across_a_save_and_load", 1, CreativeCrateTests::survivesSave);
        tests.test("creativecrate_never_pushes_into_an_inventory_beside_it", 100, CreativeCrateTests::neverPushes);
    }

    private static void givesEndlessly(GameTestHelper helper) {
        place(helper, CRATE);
        set(helper, CRATE, new ItemStack(Items.IRON_INGOT, 5));
        var crate = handler(helper, CRATE);
        var iron = ItemResource.of(Items.IRON_INGOT);
        helper.assertTrue(crate.size() == 1, "the crate has " + crate.size() + " slots, expected one");
        for (int pull = 0; pull < PULLS; pull++) {
            try (var transaction = Transaction.openRoot()) {
                var taken = crate.extract(iron, 64, transaction);
                transaction.commit();
                helper.assertTrue(taken == 64, "pull " + pull + " took " + taken + " iron, expected 64");
            }
            helper.assertTrue(crate.getResource(0).equals(iron), "after pull " + pull + " the crate holds " + crate.getResource(0));
            helper.assertTrue(crate.getAmountAsLong(0) == 64, "after pull " + pull + " the crate reports " + crate.getAmountAsLong(0));
        }
        try (var transaction = Transaction.openRoot()) {
            helper.assertTrue(crate.extract(iron, 10, transaction) == 10, "a pull of 10 did not take 10");
            helper.assertTrue(crate.extract(ItemResource.of(Items.COPPER_INGOT), 1, transaction) == 0,
              "a crate set to iron gave copper");
        }
        helper.succeed();
    }

    private static ItemStack namedPotion() {
        var potion = PotionContents.createItemStack(Items.POTION, Potions.HEALING);
        potion.set(DataComponents.CUSTOM_NAME, Component.literal("Test brew"));
        return potion;
    }

    private static void givesWithData(GameTestHelper helper) {
        place(helper, CRATE);
        var potion = namedPotion();
        set(helper, CRATE, potion);
        var crate = handler(helper, CRATE);
        var resource = crate.getResource(0);
        helper.assertTrue(ItemStack.isSameItemSameComponents(resource.toStack(), potion),
          "the crate holds " + resource.toStack() + ", not the named healing potion it was set to");
        try (var transaction = Transaction.openRoot()) {
            helper.assertTrue(crate.extract(ItemResource.of(potion), 1, transaction) == 1, "the set potion could not be pulled");
            var water = PotionContents.createItemStack(Items.POTION, Potions.WATER);
            helper.assertTrue(crate.extract(ItemResource.of(water), 1, transaction) == 0, "a water bottle was pulled from a crate set to healing");
            helper.assertTrue(crate.extract(ItemResource.of(Items.POTION), 1, transaction) == 0, "a plain potion was pulled from a crate set to healing");
        }
        helper.succeed();
    }

    private static void givesUnstackable(GameTestHelper helper) {
        place(helper, CRATE);
        set(helper, CRATE, new ItemStack(Items.IRON_SWORD));
        var crate = handler(helper, CRATE);
        helper.assertTrue(crate.getAmountAsLong(0) == 1, "a sword crate reports " + crate.getAmountAsLong(0) + ", expected 1");
        for (int pull = 0; pull < 3; pull++) {
            try (var transaction = Transaction.openRoot()) {
                var taken = crate.extract(ItemResource.of(Items.IRON_SWORD), 64, transaction);
                transaction.commit();
                helper.assertTrue(taken == 1, "a pull of 64 swords took " + taken + ", expected 1");
            }
        }
        helper.succeed();
    }

    private static void voidsInserts(GameTestHelper helper) {
        place(helper, CRATE);
        var crate = handler(helper, CRATE);
        // Unset, it is a void.
        insertsAll(helper, crate, new ItemStack(Items.DIRT, 64));
        helper.assertTrue(crate.getResource(0).isEmpty(), "an unset crate holds " + crate.getResource(0) + " after dirt went in");

        set(helper, CRATE, new ItemStack(Items.IRON_INGOT));
        insertsAll(helper, crate, new ItemStack(Items.IRON_INGOT, 64));
        insertsAll(helper, crate, new ItemStack(Items.COPPER_INGOT, 64));
        insertsAll(helper, crate, namedPotion());
        helper.assertTrue(crate.getResource(0).equals(ItemResource.of(Items.IRON_INGOT)) && crate.getAmountAsLong(0) == 64,
          "after inserts the crate holds " + crate.getAmountAsLong(0) + " " + crate.getResource(0) + ", expected 64 iron");
        helper.succeed();
    }

    private static void insertsAll(GameTestHelper helper, ResourceHandler<ItemResource> crate, ItemStack stack) {
        try (var transaction = Transaction.openRoot()) {
            var inserted = crate.insert(ItemResource.of(stack), stack.getCount(), transaction);
            transaction.commit();
            helper.assertTrue(inserted == stack.getCount(), "the crate took " + inserted + " of " + stack);
        }
    }

    private static void unsetGivesNothing(GameTestHelper helper) {
        place(helper, CRATE);
        var crate = handler(helper, CRATE);
        helper.assertTrue(crate.getResource(0).isEmpty() && crate.getAmountAsLong(0) == 0,
          "an unset crate reports " + crate.getAmountAsLong(0) + " " + crate.getResource(0));
        try (var transaction = Transaction.openRoot()) {
            helper.assertTrue(crate.extract(ItemResource.of(Items.STONE), 1, transaction) == 0, "an unset crate gave stone");
        }
        helper.succeed();
    }

    private static void loaderPulls(GameTestHelper helper) {
        place(helper, SOURCE);
        set(helper, SOURCE, new ItemStack(Items.COBBLESTONE));
        helper.setBlock(TARGET, Blocks.CHEST);
        line(helper);
        helper.succeedWhen(() -> {
            var delivered = count(helper.getBlockEntity(TARGET, ChestBlockEntity.class));
            if (delivered < DELIVERED) throw helper.assertionException(TARGET, "the line delivered " + delivered + " of " + DELIVERED);
        });
    }

    // The source is a chest, so what left it is counted, and the line can hold only so much of it.
    private static void lineNeverBacksUp(GameTestHelper helper) {
        helper.setBlock(SOURCE, Blocks.CHEST);
        Container source = helper.getBlockEntity(SOURCE, ChestBlockEntity.class);
        for (int slot = 0; slot < source.getContainerSize(); slot++) source.setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
        place(helper, TARGET);
        set(helper, TARGET, new ItemStack(Items.IRON_INGOT));
        line(helper);
        helper.succeedWhen(() -> {
            var left = SUPPLY - count(source);
            if (left < BACKED_UP + DELIVERED) {
                throw helper.assertionException(TARGET, left + " items left the chest, expected more than the line holds");
            }
            int onLine = 0;
            for (int index = 0; index < TILES; index++) {
                onLine += helper.getBlockEntity(FIRST_TILE.east(index), BeltTileBlockEntity.class).held().size();
            }
            if (onLine >= BACKED_UP) throw helper.assertionException(TARGET, "the line backed up into the crate with " + onLine);
        });
    }

    private static void hopperPulls(GameTestHelper helper) {
        var above = CRATE.above();
        place(helper, above);
        set(helper, above, new ItemStack(Items.REDSTONE));
        helper.setBlock(CRATE, Blocks.HOPPER);
        helper.succeedWhen(() -> {
            var pulled = count(helper.getBlockEntity(CRATE, HopperBlockEntity.class));
            if (pulled < 5) throw helper.assertionException(CRATE, "the hopper pulled " + pulled + " of 5 redstone");
        });
    }

    private static void feederTakes(GameTestHelper helper) {
        var head = new BlockPos(2, 1, 2);
        var feeder = head.east();
        var tail = feeder.east();
        place(helper, head);
        set(helper, head, new ItemStack(Items.GLOWSTONE_DUST));
        helper.setBlock(tail, Blocks.CHEST);
        helper.setBlock(feeder, BlockContent.feederFor(BeltTier.BELT).defaultBlockState()
          .setValue(HorizontalDirectionalBlock.FACING, Direction.EAST));
        helper.succeedWhen(() -> {
            var carried = count(helper.getBlockEntity(tail, ChestBlockEntity.class));
            if (carried < 2) throw helper.assertionException(tail, "the feeder carried " + carried + " of 2 from the crate");
        });
    }

    // A fake player opens no screen, so this one keeps the menu the crate asked it to open.
    private static void useOpensMenu(GameTestHelper helper) {
        place(helper, CRATE);
        OpenedMenu opened = new OpenedMenu();
        var player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "beltworks_crate_opener")) {
            @Override
            public OptionalInt openMenu(@Nullable MenuProvider provider, @Nullable Consumer<RegistryFriendlyByteBuf> extraData) {
                if (provider != null) opened.menu = provider.createMenu(7, getInventory(), this);
                return OptionalInt.of(7);
            }
        };
        stand(helper, player);
        var crate = helper.absolutePos(CRATE);
        helper.getLevel().getBlockState(crate).useWithoutItem(helper.getLevel(), player,
          new BlockHitResult(Vec3.atCenterOf(crate).relative(Direction.UP, 0.5), Direction.UP, crate, false));
        helper.assertTrue(opened.menu instanceof CreativeCrateMenu, "a use on the crate opened " + opened.menu);
        var menu = (CreativeCrateMenu) opened.menu;
        helper.assertTrue(menu.crate().equals(crate) && menu.stillValid(player), "the menu opened is not the used crate's");
        helper.succeed();
    }

    private static final class OpenedMenu {
        @Nullable AbstractContainerMenu menu;
    }

    private static void menuSets(GameTestHelper helper) {
        place(helper, CRATE);
        var player = player(helper);
        var menu = new CreativeCrateMenu(1, player.getInventory(), helper.absolutePos(CRATE));
        var crate = handler(helper, CRATE);

        menu.setCarried(new ItemStack(Items.DIRT, 5));
        menu.clicked(CreativeCrateMenu.CRATE_SLOT, 0, ContainerInput.PICKUP, player);
        helper.assertTrue(crate.getResource(0).equals(ItemResource.of(Items.DIRT)), "a click with dirt set the crate to " + crate.getResource(0));
        helper.assertTrue(menu.getCarried().is(Items.DIRT) && menu.getCarried().getCount() == 5, "the click took from the cursor: " + menu.getCarried());
        helper.assertTrue(menu.shown().is(Items.DIRT) && menu.shown().getCount() == 1, "the slot shows " + menu.shown());

        var potion = namedPotion();
        menu.setCarried(potion.copy());
        menu.clicked(CreativeCrateMenu.CRATE_SLOT, 1, ContainerInput.PICKUP, player);
        helper.assertTrue(ItemStack.isSameItemSameComponents(crate.getResource(0).toStack(), potion), "a right click set " + crate.getResource(0));

        // Nothing else done to the slot changes it, or lands an item in it.
        menu.setCarried(new ItemStack(Items.STONE));
        menu.clicked(CreativeCrateMenu.CRATE_SLOT, 0, ContainerInput.QUICK_MOVE, player);
        menu.clicked(CreativeCrateMenu.CRATE_SLOT, 0, ContainerInput.PICKUP_ALL, player);
        helper.assertTrue(ItemStack.isSameItemSameComponents(crate.getResource(0).toStack(), potion) && menu.getCarried().is(Items.STONE),
          "a shift-click or double-click on the slot changed it to " + crate.getResource(0));

        // A shift-click on an inventory stack sets it and moves nothing.
        menu.setCarried(ItemStack.EMPTY);
        player.getInventory().setItem(9, new ItemStack(Items.COBBLESTONE, 7));
        menu.clicked(1 + 0, 0, ContainerInput.QUICK_MOVE, player);
        helper.assertTrue(crate.getResource(0).equals(ItemResource.of(Items.COBBLESTONE)), "a shift-click on cobblestone set " + crate.getResource(0));
        helper.assertTrue(player.getInventory().getItem(9).is(Items.COBBLESTONE) && player.getInventory().getItem(9).getCount() == 7,
          "the shift-click moved the cobblestone: the slot holds " + player.getInventory().getItem(9));

        // An empty cursor clears it.
        menu.clicked(CreativeCrateMenu.CRATE_SLOT, 0, ContainerInput.PICKUP, player);
        helper.assertTrue(crate.getResource(0).isEmpty() && menu.shown().isEmpty(), "a click with an empty cursor left " + crate.getResource(0));
        helper.succeed();
    }

    private static void breaksBlank(GameTestHelper helper) {
        place(helper, CRATE);
        set(helper, CRATE, new ItemStack(Items.DIAMOND));
        var level = helper.getLevel();
        var crate = helper.absolutePos(CRATE);

        // A pick-block with data, as the server's own does for a creative player.
        var picked = level.getBlockState(crate).getCloneItemStack(crate, level, true, player(helper));
        var blockEntity = helper.getBlockEntity(CRATE, CreativeCrateBlockEntity.class);
        var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
        blockEntity.saveCustomOnly(output);
        blockEntity.removeComponentsFromTag(output);
        BlockItem.setBlockEntityData(picked, blockEntity.getType(), output);
        picked.applyComponents(blockEntity.collectComponents());
        helper.assertTrue(picked.is(ItemContent.CREATIVE_CRATE.get()) && picked.getComponentsPatch().isEmpty(),
          "a pick-block gave " + picked + " with " + picked.getComponentsPatch());

        player(helper).gameMode.destroyBlock(crate);
        helper.runAfterDelay(1, () -> {
            var drops = level.getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(2.0));
            helper.assertTrue(drops.size() == 1, drops.size() + " items dropped, expected the crate alone");
            var dropped = drops.getFirst().getItem();
            helper.assertTrue(dropped.is(ItemContent.CREATIVE_CRATE.get()) && dropped.getComponentsPatch().isEmpty(),
              "the broken crate dropped " + dropped + " with " + dropped.getComponentsPatch());
            helper.succeed();
        });
    }

    private static void survivesSave(GameTestHelper helper) {
        place(helper, CRATE);
        var potion = namedPotion();
        set(helper, CRATE, potion);
        var registries = helper.getLevel().registryAccess();
        CompoundTag saved = helper.getBlockEntity(CRATE, CreativeCrateBlockEntity.class).saveWithFullMetadata(registries);
        CompoundTag update = helper.getBlockEntity(CRATE, CreativeCrateBlockEntity.class).getUpdateTag(registries);

        helper.setBlock(CRATE, Blocks.AIR);
        place(helper, CRATE);
        helper.assertTrue(handler(helper, CRATE).getResource(0).isEmpty(), "a crate placed anew is set to " + handler(helper, CRATE).getResource(0));
        helper.getBlockEntity(CRATE, CreativeCrateBlockEntity.class)
          .loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, registries, saved));
        helper.assertTrue(ItemStack.isSameItemSameComponents(handler(helper, CRATE).getResource(0).toStack(), potion),
          "after a save and load the crate gives " + handler(helper, CRATE).getResource(0));

        // A client is sent the item, which it draws on the sides.
        var client = new CreativeCrateBlockEntity(helper.absolutePos(CRATE), helper.getBlockState(CRATE));
        client.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, registries, update));
        helper.assertTrue(ItemStack.isSameItemSameComponents(client.item(), potion), "the update tag carried " + client.item());
        helper.succeed();
    }

    private static void neverPushes(GameTestHelper helper) {
        place(helper, CRATE);
        set(helper, CRATE, new ItemStack(Items.COBBLESTONE));
        var beside = new BlockPos[] {CRATE.east(), CRATE.west(), CRATE.north(), CRATE.south(), CRATE.above()};
        for (var at : beside) helper.setBlock(at, Blocks.CHEST);
        helper.startSequence().thenIdle(90).thenExecute(() -> {
            for (var at : beside) {
                var held = count(helper.getBlockEntity(at, ChestBlockEntity.class));
                helper.assertTrue(held == 0, "the crate pushed " + held + " into the chest at " + at);
            }
        }).thenSucceed();
    }

    // Loader, three tiles running east, loader, between SOURCE and TARGET, placed as a player would.
    private static void line(GameTestHelper helper) {
        var player = player(helper);
        player.setYRot(Direction.EAST.toYRot());
        for (int index = 0; index < TILES; index++) {
            use(helper, player, new ItemStack(ItemContent.tileFor(BeltTier.BELT)), FIRST_TILE.east(index).below(), Direction.UP);
        }
        player.setShiftKeyDown(true);
        use(helper, player, new ItemStack(BlockContent.loaderFor(BeltTier.BELT).asItem()), SOURCE, Direction.EAST);
        use(helper, player, new ItemStack(BlockContent.loaderFor(BeltTier.BELT).asItem()), TARGET, Direction.WEST);
        player.setShiftKeyDown(false);
    }

    private static void use(GameTestHelper helper, ServerPlayer player, ItemStack stack, BlockPos on, Direction face) {
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockPos absolute = helper.absolutePos(on);
        player.gameMode.useItemOn(player, helper.getLevel(), stack, InteractionHand.MAIN_HAND,
          new BlockHitResult(Vec3.atCenterOf(absolute).relative(face, 0.5), face, absolute, false));
    }

    private static void place(GameTestHelper helper, BlockPos at) {
        helper.setBlock(at, BlockContent.CREATIVE_CRATE.get());
    }

    // Set as a player would, through the crate's menu.
    private static void set(GameTestHelper helper, BlockPos at, ItemStack stack) {
        var player = player(helper);
        new CreativeCrateMenu(0, player.getInventory(), helper.absolutePos(at)).setCrateItem(stack);
    }

    private static ResourceHandler<ItemResource> handler(GameTestHelper helper, BlockPos at) {
        var handler = helper.getLevel().getCapability(Capabilities.Item.BLOCK, helper.absolutePos(at), Direction.UP);
        if (handler == null) throw helper.assertionException(at, "the crate has no item handler");
        return handler;
    }

    private static ServerPlayer player(GameTestHelper helper) {
        var player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "beltworks_crate_player"));
        stand(helper, player);
        return player;
    }

    private static void stand(GameTestHelper helper, ServerPlayer player) {
        player.setGameMode(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        var at = helper.absoluteVec(STANDING.getBottomCenter());
        player.setPos(at.x, at.y, at.z);
    }

    private static int count(Container container) {
        int total = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) total += container.getItem(slot).getCount();
        return total;
    }
}
