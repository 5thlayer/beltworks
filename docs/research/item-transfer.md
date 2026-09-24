# How items move between mods on NeoForge 26.1

Research for [#14](https://github.com/adamico/SimpleBelts/issues/14). Facts only; the contract decision belongs to #15.

## Sources and how they were read

| Short name | Path | What it is |
|---|---|---|
| NF | `~/minecraft_mods/neoforge-26.1.2.77-src/net/neoforged/neoforge/` | NeoForge 26.1.2.77 source, the version in the Mod's `gradle.properties:16` |
| MC | `~/minecraft_mods/mc-26.1.2.109-src/net/minecraft/` | Minecraft 26.1.2 source (the `mc-26.1.2-src` checkout has no `world/item` package) |
| ORI | `~/minecraft_mods/oritech-2.0.0-exp6/rearth/oritech/` | Oritech 2.0.0-exp6 for NeoForge 26.1. Class files only. It needs NeoForge `[26.1.2.77,)` (`oritech-2.0.0-exp6-jar/META-INF/neoforge.mods.toml:60-68`). It was decompiled with Vineflower 1.11.2, so the line numbers in ORI citations refer to that decompilation and are approximate. |
| ORI-old | `~/minecraft_mods/oritech-src/` | Oritech 1.2.12 source for MC 1.21.1 / NeoForge 21.1 (`gradle.properties`). Used only where the decompiler failed. |
| GTM | `~/minecraft_mods/gregtech-modern-src/src/main/java/com/gregtechceu/gtceu/` | GregTech Modern 8.0.0, branch `26.1`. Despite the branch name, it still targets MC 1.21.1 / NeoForge 21.1.215 (`gradle/libs.versions.toml:2-3`). |
| MI | `~/minecraft_mods/modern-industrialization-src/src/main/java/aztech/modern_industrialization/` | Modern Industrialization, branch `1.21.x`, targeting MC 1.21.1 / NeoForge 21.1.219 (`gradle.properties`) |
| Mod | this repo, branch `planetaryfactory` @ `2fa8756` | `common/src/main/java/rearth/belts/api/item/`, `neoforge/src/main/java/rearth/belts/neoforge/NeoforgeItemApiImpl.java` |

Create is not checked out in `~/minecraft_mods`, so this report does not cover it.

**Only Oritech has a 26.1 build on hand.** GTM and MI are 1.21.1 code. They are written against the legacy `IItemHandler` API, which cannot be registered on the 26.1 item capability (see §1.4). Their 26.1 ports will therefore have a different API surface. The per-slot and per-face rules below are the rules in their current code, not in a 26.1 build.

## 1. The NeoForge 26.1 item-transfer API

### 1.1 Capability plus transfer API: one system

NeoForge 26.1 moves items through the capability system, and the payload of that capability is the new transfer API. The item capabilities are defined in `NF/capabilities/Capabilities.java:37-49`:

- `Capabilities.Item.BLOCK`: `BlockCapability<ResourceHandler<ItemResource>, @Nullable Direction>`, created with `createSided`, so its context is a nullable face (`BlockCapability.java:116-121`).
- `Capabilities.Item.ENTITY` (void context), `ENTITY_AUTOMATION` (sided, "droppers, hoppers, and similar modded devices"), and `ITEM` (context `ItemAccess`).

There is no `Capabilities.ItemHandler` class any more. The Javadoc of `BlockCapability.java:35,48-51`, `EntityCapability.java:28,42` and `ItemCapability.java:27,40` still shows `Capabilities.ItemHandler.BLOCK` and `IItemHandler`. That Javadoc is stale, and the example does not compile against 26.1.

Lookups go through `Level#getCapability(cap, pos, state, be, side)`. When `state` or `be` is null, NeoForge fetches it, then asks each provider registered for the block and returns the first non-null result (`BlockCapability.java:197-219`). For repeated lookups at one position there is `BlockCapabilityCache` (`BlockCapabilityCache.java:16-20,31,59,137`), which is cleared only when the owner calls `Level#invalidateCapabilities(pos)`. The providers are responsible for that call (`BlockCapability.java:55-56,72-80`). `Capabilities.Item.BLOCK` is marked proxyable (`CapabilityHooks.java:57-61`).

### 1.2 `ResourceHandler<ItemResource>`

`NF/transfer/ResourceHandler.java`:

- **Indexed view.** `size()`, `getResource(i)`, and `getAmountAsLong`/`getAmountAsInt(i)` (`:40,49,66,84`). The size may change at runtime, and handlers "should be lenient" with stale indices (`:21-24,36-38`).
- **Capacity is a hint.** `getCapacityAsLong/AsInt(index, resource)` "may overestimate the actual allowed amount". The Javadoc says "the only way to know if a handler will accept a resource, is to try to insert it" (`:88-132`). An empty `resource` asks for "the general capacity at the index" (`:103`).
- **`isValid(index, resource)` is also only a hint** (`:134-144`).
- **Insert and extract.** Each exists as an indexed overload (`insert(int, T, int, TransactionContext)` `:161`, `extract(int, …)` `:208`) and an untargeted overload (`:181-191`, `:228-238`). The untargeted overload "lets the handler decide how to distribute the resource" and is "preferred". Its default implementation walks indices 0..size-1 in order and takes no account of stacking. Both return the amount moved, from 0 to `amount`. An empty resource or a negative amount throws `IllegalArgumentException` (`:156`, `TransferPreconditions`).
- Amounts are `int` in insert/extract but may be `long` in the query methods. `ItemResource` has no count: it is an immutable pair of item and data components (`NF/transfer/item/ItemResource.java:36-39`), and `ItemResource.of(stack)` drops the count (`:73-78`). Matching compares item plus components (`:198-203`).

### 1.3 Transactions: simulate and commit semantics

There is no `simulate` flag. Every mutation takes a `TransactionContext`. `NF/transfer/transaction/Transaction.java`:

- `Transaction.openRoot()` opens a root transaction. It **throws `IllegalStateException` if a transaction is already open (or closing) on the thread** (`:66-76`). `Transaction.open(parent)` nests a transaction, and a null parent means root (`:78-97`).
- `commit()` keeps the changes; for a nested transaction they last only if the parents commit too. `close()`, or leaving a try-with-resources block without committing, rolls back (`:17-31,137-159`).
- Transactions are bound to one thread (`:56-58`). `getCurrentOpenedTransaction()` is `@Deprecated` (`:112-135`).
- A **simulation** is therefore "open, operate, don't commit". The handler really mutates its state and then reverts it through `SnapshotJournal.revertToSnapshot`. Side effects such as `setChanged()` belong in `onRootCommit`, which runs only after a successful root commit (`SnapshotJournal.java:11-36,69-100`). Whether a handler is honest under simulation depends on whether it journals every piece of state it touches.
- The base implementation `StacksResourceHandler` journals each index (`NF/transfer/StacksResourceHandler.java:237-299`). It fires `onContentsChanged` once per changed index at root commit (`:194-204,295-298`).

Helpers in `NF/transfer/ResourceHandlerUtil.java` take a nullable `TransactionContext`. With `null` they open and commit their own root:

- `insertStacking` fills non-empty indices first, then empty ones (`:133-180`).
- `extractFirst` (`:178-190`).
- `move` and `moveStacking` (`:240-265`) throw if they are given no transaction while one is already open (`:250`).

### 1.4 What is deprecated

- `net.neoforged.neoforge.items.IItemHandler` is `@Deprecated(since = "1.21.9", forRemoval = true)` (`NF/items/IItemHandler.java:17-22`), as are every one of its methods and every class in `items/` and `items/wrapper/`: `ItemStackHandler`, `CombinedInvWrapper`, `SidedInvWrapper`, `InvWrapper`, `RangedWrapper` and the others (one `@Deprecated` each).
- The only bridge goes **from** `ResourceHandler` **to** `IItemHandler`: `IItemHandler.of(handler)` (`IItemHandler.java:24-35`, implemented by `ItemResourceHandlerAdapter.java:14`). It opens its own root transaction for each call, "so this wrapper cannot be used from a transactional context" (`IItemHandler.java:28-29`). The adapter's `getSlotLimit` asks for the capacity with `ItemResource.EMPTY` (`ItemResourceHandlerAdapter.java:58-60`).
- NeoForge has no adapter in the other direction, from `IItemHandler` to `ResourceHandler`. Every class in `transfer/` that implements `ResourceHandler<ItemResource>` wraps vanilla containers, stacks or entities. A mod still on `IItemHandler` cannot register on `Capabilities.Item.BLOCK` without writing its own adapter.
- `Transaction.getCurrentOpenedTransaction()` is deprecated, and five `FluidUtil` methods are `forRemoval` since 26.1.2 (`transfer/fluid/FluidUtil.java:103,142,233,349,414`).

### 1.5 Stock implementations and vanilla blocks

- `ItemStacksResourceHandler` defaults its capacity to `min(resource.getMaxStackSize(), 99)`, and to **99 for an empty resource** (`NF/transfer/item/ItemStacksResourceHandler.java:50-51`; `Item.ABSOLUTE_MAX_STACK_SIZE = 99` at `MC/world/item/Item.java:113`).
- Vanilla registrations are in `NF/capabilities/CapabilityHooks.java:80-121`:
  - The composter has its own wrapper.
  - Chests, including trapped and copper chests, go through `VanillaContainerWrapper`, combined across both halves of a double chest.
  - **Sided**, through `WorldlyContainerWrapper`: furnace, blast furnace, smoker, brewing stand and shulker box.
  - **Unsided**: barrel, chiseled bookshelf, dispenser, dropper, hopper, jukebox, crafter, decorated pot and shelf.
- `WorldlyContainerWrapper` (`NF/transfer/item/WorldlyContainerWrapper.java:29-89`):
  - Its index space is `getSlotsForFace(side)`, so a furnace's top face has one slot (`MC/world/level/block/entity/AbstractFurnaceBlockEntity.java:47-49,268-274`).
  - With a `null` side it exposes every slot, still checks `canPlaceItemThroughFace(…, null)` on insert, and skips `canTakeItemThroughFace` on extract (`:39-41,77,86`).
- `VanillaContainerWrapper` (`:160-205`):
  - It uses `canPlaceItem` as `isValid`.
  - It caps the furnace fuel slot to 1 bucket and the brewing bottle slots to 1.
  - It schedules `setChanged()` for root commit (`:81-101,209-226`).
- The vanilla hopper hooks move one item per root transaction: extraction in `NF/transfer/item/VanillaInventoryCodeHooks.java:32-61`, insertion in `:69-94`.

## 2. What each tech mod exposes

### 2.1 Oritech 2.0.0-exp6 (26.1)

Oritech 2.0 dropped its own `ItemApi.InventoryStorage` (the Mod's API is a fork of it; compare `ORI-old/common/src/main/java/rearth/oritech/api/item/ItemApi.java`) and uses NeoForge's API directly.

- **Registration.** Every block entity type annotated `@AssignSidedInventory` gets `Capabilities.Item.BLOCK → ((ItemProvider) be).getItemLookup(side)` (`ORI/init/BlockEntitiesContent.class`, `registerBlockEntityCapabilities`, decompiled `:446-459`). `ItemProvider` is a single method, `ResourceHandler<ItemResource> getItemLookup(@Nullable Direction)` (`ORI/api/transfer/item/ItemProvider.class`).
- **Storage.** `SimpleInventoryStorage extends ItemStacksResourceHandler`, so it is transactional through the NeoForge journals and uses the default capacity rules (`ORI/api/transfer/item/SimpleInventoryStorage.class`).
- **Per-slot rules.** Machines use `InOutInventoryStorage`, whose `getExternalAccess()` is a `DelegatingResourceHandler` over the whole inventory (`ORI/api/transfer/item/InOutInventoryStorage.class`, decompiled `:16-55`):
  - Indexed `insert` returns 0 unless the index is an input slot, and indexed `extract` returns 0 unless it is an output slot.
  - Untargeted `insert` and `extract` walk only the input or output range.
  - `size()` is the **full** inventory size.
  - `isValid` and `getCapacity` are **not** overridden, so they report output slots as valid and with full capacity for insertion.
- **Sidedness.** `MachineBlockEntity.getItemLookup(side)` (`ORI/block/base/entity/MachineBlockEntity.class`, decompiled `:591-595`) depends on a per-machine `InventoryInputMode`, which is one of `FILL_LEFT_TO_RIGHT` (the default, `:86`), `FILL_EVENLY` or `SIDED` (`ORI/util/InventoryInputMode.class`).
  - **Not `SIDED`:** the same external-access handler on every face and for `null`.
  - **`SIDED`:** a handler per face, cached in `sidedInventories` (`getDirectedStorage`, `:597-640`; the decompiler lost the horizontal branch, so the logic is taken from the bytecode of `MachineBlockEntity$3` and from `ORI-old/…/MachineBlockEntity.java:537-598`). If the machine has at most one input slot, or the side is `null`, the face gets plain external access. `UP` is insert-only (extract returns 0) and `DOWN` is extract-only (insert returns 0). Each horizontal face N/E/S/W accepts only input slot `inputStart + ordinal % inputCount`, and its untargeted insert is redirected there.
  - `FILL_EVENLY` overrides insert so that it spreads `amount / inputCount` (at least 1) per input slot, starting with the emptiest one. An indexed insert into any input slot is redirected to this spreading insert (`MachineInventoryStorage`, `:671-715`).
- **Multiblocks.** Core blocks return a `DelegatingInventoryStorage` over the controller. While the multiblock is not enabled, it reports size 0 and moves nothing (`ORI/block/entity/MachineCoreEntity.class`, `:136-141,154-156`; `DelegatingInventoryStorage.class`, `:14-57`).
- **Invalidation.** `MachineBlockEntity` does not call `invalidateCapabilities` (it is called only from `CentrifugeBlockEntity`, `TaintedRefineryBlockEntity` and `AddonBlockEntity`). A handler cached through `BlockCapabilityCache` therefore does not follow a change of input mode.
- **How Oritech itself moves items.** Its item pipe opens a root transaction, simulates the extract in a nested transaction, then inserts into the target and extracts exactly the inserted amount within the root before committing (`ORI/block/entity/pipes/ItemPipeInterfaceEntity.class`, `:111-149`). It caches targets with `BlockCapabilityCache` (`:46,236`).

### 2.2 GregTech Modern (1.21.1 code on branch `26.1`)

- **Registration.** `Capabilities.ItemHandler.BLOCK → machine.getItemHandlerCap(side, true)`, a legacy `IItemHandlerModifiable` (`GTM/api/block/MetaMachineBlock.java:470-475`).
- **Composition.** `getItemHandlerCap` (`GTM/api/machine/MetaMachine.java:802-824`) collects every trait that is an `IItemHandlerModifiable` and has `hasCapability(side)`; by default that is every side (`api/machine/trait/MachineTrait.java:41,67-68`). It wraps them in a **new** `IOFilteredInvWrapper extends CombinedInvWrapper` on each call:
  - IO is `BOTH`, or `OUT` on the auto-output face unless input from the output face is allowed (`:811-816`).
  - The in and out filters come from an `ItemFilterCover` on that face (`:765-781`).
  - For a non-null side, the face's cover can wrap or replace the result (`:820-823`).
  - Wrapper behavior (`GTM/api/misc/IOFilteredInvWrapper.java:32-52`): insert returns the whole stack when IO has no `IN` or the filter rejects it. Extract first does a simulated extract, applies the out-filter, then extracts for real.
- **Per-slot rules.** `NotifiableItemStackHandler` gates on `capabilityIO`: insert needs `IN` and extract needs `OUT` (`api/machine/trait/NotifiableItemStackHandler.java:288-312`, `ICapabilityTrait.java:9-15`). Machine slots are set up as follows:
  - `importItems`: handler `IN`, capability `BOTH`. **Automation can extract from input slots** (`api/machine/WorkableTieredMachine.java:72`).
  - `exportItems`: `OUT`/`OUT`, which rejects insertion (`:73`).
  - Circuit slot: capability `NONE` (`api/machine/SimpleTieredMachine.java:85`).
  - `isItemValid` checks only the slot filter, not IO (`api/transfer/item/CustomItemStackHandler.java:47-49`).
- **Covers.**
  - `ShutterCover` returns `null`, meaning no capability, while it is working (`common/cover/ShutterCover.java:57-58`).
  - `ConveyorCover` wraps the handler. When the conveyor exports, outside insertion through that face is blocked while `manualIOMode` is `DISABLED` (the default), and the same applies to extraction when it imports (`common/cover/ConveyorCover.java:79,490-541`).
- **Slot limits above 64.** `QuantumChestMachine` exposes one slot with `getSlotLimit = maxAmount` saturated to int, and `getStackInSlot` returns a stack whose count is the stored amount, which can be far above the item's max stack size. It exposes nothing on its front face (`common/machine/storage/QuantumChestMachine.java:131-136,336-371`).
- **Invalidation.** `invalidateCapabilities` does not occur anywhere in GTM's `src/main/java`, even though covers change what a face returns.
- **Simulation honesty.** It follows the `IItemHandler` `simulate` flag per call, with no transactions.

### 2.3 Modern Industrialization (1.21.1 code)

- **Registration.** `Capabilities.ItemHandler.BLOCK → getInventory().itemStorage.itemHandler`. **The side is ignored:** every face and `null` get the same handler (`MI/machines/MachineBlockEntity.java:258-263`). Barrels register a one-slot `SlotItemHandler` (`MI/materials/part/BarrelPart.java:99`, `MI/MICapabilities.java:54`).
- **Per-slot rules.** Each `ConfigurableItemStack` has `pipesInsert` and `pipesExtract` flags, which the player can reconfigure. Stock slots:
  - Input: insert only.
  - Output: extract only.
  - IO slots and machine-locked input slots are also defined.

  (`MI/inventory/ConfigurableItemStack.java:70-101`, `AbstractConfigurableStack.java:56-57`.) `insertItem` returns the stack untouched when `!pipesInsert` (`MI/inventory/MIItemStorage.java:55-95`). Extract goes through `AbstractConfigurableStack.extract`, which checks `pipesExtract` (`:291-297`). `isItemValid` checks only the item lock, not `pipesInsert` (`MIItemStorage.java:121-124`).
- **Slot limits.** Capacity is `min(adjustedCapacity, maxStackSize)`, where `adjustedCapacity` is at most 64 and can be lowered by the player (`ConfigurableItemStack.java:52,147-161,184`).
- **Simulation.** Insert computes the result and mutates only when `!simulate`. Extract runs inside MI's own Fabric-transfer port and uses `Transaction.hackyOpen()`, which nests under whatever MI transaction is open on the thread (`MIItemStorage.java:97-114`; `MI/thirdparty/fabrictransfer/api/transaction/Transaction.java:126-128`). MI therefore carries its own transaction system, separate from NeoForge's.

## 3. Quirks that break naive insertion and extraction

Each item names the source that shows it:

1. **"Any slot" is not "every slot".** A handler's `size()` can include slots that refuse outside access: Oritech external access spans the whole inventory (§2.1), and so do GTM output and circuit slots and MI output slots. Walking `0..size()` and calling indexed insert or extract gets 0 back for many indices.
2. **`isValid` and `getCapacity` are hints and can be wrong.** NeoForge says so itself (`ResourceHandler.java:97-100,138-139`). Oritech does not override them for input/output gating, and in GTM and MI `isItemValid` ignores IO. Only an attempted insert inside a transaction is authoritative.
3. **Capacity of an empty slot.** `getCapacityAsInt(i, EMPTY)` is an estimate: 99 on `ItemStacksResourceHandler`, `getMaxStackSize()` on vanilla containers, up to `Integer.MAX_VALUE` on a GTM quantum chest. The real limit depends on the resource: 1 for a bucket in a furnace fuel slot, 1 for brewing bottles (`VanillaContainerWrapper.java:176-188`).
4. **Counts above the max stack size.** Big storages (GTM quantum chest, MI barrels) report amounts above `maxStackSize`. Turning those into one `ItemStack` gives an oversized stack.
5. **Face semantics differ per mod.**
   - Vanilla sided containers remap their indices per face.
   - Oritech gives each face one direction or one slot only in `SIDED` mode.
   - GTM blocks insertion on the auto-output face and lets covers veto a face.
   - MI ignores the face entirely.
   - A `null` side means "all slots" on vanilla worldly containers and on Oritech. On GTM it means no cover wrapping.
6. **Extraction from input slots.** GTM input slots allow automated extraction (capability `BOTH`). An extractor on a non-output face of a GTM machine can pull ingredients back out.
7. **The handler can change without invalidation.** Oritech's input mode, and GTM's covers and auto-output face, change what a face returns without `invalidateCapabilities`. A `BlockCapabilityCache` holds on to the stale result.
8. **Root-transaction re-entrancy.** `Transaction.openRoot()` throws if any transaction is open on the thread. Code that opens a root for each call cannot run inside another transaction or a `ResourceHandlerUtil.move(..., null)` call. The same limit applies to `IItemHandler.of` (§1.3, §1.4).
9. **Simulate-then-commit across two transactions is not atomic.** Between two root transactions the target can change, and some handlers insert differently depending on history (Oritech `FILL_EVENLY` starts from the emptiest slot). Oritech's own pipe does simulate, insert and extract within a single root (§2.1).
10. **Untargeted insert order.** The default untargeted insert fills indices in order and does not prefer existing stacks; `insertStacking` does (`ResourceHandler.java:181-191` vs `ResourceHandlerUtil.java:134-180`). Oritech overrides the order (`FILL_EVENLY`, `SIDED`).
11. **Components matter.** Resources match on item plus data components (`ItemResource.java:198-203`, `StacksResourceHandler.java:166-170`). A filter that compares only `getItem()` finds stacks whose exact-resource extraction then succeeds, so this is not a failure, but an item-only comparison is looser than the handler's.
12. **The GTM and MI 26.1 ports are unknown.** Neither can register its current `IItemHandler` on 26.1's `Capabilities.Item.BLOCK` (§1.4). Their 26.1 behavior is not established by any source on hand.

## 4. The Mod's current item API against all this

### 4.1 Shape

`common/src/main/java/rearth/belts/api/item/ItemApi.java` defines `InventoryStorage`:

| Group | Methods |
|---|---|
| Stack-based insert | `insert(stack, simulate)`, `insertToSlot(stack, slot, simulate)` |
| Stack-based extract | `extract(stack, simulate)`, `extractFromSlot(stack, slot, simulate)`, each returning the count moved |
| Direct access | `setStackInSlot`, `getStackInSlot` |
| Size and limits | `getSlotCount`, `getSlotLimit` |
| Capability flags | `supportsInsertion()` and `supportsExtraction()`, both defaulting to `true` |

`BlockItemApi.find(level, pos, state, be, direction)` returns a nullable `InventoryStorage` (`BlockItemApi.java:10-13`). The API is the pre-2.0 Oritech API (compare `ORI-old/common/src/main/java/rearth/oritech/api/item/ItemApi.java`).

The only implementation is NeoForge's, since `enabled_platforms = neoforge` (`gradle.properties:9`). `NeoforgeItemApiImpl` (`neoforge/src/main/java/rearth/belts/neoforge/NeoforgeItemApiImpl.java`) works as follows:

- `find` calls `level.getCapability(Capabilities.Item.BLOCK, pos, state, entity, direction)` and wraps the result (`:21-30`).
- **`simulate` maps to "open a root transaction, then commit or not".** The four insert and extract methods each open `Transaction.openRoot()`, call the untargeted or indexed NeoForge method with `ItemResource.of(stack)` and `stack.getCount()`, and commit when `!simulate` (`:40-78`).
- `getStackInSlot` returns `getResource(slot).toStack(getAmountAsInt(slot))` (`:97-99`).
- `getSlotLimit` returns `getCapacityAsInt(slot, getResource(slot))`: the current resource, or `EMPTY` for an empty slot (`:106-109`).
- `setStackInSlot` extracts the current contents and inserts the new stack in one root transaction. **It commits whatever the two calls return** and logs only exceptions (`:80-94`).
- `supportsInsertion` and `supportsExtraction` are not overridden, so they are always `true`.

### 4.2 Callers

Only the loader (`ChuteBlockEntity`) uses the API:

- `acceptFromLine` (`common/src/main/java/rearth/belts/blocks/ChuteBlockEntity.java:273-284`) looks up the inventory behind the loader with `state` and `be` set to null and `side = getOwnFacing()`. That side is the face of the target that touches the loader. It calls `insert(item, true)`, returns `false` unless the full count fits, then calls `insert(item, false)` and **ignores the second result**.
- `extractOne` (`:294-309`) runs the same lookup and walks `0..getSlotCount()`. For each non-empty slot that passes the filter, it calls the **untargeted** `extract(stack.copyWithCount(1), false)`, so the item can come from any slot holding that resource. It returns the first success.
- The belt asks the loader only when the line has room (`common/src/main/java/rearth/belts/model/BeltContents.java:95-101`).
- No `BlockCapabilityCache` is used. Each call does a fresh `getCapability`.

### 4.3 Gaps between the Mod's API and the platform

Facts, each tied to §1–§3:

- **No transaction parameter.** The API's `simulate` boolean is realised as a separate root transaction per call. The Mod cannot compose simulate and commit atomically, and cannot be called while a transaction is open: `openRoot` throws (quirk 8). In `acceptFromLine`, the simulate and the commit are two root transactions, and the commit's result is discarded (quirk 9).
- **`setStackInSlot` is not a "set".** It goes through `extract` and `insert`, which obey the handler's rules. On Oritech external access, for example, it can remove the old contents from an output slot and then insert nothing, and the transaction still commits. The Mod has no callers of this method.
- **`supportsInsertion` and `supportsExtraction` carry no information.** NeoForge has no such flag, and the wrapper always says `true`. The per-face insert-only and extract-only behavior of Oritech, GTM and MI shows up only as a return value of 0.
- **`getSlotLimit` and `getStackInSlot` pass on the platform hints unchanged.** For an empty slot, `getSlotLimit` returns the estimate for `EMPTY` (99 on the stock handler), not a per-item limit (quirk 3). `getStackInSlot` can build stacks larger than the item's max stack size from big storages (quirk 4).
- **`int` only.** The API has no `long` amounts. NeoForge queries are `long`, saturated to `int` by `getAmountAsInt` and `getCapacityAsInt`.
- **No way to walk only the accessible slots.** `getSlotCount` is `size()`, which on Oritech includes slots that refuse outside access (quirk 1). `extractOne` scans every slot and relies on untargeted extract.
- **Filter granularity.** `stackMatchesFilter` compares `getItem()` only, or delegates to FTB Filter System (`ChuteBlockEntity.java:314-324`). The extract call itself matches the exact item and components of the stack it found (quirk 11).
- **No capability caching or invalidation handling.** Lookups are uncached, so stale caches are not an issue (quirk 7). The cost is one lookup per item moved.
- **Only NeoForge is implemented.** The `common` API is platform-neutral in shape, but `fabric/` no longer exists on this branch.
