// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.gametest;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import com.mojang.serialization.JsonOps;
import io.github._5thlayer.beltworks.Beltworks;
import io.github._5thlayer.groundworks.Dismantles;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.fml.ModList;

/** The Mod plays alone on Groundworks (FactoryWorks ADR-0127): nothing else is loaded beside it, and every tag it reads fills alone. */
final class PlaysAloneTests {

    // A recipe's placement info is empty when an ingredient is, which hides the very tag this checks, so the tags
    // are read from the recipe as it encodes.
    private static final Pattern TAG = Pattern.compile("\"#([a-z0-9_.-]+:[a-z0-9_./-]+)\"");

    private static final Set<String> LOADED_ALONE = Set.of("minecraft", "neoforge", "groundworks", Beltworks.MOD_ID);

    private static final Set<String> FILLS_ALONE = Set.of("minecraft", Beltworks.MOD_ID);

    private PlaysAloneTests() {
    }

    static void register(BeltGameTests.Registrar tests) {
        tests.test("only_groundworks_is_loaded_beside_the_mod", 1, PlaysAloneTests::onlyGroundworks);
        tests.test("every_tag_beltworks_reads_holds_an_entry_of_minecraft_or_beltworks", 1, PlaysAloneTests::tagsPlayAlone);
        tests.test("the_tags_of_an_encoded_recipe_are_found", 1, PlaysAloneTests::encodedTagsFound);
    }

    private static void onlyGroundworks(GameTestHelper helper) {
        List<String> extra = ModList.get().getMods().stream().map(m -> m.getModId()).filter(id -> !LOADED_ALONE.contains(id)).toList();
        helper.assertTrue(extra.isEmpty(), "the run loads " + extra + " beside Groundworks");
        helper.succeed();
    }

    /** Each tag the code or a recipe reads reaches an entry with no other mod installed. */
    private static void tagsPlayAlone(GameTestHelper helper) {
        Set<TagKey<?>> consumed = new LinkedHashSet<>();
        consumed.add(Dismantles.TOOLS);
        helper.getLevel().getServer().getRecipeManager().getRecipes().stream()
                .filter(recipe -> recipe.id().identifier().getNamespace().equals(Beltworks.MOD_ID))
                .forEach(recipe -> consumed.addAll(tagsOf(helper, recipe)));
        for (TagKey<?> tag : consumed) {
            helper.assertTrue(fillsAlone(helper, tag), tag.location() + " holds no entry of " + FILLS_ALONE);
        }
        helper.succeed();
    }

    // Beltworks' own recipes name no tag, so vanilla's chest stands in to show the pattern matches what the codec writes.
    private static void encodedTagsFound(GameTestHelper helper) {
        RecipeHolder<?> chest = helper.getLevel().getServer().getRecipeManager().recipeMap()
                .byKey(ResourceKey.create(Registries.RECIPE, Identifier.withDefaultNamespace("chest")));
        helper.assertTrue(chest != null, "vanilla has no chest recipe");
        List<Identifier> tags = tagsOf(helper, chest).stream().map(TagKey::location).toList();
        helper.assertTrue(tags.equals(List.of(Identifier.withDefaultNamespace("planks"))), "the chest recipe's tags read as " + tags);
        helper.succeed();
    }

    private static List<TagKey<?>> tagsOf(GameTestHelper helper, RecipeHolder<?> recipe) {
        var ops = helper.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE);
        String json = Recipe.CODEC.encodeStart(ops, recipe.value()).getOrThrow().toString();
        return TAG.matcher(json).results().<TagKey<?>>map(tag -> TagKey.create(Registries.ITEM, Identifier.parse(tag.group(1)))).toList();
    }

    private static <T> boolean fillsAlone(GameTestHelper helper, TagKey<T> tag) {
        return helper.getLevel().registryAccess().lookupOrThrow(tag.registry()).get(tag).stream()
                .flatMap(set -> set.stream())
                .anyMatch(holder -> holder.unwrapKey().filter(key -> FILLS_ALONE.contains(key.identifier().getNamespace())).isPresent());
    }
}
