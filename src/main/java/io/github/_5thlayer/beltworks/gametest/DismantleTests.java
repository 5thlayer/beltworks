// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.gametest;

import io.github._5thlayer.groundworks.DismantleSpan;
import io.github._5thlayer.groundworks.DismantleStart;
import io.github._5thlayer.groundworks.Dismantles;
import io.github._5thlayer.groundworks.Groundworks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import io.github._5thlayer.beltworks.BlockContent;
import io.github._5thlayer.beltworks.ItemContent;
import io.github._5thlayer.beltworks.blocks.BeltTileBlockEntity;
import io.github._5thlayer.beltworks.blocks.BeltWedgeBlock;
import io.github._5thlayer.beltworks.blocks.SplitterBlock;
import io.github._5thlayer.beltworks.model.BeltTier;
import io.github._5thlayer.beltworks.model.TransportLine;

/**
 * A Dismantle of the belt family, run by Groundworks (PlanetaryFactory #404): each test sneak-clicks
 * a start with a pickaxe, a dismantling tool by default, through the player's game mode, asks
 * {@link Dismantles#spanTo} for the end, clicks it, and holds the world, the inventory and the stored
 * start to the span. An accepted span leaves none of the tiles or wedges it draws standing and hands
 * the player a tile for each and every item they carried; a refused span changes no block, no slot
 * and no stored start, and names its reason on the action bar.
 */
final class DismantleTests {

    private static final BlockPos START = new BlockPos(2, 1, 3);
    private static final String OFF_LINE = "message.beltworks.dismantle_off_line";
    private static final String NOT_SAME_KIND = "message.groundworks.dismantle_not_same_kind";

    private DismantleTests() {
    }

    static void register(BeltGameTests.Registrar tests) {
        tests.test("a_dismantle_takes_up_a_straight_span_and_its_items", 20, helper ->
                takesUp(helper, row(helper, 6), START.east(1), START.east(4)));
        tests.test("a_dismantle_taken_end_to_start_takes_the_same_span", 20, helper ->
                takesUp(helper, row(helper, 6), START.east(4), START.east(1)));
        tests.test("a_dismantle_on_one_tile_takes_that_tile", 20, helper ->
                takesUp(helper, row(helper, 3), START.east(1), START.east(1)));
        tests.test("a_dismantle_follows_its_line_round_a_corner", 20, DismantleTests::corner);
        tests.test("a_dismantle_follows_its_line_up_a_slope_and_takes_its_wedges", 20, DismantleTests::slope);
        tests.test("a_dismantle_out_of_a_line_leaves_both_sides_carrying_and_delivering", 120, DismantleTests::middle);
        tests.test("a_dismantle_with_no_room_drops_the_rest_at_the_players_feet", 20, DismantleTests::fullInventory);
        tests.test("a_creative_dismantle_hands_over_nothing", 20, DismantleTests::creative);
        tests.test("a_dismantle_ending_on_a_splitter_changes_nothing", 20, helper -> refused(helper,
                () -> splitterAt(helper, START.east(3)), START.east(3), NOT_SAME_KIND));
        tests.test("a_dismantle_ending_beyond_a_splitter_changes_nothing", 20, helper -> refused(helper,
                () -> {
                    splitterAt(helper, START.east(3));
                    helper.setBlock(START.east(4), BeltTileTests.tile(BeltTier.BELT, Direction.EAST));
                }, START.east(4), OFF_LINE));
        tests.test("a_dismantle_ending_on_a_loader_changes_nothing", 20, helper -> refused(helper,
                () -> helper.setBlock(START.east(3), BeltTileTests.loader(BeltTier.BELT, Direction.WEST)), START.east(3),
                NOT_SAME_KIND));
        tests.test("a_dismantle_ending_on_another_line_changes_nothing", 20, helper -> refused(helper,
                () -> {
                    for (int i = 0; i < 3; i++) helper.setBlock(START.south(2).east(i), BeltTileTests.tile(BeltTier.BELT, Direction.EAST));
                }, START.south(2).east(1), OFF_LINE));
        tests.test("a_sneak_click_after_the_start_tile_broke_is_a_new_start", 20, helper ->
                staleStart(helper, tile -> helper.destroyBlock(tile), "broke"));
        tests.test("a_sneak_click_after_the_start_tile_turned_is_a_new_start", 20, helper ->
                staleStart(helper, tile -> helper.setBlock(tile, BeltTileTests.tile(BeltTier.BELT, Direction.SOUTH)), "turned"));
        tests.test("a_sneak_click_with_a_start_stored_moves_the_start", 20, DismantleTests::movesStart);
        tests.test("a_click_with_no_start_stored_takes_up_nothing", 20, DismantleTests::noStart);
        tests.test("a_sneak_use_in_the_air_clears_the_dismantle_start", 20, DismantleTests::clears);
    }

