# Changelog

## Unreleased

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
