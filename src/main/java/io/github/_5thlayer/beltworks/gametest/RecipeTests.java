// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.gametest;

import io.github._5thlayer.beltworks.BlockContent;
import io.github._5thlayer.beltworks.model.BeltTier;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.ItemLike;

import java.util.Arrays;
import java.util.List;

/**
 * Every belt piece crafts from vanilla ingredients (ADR 0002): tier 1 from its tier material, copper,
 * and each tier above from the tier below and its own tier material.
 */
final class RecipeTests {

    private RecipeTests() {
    }

    static void register(BeltGameTests.Registrar tests) {
        tests.test("every_belt_piece_crafts_from_vanilla_ingredients", 1, RecipeTests::everyPieceCrafts);
        tests.test("rotten_flesh_smokes_into_leather_in_the_smoker_only", 1, RecipeTests::rottenFleshSmokes);
    }

    private static void everyPieceCrafts(GameTestHelper helper) {
        var copper = Items.COPPER_INGOT;
        var tile = BlockContent.tileFor(BeltTier.BELT);
        crafts(helper, grid(Items.DRIED_KELP, Items.DRIED_KELP, Items.DRIED_KELP, copper, copper, copper, null, null, null), tile, 16);
        crafts(helper, grid(Items.LEATHER, Items.LEATHER, Items.LEATHER, copper, copper, copper, null, null, null), tile, 16);
        crafts(helper, grid(Items.HOPPER, copper, tile, null, null, null, null, null, null), BlockContent.loaderFor(BeltTier.BELT), 2);
        crafts(helper, grid(tile, tile, Items.COMPARATOR, copper, null, null, null, null, null), BlockContent.splitterFor(BeltTier.BELT), 1);
        for (var tier : BeltTier.values()) {
            crafts(helper, grid(BlockContent.loaderFor(tier), BlockContent.tileFor(tier), null, null, null, null, null, null, null),
              BlockContent.feederFor(tier), 1);
            if (tier == BeltTier.BELT) continue;
            var below = BeltTier.values()[tier.ordinal() - 1];
            var material = material(tier);
            var lower = BlockContent.tileFor(below);
            crafts(helper, grid(lower, lower, lower, lower, material, lower, lower, lower, lower), BlockContent.tileFor(tier), 8);
            var loader = BlockContent.loaderFor(below);
            crafts(helper, grid(loader, loader, material, null, null, null, null, null, null), BlockContent.loaderFor(tier), 2);
            crafts(helper, grid(BlockContent.splitterFor(below), material, material, null, null, null, null, null, null),
              BlockContent.splitterFor(tier), 1);
        }
        helper.succeed();
    }

    private static void rottenFleshSmokes(GameTestHelper helper) {
        var level = helper.getLevel();
        var recipes = level.getServer().getRecipeManager();
        var input = new SingleRecipeInput(new ItemStack(Items.ROTTEN_FLESH));
        var smoked = recipes.getRecipeFor(RecipeType.SMOKING, input, level)
          .orElseThrow(() -> helper.assertionException(BlockPos.ZERO, "rotten flesh does not smoke"));
        helper.assertTrue(smoked.value().assemble(input).is(Items.LEATHER), "rotten flesh smokes into something other than leather");
        helper.assertTrue(smoked.value().cookingTime() == 200, "rotten flesh smokes in " + smoked.value().cookingTime() + " ticks, not 200");
        helper.assertTrue(recipes.getRecipeFor(RecipeType.SMELTING, input, level).isEmpty(), "rotten flesh smelts in a furnace");
        helper.succeed();
    }

    /** Iron, gold and diamond for tiers 2 to 4: copper is tier 1's. */
    private static Item material(BeltTier tier) {
        return switch (tier) {
            case BELT -> Items.COPPER_INGOT;
            case IMPROVED -> Items.IRON_INGOT;
            case EXPRESS -> Items.GOLD_INGOT;
            case TURBO -> Items.DIAMOND;
        };
    }

    private static List<ItemStack> grid(ItemLike... slots) {
        return Arrays.stream(slots).map(slot -> slot == null ? ItemStack.EMPTY : new ItemStack(slot)).toList();
    }

    private static void crafts(GameTestHelper helper, List<ItemStack> slots, ItemLike result, int count) {
        var level = helper.getLevel();
        var input = CraftingInput.of(3, 3, slots);
        var crafted = level.getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level)
          .map(recipe -> recipe.value().assemble(input))
          .orElse(ItemStack.EMPTY);
        var wanted = new ItemStack(result, count);
        if (!ItemStack.matches(crafted, wanted)) {
            throw helper.assertionException(BlockPos.ZERO, slots + " crafts " + crafted + ", not " + wanted);
        }
    }
}
