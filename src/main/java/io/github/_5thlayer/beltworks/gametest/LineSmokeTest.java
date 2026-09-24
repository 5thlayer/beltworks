// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import io.github._5thlayer.beltworks.BlockContent;
import io.github._5thlayer.beltworks.ItemContent;
import io.github._5thlayer.beltworks.model.BeltTier;

/**
 * Items go from one chest to another along a belt: a loader pulls them onto a straight line of
 * tier-1 tiles and a loader at its far end pushes them into the second chest. A broken
 * registration, block entity type, tick or item capability lookup stops them arriving.
 */
final class LineSmokeTest {

    private static final int TILES = 3;
    private static final int ITEMS = 8;

    private static final BlockPos SOURCE = new BlockPos(1, 1, 1);
    private static final BlockPos FIRST_TILE = SOURCE.east(2);
    private static final BlockPos TARGET = FIRST_TILE.east(TILES + 1);

    private LineSmokeTest() {
    }

    static void register(BeltGameTests.Registrar tests) {
        tests.test("items_go_from_chest_to_chest_along_a_belt", 200, LineSmokeTest::chestToChest);
    }

    private static void chestToChest(GameTestHelper helper) {
        build(helper);
        chest(helper, SOURCE).setItem(0, new ItemStack(Items.COBBLESTONE, ITEMS));
        helper.succeedWhen(() -> {
            var arrived = chest(helper, TARGET).countItem(Items.COBBLESTONE);
            if (arrived != ITEMS) throw helper.assertionException(TARGET, arrived + " of " + ITEMS + " items arrived");
        });
    }

    /** Chest, loader, tiles running east, loader, chest, placed the way a player places them. */
    private static void build(GameTestHelper helper) {
        var player = FakePlayerFactory.getMinecraft(helper.getLevel());
        player.setGameMode(GameType.SURVIVAL);
        helper.setBlock(SOURCE, Blocks.CHEST);
        helper.setBlock(TARGET, Blocks.CHEST);
        player.setYRot(Direction.EAST.toYRot());
        for (var index = 0; index < TILES; index++) {
            use(helper, player, new ItemStack(ItemContent.tileFor(BeltTier.BELT)), FIRST_TILE.east(index).below(), Direction.UP);
        }
        // A sneak-click on a chest places the loader against it.
        player.setShiftKeyDown(true);
        use(helper, player, new ItemStack(BlockContent.loaderFor(BeltTier.BELT)), SOURCE, Direction.EAST);
        use(helper, player, new ItemStack(BlockContent.loaderFor(BeltTier.BELT)), TARGET, Direction.WEST);
        player.setShiftKeyDown(false);
    }

    private static void use(GameTestHelper helper, ServerPlayer player, ItemStack stack, BlockPos on, Direction face) {
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        var absolute = helper.absolutePos(on);
        player.gameMode.useItemOn(player, helper.getLevel(), stack, InteractionHand.MAIN_HAND,
          new BlockHitResult(Vec3.atCenterOf(absolute).relative(face, 0.5), face, absolute, false));
    }

    private static Container chest(GameTestHelper helper, BlockPos at) {
        return helper.getBlockEntity(at, ChestBlockEntity.class);
    }
}
