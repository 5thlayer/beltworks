// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.neoforged.neoforge.capabilities.Capabilities;
import io.github._5thlayer.beltworks.BeltworksConfig;
import io.github._5thlayer.beltworks.BlockContent;
import io.github._5thlayer.beltworks.blocks.BeltEndBlockEntity;
import io.github._5thlayer.beltworks.blocks.BeltTileBlockEntity;
import io.github._5thlayer.beltworks.blocks.FeederBlockEntity;
import io.github._5thlayer.beltworks.blocks.SplitterBlock;
import io.github._5thlayer.beltworks.model.BeltTier;
import io.github._5thlayer.beltworks.model.FeederArms;
import io.github._5thlayer.beltworks.model.LoaderEnergy;
import io.github._5thlayer.beltworks.model.Splitter;
import io.github._5thlayer.beltworks.model.TransportLine;

import java.util.ArrayList;
import java.util.List;

/**
 * A tier swap (GLOSSARY.md, #27): a block put by {@code setBlock} in the place of one of the same kind
 * and another tier keeps what the old one held, and a splitter swaps both its halves.
 */
final class TierSwapTests {

    private static final BlockPos LEFT = new BlockPos(4, 1, 2);
    private static final BlockPos RIGHT = LEFT.relative(Direction.EAST.getClockWise());
    private static final BlockPos AT = new BlockPos(4, 1, 4);

    private TierSwapTests() {
    }

    static void register(BeltGameTests.Registrar tests) {
        tests.test("a_splitter_swapped_at_its_left_half_swaps_both_and_keeps_its_items_and_settings", 20,
                helper -> swapsASplitter(helper, LEFT));
        tests.test("a_splitter_swapped_at_its_right_half_swaps_both_and_keeps_its_items_and_settings", 20,
                helper -> swapsASplitter(helper, RIGHT));
        tests.test("a_splitter_half_put_in_place_with_another_facing_tears_the_splitter_down", 20,
                TierSwapTests::otherFacingTearsDown);
        tests.test("a_tile_swapped_to_another_tier_keeps_its_items_where_they_sat", 20, TierSwapTests::swapsATile);
        var powered = tests.withLoaderPower(true);
        powered.test("a_loader_swapped_to_another_tier_keeps_its_filter_and_what_energy_its_tier_holds", 20,
                TierSwapTests::swapsALoader);
        powered.test("a_feeder_swapped_to_another_tier_keeps_its_filter_reach_and_what_energy_its_tier_holds", 20,
                TierSwapTests::swapsAFeeder);
    }

    private static void swapsASplitter(GameTestHelper helper, BlockPos swapped) {
        placeSplitter(helper, BeltTier.BELT, Direction.EAST);
        var left = helper.getBlockEntity(LEFT, BeltEndBlockEntity.class);
        var right = helper.getBlockEntity(RIGHT, BeltEndBlockEntity.class);
        left.getHalf().entering().restore(new ItemStack(Items.IRON_INGOT), 0.1);
        left.getHalf().leaving().restore(new ItemStack(Items.GOLD_INGOT, 3), 0.3);
        right.getHalf().entering().restore(new ItemStack(Items.COPPER_INGOT), 0.2);
        right.getHalf().leaving().restore(new ItemStack(Items.DIAMOND), 0.05);
        left.setOutputPriority(Splitter.Priority.RIGHT);
        right.setInputPriority(Splitter.Priority.LEFT);
        left.setSplitterFilter(new ItemStack(Items.COBBLESTONE), Splitter.Priority.RIGHT);
        var settings = left.splitterSettings();
        var leftItems = items(left);
        var rightItems = items(right);

        var side = swapped.equals(LEFT) ? SplitterBlock.Side.LEFT : SplitterBlock.Side.RIGHT;
        helper.setBlock(swapped, half(BeltTier.IMPROVED, side, Direction.EAST));

        if (!helper.getBlockState(LEFT).equals(half(BeltTier.IMPROVED, SplitterBlock.Side.LEFT, Direction.EAST))
              || !helper.getBlockState(RIGHT).equals(half(BeltTier.IMPROVED, SplitterBlock.Side.RIGHT, Direction.EAST))) {
            helper.fail("the splitter is " + helper.getBlockState(LEFT) + " and " + helper.getBlockState(RIGHT)
                          + ", not a tier-2 pair facing east", swapped);
        }
        left = helper.getBlockEntity(LEFT, BeltEndBlockEntity.class);
        right = helper.getBlockEntity(RIGHT, BeltEndBlockEntity.class);
        if (!items(left).equals(leftItems)) helper.fail("the left half holds " + items(left) + ", not " + leftItems, LEFT);
        if (!items(right).equals(rightItems)) helper.fail("the right half holds " + items(right) + ", not " + rightItems, RIGHT);
        for (var half : List.of(left, right)) {
            var kept = half.splitterSettings();
            if (kept.inputPriority() != settings.inputPriority() || kept.outputPriority() != settings.outputPriority()
                  || !ItemStack.matches(kept.filter(), settings.filter())) {
                helper.fail("the swapped splitter reads " + kept + ", not " + settings, half.getBlockPos());
            }
        }
        // Its watchers draw the filter on the output priority half, from the update tag.
        var update = left.getUpdateTag(helper.getLevel().registryAccess());
        if (update.read("splitter_filter", ItemStack.CODEC).filter(stack -> stack.is(Items.COBBLESTONE)).isEmpty()) {
            helper.fail("the swapped splitter's update tag carries no filter", LEFT);
        }
        noneDropped(helper, swapped);
        helper.succeed();
    }

