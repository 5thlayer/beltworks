# A tile climbs only where it is set to

_Amends ADR 0005: a tile's pitch is still derived, but from its neighbours' climbs rather than their heights alone. Amends ADR 0013: the Mod has a third key of its own._

Under ADR 0005 a tile sloped wherever a tile facing the same way stood a block up or down ahead and the pairing rule let it. Lines laid one over another, a block apart, then joined or not depending on the order they were laid in: a line's first tile, with no level tile ahead yet, sloped up into the line above (#107). A player could only avoid it by laying lines in one order.

So each tile has a **climb**, down, level or up, kept on its block state and level unless set. It is the tile's way out: an up tile feeds the tile facing the same way a block up ahead, a level tile the level one ahead, a down tile the one a block down ahead. A tile's pitch is derived as before, from two heights: the fed end is its own climb, where a tile facing the same way stands there, and level where none does; the feeder end is the height of the tile behind, level first, then a block up or down, whose climb leads into this one. A connection is so both tiles' or neither's without a separate pairing rule. A one-block climb is the lower tile set up; a longer one has every tile but the top set up. Lines a block apart never join unless a tile is set to.

A new key of the Mod's own, **Climb**, unbound by default, cycles down, level and up. As Head Reach does (ADR 0013), a press sets the held tile stack's next placement, kept on the stack until its last item is placed and drawn by the **Placement Preview**, and otherwise sets the tile under the crosshair in place. An in-place change is refused, with its reason and nothing changed, where the reshape it causes would be refused as a placement. A stretch sets each tile's climb from the heights it lays (Groundworks ADR 0004).

## Considered Options

- **Keep the derivation, and refuse the order-dependent slope.** Rejected: which slope is wrong depends on what the player means, which the world does not show.
- **Auto as a fourth climb, the default,** keeping ADR 0005's derivation for tiles never set. Rejected: the bug is that derivation, and a default that keeps it keeps the bug for every player who never binds the key.
- **A pitch key that forces a tile's shape.** Rejected: a forced foot, middle or top could draw a slope into nothing, or a joint only one of its two tiles makes.

## Consequences

- Tiles in a world saved before this have no climb and load level, so their slopes flatten the next time a tile near them is placed, broken or turned, and must be set up again.
- A tile beside a line no longer climbs over it on its own: it side-loads it, unless set up.
- The Climb key is unbound, so a pack that binds keys has one more to place, and a player who never binds it lays slopes only by stretch.
