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
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundSetPlayerInventoryPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import io.github._5thlayer.beltworks.collision.BeltCollisionRegistry;
import io.github._5thlayer.beltworks.blocks.BeltDrop;
import io.github._5thlayer.beltworks.blocks.BeltTileBlockEntity;
import io.github._5thlayer.beltworks.model.BeltContents;
import io.github._5thlayer.beltworks.model.BeltTier;
import io.github._5thlayer.beltworks.model.TileShape;

/**
 * Factorio's drop onto a belt, by vanilla's Q (#91), and an item entity landing on a tile (#92). A fake player looks at a point of a tile and
 * drops through {@code ServerPlayer.drop}, the call both of vanilla's drop actions end in, so the
 * server's own look, range and hit are what decide where the item goes. Each test reads the line,
 * the world's item entities and the inventory before and after. The offsets are typed.
 *
 * <p>Items flow east along z = 1: a chest, a loader, three tiles, a loader and a chest. The player
 * stands south of the line, three blocks off it, looking at a point of a tile. An item entity
 * landing is spawned just above a tile's top, at rest, and left to fall.
 */
final class BeltDropTests {

    private static final BlockPos SOURCE = new BlockPos(1, 1, 1);
    private static final BlockPos FROM = new BlockPos(2, 1, 1);
    private static final BlockPos FIRST_TILE = new BlockPos(3, 1, 1);
    private static final int TILES = 3;
    private static final BlockPos MIDDLE = FIRST_TILE.east();
    private static final BlockPos TO = FIRST_TILE.east(TILES);
    private static final BlockPos TARGET = TO.east();

    // A tile is 6 sixteenths high, and its top is what a drop from above hits.
    private static final double TILE_TOP = 1 + 6 / 16.0;
    // South of the line, so the look is three blocks and a tile's height across.
    private static final double STANDING_Z = 4.5;
    private static final double FAR_STANDING_Z = 6.9;

    // A line is built on the tick after its tiles are placed, and its head on the one after.
    private static final int SETTLE_TICKS = 5;
    // Long enough for one item dropped mid-line to ride to the far loader and into the chest.
    private static final int ARRIVAL_TICKS = 120;
    private static final int HELD = 5;

    // The middle tile is the line's second, so its entries sit a tile further in. An item centred
    // at offset o on it sits at 1 + o minus half an item's length, which is 0.0625.
    private static final double MIDDLE_CENTRE = 1.4375;
    private static final double MIDDLE_DOWNSTREAM = 1.6875;
    private static final double MIDDLE_UPSTREAM = 1.1875;
    // A corner is the line's third tile, and its arc is a quarter turn: half way is 2.5, a quarter 2.25.
    private static final double CORNER_HALF = 2.4375;
    private static final double CORNER_QUARTER = 2.1875;
    // The two items each side of a drop's point are an item's length, 0.125, apart.
    private static final double ITEM_LENGTH = 0.125;
    // A look is cast in floats, so the point it reaches is this close to the point aimed at.
    private static final double AIM_ERROR = 0.01;

    // Landing: an item entity spawned a hair above a tile's top joins, or rests, within a tick or two.
    private static final double LANDING_HEIGHT = 0.05;
    private static final int LANDING_TICKS = 3;
    private static final int STACK = 5;
    // A tile holds eight, and a stack of twelve lands with room for less than all of it at once.
    private static final int LONG_STACK = 12;
    private static final int LINE_CAPACITY = TILES * 8;
    // Long enough for a full line to back up behind a full chest.
    private static final int BACK_UP_TICKS = 150;
    private static final int STILL_TICKS = 40;
    private static final int SUPPLY = 27 * 64;
    private static final int CARRIED_TICKS = 8;
    // A tier-1 line moves 0.09375 blocks a tick, which is 1.875 blocks a second.
    private static final double TILE_STEP = 0.09375;
    private static final double TILE_SPEED = 1.875;
    private static final double STILL_ERROR = 1e-3;
    private static final double CARRIED_ERROR = 0.05;

    private BeltDropTests() {
    }

