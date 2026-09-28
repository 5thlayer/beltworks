#!/bin/bash
# SPDX-FileCopyrightText: 2026 5thlayer
# SPDX-License-Identifier: MIT

# Readies a Claude Code cloud session to build and test: Groundworks is published to ~/.m2 on
# the developer's machine only, so a disposable container builds against a checkout of it
# through -PsiblingBuilds, set here as a Gradle default. Does nothing on a local machine.
set -euo pipefail

if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  exit 0
fi

groundworks="$HOME/5thlayer/groundworks"
if [ ! -d "$groundworks/.git" ]; then
  GIT_LFS_SKIP_SMUDGE=1 git clone --quiet --depth 1 https://github.com/5thlayer/groundworks "$groundworks"
fi

mkdir -p "$HOME/.gradle"
if ! grep -q '^siblingBuilds=' "$HOME/.gradle/gradle.properties" 2>/dev/null; then
  printf 'siblingBuilds=true\ngroundworksDir=%s\n' "$groundworks" >> "$HOME/.gradle/gradle.properties"
fi

# Fetches the JDK, Minecraft and the dependencies now, so the session's first build starts warm.
cd "$CLAUDE_PROJECT_DIR"
sh ./gradlew --quiet compileJava compileTestJava
