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
The library mod for mass placement and **Dismantle**, for the Mod and the Pack alike: it plans what a click would lay or take up, shows the plan before the click, and carries it out. It also runs **Rotate**. The Mod bundles it in its own jar (ADR 0010). Formerly placementpreview, which only drew **Placement Plans**.
_Avoid_: placementpreview (its old name), preview lib, the renderer, Groundwork

**the Pack**:
PlanetaryFactory, the modpack that is the Mod's first consumer. Its repo is adamico/planetary-factory, checked out at `~/curseforge/Instances/PlanetaryFactory`.
_Avoid_: the modpack, PF

**Proving set**:
The inventories the Mod is checked against before a release to show that it works with any tech mod: vanilla's containers and each tech mod the Mod names. A tech mod joins once it ships a build for the Mod's Minecraft version.
_Avoid_: supported mods, compat list (the Mod works with mods outside it)

### Belts

**Belt**:
A run of tiles carrying items from one belt end to another, paid for at one belt item per tile. It carries its tier's whole throughput in one lane and holds eight items per tile, so a belt is a buffer as well as a route (ADR 0003). It climbs and descends by **slopes**, which is how one belt crosses over another (ADR 0005).
_Avoid_: conveyor, belt segment, lane

**Tile**:
One block of belt, placed and broken on its own, facing the way items travel through it: the way the player looks when placing it, turned by any **Rotate the Plan**. It holds eight items and carries them at its tier's speed. A tile fed from exactly one side, with nothing feeding it from behind, is a **corner** and turns the line through one block; otherwise it is straight. Its shape and its **pitch** are derived from its neighbours, never chosen, and re-derived when a neighbour is placed, broken or turned; a corner is always level (ADR 0004).
_Avoid_: belt block, conveyor block, segment

**Transport line**:
The contiguous run of tiles each feeding the next, around corners and across slopes, merged at runtime so the whole run ticks once. A line is derived state: each tile keeps its own share of the items, so merging and splitting a line loses nothing. It runs at its slowest tile, is loaded by the loader behind its first tile and unloaded by the one past its last, and never spans an unloaded chunk. A line closed on itself is a ring, with no first tile and no last.
_Avoid_: belt line, chain, run

**Side-load**:
A tile or other belt piece feeding the side of a straight tile. It does not join the line it feeds: two lines meet there, and the side-loading line's items merge into gaps on the other, which goes first. Factorio's side-load reduced to a merge, since one lane has no far lane to fill.
_Avoid_: T-junction, merge belt

**Tier colour**:
The colour a belt tier is painted in, yellow, red, blue and green in tier order, as Factorio does. The belt shows it as the stripes along its edges, the loader as its band, the splitter as its divider and the feeder as its arms. It is the family's only accent: everything else, from a tile's frame, sides and support to a loader's housing, is slate.
_Avoid_: stripe colour, tier tint

**Belt hand**:
Holding the use key on a belt piece with anything but a belt piece in hand: the holder takes the items reaching the aimed point at the belt's rate, while what was already past it runs on. On a tile the whole tile is aimed at. A full inventory stops taking and lets the belt run on, losing nothing.
_Avoid_: grab, pick up from belt

### Climbing

**Pitch**:
Whether a tile rises, is level or descends along its travel, derived from the height of the tile feeding it and of the tile it feeds: one block up, level or one block down. A tile whose feeder and fed tile are both higher, or both lower, does not connect to them: there is no crest and no valley (ADR 0005).
_Avoid_: incline, grade

**Slope**:
A tile whose pitch is not level: a **foot**, a **middle** or a **top**. Together they draw one straight 45° line, one block of height per block of travel. A slope is one block of line like any tile, however long its surface draws; the climb is drawn, not counted. It carries riders and a hand as a level tile does. It never turns, is refused **Rotate in Place**, takes no side-load, and a loader or splitter never meets one.
_Avoid_: ramp, incline, half slope, full slope

**Foot**:
The last tile at the lower height of a climb: level for its back six pixels, then rising to its block's top. Placing a tile above and ahead of a level tile turns that tile into a foot.

**Middle**:
A slope rising a whole block within its own block, between a foot and a top when a climb is more than one block high.

**Top**:
The first tile at the upper height of a climb: rising six pixels from its block's floor, then level. A foot turned half round.

