# A feeder's arm carries its items at its inserter's swing

A **feeder** used to move an item from **head** to **tail** in one tick, at a tenth of its tier's loader, drawn with two fixed arms. Factorio players expect an inserter's arm to swing, and an arm drawn swinging over an item that has already landed would show the lie. So a feeder has one **arm**, articulated in three segments like a Factorio inserter's, that really carries what it moves (5thlayer/beltworks#115). Its **hand** grabs at the head only what the tail accepts, as many items as its tier's inserter's hand holds, and holds them until the swing reaches the tail. The held items are saved with the block, dropped when it breaks, and kept by a tier swap up to the new hand's size. If the tail changes mid-swing, the arm waits over it, holding them.

A feeder's rate is its swing, not a share of its loader. Each half-swing follows Factorio's rule at Factorio's real seconds: it turns, half a turn or a quarter for a turned tail, and stretches between the head's and tail's reach together, lasting as long as the slower in whole Factorio ticks, three to a Minecraft tick, with what a half-swing overruns its last tick by carried into the next. The inserter's item spike is a burst of power at each end, not time, so it adds none: Factorio's chest-to-chest rates are its half-turns alone. Tier 1 swings at the burner inserter's figures, which it alone reads.

## Considered Options

- **A visual swing over the one-tick move.** Rejected. Either the item lands before the hand gets there, or a fast tier needs several arms swinging at once to keep up.
- **Keep a tenth of the loader and speed the swing to match.** Rejected. Feeders would swing faster than Factorio's inserters, which is the thing the players compare.
- **Chase items along a belt, as Factorio's inserter does.** Rejected. The hand takes what is on the head's tile when it gets there; predicting where an item will be on a line gains little.

## Consequences

- Feeders move fewer items than before: roughly 0.6, 0.83, 2.3 and 4.6 a second by tier, chest to chest, down from 1.5, 3, 4.5 and 6.
- A broken feeder drops what its hand held beside itself, and a save holds it.
