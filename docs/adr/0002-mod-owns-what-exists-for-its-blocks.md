# The Mod owns what exists only for its blocks, and the Pack changes it only through data and config

_Amended by ADR 0010: the previews are drawn by the placementpreview library. The Mod keeps its plans and its overlays._

_Amended by ADR 0011: the Dismantle and Stretch gestures move to the Groundworks library, and the `dismantles_belts` defaults move to `groundworks:dismantles`. The Mod keeps the belt family and the tile item's joining of anchors._

_Amended by Groundworks ADR 0003: Rotate is no longer the Pack's. It moves into Groundworks, which the Mod bundles, so a tile turns by Rotate in any pack and the Pack still has one `R`. The Mod states that Rotate in Place turns its own blocks, and they answer for themselves: a splitter half, a slope and a wedge refuse, and a level tile refuses where placing it turned would be refused._

Code that exists only because of the Mod's blocks belongs to the Mod, even when the Pack wrote it first: tile and splitter placement plans, the previews of those plans, the dismantle preview, the Jade plugin and the belt gametests move out of the Pack's core. Pack-wide mechanics that happen to cover belts stay in the Pack: Fast Replace groups, build reach. Rotate is pack-wide too, and lives in Groundworks, which the Mod bundles (Groundworks ADR 0003), so a tile turns by Rotate in any pack, and the Mod's blocks answer Rotate in Place themselves. The Mod is survival-playable when nothing else is installed. It ships vanilla-ingredient recipes for every item, members of `dismantles_belts` by default (`#c:tools/wrench` and `#minecraft:pickaxes`), and energy-free loaders by default. A pack changes all of that through recipes, tags and the server config, never by patching the Mod's code. This is how "works with any tech mod" holds when the Pack is only the first consumer.

## Considered Options

- **The Mod ships mechanisms only, and each pack supplies recipes and tool tags.** Rejected because a pack without a recipe script would get uncraftable belts.
- **Default recipes from common `c:` tags (steel, circuits).** Rejected because they do not resolve unless a tech mod is installed.
- **The Mod takes Rotate as well.** Rejected because Rotate is pack-wide, and the Pack would end up with two `R` bindings. Groundworks, which both depend on, takes it instead.
- **Powered loaders by default.** Rejected because vanilla has no FE source, so tiers 2 to 4 would stall in a world where only the Mod is installed.

## Consequences

- Loader power keeps the Factorio joule derivation in the Mod. A server config sets whether loaders need power (off by default) and **joules per FE** (100 by default, the Pack's rate). The Pack ships a `config/beltworks-server.toml` that switches power on: on 26.1 a server config is read from `config/`, with a world's `serverconfig/` overriding it, and `defaultconfigs/` is no longer read.
- The loader is a belt end, not *the* inserter. "No inserter anywhere" and "no long-handed inserter" are the Pack's rulings.
- Optional integrations with third-party mods, such as FTB Filter System and Jade, live in the Mod as soft dependencies.
- Recipes from the Factorio corpus, Researchd unlocks, the Engineer's Pick and reach 16 stay in the Pack.