**Wedge**:
The block drawn under a middle or a top that stands over air, reading as the junction between two slopes. It is part of its tile: placed with it for nothing, and broken with it. It needs nothing under it, so a belt can climb through open air, and it takes a replaceable block's place but never a solid one's. It follows the block under its tile: a block dug out from under a middle or top leaves a wedge in its place, and a block set in a wedge's place holds the tile up instead. It collides with nothing, so what fits under it walks under it.
_Avoid_: support (a raised line's, a different thing), pillar, scaffold

**Support**:
The struts that show a raised line held up where a builder would hold it: at a corner, at a slope's foot and top, at its line's first and last tiles, at a splitter, and at straight tiles and slopes spaced along a run by their place in the world. A belt piece there shows one only when it stands over air: nothing under it to rest on, a block with a solid top or a belt piece, and no solid block to fix to on a side its line does not use. A wedge under it holds nothing up. A support is a frame under the tile and a leg down each corner of its block (a slope's has no frame, its tilted belt tying the legs), a turn's outer leg standing back where its curve's edge is, from its tile's surface where it stands, and no higher than a level tile's, to the first block below with a solid top, or a belt piece. Standing at its tile's edge, a leg never crosses where items pass, on its own tile or on a belt piece it lands on, whichever way either runs. A leg passes a wedge, fluids, replaceable blocks and the empty outer corner of a turn it comes down on, follows the world as it changes, and has no collision; with nothing to stand on within reach, it runs out of sight. A splitter shows one support for both halves, with legs at the pair's outer corners, when either half needs it. Two neighbouring supports with a leg at the same corner share one there, standing on their blocks' boundary. A loader never shows one, being fixed to its inventory. A support is part of its tile's shape, derived, never placed and free, and exists only so a raised line reads as held up; it never limits how far a line may float (ADR 0012). Whether supports show, the spacing, and how far a leg reaches are each player's own, in the client config.
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

**Feeder**:
A block that moves items one at a time from the inventory or belt tile its **head** reaches to the one its **tail** reaches, so one belt can feed a row of machines. Either end may be a chest, a machine or a tile anywhere along a line, and the head also picks up items lying loose in the world. It moves whatever the far side accepts, with no knowledge of recipes. It has the loader's tiers and filter at a tenth of the loader's rate, and always draws power for each item at every tier, at a figure of its own, whatever the server config says of loaders. A tail with nothing to drop into waits: a feeder never drops items loose. It is drawn as a flat slate base at belt height with a column at its centre holding up its two arms. A splitter half serves as an end as a tile does; a loader never does, holding nothing. It is no belt end: a line runs past the tile it takes from or drops on.
_Avoid_: double loader, inserter, sweeper

**Head**:
The end of a feeder that takes, at the end of its **arm**, one to three blocks behind it. It is drawn as a sweeper's nozzle: an item it takes is seen until it is sucked in, and not after, while it runs through the arm to the column and on to the tail.
_Avoid_: picker, input, pickup, sweeper (its look, not its name)

**Tail**:
The end of a feeder that drops, at the end of its **arm**, one to three blocks in front of it or turned left or right like a tile, counted from the feeder.
_Avoid_: dropper, output, drop

**Arm**:
What a feeder is drawn reaching out with to its head or its tail, one arm each. An arm's reach, one to three blocks, is set on the held feeder before placing, with the **Placement Preview** drawing the arms at it, and changed on a placed feeder with the same keys. An arm passes over whatever stands between, collides with nothing, and moves at the same rate at any reach.
_Avoid_: reach (the distance, not the thing), boom, extension, pipe

**Head Reach** / **Tail Reach**:
The Mod's own two keys, each lengthening one **arm** a block at a time, from three back to one. On a held feeder they set the reach its next placement takes, which stays with the held stack until its last item is placed, and the **Placement Preview** redraws with it; on a placed feeder under the crosshair they change that arm in place.
_Avoid_: reach key, extend, adjust

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

### Rotating

**Rotate**:
Groundworks' one action on one key (`R` by default, and **Reverse Rotate** the other way on `Shift+R`) that turns a quarter at a time. It **Rotates the Plan** when the held item is rotatable, and otherwise **Rotates in Place** the block under the crosshair. The Mod gets Rotate from the library it bundles, so a tile turns by Rotate in any pack; its only keys of its own are **Head Reach** and **Tail Reach**.
_Avoid_: rotate key, turn, wrench rotate

**Rotate the Plan**:
**Rotate** on the held item: its next placement turns a quarter from the way the player looks, and the **Placement Preview** redraws with it. The turn stays with the held stack until its last item is placed. A tile, a splitter and a loader turn so, and a sneak-click stores a **stretch**'s start looking the turned way, which uses the turn up.
_Avoid_: rotate the preview, held rotate

**Rotate in Place**:
**Rotate** on a placed block: the block under the crosshair turns, and what turning means is the block's own. The Mod states it turns the Mod's own blocks and never another mod's. A level tile and a loader turn a quarter. A splitter half, a **slope** and a **wedge** are refused with their reason, and a level tile is refused with its placement's reason where the turn would slope a corner or its wedge would have no room. A refusal changes nothing.
_Avoid_: placed rotate, wrench rotate

### Building

**Stretch**:
The tiles one drag of the tile item lays, planned, charged, laid and refused whole. Groundworks runs it for any item, and the Mod builds a belt's **legs** (ADR 0011, Groundworks ADR 0004). A sneak-click stores its start, at the height **Raise** and **Lower** hold, and the way the player looks; each further sneak-click adds an **anchor**; a click lays it. Its route passes through every anchor, a leg at a time, each seen from above one straight line or two joined by one turn, the first along the stored look. It never changes height by itself, not over a hill, a drop or a line: a leg climbs or descends only by the rise the player set with Raise and Lower, as a **foot**, its **middles** and a **top** right after its first anchor, and runs level after them. A leg whose climb would turn is refused, and so is one whose climb reaches an anchor the stretch runs on from; a climb may end on the stretch's end. A tile running along a leg is kept, turned to it if it faces back, or replaced when of another tier. A line crossing a leg is an obstacle, as is anything else a tile can't take the place of and a wedge with no room: Groundworks goes round it flat, or refuses the stretch. A stretch never cuts a line: to cross one, the player presses Raise, adds an anchor past it, and presses Lower. It joins a line only where that cuts nothing: an end on a line feeds its side, and a start on a line's last tile turns that tile into the stretch, the line flowing on into it. A start or an anchor anywhere else on a line crossing it is refused.
_Avoid_: run, zoop, drag (the gesture, not what it lays), tile path

**Dismantle**:
Taking up a span of one transport line from one tile to another, both included, with a dismantling tool (by default a wrench or a pickaxe, the Mod's members of `groundworks:dismantles`): a sneak-click stores the start, a sneak-click with a start stored queues the span to the clicked tile, and a click names the last span's end, if a start is in progress, and confirms every queued span with it as one pass, whole or nothing: one refused span, as one whose start has gone, refuses the pass and changes nothing. A pass holds at most two spans, and a sneak-click with two queued is refused; a sneak-use in the air clears the start and the queue. Each span stays within its own family, so a pass may take spans of several families, but no span crosses from one to another; where two spans overlap, each tile is taken once. Groundworks runs it, and the Mod supplies the belt family (ADR 0011). The span follows the line through its corners and slopes, either way along it, and takes the tiles' wedges and every item they carry; the rest of the line keeps what it carries. Loaders and splitters are never taken: they are no tiles, so an end on one is refused as not the same kind as the start's tile. What the span drops goes to the inventory, and what does not fit drops at the player's feet. Distinct from mining, which breaks one tile.
_Avoid_: deconstruct, mass mine, unstretch

**Anchor**:
A point of a stretch's route that the player fixed: its start, each point a sneak-click adds, and the aimed end. The aim picks only where it lies seen from above; its height is the stretch's height there, never the aimed block's. A stretch passes through every anchor, and each **leg** runs from one to the next.
_Avoid_: corner (a tile's shape, not a point of a route), waypoint, node

**Leg**:
The part of a stretch from one anchor to the next, which Groundworks hands the Mod to build: its first anchor, its rise and its route seen from above. A belt's is a tile in each column, level, with a climb of slopes right after its first anchor when it rises or falls. Not a **support**'s leg, which is a strut down its block's corner.
_Avoid_: segment, section

**Placement Plan**:
What a held belt piece would do at an aimed spot: the positions it would fill, the blockstate at each, and a refusal or none. Placing executes a plan and the **Placement Preview** draws one, so both ask one rule (ADR 0006). A stretch's plan is its tiles, from the stored start through its anchors to the aimed spot; a splitter's is both halves, refused whole.
_Avoid_: placement context (vanilla's own type, one input to a plan), build plan, preview state

**Dismantle family**:
A kind of connected block that a **Dismantle** takes up as one span, with its own rule for which blocks a span follows between its two ends and what the span takes. The Mod's is the belt family, whose span follows one transport line and takes the tiles' wedges and items with them. Groundworks runs the gesture for every family alike.
_Avoid_: dismantle group, connected type

**Dismantle Plan**:
What a **Dismantle** would take up at an aimed tile: the tiles of the span from the stored start, the tiles and wedges it draws, and a refusal or none. Groundworks owns the plan and asks the belt family for it; dismantling executes a plan and the preview draws one, as with a **Placement Plan**; the two are separate things.
_Avoid_: removal plan, placement plan (for a dismantle)

**Placement Preview**:
What a player sees while holding a belt piece and aiming at a spot: what placement would put there, drawn translucent, red where it would be refused. With a dismantling tool and a dismantle's start stored, it draws the tiles the **Dismantle Plan** would take up, in red, and only the stored start when it would be refused. It changes nothing in the world.
_Avoid_: ghost (Factorio's ghost is an entity left for robots to build), hologram, blueprint preview
