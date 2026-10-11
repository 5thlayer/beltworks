// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The mod plays alone on Groundworks (ADR-0127): nothing it ships names another suite mod's id or
 * puts data or assets in its namespace. The game test {@code every_tag_beltworks_reads_holds_an_entry_of_minecraft_or_beltworks}
 * checks the tags it consumes, which only the game can resolve.
 */
class PlaysAloneTest {

    private static final Path MAIN = Path.of("src/main");

    @Test
    void nothingShippedNamesAnotherSuiteModsId() throws IOException {
        assertEquals(List.of(), PlaysAlone.foreignIds(MAIN, Beltworks.MOD_ID));
    }

    @Test
    void nothingShippedIsInAnotherSuiteModsNamespace() throws IOException {
        assertEquals(List.of(), PlaysAlone.foreignNamespaces(MAIN.resolve("resources/data"), Beltworks.MOD_ID));
        assertEquals(List.of(), PlaysAlone.foreignNamespaces(MAIN.resolve("resources/assets"), Beltworks.MOD_ID));
    }

    @Test
    void aForeignNamespaceFolderIsReported(@TempDir Path root) throws IOException {
        write(root.resolve("data/fieldworks/recipe/x.json"), "{}");
        write(root.resolve("data/beltworks/recipe/x.json"), "{}");
        write(root.resolve("data/groundworks/tags/item/dismantles.json"), "{}");
        assertEquals(List.of("data has the namespace fieldworks"), PlaysAlone.foreignNamespaces(root.resolve("data"), Beltworks.MOD_ID));
    }

    @Test
    void aForeignIdInDataIsReported(@TempDir Path root) throws IOException {
        write(root.resolve("data/beltworks/recipe/x.json"), "{\"key\":{\"a\":\"#factoryworks:belts\"}}");
        assertEquals(List.of("data/beltworks/recipe/x.json names factoryworks:belts"), PlaysAlone.foreignIds(root, Beltworks.MOD_ID));
    }

    @Test
    void anIdInJavaSourceIsReported(@TempDir Path root) throws IOException {
        write(root.resolve("java/x/Pipes.java"), "class Pipes { String id = \"pipeworks:pipe\"; }");
        assertEquals(List.of("java/x/Pipes.java names pipeworks:pipe"), PlaysAlone.foreignIds(root, Beltworks.MOD_ID));
    }

    @Test
    void anIdInTheModsTomlIsReported(@TempDir Path root) throws IOException {
        write(root.resolve("resources/META-INF/neoforge.mods.toml"), "[[dependencies.beltworks]]\nmodId = \"x\" # wireworks:cable\n");
        assertEquals(List.of("resources/META-INF/neoforge.mods.toml names wireworks:cable"), PlaysAlone.foreignIds(root, Beltworks.MOD_ID));
    }

    @Test
    void theModsOwnAndTheSharedNamespacesAreAllowed(@TempDir Path root) throws IOException {
        write(root.resolve("assets/beltworks/models/x.json"),
                "{\"a\":\"beltworks:block/x\",\"b\":\"minecraft:glass\",\"c\":\"#c:glass_blocks\",\"d\":\"groundworks:x\",\"e\":\"neoforge:y\"}");
        assertEquals(List.of(), PlaysAlone.foreignIds(root, Beltworks.MOD_ID));
    }

    private static void write(Path file, String text) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, text);
    }
}