    static void register(BeltGameTests.Registrar tests) {
        tests.test("q_aimed_at_an_empty_tile_of_a_loader_fed_line_takes_one_item_and_it_reaches_the_chest",
                SETTLE_TICKS + ARRIVAL_TICKS + 20, BeltDropTests::dropsOntoAnEmptyTile);
        tests.test("q_aimed_at_a_tiles_downstream_half_puts_the_item_further_along_than_its_upstream_half",
                SETTLE_TICKS + 20, BeltDropTests::downstreamIsFurtherThanUpstream);
        tests.test("q_aimed_at_an_occupied_point_on_a_tile_with_room_places_the_item_elsewhere_on_that_tile",
                SETTLE_TICKS + 20, BeltDropTests::occupiedPointGoesElsewhereOnTheTile);
        tests.test("q_aimed_just_downstream_of_an_occupied_point_places_the_item_right_after_it",
                SETTLE_TICKS + 20, BeltDropTests::justDownstreamGoesRightAfter);
        tests.test("q_aimed_at_a_full_tile_changes_nothing_throws_nothing_resyncs_the_slot_and_names_the_refusal",
                SETTLE_TICKS + 20, BeltDropTests::fullTileRefuses);
        tests.test("q_aimed_at_the_middle_of_a_corners_arc_drops_half_way_round_it",
                SETTLE_TICKS + 20, helper -> cornerProjectsOntoItsArc(helper, 0.3536, 0.6464, CORNER_HALF));
        tests.test("q_aimed_inside_the_corner_off_the_arc_drops_where_its_radius_meets_the_arc",
                SETTLE_TICKS + 20, helper -> cornerProjectsOntoItsArc(helper, 0.9, 0.1, CORNER_HALF));
        tests.test("q_aimed_a_quarter_of_the_way_round_a_corners_arc_drops_a_quarter_of_the_way_round_it",
                SETTLE_TICKS + 20, helper -> cornerProjectsOntoItsArc(helper, 0.1913, 0.5381, CORNER_QUARTER));
        tests.test("q_aimed_off_the_arc_on_the_radius_a_quarter_of_the_way_round_drops_a_quarter_of_the_way_round",
                SETTLE_TICKS + 20, helper -> cornerProjectsOntoItsArc(helper, 0.344, 0.1685, CORNER_QUARTER));
        tests.test("q_aimed_at_a_loader_throws_an_item_entity_as_vanilla_does",
                SETTLE_TICKS + 20, helper -> throwsWhenAimedAt(helper, FROM.getCenter()));
        tests.test("q_aimed_at_the_floor_throws_an_item_entity_as_vanilla_does",
                SETTLE_TICKS + 20, helper -> throwsWhenAimedAt(helper, new Vec3(12.5, 1, 3.5)));
        tests.test("q_aimed_at_nothing_throws_an_item_entity_as_vanilla_does",
                SETTLE_TICKS + 20, BeltDropTests::throwsWhenAimedAtNothing);
        tests.test("q_aimed_at_a_tile_beyond_the_block_interaction_range_throws_an_item_entity_as_vanilla_does",
                SETTLE_TICKS + 20, BeltDropTests::throwsBeyondRange);
        tests.test("q_aimed_at_a_tile_a_raised_block_interaction_range_reaches_drops_onto_it",
                SETTLE_TICKS + 20, BeltDropTests::raisedRangeReaches);
        tests.test("q_aimed_at_a_tile_behind_a_block_throws_an_item_entity_as_vanilla_does",
                SETTLE_TICKS + 20, BeltDropTests::throwsBehindABlock);
        tests.test("ctrl_q_aimed_at_a_tile_throws_the_whole_stack_as_vanilla_does",
                SETTLE_TICKS + 20, BeltDropTests::controlQThrowsTheStack);
        tests.test("an_item_entity_landed_on_a_tile_with_room_is_gone_within_a_tick_and_the_line_holds_it_until_it_reaches_the_chest",
                LANDING_TICKS + ARRIVAL_TICKS + 20 + SETTLE_TICKS, BeltDropTests::landsOnAnEmptyTile);
        tests.test("an_item_entity_nobody_may_pick_up_landed_on_a_tile_with_room_stays_an_entity",
                SETTLE_TICKS + LANDING_TICKS + 20, BeltDropTests::neverPickedUpStaysAnEntity);
        tests.test("a_stack_landed_on_a_tile_with_room_joins_the_line_as_one_entry_per_item_and_all_reach_the_chest",
                SETTLE_TICKS + LANDING_TICKS + ARRIVAL_TICKS + 20, BeltDropTests::stackLandsOnAnEmptyTile);
        tests.test("a_stack_landed_on_a_tile_with_room_for_part_of_it_rests_until_the_rest_joins_and_nothing_is_lost",
                SETTLE_TICKS + ARRIVAL_TICKS + 20, BeltDropTests::longStackLandsOnAnEmptyTile);
        tests.test("an_item_entity_landed_on_a_full_backed_up_line_stands_still_then_joins_once_the_chest_is_drained",
                SETTLE_TICKS + BACK_UP_TICKS + STILL_TICKS + ARRIVAL_TICKS + 20, BeltDropTests::landsOnABackedUpLine);
        tests.test("an_item_entity_landed_on_a_full_moving_line_is_carried_with_the_items_under_it",
                SETTLE_TICKS + BACK_UP_TICKS + CARRIED_TICKS + 20, BeltDropTests::landsOnAMovingLine);
        tests.test("a_player_on_a_tile_is_carried_at_the_tiles_speed_while_its_line_is_backed_up",
                SETTLE_TICKS + BACK_UP_TICKS + 20, BeltDropTests::playerIsCarriedAtTheTilesSpeed);
    }

    // The middle tile's centre, then three items along a line fed by a loader from an empty chest.
    private static void dropsOntoAnEmptyTile(GameTestHelper helper) {
        place(helper);
        Dropper player = player(helper);
        helper.startSequence()
                .thenIdle(SETTLE_TICKS)
                .thenExecute(() -> {
                    expectLine(helper, 0, "before the drop");
                    expectInventory(helper, player, HELD, "before the drop");
                    expectItemEntities(helper, 0, "before the drop");
                    expectCount(helper, TARGET, 0, "before the drop");

                    aim(helper, player, onTile(MIDDLE, 0.5, 0.5), STANDING_Z);
                    player.drop(false);

                    expectInventory(helper, player, HELD - 1, "after the drop");
                    expectItemEntities(helper, 0, "after the drop");
                    expectLine(helper, 1, "after the drop");
                    double at = first(helper, player, "after the drop");
                    if (Math.abs(at - MIDDLE_CENTRE) > AIM_ERROR) {
                        helper.fail("a drop aimed at the middle of the middle tile sat at " + at + ", expected " + MIDDLE_CENTRE, MIDDLE);
                    }
                    if (!player.actionBar.isEmpty()) helper.fail("an accepted drop said " + player.actionBar, MIDDLE);
                })
                .thenExecuteFor(ARRIVAL_TICKS, () -> expectItemEntities(helper, 0, "while the item rode"))
                .thenExecute(() -> {
                    expectCount(helper, TARGET, 1, "after the ride");
                    expectLine(helper, 0, "after the ride");
                    expectInventory(helper, player, HELD - 1, "after the ride");
                })
                .thenSucceed();
    }

