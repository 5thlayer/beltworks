## Commits

Conventional commits: `<type>(<optional scope>): <summary>`, with the summary in the imperative and lower case. The types in use are `feat`, `fix`, `refactor`, `test`, `docs`, `build`, `ci` and `chore`. A breaking change marks its type with `!` (`feat!: ...`), and its release bumps the minor (libworks' ADR 0001). A commit that closes an issue ends its body with `Closes #<n>`.

## Testing

`sh ./gradlew build` runs the JUnit tests, on a plain JVM with no Minecraft. `sh ./gradlew runGameTestServer` runs the game tests headless and names each one it ran; it fails if it ran none. `python3 -m unittest discover scripts/tests` tests the upload step against a stand-in server on localhost. CI (`.github/workflows/ci.yml`) runs all three on every push, and REUSE lint, and never publishes.

## Agent skills

### Issue tracker

GitHub Issues on `5thlayer/beltworks` (always pass `-R 5thlayer/beltworks`). See `docs/agents/issue-tracker.md`.

### Triage labels

Default five-role vocabulary (needs-triage, needs-info, ready-for-agent, ready-for-human, wontfix). See `docs/agents/triage-labels.md`.

### Domain docs

Single-context: `CONTEXT.md` + `docs/adr/` at repo root. See `docs/agents/domain.md`.

### Releases

A change players notice adds its line under `## Unreleased` in `CHANGELOG.md` as it lands. Before bumping `mod_version`, publishing to `~/.m2` or tagging a release, read `docs/agents/releases.md`: releases go through `scripts/release.sh`, and a published version never changes.

A release that involves Groundworks or the Pack follows the `release-train` skill: one owning session per checkout, releases in order Groundworks → Beltworks → Pack, and pushes only on the user's word.
