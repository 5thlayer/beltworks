# Animating the feeder

A feeder stays as it is drawn: two fixed arms, with the taken item seen rising into the head's nozzle and nothing seen between the head and the tail. No arm swings, and no item is drawn travelling along the arms.

## Why this is out of scope

Feeders are to be replaced by Factorio-faithful inserters, with an arm that swings from pickup to drop carrying the item. The motion players ask for belongs to that block, not to the feeder.

Animating the feeder would also fight its design. Its arms are fixed reach, one to three blocks behind and in front (GLOSSARY.md, Arm), not a part that moves, and the server moves an item from head to tail in one transaction, so an item drawn travelling would reach the tail after the real one is already there. Its top tier moves an item every three and a third ticks, too fast for one swinging arm carrying one item at a time. Making any of that work means reshaping the feeder into the inserter it is giving way to.

## Prior requests

- [5thlayer/beltworks#112](https://github.com/5thlayer/beltworks/issues/112): "A Feeder shows nothing moving between its head and its tail"