    private static void downstreamIsFurtherThanUpstream(GameTestHelper helper) {
        place(helper);
        Dropper player = player(helper);
        helper.startSequence()
                .thenIdle(SETTLE_TICKS)
                .thenExecute(() -> {
                    aim(helper, player, onTile(MIDDLE, 0.75, 0.5), STANDING_Z);
                    player.drop(false);
                    double downstream = first(helper, player, "after a drop at the downstream half");
                    aim(helper, player, onTile(MIDDLE, 0.25, 0.5), STANDING_Z);
                    player.drop(false);

                    List<Double> entries = positions(helper);
                    if (entries.size() != 2) helper.fail("two drops put " + entries.size() + " items on the line", MIDDLE);
                    if (Math.abs(downstream - MIDDLE_DOWNSTREAM) > AIM_ERROR) {
                        helper.fail("a drop aimed at the downstream half sat at " + downstream + ", expected " + MIDDLE_DOWNSTREAM, MIDDLE);
                    }
                    if (Math.abs(entries.getFirst() - MIDDLE_UPSTREAM) > AIM_ERROR) {
                        helper.fail("a drop aimed at the upstream half sat at " + entries.getFirst() + ", expected " + MIDDLE_UPSTREAM, MIDDLE);
                    }
                    expectInventory(helper, player, HELD - 2, "after two drops");
                    expectItemEntities(helper, 0, "after two drops");
                })
                .thenSucceed();
    }

    // The second drop is aimed at exactly where the first sits.
    private static void occupiedPointGoesElsewhereOnTheTile(GameTestHelper helper) {
        place(helper);
        Dropper player = player(helper);
        helper.startSequence()
                .thenIdle(SETTLE_TICKS)
                .thenExecute(() -> {
                    aim(helper, player, onTile(MIDDLE, 0.5, 0.5), STANDING_Z);
                    player.drop(false);
                    double first = first(helper, player, "after the first drop");
                    player.drop(false);

                    List<Double> entries = positions(helper);
                    if (entries.size() != 2) helper.fail("two drops put " + entries.size() + " items on the line", MIDDLE);
                    double apart = entries.get(1) - entries.get(0);
                    if (Math.abs(apart - ITEM_LENGTH) > 1e-6) {
                        helper.fail("a drop aimed at a taken point sat " + apart + " from the item there, expected " + ITEM_LENGTH, MIDDLE);
                    }
                    if (!entries.contains(first)) helper.fail("the first item moved from " + first + " to " + entries, MIDDLE);
                    if (entries.getFirst() < 1 || entries.getLast() > 1 + 1 - ITEM_LENGTH) {
                        helper.fail("a drop aimed at the middle tile left the tile, at " + entries, MIDDLE);
                    }
                    expectInventory(helper, player, HELD - 2, "after two drops");
                    expectItemEntities(helper, 0, "after two drops");
                })
                .thenSucceed();
    }

    private static void justDownstreamGoesRightAfter(GameTestHelper helper) {
        place(helper);
        Dropper player = player(helper);
        helper.startSequence()
                .thenIdle(SETTLE_TICKS)
                .thenExecute(() -> {
                    aim(helper, player, onTile(MIDDLE, 0.5, 0.5), STANDING_Z);
                    player.drop(false);
                    aim(helper, player, onTile(MIDDLE, 0.53, 0.5), STANDING_Z);
                    player.drop(false);

                    List<Double> entries = positions(helper);
                    if (entries.size() != 2) helper.fail("two drops put " + entries.size() + " items on the line", MIDDLE);
                    if (Math.abs(entries.get(0) - MIDDLE_CENTRE) > AIM_ERROR
                            || Math.abs(entries.get(1) - (MIDDLE_CENTRE + ITEM_LENGTH)) > AIM_ERROR) {
                        helper.fail("drops aimed at 0.5 then 0.53 sat at " + entries + ", expected "
                                + MIDDLE_CENTRE + " and " + (MIDDLE_CENTRE + ITEM_LENGTH), MIDDLE);
                    }
                })
                .thenSucceed();
    }

