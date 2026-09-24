# Direction already decided in the Pack

**Question** (#12): which belt direction decisions have the Pack's ADRs, its spec #341,
`docs/factorio-mechanics.md` and `docs/gdd.md` already made? For each, what was borrowed from
Factorio and what from Satisfactory? The result is a list of facts the pillars grilling (#13) can
start from, and an input to the Mod/Pack boundary (#9).

**Retrieved**: 2026-09-24, from the Pack's checkout at `867a8b7` (`fix(#422)`), plus its working
tree. `CONTEXT.md` had uncommitted edits at that time: the **Pitch**, **Slope**, **Foot**,
**Middle**, **Top** and **Wedge** entries and the climbing wording of **Belt** and **Stretch** exist
only in the working tree. Line numbers for `CONTEXT.md` are the working tree's.

**Sources.** All paths are the Pack's (`adamico/planetary-factory`).

- ADRs in `docs/adr/`: 0044, 0060, 0069, 0076, 0078, 0082, 0083, 0084 and 0085 decide belt
  direction. 0039 (Engineer's Pick), 0042 (provisional ADRs), 0043 (drills eject) and 0067
  (borrowed blocks) touch it.
- `CONTEXT.md`, section *Moving things* and the placement entries.
- `docs/factorio-mechanics.md`, the ledger. Rows *Transport belts* (l.553), *Inserters* (l.632),
  *Building by hand* (l.669), *Character reach* (l.1247) and the mining sub-rules (l.210-221).
- `docs/gdd.md`. Its belt content predates ADR-0060 (see tension T5), so it decides nothing current.
- Issue #341 (the fork spec, still open) and its comment of 2026-09-22.
- Closed belt tickets #383-#422 and open #405, #407, #411, #416, used only to date a decision.
- `docs/research/satisfactory-conveyor-placement.md` and `docs/research/simplebelts-coverage.md` are
  secondary. They are cited only where an ADR adopted their ruling.

**This is research, not a decision.** Attributions marked *(reading)* are this document's
inference. The source itself does not name Factorio or Satisfactory there.

## Key

- **Borrows**: **F** Factorio, **S** Satisfactory, **F+S** both, **—** neither (argued from
  Minecraft or from the medium).
- **Scope**: **Mod** is a belt mechanic any pack gets. **Pack** is tied to the Pack's progression,
  corpus, recipes, energy rate or other mods. **Mixed** means the mechanism is the Mod's but its
  numbers or binding are the Pack's.
- **Status**: the ADR's front-matter status, or the ledger's verdict where no ADR owns the row.

## 1. Framing

| # | Decision | Source | Borrows | Scope |
|---|---|---|---|---|
| 1 | The logistics puzzle is production-chain routing: what feeds what, at what ratio, over what distance. The belt-lane micro-puzzle is explicitly left out. | ADR-0044 l.45-49; `CONTEXT.md` l.151 | F (its ratios, not its lanes) | Mod |
| 2 | Factorio's belt makes four claims: throughput as a budget, the belt as a spatial constraint, balancers, and compression as a diagnostic. The Mod answers each one separately. | ADR-0044 l.51-72 | F | Mod |
| 3 | Most of Factorio's belt puzzle comes from Factorio being flat. In 3D, weaving and lane packing dissolve, and routing in Y replaces them. | ADR-0044 l.16-35; kept by ADR-0060 l.76-77 | — (from the medium) | Mod |
| 4 | Belt numbers are Factorio's, never rescaled, so that ratios hold against machines that run at Factorio's seconds. | ADR-0044 l.115-117; ADR-0060 l.81-85 | F | Mod |

## 2. The belt: throughput, buffer, cost

| # | Decision | Source | Borrows | Scope |
|---|---|---|---|---|
| 5 | Four tiers at 15, 30, 45 and 60 items/s. | ADR-0060 l.81-84; ADR-0084 l.27-29; ledger l.579-585 | F | Mixed: the numbers are Mod; Factorio's names, recipes and unlocks are Pack |
| 6 | One item per belt entry at 1/8-block spacing. A loader puts several entries on the belt in one tick when its tier needs more than 20 items/s. | ADR-0060 l.83-84; #341 l.88-91; ledger l.586-589 | F | Mod |
| 7 | A tile holds eight items, so a 64-tile belt holds 512. The belt is a buffer as well as a route. | ADR-0060 l.85; `CONTEXT.md` l.157; ledger l.621-623 | F | Mod |
| 8 | A belt has one lane and carries its tier's whole throughput. It *draws* its items in two rows of four only so they are legible. | ADR-0044 l.24-26; ledger l.611-617 | — as argued. Outcome matches S's single-stream conveyor *(reading)* | Mod |
| 9 | Belts, loaders and splitters of any tiers may be joined. Each one caps only its own flow, so a line runs at its slowest piece. | ADR-0076 l.25-27; ledger l.590-593 | F | Mod |
| 10 | Cost per length is mandatory: one belt item per tile. A stretch the player cannot pay for is refused whole. Breaking a tile returns it and its items. | ADR-0060 l.93-94; ADR-0084 l.15-16; ledger l.624-627 | F (S charges per length too, per the Satisfactory research §6) | Mod |
| 11 | The researched stack-size bonus is planned but not chosen. It waits on #25. | ADR-0060 l.84; ledger l.656-657 | F | Mixed: stacking is Mod, the research that grants it is Pack |

## 3. Shape and lines

| # | Decision | Source | Borrows | Scope |
|---|---|---|---|---|
| 12 | A belt is made of tiles: one block of belt, placed, broken and paid for on its own, facing the way items travel. | ADR-0084 l.13-18; `CONTEXT.md` l.161 | F | Mod |
| 13 | A tile's shape is derived and never chosen. A tile fed from exactly one side, with nothing behind it, is a one-block corner. | ADR-0084 l.16-18; `CONTEXT.md` l.161 (#391) | F | Mod |
| 14 | Contiguous tiles merge at runtime into one transport line, which ticks once. The line is derived state: each tile saves its own items, and the line never spans an unloaded chunk. | ADR-0084 l.22-26; `CONTEXT.md` l.202 | F (Factorio's engine concept) *(reading)* | Mod |
| 15 | A belt ends where its tiles do: at a loader, at a splitter half, or at nothing, where it backs up. The belt places no loaders. | ADR-0084 l.30-32; `CONTEXT.md` l.210 | F | Mod |
| 16 | A tile fed into the side of a straight tile merges into the gaps of that line, and the line from behind goes first. This is Factorio's side-load with no far lane. | `CONTEXT.md` l.206; ledger l.618-620 (#409) | F (adapted) | Mod |
| 17 | A two-click stretch: a sneak-click stores the start and the look direction, and the next click lays straight legs joined by corners, refused whole. Tiles already on the path are turned, or replaced when they are of another tier. | `CONTEXT.md` l.190; ledger l.565-574 (#393) | F+S: Factorio's row of tiles, laid by S's click-start, click-end gesture *(reading)* | Mod |
| 18 | A stretch is laid only on blocks with a sturdy top face. A single tile is placed the way vanilla places a block. | ADR-0084 l.19-21 (amended by ADR-0085's wedge) | — | Mod |
| 19 | Players and item entities ride a tile at its speed, around corners and off the end of the line. | `CONTEXT.md` l.161 (#396) | F | Mod |
| 20 | Belt hand: holding use on a belt takes the items reaching that point at the belt's rate, while the source keeps feeding. A full inventory stops taking and loses nothing. | `CONTEXT.md` l.186; #341 l.27, l.113-116 (#350, #396) | — (it replaces upstream's break-the-loader) | Mod |
| 21 | Tier colours are yellow, red, blue and green, shown as the belt's edge stripes, the loader's band and the splitter's divider. | `CONTEXT.md` l.198 | F | Mod |

## 4. Height and crossing

| # | Decision | Source | Borrows | Scope |
|---|---|---|---|---|
| 22 | Underground belts are excluded. A belt crosses another by climbing over it. | ADR-0044 l.20-23; ADR-0085 l.62-63, l.75-76; ledger l.594-599 | — as argued. Outcome matches S, which also crosses over (Satisfactory research §4) | Mod |
| 23 | A tile has a derived **pitch** of one block of height per block of travel: a foot, a middle or a top. There are no crests or valleys, and a corner is always level. | ADR-0085 l.12-34; `CONTEXT.md` l.165-180 | — (S allows 35° free curves, and F has no height) | Mod |
| 24 | A slope is one block of line. The climb is only drawn: the tier's throughput and eight items per tile are unchanged. | ADR-0085 l.35-37 | F (tile arithmetic kept through height) | Mod |
| 25 | A slope takes no side-load, never turns, and never meets a loader or splitter. A placement that would turn a corner into a slope is refused. | ADR-0085 l.38-43 (#419) | — | Mod |
| 26 | A middle or a top over air stands on a free **wedge**, placed and broken with its tile. No slope stands on a belt. | ADR-0085 l.44-48 (#420) | — (not S's paid, fixed-height support pole) | Mod |
| 27 | A stretch follows the ground at most one block per column on the lowest path. It crosses a line in five tiles (foot, top, level, top, foot), and lines side by side are crossed as one. Where no path fits, it is refused whole. | ADR-0085 l.48-60 (#421, #422) | — | Mod |
| 28 | Conveyor Lifts are rejected. Height is gained along a slope or not at all. | ADR-0078 l.51-52 (superseded, but nothing brought lifts back) | S rejected | Mod |
| 29 | *(Superseded.)* The belt was a spline between grid-aligned supports, bounded by Satisfactory's numbers: 35° slope, climb or turn but not both, a 1-block turn radius and 32-block reach. | ADR-0078, superseded by ADR-0084 | S | Mod |

## 5. Loaders

| # | Decision | Source | Borrows | Scope |
|---|---|---|---|---|
| 30 | There is no inserter and no swing arm. A loader at a belt's end, set against an inventory, loads or unloads it. | ADR-0060 l.95-96; `CONTEXT.md` l.214; ledger l.632-637 | F (Factorio's hidden loader) | Mixed: the loader is Mod; "no inserter at all" is a Pack ruling |
| 31 | A loader carries no items. Like Factorio's 1×1 loader, its belt distance is 0 and a line's capacity is its tiles' alone. It is a solid housing. | `CONTEXT.md` l.214 (#408) | F | Mod |
| 32 | Four loader tiers, each crafted from one rung of the inserter chain (burner, inserter, fast, bulk) and unlocked by that inserter's technology, at 15, 30, 45 and 60 items/s. | ADR-0076 l.11-19 | F | Pack: recipes and research from the Factorio corpus |
| 33 | Tier 1 is unpowered. Tiers 2 to 4 draw FE per item, derived from their inserter's swing, plus that inserter's drain, and stall at zero. Power arrives through NeoForge's energy capability. | ADR-0060 l.97-98; ADR-0076 l.21-23; ledger l.649-653; #341 l.92-93, l.104 | F | Mixed: the energy mechanism is Mod; the figures (66.5, 81.2 and 116 FE) assume the Pack's 1 FE = 100 J (ADR-0060 l.198) |
| 34 | The long-handed inserter is excluded, because there is no arm to lengthen. | ADR-0076 l.22-23; ledger l.654-655 | F (excluded) | Pack: a corpus row |
| 35 | A loader's filter takes one item or an FTB Filter System filter, and it can be set before any line reaches the loader. | ADR-0084 l.33-35; #341 l.105 | — (upstream's) | Mixed: FTB Filter System is a Pack mod |
| 36 | The tap, loading or unloading partway along a belt, is postponed because the splitter covers the main bus. | ADR-0060 l.100; `simplebelts-coverage.md` l.169-176 | — | Mod |

## 6. Splitters and balancers

| # | Decision | Source | Borrows | Scope |
|---|---|---|---|---|
| 37 | A splitter is two blocks wide, with two belts in and two out. It splits evenly, merges evenly, sends everything to the free side when the other backs up, and draws no power. | ADR-0060 l.86-87, l.99; `CONTEXT.md` l.218; ledger l.600-607 | F | Mod |
| 38 | There are four splitter tiers, from Factorio's four splitter recipes, and one splitter per tier. A green-circuit splitter must not carry express throughput. | ADR-0076 l.25, l.43-46 | F | Mixed: tiers are Mod, recipes and unlocks are Pack |
| 39 | Each splitter half is a block of belt of its tier, holding eight items. Factorio's 51-position input buffer is left out because the belt has one lane. | ADR-0076 l.29-33 (#373) | F (adapted) | Mod |
| 40 | A balancer is a pattern of splitters the player builds, never a block. | ADR-0060 l.86-87; `CONTEXT.md` l.233; ledger l.608-609 | F | Mod |
| 41 | Input priority, output priority and a filter are planned, each none, left or right. | `CONTEXT.md` l.222-232; ledger l.610 (#357, #380-#382) | F | Mod |
| 42 | A splitter placed across a straight tile line running its way takes the tile's place, refunds it and keeps its items. | `CONTEXT.md` l.218 (#394) | F *(reading)* | Mod |

## 7. Placement, Rotate, replace, dismantle, reach

| # | Decision | Source | Borrows | Scope |
|---|---|---|---|---|
| 43 | Placement is a plan, and the preview draws the same plan the click executes, red when refused. The fork has its own plan type because it cannot depend on the core. The splitter's plan lives in the core. | ADR-0069 l.15-21, l.31-35; ledger l.685-686 | F | Mixed: the contract is shared; the core's preview renderer and the splitter's plan are Pack |
| 44 | Rotate and Reverse Rotate (`R` / `Shift+R`) store a quarter-turn offset on the held stack, relative to the player's look rather than a compass direction. A mixin in the core on `BlockPlaceContext` applies it, so the fork's tile turns with no code of its own. | ADR-0083 l.11-24; `CONTEXT.md` l.374-380 | F (adapted: relative because the camera turns) | Pack: the key and the mixin live in the core |
| 45 | Placed-block rotation is planned. What turning means is the block's own. The splitter's turn and its side priority follow it. | ADR-0083 l.26-33; ledger l.217-221 (#405, #407) | F | Mixed |
| 46 | Fast Replace follows Factorio's `fast_replaceable_group`, read from the corpus. Belts and splitters of every tier form one group, and loaders another. | ADR-0082 l.11-12, l.29-31; `CONTEXT.md` l.382 (#384 open) | F | Pack: the groups come from the corpus, and the mechanism is in the core |
| 47 | Dismantle: two sneak-clicks of the Engineer's Pick take up one line from tile to tile. The items go to the inventory, and loaders and splitters are never taken. It is the belt-only precursor of the deconstruction planner. | `CONTEXT.md` l.194, l.362; ledger l.210-213, l.734-735 (#404) | F (adapted) | Mixed: the fork keys the gesture on the tag `belts:dismantles_belts`, and only the Pack puts the Pick in it |
| 48 | The Engineer's Pick is the one mining tool, in two tiers. It mines every block class and dismantles belts. | ADR-0039; `CONTEXT.md` l.404 | F | Pack |
| 49 | Build reach is 16 blocks for any **Building**, belts included, following Satisfactory's Build Gun. Anything else breaks within 4.5. | `CONTEXT.md` l.263-269; ledger l.1247-1260 (#413) | S | Pack |
| 50 | A mining drill outputs onto whatever its arrow points at, a belt included. There is no belt-specific rule. | ADR-0042 l.23-25; ADR-0043 l.13-16 | F | Mod (the belt must accept a plain insertion) |

## 8. Platform and stack rulings that bound the Mod

| # | Decision | Source | Borrows | Scope |
|---|---|---|---|---|
| 51 | The Pack's belts, loaders and splitter belong to a fork of SimpleBelts, which replaced Create. | ADR-0060 l.16-23, l.74-100 | — | Pack's choice |
| 52 | The fork never depends on the Pack. | #341 l.76; ADR-0069 l.31-35; ADR-0083 l.21-24 | — | Mod (a boundary rule) |
| 53 | Minecraft 26.1.2, NeoForge only, with the Fabric module dropped. | ADR-0060 l.16; #341 l.74, l.177 | — | Pack-driven, open for #16 |
| 54 | The `belts` mod id and upstream's id style stay: `belt_tile`… for belts, `chute`… for loaders, `splitter`… for splitters. The lang files use Factorio's names. | #341 l.75; ledger l.579-580, l.644-646 | F (names) | Open for #4 |
| 55 | Recipes come from the Factorio corpus on the Pack's Assembling surface, and upstream's kelp-and-stick recipes are swept. The turbo belt and turbo splitter are registered with no recipe. | #341 l.124-133; ADR-0076 l.50-53 | F | Pack |
| 56 | `logistics`, `logistics-2` and `logistics-3` unlock splitters and belt tiers through Researchd. | ADR-0060 l.88-89; ledger l.629-630 | F | Pack |
| 57 | Circuit-controlled belts and inserters are unargued. | ADR-0030 l.60-61; ledger l.795 | — | open |

## 9. Tensions and open contradictions

- **T1. ADR-0078's splines against ADR-0084's tiles, and a stale spec.** ADR-0084 superseded
  ADR-0078 and removed the spline, the support, the span bounds, the loader choice at open ends, the
  splitter's belt cut and the far-end hand hold (ADR-0084 l.37-38). Spec #341 is still open and still
  describes splines in several places:
  - it charges `ceil(length)` of the spline and stores that cost against a support being moved (l.99-100)
  - it removes the whole belt when a loader breaks (l.119-120)
  - it ray-casts the hand against sampled belt curves (l.114)

  Its 2026-09-22 comment orders #362, #366 and #365, which are all superseded. The spec's numbers
  and loader and splitter rules still hold. Its geometry and break rules do not.
- **T2. Three justifications for leaving out undergrounds.** ADR-0044 (a Create belt climbs over)
  gave way to ADR-0078 (a spline climbs over). Under ADR-0084 the argument "waits on #412"
  (ADR-0084 l.50-51), because flat tiles could not cross. ADR-0085 restored it from slopes. The
  exclusion held throughout, but it depends on crossing by slope staying cheap.
- **T3. Search-and-replace damage in the ADRs.** Several ADRs say "Simplebelts" where the context
  shows Create is meant:
  - ADR-0060 l.10, l.36, l.74, l.125 and l.241, for example "Why a SimpleBelts fork, and not
    Simplebelts's belts", against its own l.25 (Create is leaving) and l.245 ("Keep Create for belts")
  - ADR-0017 l.8, l.49 and l.201, for example "Simplebelts Mechanical Drill"
  - ADR-0018 l.9, and ADR-0021 l.10 and l.48

  Anyone porting these ADRs to the Mod (#11) has to repair them.
- **T4. Stale rows in the ledger.**
  - *Drag-building* (l.1404) is still `excluded` "by-consequence" of Create's endpoint-to-endpoint
    belts, yet the stretch now drags a row of tiles.
  - *Logistic robots* (l.659-665) still gives item logistics to Create.
  - *Belt shape* (l.573-574) says a stretch crossing a line "is #412's", while the underground row
    (l.594-599) and ADR-0085 say #422 shipped it.
- **T5. `docs/gdd.md` predates the switch.** It still says Minecraft 1.21.1 (l.3), and Create owns
  belts (l.21-24) and "pre-AE2 logistics" (l.50-56). It is not a source for belt direction.
- **T6. Parts of the Mod's behaviour live in the Pack's core.** In another pack, the fork loses:
  - the Rotate key and its mixin (ADR-0083), so its tile turns only by where the player looks
  - the splitter's two-block placement plan and preview (ADR-0069 l.33-35)
  - the preview renderer for the belt's own plan (ADR-0069)
  - Fast Replace (ADR-0082)
  - any item in `belts:dismantles_belts`, which the Pack fills with the Pick

  The loaders' FE figures only make sense at the Pack's 100 J per FE.
- **T7. "No inserter" is a Pack ruling written as a belt rule.** ADR-0060 and ADR-0076 make the
  loader *the* inserter and exclude the long-handed one. A Mod meant to work with any tech mod will
  sit beside other mods' inserters, pipes and item handlers. The loader-as-inserter tiers and
  recipes are the Pack's. The loader as a belt end is the Mod's.
- **T8. Throughput was deferred, then fixed.** ADR-0044 deferred the throughput budget to play and
  rejected a stack-size ladder as "a house rule" (l.88-98, l.158-161). ADR-0060 fixed 15-60 items/s
  "plus the researched bonus" (l.84). The ledger keeps the bonus `planned` pending #25 (l.656). So
  the stack-bonus question ADR-0044 closed is open again.
- **T9. Most belt ADRs are provisional.** ADR-0060 and ADR-0044 are `provisional` under ADR-0042,
  which is promoted only after Terra's rung 0 is played (ADR-0060 l.251-254). The tier, buffer, cost
  and loader rulings all rest on ADR-0060. ADR-0069, 0076, 0082, 0083, 0084 and 0085 are
  `accepted`.
- **T10. Little of the Satisfactory half remains.** ADR-0078 was the Pack's one Satisfactory-shaped
  belt decision, and it is superseded. What remains from Satisfactory:
  - reach 16 (#49), a Pack setting
  - crossing over rather than under (#22), argued from the medium
  - the two-click stretch gesture (#17), an attribution this document makes

  The Mod's stated identity blends Factorio and Satisfactory, but in the Pack's record the blend is
  "Factorio's numbers and tile shape, plus Minecraft's third axis". The pillars grilling (#13) should
  decide whether that is the intended blend.
- **T11. Floating lines are undecided.** ADR-0084 l.52 keeps a tile whose ground is removed where it
  is. ADR-0085 lets slopes climb through air on wedges. A stretch still refuses level tiles over air.
  How far a level line may float was Pack #411, now folded into the Mod's #1.
- **T12. Rotate on a slope.** `CONTEXT.md` l.169 says a slope "is never rotated", but ADR-0085
  l.42-43 says that refusal belongs to placed-block rotation (#405), which does not exist yet. The
  glossary states a contract the code has not reached.
- **T13. The Pack's glossary lags in git.** The committed `CONTEXT.md` still says a belt "is flat: a
  stretch does not climb" (ADR-0084 era). ADR-0085's glossary entries are only in the Pack's working
  tree. Anyone reading the Pack from git gets a stale belt definition.

## 10. Facts for the pillars grilling (#13)

1. From Factorio: the numbers (tiers, items/s, eight per tile, cost per tile), the tile, the
   derived corner, the transport line, belt ends, the side-load, the two-wide splitter and the
   constructed balancer, the loader and its tiers, tier colours, fast-replace groups, Rotate
   (relative) and the plan-and-preview contract.
2. From Satisfactory, still in force: build reach (Pack) and crossing by going over (as an outcome).
   The spline, its bounds and its supports were tried and superseded (ADR-0078 → 0084).
3. Argued from Minecraft or the medium: one lane, no undergrounds, derived 45° slopes, wedges, a
   stretch that follows the ground, and the belt hand.
4. Pack-specific and outside the Mod's direction: recipes and research unlocks, FE figures at
   100 J/FE, the Engineer's Pick, reach 16, "no inserter anywhere", long-handed excluded, the
   Rotate key's home in the core, the splitter's plan in the core, FTB Filter System filters, and
   the corpus-driven Replace Groups.
