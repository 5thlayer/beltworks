# Beltworks

A Minecraft belt system that blends Factorio and Satisfactory and is built to work with any tech mod. It started as a fork of Upstream and is now its own project.

## Language

### Projects

**Upstream**:
Rearth/SimpleBelts, the project the Mod was forked from, published as "Simple Conveyor Belts" on CurseForge and Modrinth. Its code and assets are licensed CC BY 4.0.
_Avoid_: SimpleBelts (when meaning the Mod), the original

**Beltworks**:
This project, the Mod. "The Mod" is an accepted alias.
_Avoid_: the fork, SimpleBelts, Belt Works, belts (that was Upstream's mod id, not a name)

**Groundworks**:
The library mod for mass placement and **Dismantle**, for the Mod and the Pack alike: it plans what a click would lay or take up, shows the plan before the click, and carries it out. The Mod bundles it in its own jar (ADR 0010). Formerly placementpreview, which only drew **Placement Plans**.
_Avoid_: placementpreview (its old name), preview lib, the renderer, Groundwork

**the Pack**:
PlanetaryFactory, the modpack that is the Mod's first consumer.
_Avoid_: the modpack, PF

**Proving set**:
The inventories the Mod is checked against before a release to show that it works with any tech mod: vanilla's containers and each tech mod the Mod names. A tech mod joins once it ships a build for the Mod's Minecraft version.
_Avoid_: supported mods, compat list (the Mod works with mods outside it)

### Belts

**Belt**:
A run of tiles carrying items from one belt end to another, paid for at one belt item per tile. It carries its tier's whole throughput in one lane and holds eight items per tile, so a belt is a buffer as well as a route (ADR 0003). It climbs and descends by **slopes**, which is how one belt crosses over another (ADR 0005).
_Avoid_: conveyor, belt segment, lane

**Tile**:
One block of belt, placed and broken on its own, facing the way items travel through it: the way the player looks when placing it. It holds eight items and carries them at its tier's speed. A tile fed from exactly one side, with nothing feeding it from behind, is a **corner** and turns the line through one block; otherwise it is straight. Its shape and its **pitch** are derived from its neighbours, never chosen, and re-derived when a neighbour is placed, broken or turned; a corner is always level (ADR 0004).
_Avoid_: belt block, conveyor block, segment

**Transport line**:
The contiguous run of tiles each feeding the next, around corners and across slopes, merged at runtime so the whole run ticks once. A line is derived state: each tile keeps its own share of the items, so merging and splitting a line loses nothing. It runs at its slowest tile, is loaded by the loader behind its first tile and unloaded by the one past its last, and never spans an unloaded chunk. A line closed on itself is a ring, with no first tile and no last.
_Avoid_: belt line, chain, run

**Side-load**:
A tile or other belt piece feeding the side of a straight tile. It does not join the line it feeds: two lines meet there, and the side-loading line's items merge into gaps on the other, which goes first. Factorio's side-load reduced to a merge, since one lane has no far lane to fill.
_Avoid_: T-junction, merge belt

**Tier colour**:
The colour a belt tier is painted in, yellow, red, blue and green in tier order, as Factorio does. The belt shows it as the stripes along its edges, the loader as its band and the splitter as its divider. It is the family's only accent: everything else, from a tile's frame and sides to a loader's housing, is slate.
_Avoid_: stripe colour, tier tint

**Belt hand**:
Holding the use key on a belt piece with anything but a belt piece in hand: the holder takes the items reaching the aimed point at the belt's rate, while what was already past it runs on. On a tile the whole tile is aimed at. A full inventory stops taking and lets the belt run on, losing nothing.
_Avoid_: grab, pick up from belt

### Climbing

**Pitch**:
Whether a tile rises, is level or descends along its travel, derived from the height of the tile feeding it and of the tile it feeds: one block up, level or one block down. A tile whose feeder and fed tile are both higher, or both lower, does not connect to them: there is no crest and no valley (ADR 0005).
_Avoid_: incline, grade

**Slope**:
A tile whose pitch is not level: a **foot**, a **middle** or a **top**. Together they draw one straight 45° line, one block of height per block of travel. A slope is one block of line like any tile, however long its surface draws; the climb is drawn, not counted. It carries riders and a hand as a level tile does. It never turns, is never turned in place, takes no side-load, and a loader or splitter never meets one.
_Avoid_: ramp, incline, half slope, full slope

**Foot**:
The last tile at the lower height of a climb: level for its back six pixels, then rising to its block's top. Placing a tile above and ahead of a level tile turns that tile into a foot.

**Middle**:
A slope rising a whole block within its own block, between a foot and a top when a climb is more than one block high.

**Top**:
The first tile at the upper height of a climb: rising six pixels from its block's floor, then level. A foot turned half round.

**Wedge**:
The block drawn under a middle or a top that stands over air, reading as the junction between two slopes. It is part of its tile: placed with it for nothing, and broken with it. It needs nothing under it, so a belt can climb through open air, and it takes a replaceable block's place but never a solid one's.
_Avoid_: support (a raised line's, a different thing), pillar, scaffold

**Support**:
The struts that show a raised line held up where a builder would hold it: at a corner, at its line's first and last tiles, a slope among them, at a splitter, and at straight tiles and slopes spaced along a run by their place in the world. A belt piece there shows one only when it stands over air: nothing under it to rest on, a block with a solid top or a belt piece, and no solid block to fix to on a side its line does not use. A wedge under it holds nothing up. A support is a crossbar under the tile and a leg down each corner of its block, from the height of a level tile's surface to the first block below with a solid top, or a belt piece. Standing at corners, a leg never crosses where items pass, on its own tile or on a belt piece it lands on, whichever way either runs. A leg passes a wedge, fluids and replaceable blocks, follows the world as it changes, and has no collision; with nothing to stand on within reach, it runs out of sight. A splitter shows one support for both halves, with legs at the pair's outer corners, when either half needs it. A loader never shows one, being fixed to its inventory. A support is part of its tile's shape, derived, never placed and free, and exists only so a raised line reads as held up; it never limits how far a line may float (ADR 0012).
_Avoid_: pillar, pole, scaffold, wedge (a slope's, a different thing), anchor (a stretch's point, which a laid tile does not know)

### Belt ends and splitters

**Belt end**:
Where a belt starts or stops: a loader, set against an inventory; a splitter half; or its last tile with nothing in front of it, where the line backs up (ADR 0004).
_Avoid_: terminator, endpoint

**Loader**:
A belt end set against an inventory: it pulls onto the belt from the inventory behind it, or pushes into it. It has tiers of its own that cap what it moves, and draws power for each item only when the server config says loaders need power (ADR 0007). It carries no items itself: like Factorio's 1×1 loader, it hands items straight between the inventory and the tile at its mouth, so a line's capacity is its tiles' alone. It is placed like any block beside the inventory, with its back to it and its mouth to the belt: on the ground it faces the player, and against a block's side it faces away from that block. It is drawn as a solid housing with a low mouth at belt height.
_Avoid_: chute (Upstream's name), inserter, funnel

**Splitter**:
A block two wide whose halves are each a block of belt of its tier: each holds eight items and carries them, and whatever stands on it, at its tier's speed. It joins two belts in to two belts out at its midline, splitting evenly, merging, and sending everything to one side when the other backs up. It draws no power. Placed across a straight tile line running its way, it takes the tile's place, refunds it and keeps its items (ADR 0007).
_Avoid_: merger, tunnel

**Input priority**:
A splitter's preference for one of its two inputs: it takes from that side first, and from the other whenever that side cannot move. None, left or right. Lost when the splitter is broken.
_Avoid_: input filter

**Output priority**:
A splitter's preference for one of its two outputs: everything goes to that side, and to the other only when that side is backed up. None, left or right. Lost when the splitter is broken.

**Splitter filter**:
An item, or a filter item standing for several, set on a splitter with an output priority side. What it matches goes only to that side, and everything else only to the other; either waits when its side is backed up, never overflowing. Clearing the output priority clears the filter. Matches by item, as a loader's filter does.
_Avoid_: sorter, filter splitter

**Balancer**:
A pattern of splitters that spreads several belts evenly across several others. Built by the player, never a block.
_Avoid_: balancer block

### Building

**Stretch**:
The tiles one drag of the tile item lays, placed, charged and refused as one. A sneak-click stores its start and the way the player looks; the next click lays it to the aimed spot, in one straight leg or two joined by one turn, the first leg along the stored look. Each sneak-click before that adds an **anchor** where the stretch would end. It follows the ground, each tile one block up, level or one block down from the one before, and is refused whole at a step it cannot climb. A line running across its path is climbed over in five tiles, a foot, a top, a level tile on the line, a top and a foot; a stretch never plans a path under a line. Any other tile already on its path is turned to it, or replaced when of another tier.
_Avoid_: run, zoop, drag (the gesture, not what it lays), tile path

**Dismantle**:
Taking up a span of one transport line from one tile to another, both included, with a dismantling tool (by default a wrench or a pickaxe): a sneak-click stores the start, and a click names the end. A sneak-click with a start stored moves it there. The span follows the line through its corners and slopes, either way along it, and takes the tiles' wedges and every item they carry; the rest of the line keeps what it carries. Loaders and splitters are never taken. What the span drops goes to the inventory, and what does not fit drops at the player's feet. Distinct from mining, which breaks one tile.
_Avoid_: deconstruct, mass mine, unstretch

**Anchor**:
A point of a stretch's route that the player fixed, at its height: its start, each point a sneak-click adds, and the aimed end. A stretch passes through every anchor, and the item placed decides how to join each anchor to the next.
_Avoid_: corner (a tile's shape, not a point of a route), waypoint, node

**Placement Plan**:
What a held belt piece would do at an aimed spot: the positions it would fill, the blockstate at each, and a refusal or none. Placing executes a plan and the **Placement Preview** draws one, so both ask one rule (ADR 0006). A stretch's plan is its tiles, from the stored start to the aimed spot; a splitter's is both halves, refused whole.
_Avoid_: placement context (vanilla's own type, one input to a plan), build plan, preview state

**Dismantle family**:
A kind of connected block that a **Dismantle** takes up as one span, with its own rule for which blocks a span follows between its two ends and what the span takes. The Mod's is the belt family, whose span follows one transport line and takes the tiles' wedges and items with them. Groundworks runs the gesture for every family alike.
_Avoid_: dismantle group, connected type

**Dismantle Plan**:
What a **Dismantle** would take up at an aimed tile: the tiles of the span from the stored start, and a refusal or none. Dismantling executes a plan and the preview draws one, as with a **Placement Plan**; the two are separate things.
_Avoid_: removal plan, placement plan (for a dismantle)

**Placement Preview**:
What a player sees while holding a belt piece and aiming at a spot: what placement would put there, drawn translucent, red where it would be refused. With a dismantling tool and a dismantle's start stored, it draws the tiles the **Dismantle Plan** would take up, in red, and only the stored start when it would be refused. It changes nothing in the world.
_Avoid_: ghost (Factorio's ghost is an entity left for robots to build), hologram, blueprint preview
