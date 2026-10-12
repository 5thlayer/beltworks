## Commits

Conventional commits: `<type>(<optional scope>): <summary>`, with the summary in the imperative and lower case. The types in use are `feat`, `fix`, `refactor`, `test`, `docs`, `build`, `ci` and `chore`. A breaking change marks its type with `!` (`feat!: ...`), and its release bumps the minor (libworks' ADR 0001). A commit that closes an issue ends its body with `Closes #<n>`.

## Testing

`sh ./gradlew build` runs the JUnit tests, on a plain JVM with no Minecraft. `sh ./gradlew runGameTestServer` runs the game tests headless, with only Groundworks and the libworks runtime nested in the Mod's jar beside it, and names each one it ran; it fails if it ran none. Jade, JEI and Energized Power are on the dev client only. `PlaysAloneTest` fails on another Module's id, namespace or required dependency in the Mod's code or resources, or on a tag the code reads that the game tests don't check, and `PlaysAloneTests` on a tag the Mod reads or its data names that only another mod fills (FactoryWorks ADR-0127). CI (`.github/workflows/ci.yml`) calls libworks' `module-ci.yml` at the tag of the plugin `build.gradle` applies, which runs both on every push, and REUSE lint, and never publishes. Data only the Mod's own runs load lives in `src/gametest/resources`, which never ships (libworks ADR 0004): `DevPacks` switches on `gametest_pack/` in the game tests and `dev_pack/` in the dev client, when the Mod has one, the datapacks that stand in for what the Pack would add. Packs a test switches on itself, such as one per batch, go under `gametest_packs/<name>/`.

## Agent skills

### Issue tracker

GitHub Issues on `5thlayer/beltworks` (always pass `-R 5thlayer/beltworks`). See `docs/agents/issue-tracker.md`.

### Triage labels

Default five-role vocabulary (needs-triage, needs-info, ready-for-agent, ready-for-human, wontfix). See `docs/agents/triage-labels.md`.

### Domain docs

Single-context: `GLOSSARY.md` + `docs/adr/` at repo root. See `docs/agents/domain.md`.

### Releases

A change players notice adds its line under `## Unreleased` in `CHANGELOG.md` as it lands. Before bumping `mod_version`, publishing to `~/.m2`, tagging a release or uploading to Modrinth or CurseForge, read `docs/agents/releases.md`: releases go through the libworks plugin's `release` task, which uploads last with its `upload` task, and a published version never changes, in `~/.m2` or on either site. CI never publishes.

A release that involves Groundworks follows the `release-train` skill: one owning session per checkout, releases in order Groundworks → Beltworks, and pushes only on the user's word.
