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
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.crafting.Recipe;
import net.neoforged.fml.ModList;

/** The Mod plays alone on Groundworks (ADR-0127): nothing else is loaded beside it, and every tag it reads fills alone. */
final class PlaysAloneTests {

    // A recipe's placement info is empty when an ingredient is, which hides the very tag this checks, so the tags
    // are read from the recipe as it encodes.
    private static final Pattern TAG = Pattern.compile("\"#([a-z0-9_.-]+:[a-z0-9_./-]+)\"");

    private static final Set<String> BASE = Set.of("minecraft", "neoforge", "groundworks", Beltworks.MOD_ID);

    private PlaysAloneTests() {
    }

    static void register(BeltGameTests.Registrar tests) {
        tests.test("only_groundworks_is_loaded_beside_the_mod", 1, PlaysAloneTests::onlyGroundworks);
        tests.test("every_tag_beltworks_reads_holds_an_entry_of_minecraft_or_beltworks", 1, PlaysAloneTests::tagsPlayAlone);
    }

    private static void onlyGroundworks(GameTestHelper helper) {
        List<String> extra = ModList.get().getMods().stream().map(m -> m.getModId()).filter(id -> !BASE.contains(id)).toList();
        helper.assertTrue(extra.isEmpty(), "the run loads " + extra + " beside Groundworks");
        helper.succeed();
    }

    /** Each tag the code or a recipe reads reaches an entry with no other mod installed. A tag only other mods fill leaves it inert. */
    private static void tagsPlayAlone(GameTestHelper helper) {
        Set<TagKey<?>> read = new LinkedHashSet<>();
        read.add(Dismantles.TOOLS);
        var ops = helper.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE);
        helper.getLevel().getServer().getRecipeManager().getRecipes().stream()
                .filter(recipe -> recipe.id().identifier().getNamespace().equals(Beltworks.MOD_ID))
                .map(recipe -> Recipe.CODEC.encodeStart(ops, recipe.value()).getOrThrow().toString())
                .flatMap(json -> TAG.matcher(json).results())
                .forEach(tag -> read.add(TagKey.create(Registries.ITEM, Identifier.parse(tag.group(1)))));
        var alone = Set.of("minecraft", Beltworks.MOD_ID);
        for (TagKey<?> tag : read) {
            helper.assertTrue(reachesAlone(helper, tag, alone), tag.location() + " holds no entry of " + alone);
        }
        helper.succeed();
    }

    private static <T> boolean reachesAlone(GameTestHelper helper, TagKey<T> tag, Set<String> namespaces) {
        return helper.getLevel().registryAccess().lookupOrThrow(tag.registry()).get(tag).stream()
                .flatMap(set -> set.stream())
                .anyMatch(holder -> holder.unwrapKey().filter(key -> namespaces.contains(key.identifier().getNamespace())).isPresent());
    }
}