    // Eight items abreast a tile leave no gap. The tile is filled straight onto its line, in the one tick.
    private static void fullTileRefuses(GameTestHelper helper) {
        place(helper);
        Dropper player = player(helper);
        BeltTileBlockEntity tile = helper.getBlockEntity(MIDDLE, BeltTileBlockEntity.class);
        helper.startSequence()
                .thenIdle(SETTLE_TICKS)
                .thenExecute(() -> {
                    for (int slot = 0; slot < 8; slot++) {
                        if (!tile.dropOnto(new ItemStack(Items.COBBLESTONE), slot / 8.0 + 0.0625)) {
                            helper.fail("the tile refused item " + slot + " of the eight it holds", MIDDLE);
                        }
                    }
                    List<Double> before = positions(helper);
                    expectLine(helper, 8, "before the drop");

                    aim(helper, player, onTile(MIDDLE, 0.5, 0.5), STANDING_Z);
                    player.drop(false);

                    expectInventory(helper, player, HELD, "after a refused drop");
                    expectItemEntities(helper, 0, "after a refused drop");
                    expectLine(helper, 8, "after a refused drop");
                    if (!positions(helper).equals(before)) {
                        helper.fail("a refused drop moved the line's items from " + before + " to " + positions(helper), MIDDLE);
                    }
                    if (!player.actionBar.equals(List.of(BeltDrop.FULL))) {
                        helper.fail("a refused drop said " + player.actionBar + " on the action bar, expected [" + BeltDrop.FULL + "]", MIDDLE);
                    }
                    // Vanilla's client has taken the item from its copy, so the slot is sent back as the server has it.
                    List<ClientboundSetPlayerInventoryPacket> resent = player.sent.stream()
                            .filter(ClientboundSetPlayerInventoryPacket.class::isInstance)
                            .map(ClientboundSetPlayerInventoryPacket.class::cast).toList();
                    if (resent.size() != 1 || resent.getFirst().slot() != 0 || resent.getFirst().contents().getCount() != HELD
                            || !resent.getFirst().contents().is(Items.COBBLESTONE)) {
                        helper.fail("a refused drop resent " + resent + ", expected slot 0 with " + HELD + " cobblestone", MIDDLE);
                    }
                })
                .thenSucceed();
    }

    // A row east, then a tile facing south: the row feeds it from the west, so it is a corner whose arc
    // runs from the middle of its west edge to the middle of its south edge, turning about its
    // south-west corner. Its offset, round the arc, is the angle about that corner, a quarter turn
    // being the whole tile: aimed along a radius, inside the arc or out, the item lands where the
    // radius meets it.
    private static void cornerProjectsOntoItsArc(GameTestHelper helper, double east, double south, double expected) {
        BlockPos corner = FIRST_TILE.east(2);
        helper.setBlock(FIRST_TILE, BeltTileTests.tile(BeltTier.BELT, Direction.EAST));
        helper.setBlock(FIRST_TILE.east(), BeltTileTests.tile(BeltTier.BELT, Direction.EAST));
        helper.setBlock(corner, BeltTileTests.tile(BeltTier.BELT, Direction.SOUTH));
        helper.setBlock(corner.south(), BeltTileTests.tile(BeltTier.BELT, Direction.SOUTH));
        helper.setBlock(corner.south(2), BeltTileTests.tile(BeltTier.BELT, Direction.SOUTH));
        Dropper player = player(helper);
        BeltTileBlockEntity tile = helper.getBlockEntity(corner, BeltTileBlockEntity.class);
        helper.startSequence()
                .thenIdle(SETTLE_TICKS)
                .thenExecute(() -> {
                    if (tile.shape() != TileShape.FROM_RIGHT) helper.fail("the tile the row feeds from its right is " + tile.shape() + ", expected a corner", corner);
                    if (tile.line() == null || tile.index() != 2) helper.fail("the corner is tile " + tile.index() + " of its line, expected 2", corner);
                    expectInventory(helper, player, HELD, "before the drop");
                    // From the east, so the look crosses nothing before the corner tile.
                    aim(helper, player, onTile(corner, east, south), 1.5, 8.0);
                    player.drop(false);

                    List<Double> entries = tile.line().contents().entries().stream().map(BeltContents.Entry::position).toList();
                    if (entries.size() != 1) helper.fail("a drop onto the corner put " + entries.size() + " items on the line", corner);
                    if (Math.abs(entries.getFirst() - expected) > AIM_ERROR) {
                        helper.fail("a drop aimed at " + east + " east and " + south + " south of the corner sat at " + entries.getFirst() + ", expected " + expected, corner);
                    }
                    expectInventory(helper, player, HELD - 1, "after the drop");
                    expectItemEntities(helper, 0, "after the drop");
                })
                .thenSucceed();
    }

    private static void throwsWhenAimedAt(GameTestHelper helper, Vec3 point) {
        place(helper);
        Dropper player = player(helper);
        helper.startSequence()
                .thenIdle(SETTLE_TICKS)
                .thenExecute(() -> {
                    aim(helper, player, point, STANDING_Z);
                    player.drop(false);
                    expectThrown(helper, player, 1);
                })
                .thenSucceed();
    }

    private static void throwsWhenAimedAtNothing(GameTestHelper helper) {
        place(helper);
        Dropper player = player(helper);
        helper.startSequence()
                .thenIdle(SETTLE_TICKS)
                .thenExecute(() -> {
                    Vec3 feet = helper.absoluteVec(new Vec3(MIDDLE.getX() + 0.5, 1, STANDING_Z));
                    player.setPos(feet.x, feet.y, feet.z);
                    player.setXRot(-90);
                    player.drop(false);
                    expectThrown(helper, player, 1);
                })
                .thenSucceed();
    }

