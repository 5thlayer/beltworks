// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.model;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** The {@code model} package needs neither Minecraft nor NeoForge, so plain JUnit can test it (ADR 0009). */
class PlatformFreeTest {

    // A reference to a class, however it is written in the source, ends up in the constant pool
    // under its internal name. Mojang's libraries come with Minecraft, so they count as Minecraft.
    private static final List<String> PLATFORM_PACKAGES = List.of("net/minecraft/", "com/mojang/", "net/neoforged/");

    @Test
    void noClassInTheModelPackageReferencesMinecraftOrNeoForge() throws IOException, URISyntaxException {
        var classes = classesUnder(BeltTier.class);
        assertFalse(classes.isEmpty(), "found no classes to check");

        var offenders = new ArrayList<String>();
        for (var file : classes) {
            var constants = new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1);
            for (var prefix : PLATFORM_PACKAGES) {
                if (constants.contains(prefix)) offenders.add(file.getFileName() + " references " + prefix);
            }
        }
        assertEquals(List.of(), offenders);
    }

    private static List<Path> classesUnder(Class<?> member) throws IOException, URISyntaxException {
        var directory = Path.of(member.getResource(member.getSimpleName() + ".class").toURI()).getParent();
        try (Stream<Path> files = Files.walk(directory)) {
            return files.filter(file -> file.toString().endsWith(".class")).toList();
        }
    }
}
