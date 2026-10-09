# The Mod requires Groundworks instead of nesting it

ADR 0010 had the Mod bundle its library with jar-in-jar so that it ran with nothing else installed, and ADR 0011 kept that for Groundworks. But Groundworks is a mod of its own, with its own Modrinth and CurseForge projects, and the Pack loads one Groundworks whatever the Mod bundles. A nested copy gains nothing there: NeoForge picks one version of the two, and the Mod's jar carries a second copy of the library for the pick. Groundworks now loads once, from its own project, and the Mod declares it a required dependency in `neoforge.mods.toml` (range `[0.5.4,0.6)`) and on both sites, so each site's installer fetches it.

The cost is that a player who installs Beltworks alone by hand must now install Groundworks too. ADR 0002's promise that the Mod is playable on its own still holds: it concerns the Mod's gameplay needing no tech mod, and Groundworks is a library, not a tech mod.

## Considered Options

- **Keep nesting.** Rejected. It ships a second copy of a library the Pack already loads, and ties every Groundworks fix to a Beltworks release.

## Consequences

- `build.gradle` no longer calls `jarJar`; the range feeds `neoforge.mods.toml` and the `-PsiblingBuilds` check.
- `modrinth_dependencies` and `curseforge_dependencies` name Groundworks, and `scripts/upload.py` no longer checks a nested licence.
- ADR 0010 and ADR 0011 are superseded on bundling only.