    // 5.4 blocks from the tile's centre to the eye against the 4.5 a player reaches.
    private static void throwsBeyondRange(GameTestHelper helper) {
        place(helper);
        Dropper player = player(helper);
        helper.startSequence()
                .thenIdle(SETTLE_TICKS)
                .thenExecute(() -> {
                    aim(helper, player, onTile(MIDDLE, 0.5, 0.5), FAR_STANDING_Z);
                    Vec3 eye = player.getEyePosition();
                    double distance = eye.distanceTo(helper.absoluteVec(onTile(MIDDLE, 0.5, 0.5)));
                    if (distance <= player.blockInteractionRange()) {
                        helper.fail("the tile is " + distance + " from the eye, inside the " + player.blockInteractionRange() + " reach", MIDDLE);
                    }
                    player.drop(false);
                    expectThrown(helper, player, 1);
                })
                .thenSucceed();
    }

    // A pack that raises the attribute raises the range: the same look, from the same spot.
    private static void raisedRangeReaches(GameTestHelper helper) {
        place(helper);
        Dropper player = player(helper);
        helper.startSequence()
                .thenIdle(SETTLE_TICKS)
                .thenExecute(() -> {
                    player.getAttribute(Attributes.BLOCK_INTERACTION_RANGE).setBaseValue(8);
                    aim(helper, player, onTile(MIDDLE, 0.5, 0.5), FAR_STANDING_Z);
                    player.drop(false);

                    expectInventory(helper, player, HELD - 1, "after the drop");
                    expectItemEntities(helper, 0, "after the drop");
                    expectLine(helper, 1, "after the drop");
                })
                .thenSucceed();
    }

    // What the player looks at is the block in front, not the tile past it.
    private static void throwsBehindABlock(GameTestHelper helper) {
        place(helper);
        // Half way along the look, a block's height above the floor.
        BlockPos wall = new BlockPos(MIDDLE.getX(), 2, 3);
        helper.setBlock(wall, Blocks.STONE);
        Dropper player = player(helper);
        helper.startSequence()
                .thenIdle(SETTLE_TICKS)
                .thenExecute(() -> {
                    aim(helper, player, onTile(MIDDLE, 0.5, 0.5), STANDING_Z);
                    player.drop(false);
                    expectThrown(helper, player, 1);
                })
                .thenSucceed();
    }

    private static void controlQThrowsTheStack(GameTestHelper helper) {
        place(helper);
        Dropper player = player(helper);
        helper.startSequence()
                .thenIdle(SETTLE_TICKS)
                .thenExecute(() -> {
                    aim(helper, player, onTile(MIDDLE, 0.5, 0.5), STANDING_Z);
                    player.drop(true);

                    expectInventory(helper, player, 0, "after Ctrl+Q");
                    expectLine(helper, 0, "after Ctrl+Q");
                    List<ItemEntity> thrown = itemEntities(helper);
                    if (thrown.size() != 1 || thrown.getFirst().getItem().getCount() != HELD) {
                        helper.fail("Ctrl+Q threw " + thrown.stream().map(entity -> entity.getItem().getCount()).toList()
                                + " cobblestone, expected one entity of " + HELD, MIDDLE);
                    }
                    if (!player.actionBar.isEmpty()) helper.fail("Ctrl+Q said " + player.actionBar, MIDDLE);
                })
                .thenSucceed();
    }

    // One item spawned just above the middle tile: no room is needed beyond one gap, so it joins.
    private static void landsOnAnEmptyTile(GameTestHelper helper) {
        place(helper);
        ItemEntity[] item = new ItemEntity[1];
        helper.startSequence()
                .thenIdle(SETTLE_TICKS)
                .thenExecute(() -> {
                    expectLine(helper, 0, "before the landing");
                    expectItemEntities(helper, 0, "before the landing");
                    item[0] = land(helper, MIDDLE, 0.5, 1);
                })
                .thenIdle(LANDING_TICKS)
                .thenExecute(() -> {
                    if (!item[0].isRemoved()) helper.fail("the item entity is still in the world " + LANDING_TICKS + " ticks after landing on a tile with room", MIDDLE);
                    expectItemEntities(helper, 0, "after the landing");
                    expectLine(helper, 1, "after the landing");
                    expectCount(helper, TARGET, 0, "after the landing");
                })
                .thenExecuteFor(ARRIVAL_TICKS, () -> expectItemEntities(helper, 0, "while the item rode"))
                .thenExecute(() -> {
                    expectCount(helper, TARGET, 1, "after the ride");
                    expectLine(helper, 0, "after the ride");
                })
                .thenSucceed();
    }

    // Joining a line is a pickup, so an item set never to be picked up, as /give's fake item is, is left alone.
    private static void neverPickedUpStaysAnEntity(GameTestHelper helper) {
        place(helper);
        ItemEntity[] item = new ItemEntity[1];
        helper.startSequence()
                .thenIdle(SETTLE_TICKS)
                .thenExecute(() -> {
                    item[0] = land(helper, MIDDLE, 0.5, 1);
                    item[0].setNeverPickUp();
                })
                .thenIdle(LANDING_TICKS)
                .thenExecute(() -> {
                    if (item[0].isRemoved()) helper.fail("an item nobody may pick up left the world on landing on a tile with room", MIDDLE);
                    expectLine(helper, 0, "after an item nobody may pick up landed");
                })
                .thenSucceed();
    }

