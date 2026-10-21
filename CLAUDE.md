# Beltworks

Factorio-style belts for Minecraft, laid block by block on the grid and built to work with any tech mod. It started as a fork of Simple Conveyor Belts, is now its own project, and requires Groundworks. See `GLOSSARY.md` for the domain glossary.

## Workflow

Commit on the current branch; open a feature branch only when the user asks for one. Nothing is pushed without the user's word. Asking a session to steward a PR is the user's word for that PR's pushes, its rebases onto `main`, and its merge, under the `steward` skill.

Anything that changes Beltworks' behaviour gets a `/code-review`: the project skill in `.claude/skills/code-review`, from mattpocock/skills, never the built-in review of the same name. Doc and plumbing changes skip it: that covers `CLAUDE.md`, `GLOSSARY.md`, ADRs, `docs/`, `.claude/`, submodule bumps, and tooling or CI config.

## Commits

Conventional commits: `<type>(<optional scope>): <summary>`, with the summary in the imperative and lower case. The types in use are `feat`, `fix`, `refactor`, `test`, `docs`, `build`, `ci` and `chore`. A breaking change marks its type with `!` (`feat!: ...`), and its release bumps the minor (libworks' ADR 0001). A commit that closes an issue ends its body with `Closes #<n>`.

## Testing

### Running the tests

- `sh ./gradlew build` runs the JUnit tests, on a plain JVM with no Minecraft.
- `sh ./gradlew runGameTestServer` runs the game tests headless, a real player on a real server with only Groundworks and the libworks runtime the Mod nests beside it, and names each one it ran; it fails if it ran none.
- The `skillworks:gametest-loop` skill runs a subset, `--tests 'beltworks:<glob>' 1`, one glob a run, so a ticket's new game tests share a name prefix that selects them all.
- The libworks plugin's `checkPlaysAlone`, which `build` runs, fails on another Module's id, namespace or required dependency in the Mod's code or resources (libworks ADR 0005).
- `PlaysAloneTest` fails on a tag the code reads that the game tests don't check, and `PlaysAloneTests` on a tag the Mod reads or its data names that only another mod fills (FactoryWorks ADR-0127).
- CI (`.github/workflows/ci.yml`) runs both through libworks' `module-ci.yml`, at the tag of the plugin `build.gradle` applies, on every push, with REUSE lint, and never publishes.

### Game-test code

- Everything that exists only for the Mod's own runs lives in the `gametest` source set, which never ships (libworks ADRs 0004 and 0011): its game-test code, its test-only resources and its packs.
- `BeltGameTests` hooks itself in from `FMLConstructModEvent`, and `src/main` never names game-test code.
- Where shipped code must report to a test, `main` holds a hook with no test vocabulary that the game-test side fills, as `BlockUpdatesSent` does for the block updates `ServerLevelUpdateMixin` sees.
- A new game test class is registered by a line in `BeltGameTests.registerTests`, and its tests stand on the Mod's `gametest/platform` or `gametest/long_platform` structure, in `src/gametest/resources`.
- `DevPacks` switches on `gametest_pack/` in the game tests and `dev_pack/` in the dev client, when the Mod has one, the datapacks that stand in for what the Pack would add.
- Packs a test switches on itself, such as one per batch, go under `gametest_packs/<name>/`.

### The dev client

- Jade, JEI and Energized Power are on the dev client only.
- Neither run loads a client, so a change to a screen, a model or a texture isn't done when the tests pass: list it for the user to check in the dev client.
- The `skillworks:quicklaunch` skill opens the dev client into the most recent save in `run/saves`, one client per checkout.

### Vanilla and NeoForge sources

- Vanilla's code, with NeoForge's patches, is in `build/moddev/artifacts/minecraft-patched-*-sources.jar`.
- NeoForge's own classes (`net/neoforged/...`) are in `~/.gradle/caches/modules-2/files-2.1/net.neoforged/neoforge/<version>/*/neoforge-<version>-sources.jar`, `<version>` being `neoforge_version` in `gradle.properties` (the cache keeps older ones).
- Read either with `unzip -p <jar> <path>/X.java`, and check a claim about vanilla or NeoForge behaviour there before stating it.

## Releases

A change players notice adds its line under `## Unreleased` in `CHANGELOG.md` as it lands. Before bumping `mod_version`, publishing to `~/.m2`, tagging a release or uploading to Modrinth or CurseForge, read `docs/agents/releases.md`: releases go through the libworks plugin's `release` task, which uploads last with its `upload` task, and a published version never changes, in `~/.m2` or on either site. CI never publishes.

A release that involves Groundworks follows the `release-train` skill: one owning session per checkout, releases in order Groundworks → Beltworks, and pushes only on the user's word.

## Agent skills

### Issue tracker

GitHub Issues on `5thlayer/beltworks` (always pass `-R 5thlayer/beltworks`). See `docs/agents/issue-tracker.md`.

### Triage labels

Default five-role vocabulary (needs-triage, needs-info, ready-for-agent, ready-for-human, wontfix). See `docs/agents/triage-labels.md`.

### Domain docs

Single-context: `GLOSSARY.md` + `docs/adr/` at repo root. See `docs/agents/domain.md`.
