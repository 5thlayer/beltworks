# What to check in game

What the unit and game tests cannot see: how a change looks and feels in a running client. Run `scripts/quicklaunch.sh`, which opens the latest save and refuses while a client is already open, try each check, and add the checks a change brings.

## A belt's underside (#57)

The belt family wears slate, with tier colour its only accent. See **Tier colour** in `CONTEXT.md`.

- Look up at a floating line from below: its underside is one flat slate, with no step or shade where tiles meet, and a rib runs down each side of it in darker slate, one pixel wide and one pixel deep, unbroken from tile to tile.
- Follow the ribs round a floating corner: the outer one steps round the curve with the outer wall, and the inner one is a single pixel at the turn's inner corner. Both line up with the ribs of the straight tiles either side.
- Follow them along a climb through air, level tile to foot, middle, top and level again, and the same down a descent: they run under the slope's pitched underside and on under each wedge, and line up where each piece meets the next.
- Look at a tile, a corner and a slope on the ground from the side: the ribs stand on the block below, the belt's surface is where it was, and a one-pixel slot shows between the ribs at a tile's open end.
- A splitter shows a rib down both sides of each half, two side by side under its divider.
- A floating tile's support: its frame's struts along the tile's sides meet the ribs flush, with no flicker. The struts across the tile's ends sit one pixel below the raised underside.
- Check each tier: the ribs are the same slate on all four.

## Supports (ADR 0012)

A raised line shows a **support** where a builder would hold it up. See **Support** in `CONTEXT.md`.

- A floating corner shows a support: a leg up each corner of its block to the tile's top, and a frame under the tile tying the legs along each edge. The legs meet the tile's corners with no gap and no flicker. On a turn, the leg at its outer corner stands back where the curve's outer edge is, and the struts to it run at an angle. A floating line's first and last tiles show a support too.
- A straight floating line shows a support every 8th block along it, at x (or z) a multiple of 8, and none on the straight tiles between. Build the same line from its other end, or the other way: the supports stand at the same blocks.
- A tile on the ground, or on top of another tile, shows none.
- A tile beside a solid block on a side its line does not use shows none: left or right of a straight tile, or behind a corner or on the side opposite its entry. A solid block ahead of a line's last tile, or behind its first, doesn't count, and a support shows.
- Legs reach the first block below with a solid top, or a belt piece. They pass water and tall grass, and anything else without a solid top, such as a torch, a fence or a bottom slab.
- Place a block under a floating line's last tile, a few blocks down. The legs shorten to it at once. Dig it out and they lengthen again. Try it 30 blocks down too.
- Over a drop deeper than 64 blocks, the legs run on out of sight.
- A raised line crossing a lower one at right angles: its legs land on the lower tile's corners, clear of the items it carries.
- A raised line one block above a lower line that turns under it: the leg over the corner's empty outer corner passes it and reaches the ground below, with no foot stopping in mid-air beside the curve. Its other legs land on the curve.
- Walk through the legs. They have no collision.
- Stand under a raised line's last tile and look up, then look down at it from above. Its legs stay drawn while the tile is off screen.
- A floating slope's foot and top show a support, whether or not its line stops there; its middles show one only where the spacing falls on them, with no frame. Check a climb and a descent, each mid-line and at a line's ends, and a long climb across a multiple of 8.
- A slope's legs pass its wedge down to the ground, and meet its surface: a foot's rise to a level tile's surface, and a top's stop at its low edge, at the tile's floor, never sticking up above the belt. A slope's support has no frame, so nothing shows through its belt.
- A floating splitter shows one support: a leg at each of the pair's four outer corners, and a frame under both halves. It still shows one when only one half is over air. Place a solid block against either outer side and it goes; one behind or ahead of a half doesn't count. Check a splitter facing each way.
- A floating loader never shows a support.

### In the Placement Preview

Holding tiles or a splitter, before the click:

