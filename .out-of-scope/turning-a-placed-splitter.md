# Turning a placed splitter

A placed splitter is never turned. Rotate in Place on either half is refused with its reason, and nothing changes.

## Why this is out of scope

Factorio lets a player turn a placed splitter a half turn, and in practice no one does: a splitter is broken and placed again the other way. Turning a splitter whole would mean moving two blocks together, picking a pivot, refitting both halves' items and settings across the new facing, and refusing when either new spot is blocked. Groundworks' `TurnsInPlace` turns only the aimed block, so all of that would need a new contract as well, for a move nobody makes.

The refusal also keeps the splitter on the Pack's placed-block Rotate deny list, which is where it belongs.

## Prior requests

- [5thlayer/beltworks#28](https://github.com/5thlayer/beltworks/issues/28): "A splitter turns whole when rotated"
