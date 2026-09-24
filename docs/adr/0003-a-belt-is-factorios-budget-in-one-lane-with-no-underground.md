# A belt is Factorio's throughput budget in one lane, and has no underground belt

Most of what makes Factorio's belt a puzzle comes from Factorio being flat. Underground belts solve weaving, getting two lines past each other when neither can go over. Two lanes per belt are the same compression trick one level down. Minecraft has a third axis, so both problems dissolve: a belt that must cross another goes over it, along slopes (ADR 0005), or the player routes it under by hand. What survives the move to 3D is the rest of Factorio's belt, and the Mod keeps all of it. Throughput is a known budget per tier, with Factorio's figures (15, 30, 45 and 60 items/s) as defaults a pack can retune. Items travel one per entry at an eighth of a block, and a loader puts several entries on the belt in one tick when its tier needs more than 20 items/s. A tile holds eight items, so a belt is a buffer as well as a route. Each tile costs one belt item, and a stretch the player cannot pay for is refused whole. Belts, loaders and splitters of any tiers may be joined, and each caps only its own flow, so a line runs at its slowest piece. Balancers are built from splitters, and a backed-up belt still shows which machine is slow.

Ported from the Pack's ADR-0044, the belt part of its ADR-0060 and ADR-0076's rule on mixed tiers.

## Considered Options

- **Underground belts.** Rejected. They answer a problem this medium does not have, and the pillars refuse them. Going under stays possible by placing tiles by hand, but a stretch never plans a path under.
- **Two lanes per belt, as in Factorio.** Deferred, not rejected. Belts have one lane now. The spec keeps two possible: the drawn rows stay visual, and the save and network formats stay extensible.
- **A belt that costs one item for any length**, as Upstream's did. Rejected, because cost per length is what makes a belt's route a decision.

## Consequences

- The researched stack-size bonus is still open. The stacking would be the Mod's, and the research that grants it a pack's.
- The tap, loading or unloading partway along a belt, is postponed, since the splitter covers the main bus.
- Excluding undergrounds depends on crossing over staying cheap. If slopes become costly to build, this decision reopens.
