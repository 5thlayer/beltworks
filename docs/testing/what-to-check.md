# What to check in game

What the unit and game tests cannot see: how a change looks and feels in a running client. Run `./gradlew runClient`, try each check, and add the checks a change brings.

## Supports (ADR 0012)

A raised line shows a **support** where a builder would hold it up. See **Support** in `CONTEXT.md`.

- A floating corner shows a support: a leg up each corner of its block to the tile's top, and a frame under the tile tying the legs along each edge. The legs meet the tile's corners with no gap and no flicker. On a turn, the leg at its outer corner stands back where the curve's outer edge is, and the struts to it run at an angle. A floating line's first and last tiles show a support too.
- A straight tile mid-line shows none.
- A tile on the ground, or on top of another tile, shows none.
- A tile beside a solid block on a side its line does not use shows none: left or right of a straight tile, or behind a corner or on the side opposite its entry. A solid block ahead of a line's last tile, or behind its first, doesn't count, and a support shows.
- Legs reach the first block below with a solid top, or a belt piece. They pass water and tall grass, and anything else without a solid top, such as a torch, a fence or a bottom slab.
- Place a block under a floating line's last tile, a few blocks down. The legs shorten to it at once. Dig it out and they lengthen again. Try it 30 blocks down too.
- Over a drop deeper than 64 blocks, the legs run on out of sight.
- A raised line crossing a lower one at right angles: its legs land on the lower tile's corners, clear of the items it carries.
- Walk through the legs. They have no collision.
- Stand under a raised line's last tile and look up, then look down at it from above. Its legs stay drawn while the tile is off screen.
- A floating line ending on a foot or a top shows a support there. Its legs rise to a level tile's surface height, and under a top or a middle they pass the wedge down to the ground. Check a line that starts on a foot, one that ends on a climb's top, and one that ends at the foot of a descent. At a slope's low end, see how the legs above its surface and the frame inside its wedge look.
- A climb or a descent in the middle of a line shows no support on its feet, middles or tops.
- A floating splitter shows one support: a leg at each of the pair's four outer corners, and a frame under both halves. It still shows one when only one half is over air. Place a solid block against either outer side and it goes; one behind or ahead of a half doesn't count. Check a splitter facing each way.
- A floating loader never shows a support.