    private static void corner(GameTestHelper helper) {
        List<BlockPos> tiles = new ArrayList<>();
        for (int i = 2; i >= 1; i--) tiles.add(START.south(i));
        for (int i = 0; i < 3; i++) tiles.add(START.east(i));
        for (BlockPos tile : tiles) {
            Direction facing = tile.getX() == START.getX() && tile.getZ() > START.getZ() ? Direction.NORTH : Direction.EAST;
            helper.setBlock(tile, BeltTileTests.tile(BeltTier.BELT, facing));
        }
        carryOneEach(helper, tiles);
        takesUp(helper, tiles, tiles.getFirst(), tiles.getLast());
    }

    // Level, then a block up over air: the top stands on a wedge.
    private static void slope(GameTestHelper helper) {
        List<BlockPos> tiles = new ArrayList<>();
        int[] heights = {0, 0, 1, 1};
        for (int i = 0; i < heights.length; i++) {
            BlockPos at = START.east(i).above(heights[i]);
            helper.setBlock(at, BeltTileTests.tile(BeltTier.BELT, Direction.EAST));
            tiles.add(at);
        }
        if (!(helper.getBlockState(START.east(2)).getBlock() instanceof BeltWedgeBlock)) {
            helper.fail("the fixture's top stands on " + helper.getBlockState(START.east(2)) + ", not a wedge", START.east(2));
            return;
        }
        carryOneEach(helper, tiles);
        helper.runAfterDelay(2, () -> {
            var player = started(helper, tiles.getFirst());
            var span = accepted(helper, player, tiles.getLast(), tiles.size(), tiles.size());
            if (span == null) return;
            var wedges = span.draws().stream().filter(pos -> !span.takes().contains(pos)).toList();
            if (!wedges.equals(List.of(helper.absolutePos(START.east(2))))) {
                helper.fail("the span draws wedges " + wedges + ", not the one under the top", START.east(2));
                return;
            }
            helper.succeed();
        });
    }

    // Six tiles, each carrying one ingot; the middle four taken up, then a loader and a chest put
    // past each side.
    private static void middle(GameTestHelper helper) {
        List<BlockPos> tiles = row(helper, 6);
        helper.startSequence().thenIdle(2).thenExecute(() -> {
            var player = started(helper, START.east(1));
            accepted(helper, player, START.east(4), 4, 4);
        }).thenIdle(3).thenExecute(() -> {
            int upstream = heldOn(helper, tiles.getFirst());
            int downstream = heldOn(helper, tiles.getLast());
            if (upstream != 1 || downstream != 1) {
                helper.fail("the tiles either side of the span hold " + upstream + " and " + downstream + ", not 1 and 1", START);
            }
            helper.setBlock(START.east(1), BeltTileTests.loader(BeltTier.BELT, Direction.WEST));
            helper.setBlock(START.east(2), Blocks.CHEST);
            helper.setBlock(START.east(6), BeltTileTests.loader(BeltTier.BELT, Direction.WEST));
            helper.setBlock(START.east(7), Blocks.CHEST);
        }).thenIdle(80).thenExecute(() -> {
            int upstream = BeltTileTests.count(BeltTileTests.chest(helper, START.east(2)));
            int downstream = BeltTileTests.count(BeltTileTests.chest(helper, START.east(7)));
            if (upstream != 1 || downstream != 1) {
                helper.fail("the two sides delivered " + upstream + " and " + downstream + ", not 1 and 1", START);
            }
        }).thenSucceed();
    }

