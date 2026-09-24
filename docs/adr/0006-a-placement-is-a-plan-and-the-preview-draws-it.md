# A placement is a plan, and the preview draws it

A preview that works out for itself where blocks would land is a second copy of every placement rule, and the two drift silently. A preview that lies is worse than none, because the player builds against it. So every placement the Mod makes is computed as a **Placement Plan**: the positions it would fill, the blockstate at each, and a refusal or none. The click executes the plan and the **Placement Preview** draws the same one, so there is one rule. A stretch's plan is its tiles. A splitter's plan is both halves, refused whole. A single tile defers to vanilla's placement. A **Dismantle Plan** follows the same contract for taking tiles up. The Mod draws its own plans with a renderer of its own, so a pack does not have to supply a preview (ADR 0002). The client builds the plan locally and draws the blocks' own models, translucent and red when refused, with no keybind and no toggle. The server's answer on the click stays authoritative.

Ported from the Pack's ADR-0069, keeping only the Mod's part. Poles, rigs and other packs' blocks are theirs.

## Considered Options

- **A client-side registry of preview rules keyed by item.** Rejected. It is the drift this decision exists to prevent.
- **Leave the drawing to a pack's own preview.** This is how the Pack did it before the move. Rejected, because the Mod must be usable with nothing else installed, and building belts with no preview is tedious.

## Consequences

- A pack with a generic placement preview of its own must skip the Mod's items, or they draw twice.
- A plan that disagrees with placement is the defect this contract exists to prevent. Plans are checked by gametests that place in a world, and whether the preview draws correctly is a human check.
