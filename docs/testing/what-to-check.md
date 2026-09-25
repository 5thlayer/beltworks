# What to check in game

What the unit and game tests cannot see: how a change looks and feels in a running client. Run `scripts/quicklaunch.sh`, which opens the latest save and refuses while a client is already open, try each check, and add the checks a change brings.

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

### Support settings

In the Mods menu, Beltworks' config screen, or `config/beltworks-client.toml`, with a long straight floating line in view:

- Turn **Show supports** off. Every support goes, on tiles, slopes and splitters. Turn it on and they come back, with no restart.
- Set **Support spacing** to 4. The line's spaced supports move to every 4th block at once. Corners, line ends, feet and tops keep theirs.
- Set **Support reach** to 8 over a drop deeper than that. The legs stop 8 blocks down and run on out of sight; set it back to 64 and they reach the ground again, with no restart. Look up at a long leg's foot from below with the tile off screen: it stays drawn.

## Rotate (Groundworks ADR 0003)

With nothing but Beltworks installed. See **Rotate** in `CONTEXT.md`.

- `R` and `Shift+R` are listed once each, under Groundworks' key category, in the controls screen.
- Holding tiles, press `R`: the Placement Preview redraws a quarter clockwise at once, and `Shift+R` turns it back. The click places what the preview drew. Do the same with a splitter and with a loader on the ground.
- Press `R`, then sneak-click: the stretch's start is drawn facing the turned way, and the stretch runs out along it. With the start stored, press `R`: the stretch drawn does not change.
- With nothing held, aim at a level tile and press `R`: it turns, and the tiles around it re-derive their corners. A loader turns too.
- Aim at a splitter half, a slope and a wedge, and press `R`: each stays put, and its reason shows on the action bar.
- Aim at a vanilla block with a facing, such as a furnace, and press `R`: nothing happens, and nothing is said.
