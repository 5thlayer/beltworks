// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * NOTICE credits the works the Mod builds on, as CC BY requires. The libworks plugin puts NOTICE in
 * the jar and checks it is there; what it says is the Mod's own to check.
 */
class NoticeTest {

    @ParameterizedTest
    @ValueSource(strings = {"Rearth", "malcolmriley"})
    void noticeCredits(String author) throws IOException {
        // Gradle runs the tests from the project directory.
        assertTrue(Files.readString(Path.of("NOTICE")).contains(author), "NOTICE credits " + author);
    }
}