    // Five items on the ground become five entries, one item each, in the one tick there is room for them.
    private static void stackLandsOnAnEmptyTile(GameTestHelper helper) {
        place(helper);
        helper.startSequence()
                .thenIdle(SETTLE_TICKS)
                .thenExecute(() -> land(helper, MIDDLE, 0.5, STACK))
                .thenIdle(LANDING_TICKS)
                .thenExecute(() -> {
                    expectItemEntities(helper, 0, "after a stack of " + STACK + " landed");
                    expectLine(helper, STACK, "after a stack of " + STACK + " landed");
                    expectCount(helper, TARGET, 0, "after the landing");
                })
                .thenExecuteFor(ARRIVAL_TICKS, () -> expectItemEntities(helper, 0, "while the items rode"))
                .thenExecute(() -> {
                    expectCount(helper, TARGET, STACK, "after the ride");
                    expectLine(helper, 0, "after the ride");
                })
                .thenSucceed();
    }

    // A tile holds eight, so what does not fit waits as an entity and joins as the line runs on and leaves room.
    private static void longStackLandsOnAnEmptyTile(GameTestHelper helper) {
        place(helper);
        ItemEntity[] item = new ItemEntity[1];
        boolean[] rested = new boolean[1];
        helper.startSequence()
                .thenIdle(SETTLE_TICKS)
                .thenExecute(() -> item[0] = land(helper, MIDDLE, 0.5, LONG_STACK))
                .thenIdle(1)
                .thenExecute(() -> {
                    if (item[0].isRemoved()) helper.fail("a stack of " + LONG_STACK + " landed on a tile that holds eight and all of it joined at once", MIDDLE);
                    rested[0] = true;
                })
                .thenExecuteFor(ARRIVAL_TICKS, () -> {
                    int held = helper.getBlockEntity(MIDDLE, BeltTileBlockEntity.class).line().size();
                    int resting = itemEntities(helper).stream().mapToInt(entity -> entity.getItem().getCount()).sum();
                    int arrived = BeltTileTests.count(BeltTileTests.chest(helper, TARGET));
                    // The one handed on from the line's end to the chest can be in neither for a tick.
                    int total = held + resting + arrived;
                    if (total > LONG_STACK || total < LONG_STACK - 1) {
                        helper.fail("the line holds " + held + ", " + resting + " lie and " + arrived + " arrived, " + total + " of the " + LONG_STACK, MIDDLE);
                    }
                })
                .thenExecute(() -> {
                    if (!rested[0]) helper.fail("the stack never rested", MIDDLE);
                    expectItemEntities(helper, 0, "after the ride");
                    expectCount(helper, TARGET, LONG_STACK, "after the ride");
                    expectLine(helper, 0, "after the ride");
                })
                .thenSucceed();
    }

    // The source holds what fills the line exactly and the target is full, so the line backs up; once the
    // target is emptied the line runs dry, and the room that leaves is where the waiting item joins.
    private static void landsOnABackedUpLine(GameTestHelper helper) {
        place(helper);
        BeltTileTests.fill(helper, SOURCE, LINE_CAPACITY);
        fillTarget(helper);
        ItemEntity[] item = new ItemEntity[1];
        double[] at = new double[2];
        helper.startSequence()
                .thenIdle(SETTLE_TICKS + BACK_UP_TICKS)
                .thenExecute(() -> {
                    expectLine(helper, LINE_CAPACITY, "when backed up");
                    item[0] = land(helper, MIDDLE, 0.5, 1);
                })
                .thenIdle(LANDING_TICKS)
                .thenExecute(() -> {
                    if (item[0].isRemoved()) helper.fail("an item landed on a full line and joined it", MIDDLE);
                    at[0] = item[0].getX();
                    at[1] = item[0].getZ();
                    expectLine(helper, LINE_CAPACITY, "after the landing");
                })
                .thenExecuteFor(STILL_TICKS, () -> {
                    if (item[0].isRemoved()) helper.fail("an item resting on a backed-up line was taken while it was full", MIDDLE);
                    if (Math.abs(item[0].getX() - at[0]) > STILL_ERROR || Math.abs(item[0].getZ() - at[1]) > STILL_ERROR) {
                        helper.fail("an item resting on a backed-up line moved to " + item[0].getX() + ", " + item[0].getZ()
                                + " from " + at[0] + ", " + at[1], MIDDLE);
                    }
                    expectLine(helper, LINE_CAPACITY, "while the line was stopped");
                    expectItemEntities(helper, 1, "while the line was stopped");
                })
                .thenExecute(() -> {
                    BeltTileTests.chest(helper, TARGET).clearContent();
                    if (BeltTileTests.count(BeltTileTests.chest(helper, TARGET)) != 0) helper.fail("the chest was not drained", TARGET);
                })
                .thenExecuteFor(ARRIVAL_TICKS, () -> {
                    if (itemEntities(helper).size() > 1) helper.fail("the landed item was duplicated", MIDDLE);
                })
                .thenExecute(() -> {
                    expectItemEntities(helper, 0, "after the line cleared");
                    expectLine(helper, 0, "after the line cleared");
                    expectCount(helper, TARGET, LINE_CAPACITY + 1, "after the line cleared");
                })
                .thenSucceed();
    }

