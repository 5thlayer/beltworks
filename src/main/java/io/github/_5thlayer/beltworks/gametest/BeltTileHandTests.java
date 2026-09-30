// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import io.github._5thlayer.beltworks.blocks.BeltTileBlockEntity;
import io.github._5thlayer.beltworks.model.BeltTier;
import io.github._5thlayer.beltworks.model.TransportLine;

/**
 * The belt hand and items on tiles (PlanetaryFactory #396). A hand held on a tile takes whatever is on it at the
 * line's rate, fed once a tick as the client resends it; an item entity put on a tile joins its
 * line and is carried along it to the far chest, round a corner and up and down a step
 * (PlanetaryFactory #417, #92). Rates are typed.
 */
final class BeltTileHandTests {

    // A chest behind an east-facing loader, eight tiles east, a west-facing loader, a chest.
    private static final BlockPos SOURCE = new BlockPos(1, 1, 3);
    private static final BlockPos FROM = new BlockPos(2, 1, 3);
    private static final BlockPos FIRST_TILE = new BlockPos(3, 1, 3);
    private static final int TILES = 8;
    private static final BlockPos TO = FIRST_TILE.east(TILES);
    private static final BlockPos TARGET = TO.east();
    private static final BlockPos HELD = FIRST_TILE.east(4);
    private static final BlockPos LAST = FIRST_TILE.east(TILES - 1);
    private static final BlockPos STANDING = new BlockPos(6, 1, 5);

    private static final int SUPPLY = 27 * 64;
    private static final int WARMUP_TICKS = 120;
    // A multiple of four ticks: a tier-1 line moves a whole number of items only every four.
    private static final int HOLD_TICKS = 100;
    private static final int TIER_1_ITEMS_PER_SECOND = 15;
    private static final int HELD_ITEMS = TIER_1_ITEMS_PER_SECOND * HOLD_TICKS / 20;
    private static final int BACKUP_TICKS = 400;

    // A tier-1 tile's line carries an item 0.09375 blocks a tick: six tiles, a loader and a chest take
    // it 64 ticks, and a corner or a step takes a little longer; these leave a wide margin.
    private static final BlockPos RIDE_FIRST = new BlockPos(1, 1, 1);
    private static final int RIDE_TILES = 6;
    private static final int RIDE_TICKS = 120;

    // A row east, a corner, then a column south.
    private static final BlockPos CORNER = new BlockPos(4, 1, 1);
    private static final int CORNER_TICKS = 140;

    // Two level tiles, a foot, then a top and two level tiles a block up; and the same down.
    private static final BlockPos STEP_RIDE_FIRST = new BlockPos(1, 1, 1);
    private static final int STEP_RIDE_TILES = 6;
    private static final int STEP_RIDE_AT = 3;
    private static final int STEP_RIDE_TICKS = 120;

    private BeltTileHandTests() {
    }

    static void register(BeltGameTests.Registrar tests) {
        tests.test("a_held_tile_fills_the_inventory_at_its_lines_rate", WARMUP_TICKS + HOLD_TICKS + 20,
                BeltTileHandTests::fillsTheInventory);
        tests.test("a_backed_up_lines_last_tile_held_gives_up_its_items", BACKUP_TICKS + 20 + 20,
                BeltTileHandTests::lastTileOfABackedUpLine);
        tests.test("an_item_placed_on_a_tile_joins_its_line_and_reaches_the_far_chest", RIDE_TICKS + 40,
                BeltTileHandTests::itemJoinsAndArrives);
        tests.test("an_item_placed_on_a_tile_joins_its_line_and_reaches_the_far_chest_round_a_corner", CORNER_TICKS + 40,
                BeltTileHandTests::itemJoinsAndArrivesRoundACorner);
        tests.test("an_item_placed_on_a_tile_joins_its_line_and_reaches_the_far_chest_up_a_step", STEP_RIDE_TICKS + 40,
                helper -> itemJoinsAndArrivesOverAStep(helper, true));
        tests.test("an_item_placed_on_a_tile_joins_its_line_and_reaches_the_far_chest_down_a_step", STEP_RIDE_TICKS + 40,
                helper -> itemJoinsAndArrivesOverAStep(helper, false));
    }

    // A line is built on the tick after its tiles are placed, and its head on the one after.
    private static final int SETTLE_TICKS = 5;
    // The item is dropped from a little above the tile and joins as it lands.
    private static final int FALL_TICKS = 15;

