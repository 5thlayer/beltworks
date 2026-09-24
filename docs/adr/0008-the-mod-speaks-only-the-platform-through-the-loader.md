# The Mod speaks only the platform, through the loader, and believes only a committed move

"Works with any tech mod" means the Mod works with any block that exposes NeoForge's item capability (`Capabilities.Item.BLOCK`), and it has no code written for particular mods. The loader is the only door between a belt and another mod's inventory. Tiles and splitters expose no item handler, so pipes, hoppers and other mods' inserters never reach into a line, and a line's items stay the Mod's own derived state within its tier's budget. The loader asks through the face of the target that touches it, never the null side. It moves items only through untargeted operations, so the target's own rules decide slots and faces. It reads what the target holds only to choose what to try. Each move is one real insert or extract inside one root transaction. The loader commits only when the amount actually moved is what the line can hand over, and it never relies on a simulation, `isValid`, a capacity or a slot limit. The target's handler is looked up afresh for every move, so a mod that changes a face's handler without invalidating it still works. Energy follows the same rule: the loader takes NeoForge's energy capability on any face.

The contract is checked against the **proving set**: vanilla's containers (chest, double chest, furnace, hopper, shulker box, composter) as gametests in the Mod, plus Oritech and Railcraft Reborn, each as gametests that run only when that mod is present or else as a checklist. A tech mod joins once it ships a build for the Mod's Minecraft version.

## Considered Options

- **Keep simulate-then-commit and require machines to simulate honestly.** Rejected. Two root transactions are not atomic, NeoForge calls only an attempted move authoritative, and a real move that took nothing would lose the item.
- **An adapter for mods still on `IItemHandler`.** Rejected. It is deprecated for removal and cannot be registered on 26.1's item capability.
- **Integrations for specific mods.** Rejected. They tie compatibility to whichever mods are written for. Optional integrations that add features, such as FTB Filter System and Jade, stay allowed (ADR 0002).
- **Tiles that accept inserts from other mods, as Create's belts take funnels.** Rejected. Throughput would escape the tier budget and other mods could pull items out of a line. Pipe input goes into a chest with a loader in front of it.
- **Caching the target with `BlockCapabilityCache`.** Rejected for now. Oritech's input modes and GTM's covers change a face without invalidating it. A loader moves only a few items a second, so a lookup per move is cheap.

## Consequences

- The Mod's `ItemApi`, with its `simulate` flag and one root transaction per call, cannot express this contract. It is either made transaction-scoped or replaced with direct calls to `ResourceHandler`.
- A face that refuses, is full, or belongs to a multiblock that isn't formed moves nothing. The line backs up and loses nothing. That is the contract working, not a failure.
- Unsupported: mods without `Capabilities.Item.BLOCK`, entities (chest minecarts, carts, `ENTITY_AUTOMATION`), fluids, other mods reaching into a line, and targets whose committed moves are false. The last is the other mod's bug.
- A tech mod's own sneak-click gesture wins everywhere except on a belt tile. Dismantle takes only tiles, so it claims only tiles, even while a start is stored.
- A tile placed by any means, such as a gadget, a structure or `setblock`, re-derives its shape and pitch and its neighbours' (ADR 0004), and that leaves a valid line. Items and splitter settings are not copied. The proving set includes tiles placed this way.