- Aim a single tile out from the side of a pillar, over air: the preview draws its support, and the click leaves the same one. Aim it on the ground: none.
- Sneak-click a start on the ground, press `G`, and aim a few blocks ahead and to the side, so the stretch turns in mid-air. The preview draws the supports the click leaves, see-through in the plan's white: at the top on its wedge, the corner, the last tile, and any straight tile the spacing falls on. Click: the laid supports stand where the preview drew them, and no more.
- Aim the same stretch one block further: the last tile's support moves with the end, and the tile that was last loses its own, unless the spacing or a corner holds it.
- Stretch a raised line out from the last tile of a raised line already laid: the laid tile's support stays drawn as it is until the click, and afterwards it goes if the tile is no longer its line's last. The preview draws only the planned pieces' supports, and the world's stay as they are.
- Aim a stretch that gets refused, such as `G` twice aimed only three blocks ahead: its supports draw red with its tiles.
- Aim a stretch over a block or a lower line a few blocks down: the preview's legs stop on it, as the laid ones do.
- Aim a splitter in mid-air, and with one half over a solid block: its preview shows one support under both halves, as the click leaves it. Refused, it draws red.
- Turn **Show supports** off: the preview draws none, with no restart and without moving the aim.

### Support settings

In the Mods menu, Beltworks' config screen, or `config/beltworks-client.toml`, with a long straight floating line in view:

- Turn **Show supports** off. Every support goes, on tiles, slopes and splitters. Turn it on and they come back, with no restart.
- Set **Support spacing** to 4. The line's spaced supports move to every 4th block at once. Corners, line ends, feet and tops keep theirs.
- Set **Support reach** to 8 over a drop deeper than that. The legs stop 8 blocks down and run on out of sight; set it back to 64 and they reach the ground again, with no restart. Look up at a long leg's foot from below with the tile off screen: it stays drawn.

## Rotate (Groundworks ADR 0003)

With nothing but Beltworks installed. See **Rotate** in `CONTEXT.md`.

- `R` and `Shift+R` are listed once each, under Groundworks' key category, in the controls screen.
- Holding tiles, press `R`: the Placement Preview redraws a quarter clockwise at once, and `Shift+R` turns it back. The click places what the preview drew. Do the same with a splitter and with a loader on the ground.
- With nothing held, aim at a level tile and press `R`: it turns, and the tiles around it re-derive their corners. A loader turns too.
- Aim at a splitter half, a slope and a wedge, and press `R`: each stays put, and its reason shows on the action bar.
- Aim at a vanilla block with a facing, such as a furnace, and press `R`: nothing happens, and nothing is said.

## Stretch (Groundworks ADR 0004)

With nothing but Beltworks installed. See **Stretch** in `CONTEXT.md`.

- Sneak-click a tile item on the ground, press `G` once, and aim a few blocks ahead over flat ground. The preview draws a foot and a top right after the start, with the top's wedge under it, then level tiles one block up in mid-air. Click: what is laid, wedge included, is what the preview drew, and it charges one tile per tile, none for the wedge.
- Press `G` twice and aim only three blocks ahead: the preview draws red and the click lays nothing. Aim further and it turns clear.
- Lay a line across your path. Stretch at its height towards it and past: the preview goes round the line's end if it is short, and draws red if it runs wide. It never draws over the line.
- Cross it: sneak-click a start, press `G`, sneak-click an anchor past the line, press `B`, and click further on. The line underneath still carries, and the new one runs over it.
- Aim a stretch's end at a line's side: the stretch stops short of it and feeds its side.
- Sneak-click an anchor, then aim to the side of it: the preview draws a corner at the anchor, as the click lays it.
- From a start one block up, press `B` and aim at the column where the foot lands: the preview draws a top and a foot ending there, and the click lays them.
- Sneak-click a start on the last tile of a line, looking across it, and stretch away: the tile turns into the stretch, and items on the line run on into it. Start on a tile in the middle of the line instead: the preview draws red and the click lays nothing.
