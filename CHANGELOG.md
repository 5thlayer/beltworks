# Changelog

## Unreleased

- Requires Groundworks 0.5.4 or later, where it accepted any 0.5. Groundworks is no longer inside the jar: install it alongside Beltworks, from Modrinth or CurseForge, where it is listed as a required dependency.

## 0.3.17

- Nests Groundworks 0.5.4: a footprint part left in a world after a mod changed its shape no longer crashes the game when used, aimed at or broken, and Groundworks' item tags have names in EMI (5thlayer/groundworks#42).

## 0.3.16

- Nests Groundworks 0.5.2, which adds footprints: a block placed and broken whole across several positions (5thlayer/groundworks#40).

## 0.3.15

- A belt tile, splitter, loader or feeder of another tier now replaces a placed one in place with a click, up or down, for one item charged and the old one handed back. The new block keeps what the old one held: a tile its items and its shape, a splitter both halves' items and its settings, a loader its filter and facing, a feeder its filter, reach and tail turn, and each the energy its tier holds. A sneak-click places beside instead (5thlayer/beltworks#94).

## 0.3.14

- Nests Groundworks 0.5.1: a stretch pays from the held stack first, and hands the tiles it replaces back into the slot it freed. Groundworks' Fast Replace comes with it, which belt pieces join in a later release.
## 0.3.13

- An item that lands on a belt tile, thrown, spilled or dropped from above, now joins the belt where it lands when there is room for it, a stack one item at a time as room allows, instead of riding past the belt's own items. With no room it rests on the items under it: it moves with them on a moving belt and stands still with them on one that has backed up, and joins once its spot has room. Players on a tile are still carried at the tile's speed (5thlayer/beltworks#92).
- Aiming at a belt tile and pressing the drop key puts one item from the selected slot on the belt, at the point you aim at, instead of throwing it. If that point is taken the item goes to the nearest free place on the same tile, and a tile with no room leaves the item in your slot and says so on the action bar. Aiming at anything else, beyond your block interaction range, or dropping the whole stack with Ctrl+Q throws as before (5thlayer/beltworks#91).
- A splitter's screen has a filter slot beside output priority: click it with an item on the cursor to set the filter, which copies the item and takes nothing, click it with an empty cursor to clear it, or drag an item onto it from JEI's list. The screen shows your inventory below the settings, so any item can be picked up and used. What the filter matches, as a loader's does, goes only to the output-priority side and everything else only to the other, each waiting when its side backs up, so a blocked item holds its lane. Setting a filter turns output priority on for the side shown, turning it off clears the filter, and the filter item is drawn on the priority half (5thlayer/beltworks#22).
- A splitter's screen also sets an input priority, above output priority: the splitter takes from that side first, and from the other whenever that side has nothing to pass. Breaking the splitter clears it (5thlayer/beltworks#21).
- A splitter has a screen, opened by sneaking and using either half with an empty hand. Its output priority sends everything to one side, and to the other only when that side backs up. Breaking the splitter clears it (5thlayer/beltworks#20).

## 0.3.12

- Nests Groundworks 0.5.0.

## 0.3.11

- Splitters placed back to back pass items on, as with a tile between them; each passes no more than its own tier (5thlayer/beltworks#85).
- A splitter half hands items straight into a loader facing it and side-loads a tile line it faces, and a loader loads straight into a splitter half, with no tile between (5thlayer/beltworks#89).
- Every belt tile, loader and splitter crafts from vanilla ingredients. Tier 1 belt tiles come 16 at a time from dried kelp or leather over copper; each higher tier is an upgrade of the tier below with iron, gold or diamond. Loaders start from a hopper, splitters from a comparator, and the smoker turns rotten flesh into leather. Each recipe shows in the recipe book once its ingredient is in hand (5thlayer/beltworks#17).

## 0.3.10

- A feeder moves items one at a time from the inventory behind it to the one in front, at 1.5 items a second (5thlayer/beltworks#67).
- A feeder takes from and drops onto a belt: any level tile of a line, straight or corner, and a splitter half. Its head takes from anywhere on the tile while the line runs on; its tail drops at the tile's midpoint, waiting for a gap. A slope or a loader is never an end (5thlayer/beltworks#68).
- Feeders come in every tier: fast, express and turbo feeders move 3, 4.5 and 6 items a second, each a tenth of its tier's loader, and craft from that tier's loader and belt tile. They take a loader's filter, set by clicking with an item and cleared with an empty hand. Each tier's FE per item is its own entry under `feederJoulesPerItem` in the server config (5thlayer/beltworks#69).
- A feeder's head and tail each reach one, two or three blocks, over whatever stands between and at the same rate, and its tail may turn left or right. (5thlayer/beltworks#70).
- A feeder's head also takes items lying loose at its target block and delivers them. A tail never drops items loose: aimed at air, it waits (5thlayer/beltworks#72).
- The Mod's own two keys, Head Reach (`H`) and Tail Reach (`J`), lengthen a feeder's arm a block per press, from one to three and back to one. Aimed at a placed feeder, a press changes that arm and says its new reach; on a held feeder, it sets the reach every feeder placed from the stack takes, and the Placement Preview draws the arms at it (5thlayer/beltworks#71).
- A feeder needs power only when loaders do: with `loadersNeedPower` off, the default, every feeder moves items for free, and with it on, every tier pays its `feederJoulesPerItem` for each item, tier 1 included (5thlayer/beltworks#80).
- A feeder is drawn as a flat slate base at belt height with a column at its centre, holding its two arms in its tier colour at their reach: the head ends in a sweeper nozzle, which sucks in each item it takes, and the tail in a spout. Its filter is shown on the column's sides, and it collides only with its base and column (5thlayer/beltworks#73).
- Rotate turns a placed feeder's tail, straight, right, left and back, as it turns a tile's way out; its head stays where it reaches (5thlayer/beltworks#66).

## 0.3.9

- The jar carries its licensing: LICENSE, NOTICE crediting Rearth and malcolmriley, and the licence texts. It nests Groundworks 0.4.6, which carries its own.

## 0.3.8

- Nests Groundworks 0.4.5: a stretch is refused whole where the player may not build (5thlayer/groundworks#16).

## 0.3.7

- A Dismantle queues spans: a sneak-click with a start stored queues the span to the clicked tile, and one click confirms up to two spans at once (Groundworks 0.4.4).
- A loader's tier-colour cheeks run straight on into the stripes of the belt it feeds, and its mouth is as wide as the belt between them.

## 0.3.6

- The belt family wears slate: a tile's sides and underside, a slope's, a wedge's, a splitter's and a support's now match the loader's housing, with tier colour the only accent. The splitter item no longer shows the posts the placed splitter never had.
- Two neighbouring supports share the leg at their common corner, one leg on the blocks' boundary, where before two stood side by side.
- A belt's underside is one flat slate, with no step where tiles meet, and a darker rib runs down each side of it, round a corner, under a slope and its wedge, and down both sides of each splitter half.

## 0.3.5

- The Placement Preview draws the supports a click will leave, under a planned tile, a stretch's tiles or a splitter, in the plan's tint: red when it's refused.

## 0.3.4

- A middle or top whose block under it is dug out stands on a wedge in the block's place, where before the slope's junction was left open. A block set in a wedge's place holds its slope up instead, rather than breaking it.
- A wedge no longer collides, so a player fits under a raised slope where there is room. It can still be aimed at and broken.

## 0.3.3

- Groundworks 0.4.3 ships inside the jar in place of 0.4.2, telling each leg of a stretch where it stands in it.
- A stretch's climb may end on the stretch's end: Lower aimed at the column where the foot lands lays it.
- A stretch's preview draws the corner at an anchor where the stretch turns, as it is laid.
- A stretch started on a line's last tile takes it up, turning it into the stretch so the line flows on into it. Started anywhere else on a line crossing it, the stretch is still refused, since turning that tile would cut the line.

## 0.3.2

- Groundworks 0.4.2 ships inside the jar in place of 0.4.1. After Rotate (`R`), a belt stretch's start runs the turned way again, and storing it uses the turn up.

## 0.3.1

- A stretch whose climb doesn't fit before its next anchor or turn now says so, rather than that a belt slope never turns.

## 0.3.0

- A belt stretch runs in Groundworks, with the same sneak-clicks. It no longer follows the ground or climbs a crossing line on its own: it climbs or descends only where you press Raise (`G`) or Lower (`B`), as a foot, middles and a top right after the leg's first anchor, and ends in mid-air if you like. A block or a crossing line in its way is gone round flat, or the stretch is refused; a stretch never cuts a line, so you cross one by raising, adding an anchor past it, and lowering. A stretch's start no longer takes the turn of Rotate the Plan.
- A tile replaced by one of another tier keeps the items it carried, whatever replaced it.

## 0.2.2

- The Dismantle game tests hold the first tool in `groundworks:dismantles` rather than an iron pickaxe, so they pass in a pack that trims the tag to its own tools.

## 0.2.1

- Groundworks 0.4.1 ships inside the jar in place of 0.3. Beltworks' belts keep their own stretch for now, so nothing in play changes.

## 0.2.0

- Rotate (`R`) and Reverse Rotate (`Shift+R`), from Groundworks 0.3, with nothing else installed. A held tile, splitter or loader places turned a quarter per press, and a stretch's start is stored looking the turned way. A placed level tile or loader turns in place; a splitter half, a slope and a wedge refuse and say why, as does a level tile whose turn would slope a corner or leave its wedge no room. Rotate in Place never turns another mod's block.
- Raise and Lower, from Groundworks 0.3.
- A raised turn's leg at its empty outer corner no longer stops in mid-air beside the curve: it passes on to the ground below.

## 0.1.1

- Groundworks 0.1 now ships inside the jar, so nothing else needs installing. A tile, splitter or stretch shows its plan before the click, and a refused placement says why.
- Groundworks also runs the Stretch and the Dismantle. With a pickaxe or wrench, a sneak-click stores the Dismantle's start and a click names its end. An end on a splitter or loader is refused as not the same kind.
- A raised belt shows supports: legs down to the first solid top or belt piece, at corners, line ends, a slope's foot and top, splitters, and every 8 blocks along a straight run. They are only drawn: nothing is placed, and they never refuse a placement.
- A client config, `beltworks-client.toml`, holds each player's view of supports: whether they show, the spacing and how far a leg reaches. It's editable from the Mods menu and takes effect at once.
- Loaders need no power unless the server config says so. `beltworks-server.toml` has `loadersNeedPower` (off by default) and `joulesPerFe`.
- With Jade installed, a belt tile has a tooltip.
- Upstream's recipes for belt items the Mod doesn't have are gone, so none fails to load.

## 0.1.0

- Renamed to Beltworks, a mod of its own, with its version restarting at 0.1.0.
- A clean break from `belts:`: every block, item, tag, recipe and payload is now under `beltworks:`, and nothing is remapped. Old `belts:` blocks and items vanish from existing worlds.
- NeoForge only, for Minecraft 26.1.2, with no dependencies beyond NeoForge.

## Upstream: Simple Conveyor Belts

Beltworks started as a fork of Simple Conveyor Belts by Rearth. These are Upstream's entries up to the fork point, kept as they were.

- Update / migrate to 26.1.2
- Add mk2 belt (twice as fast)
- Add entity & player collisions to belts
- Add entity movement to belts
