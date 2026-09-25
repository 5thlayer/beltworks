# Releases

A release reaches the Pack through the local maven repository (`~/.m2`) at a version of its own. The Pack pins that version and compares the jar's sha256. That only works if the jar published at a version is the one jar that version ever names.

## Cutting a release

1. Bump `mod_version` in `gradle.properties` to a version `~/.m2/repository/io/github/5thlayer/beltworks/` does not hold yet, and commit it.
2. `./gradlew publishToMavenLocal`. The published jar is the `jar` task's, with Groundworks nested under `META-INF/jarjar/`. The Pack loads the Groundworks that Beltworks bundles, so check `META-INF/jarjar/metadata.json` names the Groundworks and range you meant to ship.
3. Tag the released commit with an annotated `beltworks-v<version>`.

## A published version is final

A version in `~/.m2` never changes. A fix, or a rebuild against another Groundworks, is the next patch version. `publishToMavenLocal` refuses a version that is already there (`build.gradle`), and nothing is deleted or overwritten by hand to get past it.

## Tags

A Beltworks tag is `beltworks-v<version>`. This repo still carries upstream SimpleBelts' `v0.1.0` to `v0.2.2`, and Beltworks' own versions restarted at 0.1.0, so a bare `v<version>` would collide with them. `git tag -l 'beltworks-v*' -n` lists the releases.

## Releasing an older commit

To release a commit behind main, check it out in a worktree, publish it with `-Pmod_version=<version>` instead of committing the bump, and tag that commit. The tag's message records the command and the jar's sha256. `beltworks-v0.1.1` is one: the #49 commit, the last to nest Groundworks 0.1.

## Versions so far

- `0.1.0` in `~/.m2` predates this rule and nests Groundworks 0.3. It has no tag, and the Pack does not pin it.
- `0.1.1`: the #49 commit, nesting Groundworks 0.1.0 (`[0.1,0.2)`).
- Main's next release is `0.2.0`.
