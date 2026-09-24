# Belts are tiles, merged at runtime into transport lines

A belt is made of **tiles**, as in Factorio. A tile is one block of belt, placed, broken and paid for on its own, facing the way items travel. It is straight, or a one-block corner when it is fed from exactly one side and from nothing behind, and its shape is derived from its neighbours, never chosen. Contiguous tiles, each feeding the next, are merged at runtime into one **transport line** that ticks once for the whole run. The line is derived state: each tile saves its own share of the items, and the line is rebuilt when a tile is placed, broken or turned, or when its chunk loads or unloads, so it never spans an unloaded chunk. A belt ends where its tiles do: at a loader, which meets a tile face to face and carries no items itself, at a splitter half, or at nothing, where the line backs up. The belt places no loaders. A stretch is laid only on blocks with a sturdy top face and is refused whole otherwise, while a single tile is placed as vanilla places a block. A loader's filter is set whether or not a line reaches it, so it can be set before the belt is built.

Ported from the Pack's ADR-0084, which superseded its ADR-0078.

## Considered Options

- **A spline between grid-aligned supports, bounded by Satisfactory's numbers** (35° slope, climb or turn but not both, 32-block reach). This was the Pack's earlier belt. Rejected: it read as organic rather than as a factory, and the player had to learn a geometry Factorio never asks for. The pillars refuse spline belts and free curves.
- **Drawing modes over the spline.** Rejected, because they would only put a generator over the same model.
- **Supports as a belt end.** Rejected. A line's last tile is already a dead end, and a tile on the ground needs no post. Supports return in a different role: what a raised line stands on.

## Consequences

- The spline belt item, the support as belt end, the span bounds, the loader choice at open ends, the splitter's belt cut and the far-end hand hold are gone from the Mod.
- A tile whose ground is removed after it is placed stays where it is. How far a line may float is decided with raised lines.