    // As before the tier swap: another facing is a break and a placement, and the old pair goes.
    private static void otherFacingTearsDown(GameTestHelper helper) {
        placeSplitter(helper, BeltTier.BELT, Direction.EAST);
        helper.getBlockEntity(RIGHT, BeltEndBlockEntity.class).getHalf().entering().restore(new ItemStack(Items.IRON_INGOT), 0.2);

        helper.setBlock(LEFT, half(BeltTier.IMPROVED, SplitterBlock.Side.LEFT, Direction.NORTH));

        if (!helper.getBlockState(RIGHT).isAir()) helper.fail("the old right half stands as " + helper.getBlockState(RIGHT), RIGHT);
        if (helper.getEntities(EntityType.ITEM).size() != 1) {
            helper.fail("the torn-down splitter dropped " + helper.getEntities(EntityType.ITEM).size() + " items, not its one", RIGHT);
        }
        helper.succeed();
    }

    // StretchTests counts what a replaced tile hands on; this holds it to where it sat.
    private static void swapsATile(GameTestHelper helper) {
        helper.setBlock(AT, tile(BeltTier.BELT));
        var shares = List.of(new TransportLine.Share<>(0.2, new ItemStack(Items.IRON_INGOT)),
          new TransportLine.Share<>(0.7, new ItemStack(Items.GOLD_INGOT, 2)));
        helper.getBlockEntity(AT, BeltTileBlockEntity.class).carry(shares);
        var before = shares(helper.getBlockEntity(AT, BeltTileBlockEntity.class).held());

        helper.setBlock(AT, tile(BeltTier.EXPRESS));

        var after = shares(helper.getBlockEntity(AT, BeltTileBlockEntity.class).held());
        if (!after.equals(before)) helper.fail("the tier-3 tile holds " + after + ", not " + before, AT);
        noneDropped(helper, AT);
        helper.succeed();
    }

    private static void swapsALoader(GameTestHelper helper) {
        helper.setBlock(AT, loader(BeltTier.IMPROVED, Direction.EAST));
        var loader = helper.getBlockEntity(AT, BeltEndBlockEntity.class);
        loader.assignFilterItem(new ItemStack(Items.IRON_INGOT), player(helper));
        loader.getEnergy().insertFe(Long.MAX_VALUE);
        var joules = loader.getEnergy().joules();
        if (joules == 0) helper.fail("the tier-2 loader took no energy", AT);

        helper.setBlock(AT, loader(BeltTier.EXPRESS, Direction.EAST));
        loader = helper.getBlockEntity(AT, BeltEndBlockEntity.class);
        keepsFilter(helper, loader.filteredItem(), "tier-3 loader");
        var kept = Math.min(joules, full(new LoaderEnergy(BeltTier.EXPRESS, BeltworksConfig.loaderPower())));
        if (loader.getEnergy().joules() != kept) helper.fail("the tier-3 loader holds " + loader.getEnergy().joules() + " J, not " + kept, AT);
        // Its own face, on its own buffer, rather than the removed loader's.
        var face = helper.getLevel().getCapability(Capabilities.Energy.BLOCK, helper.absolutePos(AT), null);
        if (face == null || face.getAmountAsLong() != loader.getEnergy().storedFe()) {
            helper.fail("the tier-3 loader's energy face reads " + (face == null ? "nothing" : face.getAmountAsLong() + " FE")
                          + ", not its " + loader.getEnergy().storedFe(), AT);
        }

        helper.setBlock(AT, loader(BeltTier.BELT, Direction.EAST));
        loader = helper.getBlockEntity(AT, BeltEndBlockEntity.class);
        keepsFilter(helper, loader.filteredItem(), "tier-1 loader");
        if (loader.getEnergy().joules() != 0) helper.fail("the unpowered tier-1 loader holds " + loader.getEnergy().joules() + " J", AT);

        helper.setBlock(AT, loader(BeltTier.IMPROVED, Direction.NORTH));
        keepsFilter(helper, helper.getBlockEntity(AT, BeltEndBlockEntity.class).filteredItem(), "tier-2 loader turned north");
        helper.succeed();
    }

