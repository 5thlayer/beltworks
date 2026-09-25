# Groundworks runs the Dismantle and the Stretch, and the Mod supplies the belt's part

The Mod and the Pack each had a **Dismantle**, one for belts and one for pipes, and the Pack's player holds one tool over both. The two had already drifted: the Mod confirms with a click, the Pack with a second sneak-click. This is the argument ADR 0010 made for the preview. Two copies on one screen drift apart, and ADR 0009 ports them together, so they would be fixed twice. So the gestures live in one library. placementpreview becomes **Groundworks**, the library for mass placement and Dismantle. It plans what a click would lay or take up, shows the plan, and carries it out. The Mod bundles it with jar-in-jar, as before (ADR 0002). By ADR 0002's own rule, what the Mod keeps is only what exists for its blocks.

- **Dismantle.**
  - Groundworks owns the gesture, the stored starts and queue, the tool tag `groundworks:dismantles`, the generic refusals, the removal, the hand-over and the preview.
  - A **Dismantle family** owns its span from a start to an end: what it draws, what it takes, and its own refusals. It also owns a hook run over everything taken before any of it is removed, and whether a stored start is still the same start.
  - The Mod supplies the belt family:
    - The span follows one transport line, as `Dismantle.span` does.
    - It takes tiles, and draws their wedges. A tile's removal takes its wedge through the Mod's own blocks, so Groundworks never hears of wedges.
    - It releases every tile's carried items first.
    - Its start holds only while the tile keeps its facing.
  - A pass may take up spans of several families, but one span never crosses from one family to another.
- **Stretch.**
  - Groundworks owns the gesture, the stored **anchors**, the route seen from above, one held item charged per placed block, refuse-whole, the return fit and the anchor marker.
  - The item owns only how one anchor is joined to the next.
  - Groundworks never changes height unless the player makes the height gesture. An obstacle on a leg gets a flat detour around it.

## Considered Options

- **Keep the two copies.** Rejected: they had already drifted before either was ported.
- **A dismantle library of its own.** Rejected. It would be a third repository to port at every Minecraft version and a second nested jar, for roughly 400 lines. Also, a Dismantle Plan was already the sibling of a Placement Plan (ADR 0006).
- **The Mod exposes its Dismantle as an API, and the Pack registers pipes into it.** Rejected for the reason ADR 0010 gave for the renderer: the Mod would own code that does not exist only for its blocks.
- **A family is a graph of joined blocks, with a flag for directed families**, as the Pack's ADR 0086 foresaw for belts. Rejected. A transport line's direction, rings and slopes would each become a flag in the library. With the family owning its span, the belt's line scan stays in the Mod unchanged, and pipes use the library's shortest-path helper.
- **The item decides every height of a stretch**, given a flat route. Rejected, because each item would grow its own height rules. Height is the route's, and the route is Groundworks'.
- **Extract the Stretch only when a second consumer exists.** Rejected. Pipes placed by stretch are filed with this move, and Groundworks stays 0.x until they use it.

## Consequences

- ADR 0010's library is renamed and widened. Its mod id is `groundworks`, restarting at 0.1.0, since placementpreview was never published. The Pack's generic preview and the plan type stay as ADR 0010 left them.
- ADR 0002's defaults for `dismantles_belts` move to `groundworks:dismantles`. The Mod still adds pickaxes and `#c:tools/wrench` to it, and a pack trims it through tags.
- The queue of stored dismantles (#43) is built in Groundworks, not in the Mod.
- The Mod's `Dismantling`, `DismantlePlan`, dismantle Takeover and stretch gesture go. `Dismantle.span`, the line scan and the tile shaping stay, behind the family and the item's joining of anchors. The Pack's `BeltClaim` and its no-order workaround between the two Takeovers go too.
- When the Stretch moves, it stops following the ground and stops climbing a crossing line on its own (the Pack's #422). The height gesture, how an anchor gets its height and the detour rules are decided in Groundworks before the Stretch moves. Support for floating tiles stays the Mod's (#1).
