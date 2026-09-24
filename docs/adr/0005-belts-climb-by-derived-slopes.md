# Belts climb by derived slopes

A tile has a **pitch**, level, rising or descending along its travel, at one block of height per block of travel. Like a corner, it is derived and never chosen. It follows from two heights relative to the tile, each one up, level or one down: the tile feeding it and the tile it feeds.

| feeder \ fed | −1 | 0 | +1 |
|---|---|---|---|
| **−1** | no connection | top (up) | middle (up) |
| **0** | top (down) | level | foot (up) |
| **+1** | middle (down) | foot (down) | no connection |

A connection is both tiles' or neither's. A corner is always level. Pitch is re-derived when a tile within two blocks is placed, broken or turned, since a slope's other end is a block up or down, where no neighbour update reaches. What a tile feeds is chosen in order: a level tile ahead facing the same way, then one a block up or down ahead facing the same way, then a level tile ahead facing another way. So a tile beside a line climbs over it rather than side-loading it.

The shapes draw one straight 45° line, each inside its own block. In pixels from each tile's floor, where a level tile's surface is at 6: a **foot** is flat at 6 for 6 px, then rises to 16; a **middle** rises from 0 to 16; a **top** rises from 0 to 6, then is flat for 10 px. A slope is one block of line, as a corner is. The climb is drawing only: a line over a hill carries its tier's throughput and holds eight items a tile, and the line scan follows a line across heights.

A slope takes no side-load. A tile that would be both a corner and a slope is a slope, and a placement that would turn a corner into a slope is refused, with its reason and nothing changed. A tile facing another's side a block up or down is a crossing and places level. A loader or splitter half meets the level end of a foot or a top as it meets a level tile. A middle or top over air stands on a **wedge**, placed with its tile for nothing and broken with it, so a belt can climb through open air. A wedge takes the place of a replaceable block. A tile, loader, splitter, machine, fluid or block entity there refuses the placement, so a slope never stands on a belt.

A two-click stretch follows the ground one block up or down at a time, taking the lowest heights that change by at most a block per column. It crosses a line in its path in five tiles: a foot, a top, a level tile over the line, a top and a foot. Lines side by side are crossed as one. Its start and its end are never crossed, since aiming at a line means joining it. It is refused whole where no such path fits.

Ported from the Pack's ADR-0085.

## Considered Options

- **Underground belts.** Rejected, as ADR 0003 argues.
- **One 1:1 slope shape per tile.** Rejected. A 45° slope from a level tile's surface to the next block's would draw 6 px outside its block at each end.
- **Crests and valleys,** a tile rising and falling inside one block. Rejected. Neither reads as a belt, and each would need a shape of its own.
- **A wedge under tops only.** Rejected, because a middle over air would float.

## Consequences

- A rider is lifted over each rising collision slab, so on a slope it climbs in small hops.
- The Mod has no in-place rotation. A pack that turns placed blocks, as the Pack's Rotate does, must refuse a slope.
