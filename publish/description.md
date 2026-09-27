> **Want spline belts?** Those are **Simple Conveyor Belts** by Rearth, the mod Beltworks started from: [GitHub](https://github.com/Rearth/SimpleBelts) · [CurseForge](https://www.curseforge.com/minecraft/mc-mods/simple-conveyor-belts). Beltworks is a different mod, with belts laid on the block grid.

Beltworks adds Factorio-style belts to Minecraft, laid block by block on the grid and built to work with any tech mod.

**NeoForge, Minecraft 26.1.2 only.** Beltworks is early work, so please report any issues you find on [GitHub](https://github.com/5thlayer/beltworks/issues).

## Features

- **Tiles merged into transport lines.** A belt is laid one tile per block, facing the way you look. Tiles feeding one another join into a transport line that moves as one, around corners and up and down slopes. A tile holds eight items, so a line is a buffer as well as a route, and when its end backs up you can see the items waiting.
- **One gesture lays a whole stretch.** Sneak-click a tile item to start a stretch, sneak-click again to add an anchor, then click where it ends. The stretch is laid, and paid for, in one go, and goes round a block in its way. While you aim, a preview draws what the click would lay, in red where it would be refused.
- **Slopes, and crossing over a line.** A line climbs or descends one block per block of travel. Press Raise (`G`) or Lower (`B`) while laying a stretch to change its height, so you cross a line by raising, adding an anchor past it, and lowering.
- **Four tiers.** Transport belts, splitters and loaders carry 15, 30, 45 and 60 items a second. Each piece caps only its own flow, so a line of mixed tiers runs at its slowest piece.
- **Splitters.** A splitter takes two belts in and sends two belts out, splitting evenly and sending everything to one side when the other backs up. Build balancers from them.
- **Works with any tech mod.** A loader set against any inventory, vanilla or modded, pulls items from it onto the belt or pushes them into it. Right-click a loader with an item to filter what it moves; FTB Filter System's smart filters work too.
- **Dismantle.** Sneak-click a line's tile with a wrench or a pickaxe, then click another tile of the same line: everything between them comes up with its items.

## Dependencies

None beyond NeoForge. Groundworks, the library that plans and previews placements, is bundled inside the jar, so there is nothing else to install.

## Credits

Beltworks builds on two works, both under [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/):

- **Simple Conveyor Belts** by Rearth ([GitHub](https://github.com/Rearth/SimpleBelts) · [CurseForge](https://www.curseforge.com/minecraft/mc-mods/simple-conveyor-belts)), whose code and textures Beltworks modifies.
- **unused-textures** by malcolmriley ([GitHub](https://github.com/malcolmriley/unused-textures)), which the belt textures come from.

Code is MIT and assets are CC BY 4.0. The jar carries the full LICENSE and NOTICE; source is on [GitHub](https://github.com/5thlayer/beltworks).