    // The item is put on once the line is built. It is gone from the world as soon as the line takes it, and is in
    // the far chest, alone, when it gets there.
    private static void arrives(GameTestHelper helper, BlockPos at, BlockPos first, BlockPos chest, int ticks) {
        ItemEntity[] placed = new ItemEntity[1];
        helper.startSequence()
                .thenIdle(SETTLE_TICKS)
                .thenExecute(() -> placed[0] = rider(helper, at))
                .thenIdle(FALL_TICKS)
                .thenExecute(() -> {
                    ItemEntity item = placed[0];
                    if (!item.isRemoved()) helper.fail("an item put on a tile is still in the world and did not join its line", first);
                    var line = helper.getBlockEntity(first, BeltTileBlockEntity.class).line();
                    if (line == null || line.size() != 1) helper.fail("the tile's line holds " + (line == null ? "no line" : line.size() + " items") + " after the item landed, expected 1", first);
                })
                .thenIdle(ticks)
                .thenExecute(() -> {
                    int arrived = BeltTileTests.count(BeltTileTests.chest(helper, chest));
                    if (arrived != 1) helper.fail("the far chest holds " + arrived + " items, expected the 1 that was put on the tile", chest);
                    var line = helper.getBlockEntity(first, BeltTileBlockEntity.class).line();
                    if (line != null && line.size() != 0) helper.fail("the line still holds " + line.size() + " items", first);
                    nothingOnTheGround(helper);
                })
                .thenSucceed();
    }

    // Up or down a step, the item is put on the row's first tile and the row's last tile feeds a loader.
    private static void itemJoinsAndArrivesOverAStep(GameTestHelper helper, boolean up) {
        List<BlockPos> tiles = new ArrayList<>();
        for (int tile = 0; tile < STEP_RIDE_TILES; tile++) {
            BlockPos at = STEP_RIDE_FIRST.east(tile);
            if (up == tile >= STEP_RIDE_AT) {
                helper.setBlock(at, Blocks.STONE);
                at = at.above();
            }
            helper.setBlock(at, BeltTileTests.tile(BeltTier.BELT, Direction.EAST));
            tiles.add(at);
        }
        BlockPos loader = tiles.getLast().east();
        helper.setBlock(loader, BeltTileTests.loader(BeltTier.BELT, Direction.WEST));
        helper.setBlock(loader.east(), Blocks.CHEST);
        arrives(helper, tiles.getFirst(), tiles.getFirst(), loader.east(), STEP_RIDE_TICKS);
    }

    private static void fillsTheInventory(GameTestHelper helper) {
        place(helper, false);
        ServerPlayer player = player(helper, "beltworks_tile_hand");
        BeltTileBlockEntity held = helper.getBlockEntity(HELD, BeltTileBlockEntity.class);

        int[] pastTheTile = new int[1];
        int[] arrivedBefore = new int[1];
        int[] suppliedBefore = new int[1];
        helper.startSequence()
                .thenIdle(WARMUP_TICKS)
                .thenExecute(() -> {
                    pastTheTile[0] = onTilesPast(helper);
                    arrivedBefore[0] = BeltTileTests.count(BeltTileTests.chest(helper, TARGET));
                    suppliedBefore[0] = BeltTileTests.count(BeltTileTests.chest(helper, SOURCE));
                    if (pastTheTile[0] == 0) helper.fail("nothing is past the held tile, so this proves nothing", TO);
                })
                .thenExecuteFor(HOLD_TICKS, () -> held.holdHand(player))
                .thenExecute(() -> {
                    int taken = cobblestone(player);
                    if (Math.abs(taken - HELD_ITEMS) > 1) {
                        helper.fail("holding a tier-1 tile for " + HOLD_TICKS + " ticks took " + taken
                                + " items, expected " + HELD_ITEMS, HELD);
                    }
                    int arrived = BeltTileTests.count(BeltTileTests.chest(helper, TARGET)) - arrivedBefore[0];
                    if (arrived != pastTheTile[0]) {
                        helper.fail(arrived + " items reached the line's end while a tile was held, expected the "
                                + pastTheTile[0] + " already past it", TARGET);
                    }
                    int loaded = suppliedBefore[0] - BeltTileTests.count(BeltTileTests.chest(helper, SOURCE));
                    if (Math.abs(loaded - HELD_ITEMS) > 1) {
                        helper.fail("the source loaded " + loaded + " items while the tile was held, expected "
                                + HELD_ITEMS, SOURCE);
                    }
                    nothingOnTheGround(helper);
                })
                .thenSucceed();
    }

    // The head sits flush with the last tile's front, so it is on the tile like any other item (PlanetaryFactory #408).
    private static void lastTileOfABackedUpLine(GameTestHelper helper) {
        place(helper, true);
        ServerPlayer player = player(helper, "beltworks_tile_hand_end");
        BeltTileBlockEntity last = helper.getBlockEntity(LAST, BeltTileBlockEntity.class);

        helper.startSequence()
                .thenIdle(BACKUP_TICKS)
                .thenExecute(() -> {
                    var line = last.line();
                    if (line == null || line.size() < line.capacity()) {
                        helper.fail("the line has not backed up, so this proves nothing about its end", LAST);
                    }
                })
                .thenExecuteFor(20, () -> last.holdHand(player))
                .thenExecute(() -> {
                    int taken = cobblestone(player);
                    if (Math.abs(taken - TIER_1_ITEMS_PER_SECOND) > 1) {
                        helper.fail("holding a backed-up line's last tile for a second took " + taken
                                + " items, expected " + TIER_1_ITEMS_PER_SECOND, LAST);
                    }
                    nothingOnTheGround(helper);
                })
                .thenSucceed();
    }

