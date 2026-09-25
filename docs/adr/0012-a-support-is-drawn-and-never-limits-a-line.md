# A support is drawn and never limits a line

A tile can stand anywhere, and it stays when the block under it goes (the Pack's #392). Once the Stretch moves into Groundworks (ADR 0011), a leg keeps its height across a dip instead of following the ground, so raised lines become the common case. They should read as held up. So a raised line shows a **support**: a crossbar under the tile and a leg down each corner of its block, to the first block below with a solid top, or a belt piece. It shows where a builder would put one: at a corner, a foot or a top, at a line's first and last tiles, at a splitter, and at straight tiles spaced along a run by their world position. It shows only over air. A tile resting on a block with a solid top or on a belt piece needs none, and neither does one fixed to a solid block on a side its line does not use.

A support is part of the tile's shape. Like a corner or a pitch, it is derived and never chosen. It is never placed and costs nothing, and it never refuses a placement: a line may float as far as the player likes. The legs are drawn, with no collision, and follow the world as it changes, however far below the change is. Standing at block corners, a leg never crosses where items pass on either belt, whichever way the two run.

## Considered Options

- **At most N unsupported tiles in a row, refused beyond that.** Rejected. It limits where a line can go and adds a refusal the player cannot see coming, when the aim was only how a raised line looks.
- **A support block placed by the player, with a recipe or free as a pack chooses.** Rejected. Any block with a solid top would count too, so a price on the support item could always be dodged, and height would stop being cheap.
- **A support block placed by the Stretch, one support item charged per floating tile, Satisfactory's conveyor poles.** Rejected. A support appears and goes as the ground under a line changes. A charge would need a paid mark on a tile whose look is otherwise pure shape, and laying a line on the ground and then digging it out gets around the charge.
- **A support on every tile over air.** Rejected. It turns a raised line into a fence.
- **A support at every stretch anchor.** Rejected. A laid tile does not know the anchors of the stretch that laid it. Corners, feet, tops and line ends are where anchors land, and a tile can derive them from its neighbours.
- **A leg down each side of the tile.** Rejected. A leg landing on a belt running across would stand where its items pass.

## Consequences

- ADR 0003's condition holds, since crossing over costs nothing more than it did.
- Groundworks' height gesture (5thlayer/placementpreview#7) needs no support rule. A level leg across a dip shows legs down into it, and nothing is refused.
- Adding a limit on floating later would break raised lines that worlds already have, so it would take a new decision.
- The support is the Mod's, as ADR 0011 left it: the rule, the spacing, the legs and the drawing. The Placement Preview draws each planned tile's support through Groundworks' overlay hook (ADR 0010), with the same code as a placed tile, and works it out from the world with the plan's tiles counted as placed, so it matches what the click lays (ADR 0006). Groundworks never hears of supports. If the Pack's stretched pipes want supports too, a shared support in Groundworks is decided then.
- Showing supports is a client setting, on by default, as is the spacing along a straight run.
