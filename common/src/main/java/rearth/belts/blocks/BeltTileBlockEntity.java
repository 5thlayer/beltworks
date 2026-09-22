package rearth.belts.blocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;
import rearth.belts.BlockEntitiesContent;
import rearth.belts.model.BeltTier;
import rearth.belts.model.LineScan;
import rearth.belts.model.TransportLine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * One block of belt, and, where it is the head of its line, the {@link TransportLine} that ticks
 * for the whole run (PlanetaryFactory #398).
 *
 * <p>The line is derived state: each tile saves only its own share of the items, so the run can be
 * merged and split without a codec of its own. A tile rescans only when something told it to,
 * because a rescan walks the whole run.
 */
public class BeltTileBlockEntity extends BlockEntity {

    private final BeltTier tier;

    // Only the head of a line holds one; every other tile of the run ticks nothing.
    private @Nullable TransportLine<ItemStack> line;
    private List<BlockPos> tiles = List.of();
    private @Nullable BlockPos head;
    private boolean rescan = true;

    // This tile's own share of the items, indexed from itself, while no line holds them.
    private List<TransportLine.Load<ItemStack>> carried = new ArrayList<>();

    public BeltTileBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntitiesContent.BELT_TILE.get(), pos, state);
        tier = ((BeltTileBlock) state.getBlock()).tier();
    }

    public BeltTier tier() {
        return tier;
    }

    /** The way items on this tile travel. */
    public Direction travel() {
        return getBlockState().getValue(HorizontalDirectionalBlock.FACING);
    }

    public void tick() {
        if (rescan) rebuild();
        if (line == null) return;
        if (line.tick(this::takeFromLoader, this::giveToLoader)) setChanged();
    }

    /** Tells this tile its run has changed under it, so it scans again on its next tick. */
    public void invalidate() {
        rescan = true;
    }

    private void invalidate(@Nullable BlockPos pos) {
        if (pos == null) return;
        tileAt(pos).ifPresent(BeltTileBlockEntity::invalidate);
    }

    // Asked of loaded chunks only: a line stops at a chunk's edge rather than pulling the next
    // chunk in, and resumes when that chunk loads and its tiles scan.
    private Optional<BeltTileBlockEntity> tileAt(BlockPos pos) {
        return level == null || !level.isLoaded(pos) ? Optional.empty()
                 : level.getBlockEntity(pos, BlockEntitiesContent.BELT_TILE.get());
    }

    private void rebuild() {
        rescan = false;
        if (level == null) return;
        // A tile placed against a run brings the whole run's heads and tails into question.
        var first = head == null;
        var scanned = LineScan.through(spot(worldPosition), this::travelAt);
        if (scanned.isEmpty()) return;
        var run = scanned.stream().map(BeltTileBlockEntity::pos).toList();
        var scannedHead = run.getFirst();
        if (!scannedHead.equals(head)) invalidate(head);
        head = scannedHead;
        if (first) for (var side : Direction.Plane.HORIZONTAL) invalidate(worldPosition.relative(side));

        if (!head.equals(worldPosition)) {
            var handedOver = line != null && !line.contents().isEmpty();
            release();
            // Only when there is something for it: every tile of a run scans when its chunk loads,
            // and a nudge each would rebuild the line once per tile.
            if (handedOver || !carried.isEmpty()) invalidate(head);
            return;
        }
        buildLine(run);
    }

    private void buildLine(List<BlockPos> run) {
        var tierList = new ArrayList<BeltTier>(run.size());
        var members = new ArrayList<BeltTileBlockEntity>(run.size());
        for (var pos : run) {
            var tile = tileAt(pos).orElse(null);
            // The run was scanned from the block states, so a missing block entity means the world
            // moved under the scan; the next tick scans again.
            if (tile == null) {
                rescan = true;
                return;
            }
            tierList.add(tile.tier);
            members.add(tile);
        }

        // Every member lets go before any is drained: a tile joining two runs makes one of their
        // heads a member, and block entities tick in an order nothing here decides, so a member
        // still holding a line would otherwise strand its items where nothing looks again.
        for (var member : members) member.release();

        var loads = new ArrayList<TransportLine.Load<ItemStack>>();
        for (var index = 0; index < members.size(); index++) {
            var member = members.get(index);
            if (member.carried.isEmpty()) continue;
            for (var load : member.carried) {
                loads.add(new TransportLine.Load<>(index + load.tile(), load.offset(), load.payload()));
            }
            member.carried = new ArrayList<>();
            member.setChanged();
        }
        loads.sort(Comparator.comparingDouble(load -> load.tile() + load.offset()));

        tiles = run;
        line = new TransportLine<>(tierList);
        for (var spilled : line.restore(loads)) drop(spilled);
        setChanged();
    }

    /** Hands this line's items back to its tiles and stops ticking it. */
    private void release() {
        if (line == null) return;
        for (var load : line.spill()) {
            var tile = load.tile() < tiles.size() ? tileAt(tiles.get(load.tile())).orElse(null) : null;
            if (tile == null) {
                drop(load.payload());
                continue;
            }
            tile.carried.add(new TransportLine.Load<>(0, load.offset(), load.payload()));
            tile.setChanged();
        }
        line = null;
        tiles = List.of();
        setChanged();
    }

    private void drop(ItemStack stack) {
        if (level == null || level.isClientSide()) return;
        var at = worldPosition.getCenter();
        level.addFreshEntity(new ItemEntity(level, at.x, at.y, at.z, stack));
    }

    private LineScan.@Nullable Travel travelAt(LineScan.Spot spot) {
        return tileAt(pos(spot)).map(tile -> {
            var facing = tile.travel();
            return new LineScan.Travel(facing.getStepX(), facing.getStepZ());
        }).orElse(null);
    }

    private static LineScan.Spot spot(BlockPos pos) {
        return new LineScan.Spot(pos.getX(), pos.getY(), pos.getZ());
    }

    private static BlockPos pos(LineScan.Spot spot) {
        return new BlockPos(spot.x(), spot.y(), spot.z());
    }

    /** The loader feeding the head of the line, which faces the way the line travels. */
    private @Nullable ItemStack takeFromLoader() {
        var travel = travel();
        var loader = loaderAt(worldPosition.relative(travel.getOpposite()), travel);
        return loader == null ? null : loader.extractOne();
    }

    /** The loader past the last tile, which faces back along the line, into it. */
    private boolean giveToLoader(ItemStack item) {
        if (tiles.isEmpty()) return false;
        var last = tiles.getLast();
        var travel = travel();
        var loader = loaderAt(last.relative(travel), travel.getOpposite());
        return loader != null && loader.acceptFromLine(item);
    }

    private @Nullable ChuteBlockEntity loaderAt(BlockPos pos, Direction facing) {
        if (level == null || !level.isLoaded(pos)) return null;
        var loader = level.getBlockEntity(pos, BlockEntitiesContent.CHUTE_BLOCK.get()).orElse(null);
        if (loader == null || loader.isSplitter() || loader.isSupport()) return null;
        return loader.getOwnFacing() == facing ? loader : null;
    }

    /** The line this tile belongs to, whichever tile of the run holds it. */
    public @Nullable TransportLine<ItemStack> line() {
        if (line != null) return line;
        if (head == null || level == null || head.equals(worldPosition)) return null;
        return tileAt(head).map(tile -> tile.line).orElse(null);
    }

    /** What this tile itself carries, for a tooltip (#398). */
    public List<ItemStack> heldHere() {
        var run = line != null ? this : (head == null ? null : tileAt(head).orElse(null));
        if (run == null || run.line == null) return List.of();
        var index = run.tiles.indexOf(worldPosition);
        if (index < 0) return List.of();
        return run.line.spill().stream().filter(load -> load.tile() == index)
                 .map(TransportLine.Load::payload).toList();
    }

    // Catches every removal, so a break, an explosion or a command leaves the run scanning again.
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        // Through the head, so every tile of the run holds its own share before the run is cut:
        // the tiles past this one keep their items and run dry rather than losing them (#383).
        (head == null ? Optional.of(this) : tileAt(head)).ifPresent(BeltTileBlockEntity::release);
        for (var load : carried) drop(load.payload());
        carried = new ArrayList<>();
        invalidate(head);
        for (var side : Direction.Plane.HORIZONTAL) invalidate(pos.relative(side));
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        // A line's items are the head's until a tile of its own saves them (#395); they carry
        // their index along the line, which for the head is its index in the run, zero.
        var loads = line != null ? line.spill() : carried;
        var list = output.childrenList("carried");
        for (var load : loads) {
            var child = list.addChild();
            child.putInt("tile", load.tile());
            child.putDouble("offset", load.offset());
            child.store("stack", ItemStack.OPTIONAL_CODEC, load.payload());
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        carried = new ArrayList<>();
        for (var child : input.childrenListOrEmpty("carried")) {
            carried.add(new TransportLine.Load<>(child.getIntOr("tile", 0), child.getDoubleOr("offset", 0),
              child.read("stack", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY)));
        }
        line = null;
        tiles = List.of();
        head = null;
        rescan = true;
    }
}
