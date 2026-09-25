# Releases

A release reaches the Pack through the local maven repository (`~/.m2`) at a version of its own. The Pack pins that version and compares the jar's sha256. That only works if the jar published at a version is the one jar that version ever names.

## The changelog

Each change players notice adds its line under `## Unreleased` in `CHANGELOG.md` when it lands, written for players rather than as the commit subject. A release ships what Unreleased lists, so the changelog is written as the work is done and never reconstructed from commits.

## Cutting a release

`scripts/release.sh <version>` from a clean main. It refuses a version that is already tagged or in `~/.m2`, and an empty Unreleased. It then:

1. sets `mod_version` and turns `## Unreleased` into `## <version>` under a fresh, empty Unreleased
2. runs the build and the game tests, putting both files back if either fails
3. commits `Beltworks <version>`, runs `publishToMavenLocal`, and tags `beltworks-v<version>` with the jar's sha256

It pushes nothing, and ends by printing the push command. The published jar is the `jar` task's, with Groundworks nested under `META-INF/jarjar/`. The Pack loads the Groundworks that Beltworks bundles, so check that `META-INF/jarjar/metadata.json` names the Groundworks and the range you meant to ship.

## A published version is final

A version in `~/.m2` never changes. A fix, or a rebuild against another Groundworks, is the next patch version. `publishToMavenLocal` refuses a version that is already there (`build.gradle`), and nothing is deleted or overwritten by hand to get past it.

## Tags

A Beltworks tag is `beltworks-v<version>`. This repo still carries upstream SimpleBelts' `v0.1.0` to `v0.2.2`, and Beltworks' own versions restarted at 0.1.0, so a bare `v<version>` would collide with them. `git tag -l 'beltworks-v*' -n` lists the releases.

## Releasing an older commit

To release a commit behind main, check it out in a worktree, publish it with `-Pmod_version=<version>` instead of committing the bump, and tag that commit. The tag's message records the command and the jar's sha256. `beltworks-v0.1.1` is one: the #49 commit, the last to nest Groundworks 0.1.

## The stray 0.1.0

The `0.1.0` in `~/.m2` predates these rules and nests Groundworks 0.3. It has no tag, is not a release, and is never pinned.