    private static void fullInventory(GameTestHelper helper) {
        List<BlockPos> tiles = row(helper, 3);
        helper.runAfterDelay(2, () -> {
            var player = started(helper, tiles.getFirst());
            for (int slot = 0; slot < player.getInventory().getNonEquipmentItems().size(); slot++) {
                if (player.getInventory().getItem(slot).isEmpty()) {
                    player.getInventory().setItem(slot, new ItemStack(Items.DIRT, 64));
                }
            }
            click(helper, player, tiles.getLast());
            for (BlockPos tile : tiles) {
                if (!helper.getBlockState(tile).isAir()) {
                    helper.fail("a dismantle with no room left " + helper.getBlockState(tile), tile);
                    return;
                }
            }
            Map<Item, Integer> dropped = new HashMap<>();
            for (ItemEntity entity : helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                    new AABB(player.blockPosition()).inflate(3))) {
                dropped.merge(entity.getItem().getItem(), entity.getItem().getCount(), Integer::sum);
            }
            Map<Item, Integer> expected = Map.of(ItemContent.tileFor(BeltTier.BELT), 3, Items.IRON_INGOT, 3);
            if (!expected.equals(dropped)) {
                helper.fail("a dismantle with no room dropped " + dropped + " at the player's feet, not " + expected, START);
                return;
            }
            helper.succeed();
        });
    }

    private static void creative(GameTestHelper helper) {
        List<BlockPos> tiles = row(helper, 3);
        helper.runAfterDelay(2, () -> {
            var player = started(helper, tiles.getFirst());
            player.setGameMode(GameType.CREATIVE);
            click(helper, player, tiles.getLast());
            for (BlockPos tile : tiles) {
                if (!helper.getBlockState(tile).isAir()) {
                    helper.fail("a creative dismantle left " + helper.getBlockState(tile), tile);
                    return;
                }
            }
            Map<Item, Integer> carried = inventory(player);
            carried.remove(player.getMainHandItem().getItem());
            if (!carried.isEmpty()) {
                helper.fail("a creative dismantle handed over " + carried, START);
                return;
            }
            helper.succeed();
        });
    }

    private static void refused(GameTestHelper helper, Runnable setUp, BlockPos end, String reason) {
        row(helper, 3);
        setUp.run();
        helper.runAfterDelay(2, () -> {
            var player = started(helper, START);
            var span = spanOf(helper, player, end);
            if (span == null || !span.isRefused() || !span.takes().isEmpty()) {
                helper.fail("an end off the start's line was planned as " + (span == null ? "nothing" : span.takes()), end);
                return;
            }
            Map<BlockPos, BlockState> before = world(helper);
            Map<Item, Integer> carried = inventory(player);
            var stored = player.getMainHandItem().getComponents();
            player.heard.clear();
            click(helper, player, end);

            Map<BlockPos, BlockState> after = world(helper);
            List<BlockPos> changed = before.keySet().stream().filter(pos -> before.get(pos) != after.get(pos)).toList();
            if (!changed.isEmpty()) {
                helper.fail("a refused dismantle changed " + changed.size() + " blocks, first at " + changed.getFirst(), end);
                return;
            }
            if (!carried.equals(inventory(player)) || !Objects.equals(stored, player.getMainHandItem().getComponents())) {
                helper.fail("a refused dismantle changed the player's inventory or the stored start", end);
                return;
            }
            if (!player.heard.equals(List.of(reason))) {
                helper.fail("the player was told " + player.heard + ", not " + reason, end);
                return;
            }
            helper.succeed();
        });
    }

    /**
     * The start's tile is changed by {@code change}, which the failures call {@code how}. A click is
     * then no dismantle's, since a live start's would be refused off its line, and the next
     * sneak-click stores a fresh start.
     */
    private static void staleStart(GameTestHelper helper, Consumer<BlockPos> change, String how) {
        List<BlockPos> tiles = row(helper, 4);
        var player = started(helper, tiles.getFirst());
        change.accept(tiles.getFirst());
        player.heard.clear();
        click(helper, player, tiles.get(2));
        if (!player.heard.isEmpty()) {
            helper.fail("a click after the start " + how + " was answered with " + player.heard + ", as if the start were live", tiles.get(2));
            return;
        }
        sneakClick(helper, player, tiles.get(2));
        if (!helper.absolutePos(tiles.get(2)).equals(storedStart(player))) {
            helper.fail("a click after the start " + how + " stored " + storedStart(player) + ", not the clicked tile", tiles.get(2));
            return;
        }
        for (BlockPos tile : tiles.subList(1, tiles.size())) {
            if (!(helper.getLevel().getBlockEntity(helper.absolutePos(tile)) instanceof BeltTileBlockEntity)) {
                helper.fail("a click after the start " + how + " took up a tile", tile);
                return;
            }
        }
        helper.succeed();
    }

    private static void movesStart(GameTestHelper helper) {
        List<BlockPos> tiles = row(helper, 4);
        var player = started(helper, tiles.getFirst());
        sneakClick(helper, player, tiles.get(2));
        if (!helper.absolutePos(tiles.get(2)).equals(storedStart(player))) {
            helper.fail("a sneak-click with a start stored left " + storedStart(player) + " as the start, not the clicked tile", tiles.get(2));
            return;
        }
        for (BlockPos tile : tiles) {
            if (!(helper.getLevel().getBlockEntity(helper.absolutePos(tile)) instanceof BeltTileBlockEntity)) {
                helper.fail("a sneak-click with a start stored took up a tile", tile);
                return;
            }
        }
        helper.succeed();
    }

    private static void noStart(GameTestHelper helper) {
        List<BlockPos> tiles = row(helper, 3);
        var player = new ListeningPlayer(helper);
        player.setGameMode(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
        click(helper, player, tiles.get(1));
        if (storedStart(player) != null) {
            helper.fail("a click with no start stored stored one", tiles.get(1));
            return;
        }
        for (BlockPos tile : tiles) {
            if (!(helper.getLevel().getBlockEntity(helper.absolutePos(tile)) instanceof BeltTileBlockEntity)) {
                helper.fail("a click with no start stored took up a tile", tile);
                return;
            }
        }
        helper.succeed();
    }

    private static void clears(GameTestHelper helper) {
        List<BlockPos> tiles = row(helper, 3);
        var player = started(helper, tiles.getFirst());
        player.setShiftKeyDown(true);
        player.gameMode.useItem(player, helper.getLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND);
        if (storedStart(player) != null) {
            helper.fail("a sneak-use in the air left the dismantle's start stored", tiles.getFirst());
            return;
        }
        helper.succeed();
    }

    /**
     * Asks the span of a click at {@code end}, clicks, and holds the world to it: none of the tiles
     * or wedges it draws standing, {@code tiles} tile items and {@code items} ingots handed over, and
     * the start cleared.
     */
    private static void takesUp(GameTestHelper helper, List<BlockPos> tiles, BlockPos start, BlockPos end) {
        int span = Math.abs(tiles.indexOf(end) - tiles.indexOf(start)) + 1;
        helper.runAfterDelay(2, () -> {
            var player = started(helper, start);
            if (accepted(helper, player, end, span, span) != null) helper.succeed();
        });
    }

    private static @Nullable DismantleSpan accepted(GameTestHelper helper, ListeningPlayer player, BlockPos end, int tiles, int items) {
        var span = spanOf(helper, player, end);
        if (span == null || span.isRefused() || span.takes().size() != tiles) {
            helper.fail("the dismantle to " + end + " was planned as " + (span == null ? "nothing"
                    : span.isRefused() ? span.refusal() : span.takes().size() + " tiles"), end);
            return null;
        }
        click(helper, player, end);
        for (BlockPos pos : span.draws()) {
            if (!helper.getLevel().getBlockState(pos).isAir()) {
                helper.fail("the span drew " + pos + " and the click left " + helper.getLevel().getBlockState(pos), helper.relativePos(pos));
                return null;
            }
        }
        Map<Item, Integer> expected = Map.of(ItemContent.tileFor(BeltTier.BELT), tiles, Items.IRON_INGOT, items);
        Map<Item, Integer> carried = inventory(player);
        carried.remove(player.getMainHandItem().getItem());
        if (!expected.equals(carried)) {
            helper.fail("the dismantle handed over " + carried + ", not " + expected, end);
            return null;
        }
        if (!helper.getEntities(EntityType.ITEM).isEmpty()) {
            helper.fail("the dismantle left items on the ground", end);
            return null;
        }
        if (storedStart(player) != null) {
            helper.fail("a dismantle left its start stored", end);
            return null;
        }
        return span;
    }

    /** {@code count} tier-1 tiles running east from {@link #START}, each carrying one ingot. */
    private static List<BlockPos> row(GameTestHelper helper, int count) {
        List<BlockPos> tiles = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            helper.setBlock(START.east(i), BeltTileTests.tile(BeltTier.BELT, Direction.EAST));
            tiles.add(START.east(i));
        }
        carryOneEach(helper, tiles);
        return tiles;
    }

    private static void carryOneEach(GameTestHelper helper, List<BlockPos> tiles) {
        for (BlockPos tile : tiles) {
            helper.getBlockEntity(tile, BeltTileBlockEntity.class)
                    .carry(List.of(new TransportLine.Share<>(0.5, new ItemStack(Items.IRON_INGOT))));
        }
    }

    private static void splitterAt(GameTestHelper helper, BlockPos left) {
        for (SplitterBlock.Side side : SplitterBlock.Side.values()) {
            helper.setBlock(side == SplitterBlock.Side.LEFT ? left : left.relative(Direction.EAST.getClockWise()),
                    BlockContent.splitterFor(BeltTier.BELT).defaultBlockState()
                            .setValue(HorizontalDirectionalBlock.FACING, Direction.EAST)
                            .setValue(SplitterBlock.SIDE, side));
        }
    }

    /** A survival player holding the pickaxe, standing on the platform, who has sneak-clicked a start at {@code start}. */
    private static ListeningPlayer started(GameTestHelper helper, BlockPos start) {
        var player = new ListeningPlayer(helper);
        player.setGameMode(GameType.SURVIVAL);
        player.setPos(helper.absoluteVec(new Vec3(START.getX() + 0.5, START.getY(), START.getZ() - 1.5)));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
        sneakClick(helper, player, start);
        if (!helper.absolutePos(start).equals(storedStart(player))) {
            helper.fail("a sneak-click on a tile stored " + storedStart(player) + " as the start", start);
        }
        return player;
    }

    /** Where the held stack's stored start is, live or not, or null when none is stored. */
    private static @Nullable BlockPos storedStart(ListeningPlayer player) {
        DismantleStart start = player.getMainHandItem().get(Groundworks.DISMANTLE_START.get());
        return start == null ? null : start.pos();
    }

    private static @Nullable DismantleSpan spanOf(GameTestHelper helper, ListeningPlayer player, BlockPos end) {
        return Dismantles.spanTo(helper.getLevel(), player.getMainHandItem(), helper.absolutePos(end));
    }

    private static void sneakClick(GameTestHelper helper, ListeningPlayer player, BlockPos at) {
        player.setShiftKeyDown(true);
        use(helper, player, at);
        player.setShiftKeyDown(false);
    }

    private static void click(GameTestHelper helper, ListeningPlayer player, BlockPos at) {
        player.setShiftKeyDown(false);
        use(helper, player, at);
    }

    private static void use(GameTestHelper helper, ListeningPlayer player, BlockPos at) {
        BlockPos absolute = helper.absolutePos(at);
        player.gameMode.useItemOn(player, helper.getLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(absolute), Direction.UP, absolute, false));
    }

    private static int heldOn(GameTestHelper helper, BlockPos tile) {
        int held = 0;
        for (ItemStack stack : helper.getBlockEntity(tile, BeltTileBlockEntity.class).heldHere()) held += stack.getCount();
        return held;
    }

    private static Map<BlockPos, BlockState> world(GameTestHelper helper) {
        return BlockPos.betweenClosedStream(helper.getBounds())
                .map(BlockPos::immutable)
                .collect(Collectors.toMap(Function.identity(), pos -> helper.getLevel().getBlockState(pos)));
    }

    private static Map<Item, Integer> inventory(ListeningPlayer player) {
        Map<Item, Integer> counts = new HashMap<>();
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (!stack.isEmpty()) counts.merge(stack.getItem(), stack.getCount(), Integer::sum);
        }
        return counts;
    }
}
