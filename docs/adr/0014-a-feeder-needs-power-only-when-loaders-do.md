# A feeder needs power only when loaders do

The **feeder** was first priced apart from the loader power switch: it paid FE for every item at every tier, even with `loadersNeedPower` off, so that its flexibility had a cost (5thlayer/beltworks#66). But vanilla has no FE source, and the Mod ships none (ADR 0002). With only the Mod installed, a feeder could never move anything, in survival or in creative, which broke ADR 0002's promise that the Mod is playable on its own. That promise is also why loaders are free by default.

So a feeder follows the same switch as loaders. With `loadersNeedPower` off, the default, it moves items for free at every tier. With it on, it pays its own per-tier figure, `feederJoulesPerItem`, for each item at every tier, tier 1 included. Only then does it expose an energy face, as a powered loader does. A pack that turns power on, like the Pack, sees no change.

## Considered Options

- **A creative-only energy source block.** Rejected. ADR 0002 has the Mod ship no energy source, and a world with only the Mod installed would still be unplayable in survival.
- **Free feeders in creative mode.** Rejected. A feeder ticks with no player nearby, so "creative" has no clear meaning for it.

## Consequences

- The Feeder entry in `GLOSSARY.md` says it draws power only when loaders need it.
- A feeder's cost now sits with the pack that turns power on, not with the Mod.
