# The Mod owns what exists only for its blocks, and the Pack changes it only through data and config

Code that exists only because of the Mod's blocks belongs to the Mod, even when the Pack wrote it first: tile and splitter placement plans, the previews of those plans, the dismantle preview, the Jade plugin and the belt gametests move out of the Pack's core. Pack-wide mechanics that happen to cover belts stay in the Pack: Rotate and its `BlockPlaceContext` mixin, Fast Replace groups, build reach. So in another pack a tile turns only by where the player looks. The Mod is survival-playable when nothing else is installed. It ships vanilla-ingredient recipes for every item, members of `dismantles_belts` by default (`#c:tools/wrench` and `#minecraft:pickaxes`), and energy-free loaders by default. A pack changes all of that through recipes, tags and the server config, never by patching the Mod's code. This is how "works with any tech mod" holds when the Pack is only the first consumer.

## Considered Options

- **The Mod ships mechanisms only, and each pack supplies recipes and tool tags.** Rejected because a pack without a recipe script would get uncraftable belts.
- **Default recipes from common `c:` tags (steel, circuits).** Rejected because they do not resolve unless a tech mod is installed.
- **The Mod takes Rotate as well.** Rejected because Rotate is pack-wide, and the Pack would end up with two `R` bindings.
- **Powered loaders by default.** Rejected because vanilla has no FE source, so tiers 2 to 4 would stall in a world where only the Mod is installed.

## Consequences

- Loader power keeps the Factorio joule derivation in the Mod. A server config sets whether loaders need power (off by default) and **joules per FE** (100 by default, the Pack's rate). The Pack ships `defaultconfigs` that switch power on.
- The loader is a belt end, not *the* inserter. "No inserter anywhere" and "no long-handed inserter" are the Pack's rulings.
- Optional integrations with third-party mods, such as FTB Filter System and Jade, live in the Mod as soft dependencies.
- Recipes from the Factorio corpus, Researchd unlocks, the Engineer's Pick and reach 16 stay in the Pack.
