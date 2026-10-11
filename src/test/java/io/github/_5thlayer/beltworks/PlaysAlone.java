// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

/** Scans what a mod ships for another suite mod's namespace, which stops it playing alone on Groundworks (ADR-0127). */
final class PlaysAlone {

    private static final Set<String> SUITE = Set.of("beltworks", "pipeworks", "wireworks", "craftworks", "fieldworks", "factoryworks");

    private PlaysAlone() {
    }

    /** The folders under {@code data} or {@code assets} named for another suite mod. */
    static List<String> foreignNamespaces(Path root, String own) throws IOException {
        List<String> found = new ArrayList<>();
        if (!Files.isDirectory(root)) {
            return found;
        }
        try (Stream<Path> namespaces = Files.list(root)) {
            namespaces.map(p -> p.getFileName().toString())
                    .filter(ns -> SUITE.contains(ns) && !ns.equals(own))
                    .sorted()
                    .forEach(ns -> found.add(root.getFileName() + " has the namespace " + ns));
        }
        return found;
    }

    /** Every literal {@code <suite mod>:<path>} id in a file under {@code root}, the mod's own excepted. */
    static List<String> foreignIds(Path root, String own) throws IOException {
        String others = SUITE.stream().filter(ns -> !ns.equals(own)).sorted().collect(Collectors.joining("|"));
        Pattern id = Pattern.compile("\\b(" + others + "):[a-z0-9_./-]+");
        List<String> found = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
                Matcher ids = id.matcher(text(file));
                while (ids.find()) {
                    found.add(root.relativize(file).toString().replace('\\', '/') + " names " + ids.group());
                }
            }
        }
        return found;
    }

    private static String text(Path file) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        if (file.toString().endsWith(".nbt")) {
            try (InputStream in = new GZIPInputStream(Files.newInputStream(file))) {
                bytes = in.readAllBytes();
            }
        }
        // Latin-1 maps every byte to a char, so a binary file still reads and an id in it still matches.
        return new String(bytes, StandardCharsets.ISO_8859_1);
    }
}
