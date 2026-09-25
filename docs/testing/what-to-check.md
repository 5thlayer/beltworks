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