    private static void itemJoinsAndArrives(GameTestHelper helper) {
        for (int tile = 0; tile < RIDE_TILES; tile++) {
            helper.setBlock(RIDE_FIRST.east(tile), BeltTileTests.tile(BeltTier.BELT, Direction.EAST));
        }
        BlockPos loader = RIDE_FIRST.east(RIDE_TILES);
        helper.setBlock(loader, BeltTileTests.loader(BeltTier.BELT, Direction.WEST));
        helper.setBlock(loader.east(), Blocks.CHEST);
        arrives(helper, RIDE_FIRST, RIDE_FIRST, loader.east(), RIDE_TICKS);
    }

    // A row east, a corner turning south, and a column south that ends in a loader and a chest.
    private static void itemJoinsAndArrivesRoundACorner(GameTestHelper helper) {
        // Downstream first, since a tile's shape is set when what feeds it is placed.
        for (int tile = 3; tile >= 1; tile--) {
            helper.setBlock(CORNER.south(tile), BeltTileTests.tile(BeltTier.BELT, Direction.SOUTH));
        }
        helper.setBlock(CORNER, BeltTileTests.tile(BeltTier.BELT, Direction.SOUTH));
        for (int tile = 1; tile <= 2; tile++) {
            helper.setBlock(CORNER.west(tile), BeltTileTests.tile(BeltTier.BELT, Direction.EAST));
        }
        helper.setBlock(CORNER.south(4), BeltTileTests.loader(BeltTier.BELT, Direction.NORTH));
        helper.setBlock(CORNER.south(5), Blocks.CHEST);
        arrives(helper, CORNER.west(2), CORNER.west(2), CORNER.south(5), CORNER_TICKS);
    }

    private static ItemEntity rider(GameTestHelper helper, BlockPos tile) {
        // Dropped from above, so it lands on the belt's surface rather than inside it.
        Vec3 at = helper.absoluteVec(tile.getCenter()).add(0, 0.3, 0);
        // Not cobblestone: tests run side by side, and a neighbour counts the cobblestone lying near it.
        ItemEntity item = new ItemEntity(helper.getLevel(), at.x, at.y, at.z, new ItemStack(Items.STICK));
        item.setDeltaMovement(Vec3.ZERO);
        // Kept from any player nearby, but not never picked up, which a line leaves alone.
        item.setPickUpDelay(Short.MAX_VALUE - 1);
        helper.getLevel().addFreshEntity(item);
        return item;
    }

    private static void place(GameTestHelper helper, boolean targetFull) {
        helper.setBlock(SOURCE, Blocks.CHEST);
        helper.setBlock(FROM, BeltTileTests.loader(BeltTier.BELT, Direction.EAST));
        for (int tile = 0; tile < TILES; tile++) {
            helper.setBlock(FIRST_TILE.east(tile), BeltTileTests.tile(BeltTier.BELT, Direction.EAST));
        }
        helper.setBlock(TO, BeltTileTests.loader(BeltTier.BELT, Direction.WEST));
        helper.setBlock(TARGET, Blocks.CHEST);
        BeltTileTests.fill(helper, SOURCE, SUPPLY);
        if (targetFull) {
            var target = BeltTileTests.chest(helper, TARGET);
            for (int slot = 0; slot < target.getContainerSize(); slot++) target.setItem(slot, new ItemStack(Items.DIRT, 64));
        }
    }

    // By the hand's own point, since an item straddling the held tile's front is already past it.
    private static int onTilesPast(GameTestHelper helper) {
        var line = helper.getBlockEntity(FIRST_TILE, BeltTileBlockEntity.class).line();
        if (line == null) return 0;
        double point = TransportLine.handPoint(HELD.getX() - FIRST_TILE.getX());
        return (int) line.contents().entries().stream().filter(entry -> entry.position() > point).count();
    }

    // A player of its own: the shared fake player's inventory is every test's at once.
    private static ServerPlayer player(GameTestHelper helper, String name) {
        ServerPlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
        player.setGameMode(GameType.SURVIVAL);
        var at = helper.absoluteVec(STANDING.getBottomCenter());
        player.setPos(at.x, at.y, at.z);
        return player;
    }

    private static int cobblestone(ServerPlayer player) {
        return ContainerHelper.clearOrCountMatchingItems(player.getInventory(),
                stack -> stack.is(Items.COBBLESTONE), 0, true);
    }

    private static void nothingOnTheGround(GameTestHelper helper) {
        int lying = helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(2.0))
                .stream().mapToInt(entity -> entity.getItem().getCount()).sum();
        if (lying != 0) helper.fail(lying + " items lie on the ground");
    }
}