    // The source never runs dry and the target never fills, so the line is full and moving: there is
    // never room to join, and the item rides as far as the items under it do.
    private static void landsOnAMovingLine(GameTestHelper helper) {
        place(helper);
        BeltTileTests.fill(helper, SOURCE, SUPPLY);
        ItemEntity[] item = new ItemEntity[1];
        double[] at = new double[1];
        helper.startSequence()
                .thenIdle(SETTLE_TICKS + BACK_UP_TICKS)
                .thenExecute(() -> {
                    expectFull(helper, "when full and moving");
                    item[0] = land(helper, MIDDLE, 0.05, 1);
                })
                .thenIdle(LANDING_TICKS)
                .thenExecute(() -> at[0] = item[0].getX())
                .thenIdle(CARRIED_TICKS)
                .thenExecute(() -> {
                    if (item[0].isRemoved()) helper.fail("an item landed on a full moving line and joined it", MIDDLE);
                    double carried = item[0].getX() - at[0];
                    double expected = CARRIED_TICKS * TILE_STEP;
                    if (Math.abs(carried - expected) > CARRIED_ERROR) {
                        helper.fail("an item on a full moving line was carried " + carried + " blocks in " + CARRIED_TICKS + " ticks, expected " + expected, MIDDLE);
                    }
                    expectFull(helper, "after the carry");
                })
                .thenSucceed();
    }

