# The Mod is NeoForge-only, one Minecraft version at a time

The Mod runs on NeoForge and nothing else. Fabric does not come back, and ADR 0008 is the only compatibility contract. The Architectury API goes along with the Fabric support it existed for. The Mod is one NeoForge project built with ModDevGradle, not `common/` and `neoforge/` projects built with Architectury Loom. Registries, events and networking use NeoForge directly, and logic that needs no loader lives in its own package rather than its own project. The Mod supports one Minecraft version at a time. Each NeoForge build is tied to one exact Minecraft patch, so that version is an exact patch too. There are no backports and no maintained old branches, and when the Mod moves, the version it leaves stops getting fixes. The Mod moves when the Pack chooses to move, as the first step of the Pack's port. Under the name Beltworks, the Mod's version restarts at `0.1.0`, and the Minecraft version goes in the jar's name rather than in the version string.

## Considered Options

- **Bring Fabric back.** Rejected because it would need a second compatibility contract against Fabric's Transfer API, with its own quirks, proving set and gametests, which doubles the Mod's main promise. The Pack and the proving set are NeoForge.
- **Keep the Architectury API, or keep only its multiloader layout.** Rejected because with one loader it is only plumbing. Every Minecraft update would wait on Architectury's API and on its `-SNAPSHOT` Loom plugin, and players would install a library that the Pack contains only for the Mod. The rename touches every package anyway, so that is when the layout goes.
- **Keep several Minecraft versions alive.** Rejected because each branch multiplies the proving set, which needs a build of every tech mod for that version.
- **Move whenever NeoForge or the proving set is ready.** Rejected for now because the Pack is the Mod's only user until a public release, and moving without it would strand the one install that exists. The trigger is revisited at public release.
- **Continue Upstream's 2.x version numbers, or put the Minecraft version in the version string.** Rejected. The mod id `beltworks` makes a clean break from `belts:`, and with a single Minecraft version the version string doesn't need to name it.

## Consequences

- The rename also rewrites the Architectury call sites (registries, creative tab, block entity renderer, tick and interaction events, and `@ExpectPlatform` in `BeltSync` and `PlatformBlockEntityTypes`) and replaces the Gradle build.
- `mods.toml` declares the exact Minecraft patch and drops the `architectury` dependency. The Pack drops Architectury API unless another mod needs it.
- The readme's "both Fabric and NeoForge" claim and its Architectury and Fabric API dependencies go.
- While the Mod is `0.x`, moving to a new Minecraft version bumps the minor version: a move breaks the saves and packs of the version left behind, a breaking change under libworks' ADR 0001 (<https://github.com/5thlayer/libworks/blob/main/docs/adr/0001-below-1-0-an-addition-bumps-the-patch.md>), which sets the Mod's version bumps.
