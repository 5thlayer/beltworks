#!/usr/bin/env bash
# Release Beltworks <version> from HEAD: the changelog's Unreleased entries become <version>'s, the
# build and game tests pass, and the jar is published to the local maven repository and tagged.
#
#   scripts/release.sh <version>
#
# It commits and tags but pushes nothing. The rules it keeps are in docs/agents/releases.md.
# $MAVEN_REPO_LOCAL publishes somewhere other than ~/.m2/repository, to try the script out.
set -euo pipefail
cd "$(dirname "$0")/.."

fail() { echo "release: $*" >&2; exit 1; }

version="${1:-}"
[[ "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || fail "usage: scripts/release.sh <major.minor.patch>"
tag="beltworks-v$version"
repo="${MAVEN_REPO_LOCAL:-$HOME/.m2/repository}"
published="$repo/io/github/5thlayer/beltworks/$version"

[[ -z "$(git status --porcelain)" ]] || fail "the working tree has changes; commit or stash them first."
! git rev-parse -q --verify "refs/tags/$tag" > /dev/null || fail "$tag already exists."
[[ ! -e "$published" ]] || fail "$version is already in $published, and a published version never changes."

# A released Beltworks never nests an unreleased Groundworks. -PsiblingBuilds can come from the
# environment or any gradle.properties, so Gradle itself is asked: under it the build names the
# Groundworks checkout on a siblingBuilds: line.
sibling="$(sh ./gradlew -q help 2>&1 | grep '^siblingBuilds:' || true)"
[[ -z "$sibling" ]] || fail "the build is under -PsiblingBuilds (${sibling#siblingBuilds: }); unset it to release."

# The entries between "## Unreleased" and the next heading are what this release ships.
entries="$(awk '/^## /{on = ($0 == "## Unreleased"); next} on && NF' CHANGELOG.md)"
[[ -n "$entries" ]] || fail "CHANGELOG.md has nothing under \"## Unreleased\"; a release ships what it lists."

# Until the commit, a failure puts both files back, so a failed release leaves nothing behind.
trap 'git checkout -- gradle.properties CHANGELOG.md' EXIT
sed -i.bak "s/^mod_version = .*/mod_version = $version/" gradle.properties && rm gradle.properties.bak
awk -v v="$version" '{print} $0 == "## Unreleased" {print ""; print "## " v}' CHANGELOG.md > CHANGELOG.md.new
mv CHANGELOG.md.new CHANGELOG.md

sh ./gradlew build runGameTestServer
git commit -q -m "Beltworks $version" -- gradle.properties CHANGELOG.md
trap - EXIT

sh ./gradlew "-Dmaven.repo.local=$repo" publishToMavenLocal
sha="$(shasum -a 256 "$published/beltworks-$version.jar" | cut -d' ' -f1)"
git tag -a "$tag" -m "Beltworks $version" -m "jar sha256 $sha"

echo "Published $published"
echo "jar sha256 $sha"
echo "Push with: git push origin HEAD $tag"
