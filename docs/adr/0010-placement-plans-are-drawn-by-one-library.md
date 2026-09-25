# Placement Plans are drawn by one library, placementpreview

The Mod and the Pack both need a **Placement Preview**, and in the Pack a player sees both at once: belts from the Mod, and poles, rigs and pumps from the Pack. Two renderers would drift in tint, culling and replace colour right where the player looks. Rendering is also the code each Minecraft port breaks first, and ADR 0009 ports the Mod and the Pack together, so two copies would be fixed twice at every port. So the drawing lives in one library mod, **placementpreview**: its own repository, MIT, NeoForge only, on the Mod's Minecraft version. It owns the plan type (the positions a placement would fill, the blockstate at each, the positions it replaces, and a refusal or none), the interface an item implements to plan its own placement, the plan for a block that places as vanilla does, and the renderer. The renderer has hooks for a consumer's overlays, such as a splitter's belt surface, a stretch's stored start or a pole's area, and for a preview that takes the frame instead of a plan, such as a dismantle's outline. Refusals are open, so each consumer names its own. The library draws only the items a consumer opts in and keeps no list of mods. The Mod bundles it with jar-in-jar, so the Mod still runs with nothing else installed (ADR 0002). The Pack depends on it directly.

This replaces ADR 0006's "the Mod draws its own plans with a renderer of its own". The rest of ADR 0006 stands: one rule, the click executes the plan the preview draws, and the server's answer stays authoritative.

## Considered Options

- **Each of the Mod and the Pack draws its own, as ADR 0006 said.** Rejected. Both copies draw on one screen and break at the same port, so a copy saves nothing and makes the two drift apart.
- **The Mod exposes its renderer as an API, and the Pack uses it.** Rejected. The Mod would own code that doesn't exist only for its blocks (ADR 0002), and any later mod would depend on a belt mod for its previews.
- **Wait for a third consumer before extracting.** Rejected. The Pack's preview already serves many kinds of block, so the plan type and the drawing are proven. What is new is the packaging, not the design.
- **Players install the library themselves.** Rejected. A belt mod that asks for a second jar before it runs breaks ADR 0002's promise.

## Consequences

- The Pack's generic preview (its plan type, the item interface, the vanilla plan, the face hull and the drawing) is extracted into the library, not copied into the Mod. The Pack keeps its own overlays (pole areas, rig mining areas, wires) and its Dismantle Families' preview.
- The Mod's plans stay its own: `StretchPlan`, `SplitterItem.Plan` and `DismantlePlan`. The tile and splitter items implement the library's interface, and the Mod contributes its overlays (the stretch's start, the splitter's belt surface, the dismantle outline).
- ADR 0006's consequence that a pack's generic preview must skip the Mod's items goes away, because one renderer draws each item once.
- The library moves to each Minecraft version together with the Mod and the Pack (ADR 0009), and is released before them.
