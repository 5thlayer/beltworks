# The feeder owns its reach keys, the Mod's first keys of its own

Until now the Mod had no keys: it took **Rotate** from Groundworks, so a tile turned by the same key in any pack. The **feeder** needs two settings Rotate cannot express: how far its **head** reaches and how far its **tail** reaches, each one to three blocks. Both are set on the held feeder before placing, drawn by the **Placement Preview**, and changed on a placed feeder under the crosshair. So the Mod registers two keys of its own, **Head Reach** and **Tail Reach**. Each lengthens its arm a block per press and wraps from three to one, with no reverse key. A held reach stays with the stack until its last item is placed, as a held rotation does.

## Considered Options

- **A generic "adjust" pair in Groundworks,** a block-defined setting cycled held or in place, just as Rotate is. Rejected. Groundworks is the library for mass placement and Dismantle (ADR 0011), and a feeder's reach is neither. It exists only for the Mod's block, so by ADR 0002 it is the Mod's.
- **A wrench sneak-click on the arm to cycle it.** Rejected. It sets nothing before placing, and the preview could not show the reach a placement will take.
- **A GUI.** Rejected. It is too heavy for nine combinations of two numbers.

## Consequences

- The Rotate entry in `CONTEXT.md` no longer says the Mod has no keys; it names these two.
- A pack that binds keys has two more to place.
