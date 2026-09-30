# Splitters and loaders are tiered, and a splitter half is a block of belt

_Amended by [5thlayer/beltworks#27](https://github.com/5thlayer/beltworks/issues/27): a splitter is only ever of one tier. A half put in the place of a half of another tier, with the same facing and side, swaps its pair to that tier too, and each half keeps its items and the splitter its settings. A half of another facing or side is a break and a placement, as before._

Splitters and loaders have a tier per block, like belts, and each caps only its own flow. Each splitter half is a block of belt of the splitter's tier, as each side of Factorio's splitter is. It holds eight items and moves them at its tier's belt speed, and an item changes side at the midline. Factorio's 51-position input buffer is left out, because it aligns items across two lanes and the Mod's belts have one. A splitter draws no power. Placed across a straight tile line running its way, it takes the tile's place, refunds the tile and keeps its items. A loader's tier caps what it moves. When the server config switches loader power on, its energy per item is derived from the swing of the Factorio inserter that matches its tier (burner, inserter, fast, bulk), and tier 1 stays unpowered.

Ported from the Pack's ADR-0076, without its Pack rulings. Loader recipes from the inserter chain, their research unlocks and the exclusion of the long-handed inserter stay the Pack's.

## Considered Options

- **One splitter for every tier,** running at whatever belts are attached. Rejected. The cheapest splitter would carry the fastest belt's throughput, and a pack could not price splitter tiers.
- **A loader that takes its belt's tier.** Rejected for the same reason: a pack could not make faster loading cost anything.
- **Halves of two tiers,** each swapped on its own. Rejected. The pair would need a rule for which half's speed runs where items change side, for a splitter no one would build on purpose ([5thlayer/beltworks#27](https://github.com/5thlayer/beltworks/issues/27)).

## Consequences

- The joule figures assume a rate in joules per FE, which the server config sets (100 by default, per ADR 0002).