    private static void swapsAFeeder(GameTestHelper helper) {
        helper.setBlock(AT, feeder(BeltTier.EXPRESS, Direction.EAST));
        var feeder = helper.getBlockEntity(AT, FeederBlockEntity.class);
        feeder.assignFilterItem(new ItemStack(Items.IRON_INGOT), player(helper));
        var arms = new FeederArms(3, 2, FeederArms.Turn.STRAIGHT);
        feeder.setArms(arms);
        feeder.getEnergy().insertFe(Long.MAX_VALUE);
        var joules = feeder.getEnergy().joules();

        helper.setBlock(AT, feeder(BeltTier.IMPROVED, Direction.SOUTH));
        feeder = helper.getBlockEntity(AT, FeederBlockEntity.class);
        keepsFilter(helper, feeder.filteredItem(), "tier-2 feeder");
        if (!feeder.arms().equals(arms)) helper.fail("the tier-2 feeder reaches " + feeder.arms() + ", not " + arms, AT);
        var kept = Math.min(joules, full(LoaderEnergy.feeder(BeltTier.IMPROVED, BeltworksConfig.loaderPower())));
        if (kept == 0 || feeder.getEnergy().joules() != kept) helper.fail("the tier-2 feeder holds " + feeder.getEnergy().joules() + " J, not " + kept, AT);
        // Its watchers draw the filter and the arms from its update tag.
        var registries = helper.getLevel().registryAccess();
        var client = new FeederBlockEntity(helper.absolutePos(AT), helper.getBlockState(AT));
        client.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, registries, feeder.getUpdateTag(registries)));
        if (!client.filteredItem().is(Items.IRON_INGOT) || !client.arms().equals(arms)) {
            helper.fail("the tier-2 feeder's update tag carries " + client.filteredItem() + " and " + client.arms(), AT);
        }
        helper.succeed();
    }

    private static void keepsFilter(GameTestHelper helper, ItemStack filter, String what) {
        if (!filter.is(Items.IRON_INGOT)) helper.fail("the " + what + " filters " + filter + ", not iron", AT);
    }

    private static void noneDropped(GameTestHelper helper, BlockPos at) {
        var dropped = helper.getEntities(EntityType.ITEM).size();
        if (dropped != 0) helper.fail(dropped + " items lie on the ground after the swap", at);
    }

    // The most a buffer holds, read by filling a fresh one.
    private static long full(LoaderEnergy energy) {
        energy.setJoules(Long.MAX_VALUE);
        return energy.joules();
    }

    private static List<String> items(BeltEndBlockEntity half) {
        var items = new ArrayList<String>();
        for (var entry : half.getHalf().entering().entries()) items.add("entering " + describe(entry.payload()) + " at " + entry.position());
        for (var entry : half.getHalf().leaving().entries()) items.add("leaving " + describe(entry.payload()) + " at " + entry.position());
        return items;
    }

    private static List<String> shares(List<TransportLine.Share<ItemStack>> shares) {
        return shares.stream().map(share -> describe(share.payload()) + " at " + share.offset()).toList();
    }

    private static String describe(ItemStack stack) {
        return stack.getCount() + " " + BuiltInRegistries.ITEM.getKey(stack.getItem());
    }

    private static Player player(GameTestHelper helper) {
        return helper.makeMockPlayer(GameType.SURVIVAL);
    }

    private static void placeSplitter(GameTestHelper helper, BeltTier tier, Direction facing) {
        helper.setBlock(LEFT, half(tier, SplitterBlock.Side.LEFT, facing));
        helper.setBlock(RIGHT, half(tier, SplitterBlock.Side.RIGHT, facing));
    }

    private static BlockState half(BeltTier tier, SplitterBlock.Side side, Direction facing) {
        return facing(BlockContent.splitterFor(tier), facing).setValue(SplitterBlock.SIDE, side);
    }

    private static BlockState tile(BeltTier tier) {
        return facing(BlockContent.tileFor(tier), Direction.EAST);
    }

    private static BlockState loader(BeltTier tier, Direction facing) {
        return facing(BlockContent.loaderFor(tier), facing);
    }

    private static BlockState feeder(BeltTier tier, Direction facing) {
        return facing(BlockContent.feederFor(tier), facing);
    }

    private static BlockState facing(Block block, Direction facing) {
        return block.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, facing);
    }
}