    // A player is carried by their own client at the tile's speed, never the line's: the registry's
    // figure for a player on a backed-up line's tile is still the tile's.
    private static void playerIsCarriedAtTheTilesSpeed(GameTestHelper helper) {
        place(helper);
        BeltTileTests.fill(helper, SOURCE, LINE_CAPACITY);
        fillTarget(helper);
        ServerPlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "beltworks_rider"));
        player.setGameMode(GameType.SURVIVAL);
        helper.startSequence()
                .thenIdle(SETTLE_TICKS + BACK_UP_TICKS)
                .thenExecute(() -> {
                    expectLine(helper, LINE_CAPACITY, "when backed up");
                    Vec3 at = helper.absoluteVec(onTile(MIDDLE, 0.5, 0.5));
                    player.setPos(at.x, at.y, at.z);
                    double local = helper.getBlockEntity(MIDDLE, BeltTileBlockEntity.class).movedAt(0.5);
                    if (local != 0) helper.fail("the line's items under the player moved " + local + ", so the line is not stopped", MIDDLE);
                    double speed = BeltCollisionRegistry.carrySpeed(helper.getLevel(), player);
                    if (Double.isNaN(speed) || Math.abs(speed - TILE_SPEED) > 1e-9) {
                        helper.fail("a belt under a player carries them at " + speed + " blocks a second, expected " + TILE_SPEED, MIDDLE);
                    }
                    expectLine(helper, LINE_CAPACITY, "with a player on the tile");
                    expectItemEntities(helper, 0, "with a player on the tile");
                })
                .thenSucceed();
    }

    /** A stack of sticks spawned at rest just above a tile, {@code east} of the way across it, to fall onto it. */
    private static ItemEntity land(GameTestHelper helper, BlockPos tile, double east, int count) {
        Vec3 at = helper.absoluteVec(new Vec3(tile.getX() + east, TILE_TOP + LANDING_HEIGHT, tile.getZ() + 0.5));
        ItemEntity item = new ItemEntity(helper.getLevel(), at.x, at.y, at.z, new ItemStack(Items.STICK, count));
        item.setDeltaMovement(Vec3.ZERO);
        // Kept from any player nearby, but not never picked up, which a line leaves alone.
        item.setPickUpDelay(Short.MAX_VALUE - 1);
        helper.getLevel().addFreshEntity(item);
        return item;
    }

    // A moving line's head comes a tick after it loads, so it holds one item short of a backed-up line's.
    private static void expectFull(GameTestHelper helper, String when) {
        int held = helper.getBlockEntity(MIDDLE, BeltTileBlockEntity.class).line().size();
        if (held < LINE_CAPACITY - 1) helper.fail("the line holds " + held + " items " + when + ", expected it full at " + LINE_CAPACITY, MIDDLE);
    }

    private static void fillTarget(GameTestHelper helper) {
        var target = BeltTileTests.chest(helper, TARGET);
        for (int slot = 0; slot < target.getContainerSize(); slot++) target.setItem(slot, new ItemStack(Items.DIRT, 64));
    }

    /** A chest behind an east-facing loader, three tiles, a west-facing loader and a chest, with nothing in the source. */
    private static void place(GameTestHelper helper) {
        helper.setBlock(SOURCE, Blocks.CHEST);
        helper.setBlock(FROM, BeltTileTests.loader(BeltTier.BELT, Direction.EAST));
        for (int tile = 0; tile < TILES; tile++) {
            helper.setBlock(FIRST_TILE.east(tile), BeltTileTests.tile(BeltTier.BELT, Direction.EAST));
        }
        helper.setBlock(TO, BeltTileTests.loader(BeltTier.BELT, Direction.WEST));
        helper.setBlock(TARGET, Blocks.CHEST);
    }

    private static Dropper player(GameTestHelper helper) {
        Dropper player = new Dropper(helper);
        player.setGameMode(GameType.SURVIVAL);
        player.getInventory().setItem(0, new ItemStack(Items.COBBLESTONE, HELD));
        player.getInventory().setSelectedSlot(0);
        return player;
    }

    /** A point on the top of a tile, a fraction of the way east and south across its block. */
    private static Vec3 onTile(BlockPos tile, double east, double south) {
        return new Vec3(tile.getX() + east, TILE_TOP, tile.getZ() + south);
    }

    private static void aim(GameTestHelper helper, Dropper player, Vec3 point, double standingZ) {
        aim(helper, player, point, standingZ, point.x);
    }

    /** Stands the player at {@code (standingX, 1, standingZ)} and turns them to look at a point, both in the test's own frame. */
    private static void aim(GameTestHelper helper, Dropper player, Vec3 point, double standingZ, double standingX) {
        Vec3 feet = helper.absoluteVec(new Vec3(standingX, 1, standingZ));
        player.setPos(feet.x, feet.y, feet.z);
        Vec3 toward = helper.absoluteVec(point).subtract(player.getEyePosition());
        float yaw = (float) (Mth.atan2(toward.z, toward.x) * 180 / Math.PI) - 90;
        float pitch = (float) -(Mth.atan2(toward.y, Math.hypot(toward.x, toward.z)) * 180 / Math.PI);
        player.setYRot(yaw);
        // A living entity looks where its head does.
        player.setYHeadRot(yaw);
        player.setXRot(pitch);
    }

    private static List<Double> positions(GameTestHelper helper) {
        var line = helper.getBlockEntity(MIDDLE, BeltTileBlockEntity.class).line();
        if (line == null) helper.fail("no line holds the middle tile", MIDDLE);
        return line.contents().entries().stream().map(BeltContents.Entry::position).sorted().toList();
    }

    /** The first position on the line, after a drop that should have put an item there. */
    private static double first(GameTestHelper helper, Dropper player, String when) {
        List<Double> entries = positions(helper);
        if (entries.isEmpty()) {
            helper.fail("the line holds nothing " + when + "; item entities " + itemEntities(helper).size()
                    + ", action bar " + player.actionBar + ", look " + player.getXRot() + "/" + player.getYRot()
                    + " from " + player.getEyePosition(), MIDDLE);
        }
        return entries.getFirst();
    }

    private static void expectLine(GameTestHelper helper, int items, String when) {
        var line = helper.getBlockEntity(MIDDLE, BeltTileBlockEntity.class).line();
        if (line == null) helper.fail("no line holds the middle tile " + when, MIDDLE);
        if (line.size() != items) helper.fail("the line holds " + line.size() + " items " + when + ", expected " + items, MIDDLE);
    }

    private static void expectInventory(GameTestHelper helper, Dropper player, int cobblestone, String when) {
        int held = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(Items.COBBLESTONE)) held += stack.getCount();
        }
        if (held != cobblestone) helper.fail("the inventory holds " + held + " cobblestone " + when + ", expected " + cobblestone, MIDDLE);
    }

    private static List<ItemEntity> itemEntities(GameTestHelper helper) {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(2.0));
    }

    private static void expectItemEntities(GameTestHelper helper, int expected, String when) {
        int lying = itemEntities(helper).size();
        if (lying != expected) helper.fail(lying + " item entities exist " + when + ", expected " + expected, MIDDLE);
    }

    private static void expectCount(GameTestHelper helper, BlockPos chest, int expected, String when) {
        int count = BeltTileTests.count(BeltTileTests.chest(helper, chest));
        if (count != expected) helper.fail("the chest holds " + count + " cobblestone " + when + ", expected " + expected, chest);
    }

    // One item thrown, one gone from the slot, nothing on the line and nothing said.
    private static void expectThrown(GameTestHelper helper, Dropper player, int items) {
        expectInventory(helper, player, HELD - items, "after a vanilla drop");
        expectItemEntities(helper, 1, "after a vanilla drop");
        expectLine(helper, 0, "after a vanilla drop");
        if (!player.actionBar.isEmpty()) helper.fail("a vanilla drop said " + player.actionBar, MIDDLE);
        int thrown = itemEntities(helper).getFirst().getItem().getCount();
        if (thrown != items) helper.fail("vanilla threw " + thrown + " cobblestone, expected " + items, MIDDLE);
    }

    /**
     * A survival player of its own, with a connection that keeps what it is sent and a chat that
     * keeps the action bar's translation keys, since a fake player's hear and send nothing.
     */
    private static final class Dropper extends FakePlayer {
        final List<String> actionBar = new ArrayList<>();
        final List<Packet<?>> sent = new ArrayList<>();

        Dropper(GameTestHelper helper) {
            super(helper.getLevel(), new GameProfile(UUID.randomUUID(), "beltworks_belt_dropper"));
            var listener = this;
            connection = new ServerGamePacketListenerImpl(helper.getLevel().getServer(), new SilentConnection(), this,
              CommonListenerCookie.createInitial(getGameProfile(), false)) {
                @Override
                public void send(Packet<?> packet) {
                    listener.sent.add(packet);
                }

                @Override
                public void send(Packet<?> packet, io.netty.channel.ChannelFutureListener sendListener) {
                    listener.sent.add(packet);
                }
            };
        }

        @Override
        public void sendSystemMessage(Component message, boolean actionBar) {
            if (actionBar && message.getContents() instanceof TranslatableContents translatable) this.actionBar.add(translatable.getKey());
        }
    }

    private static final class SilentConnection extends Connection {
        SilentConnection() {
            super(PacketFlow.SERVERBOUND);
        }

        @Override
        public void setListenerForServerboundHandshake(net.minecraft.network.PacketListener listener) {
        }
    }
}
