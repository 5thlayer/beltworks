## Agent skills

### Issue tracker

GitHub Issues on `5thlayer/beltworks` (always pass `-R 5thlayer/beltworks`). See `docs/agents/issue-tracker.md`.

### Triage labels

Default five-role vocabulary (needs-triage, needs-info, ready-for-agent, ready-for-human, wontfix). See `docs/agents/triage-labels.md`.

### Domain docs

Single-context: `CONTEXT.md` + `docs/adr/` at repo root. See `docs/agents/domain.md`.

### Releases

Before bumping `mod_version`, publishing to `~/.m2` or tagging a release, read `docs/agents/releases.md`: a published version never changes, and tags are `beltworks-v<version>`.
