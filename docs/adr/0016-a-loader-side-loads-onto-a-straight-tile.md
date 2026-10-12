# A loader side-loads onto a straight tile, and loading partway along a belt is that

ADR 0003 postponed the tap, loading or unloading partway along a belt. A loader whose mouth faces the side of a straight, level tile now side-loads onto that tile's line, as a belt there would ([5thlayer/beltworks#24](https://github.com/5thlayer/beltworks/issues/24)). The line it feeds goes first, the loader's items fill its gaps, and the loader moves no more than its own tier allows (ADR 0007). That is the loading half of the tap, built from a block the player already has, by the side-load rule they already know. Unloading partway along a belt stays postponed: a loader never takes from a line's side.

## Considered Options

- **Keep the tap postponed and load only at a line's first tile.** Rejected. A loader set beside a mid-line tile looked like it should feed it and did nothing, and the splitter is a heavy answer to "put this chest's items on the passing belt".
- **A tap block of its own.** Rejected. It would be a fifth belt piece doing what a loader and a side-load already do between them.
- **Unloading partway too, a loader beside a tile taking from it.** Postponed, not rejected. It needs a rule for which items leave and when, and the feeder already takes from anywhere along a line one item at a time.

## Consequences

- A loader beside a line's head with nothing behind it still turns the head into a corner (the corner rule), and loads it through its entry rather than its side.
- The glossary's **Side-load** and **Transport line** entries name the loader.
