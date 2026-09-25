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
