// SPDX-FileCopyrightText: Rearth
// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: CC-BY-4.0 AND MIT

package io.github._5thlayer.beltworks.blocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;
import io.github._5thlayer.beltworks.BeltSync;
import io.github._5thlayer.beltworks.BlockEntitiesContent;
import io.github._5thlayer.beltworks.TileLineUpdate;
import io.github._5thlayer.beltworks.collision.BeltCollisionRegistry;
import io.github._5thlayer.beltworks.model.BeltContents;
import io.github._5thlayer.beltworks.model.BeltTier;
import io.github._5thlayer.beltworks.model.LineScan;
import io.github._5thlayer.beltworks.model.Pitch;
import io.github._5thlayer.beltworks.model.Splitter;
import io.github._5thlayer.beltworks.model.TileShape;
import io.github._5thlayer.beltworks.model.TransportLine;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

/**
 * One block of belt, and, where it is the head of its line, the {@link TransportLine} that ticks
 * for the whole run (PlanetaryFactory #398).
 *
 * <p>The line is derived state: each tile saves only its own share of the items, so the run can be
 * merged, split and cut at an unloaded chunk without a codec of its own (#395). A tile rescans only
 * when something told it to, because a rescan walks the whole run.
 */
public class BeltTileBlockEntity extends BlockEntity {

    private final BeltTier tier;

    // Only the head of a line holds one; every other tile of the run ticks nothing.
    private @Nullable TransportLine<ItemStack> line;
    private List<BeltTileBlockEntity> members = List.of();
    private List<BlockPos> tiles = List.of();
    // One tile in each chunk the line crosses.
    private List<BlockPos> inChunks = List.of();
    private @Nullable List<List<TransportLine.Share<ItemStack>>> shares;

    private @Nullable BeltTileBlockEntity holder;
    private int index;

    private @Nullable BlockPos head;
    private boolean rescan = true;
    // The game time the line this tile holds last moved, so a splitter handing it an item knows
    // whether the line is still to move this tick (#394).
    private long movedAt = Long.MIN_VALUE;
    // Saved and about to be removed with its chunk, so no line counts it any more (#395).
    private boolean unloaded;

    // This tile's own share of the items while no line holds them.
    private List<TransportLine.Share<ItemStack>> carried = new ArrayList<>();

    // On the tile rather than the line, so a rebuilt line keeps it (#396).
    private @Nullable HeldHand heldHand;

    public BeltTileBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntitiesContent.BELT_TILE.get(), pos, state);
        tier = ((BeltTileBlock) state.getBlock()).tier();
    }

    public BeltTier tier() {
        return tier;
    }

    public TileShape shape() {
        return getBlockState().getValue(BeltTileBlock.CORNER).model();
    }

    public Pitch pitch() {
        return getBlockState().getValue(BeltTileBlock.PITCH).model();
    }

    /** The way items on this tile travel. */
    public Direction travel() {
        return getBlockState().getValue(HorizontalDirectionalBlock.FACING);
    }

    public void tick() {
        if (unloaded) return;
        registerRide();
        if (rescan) rebuild();
        if (line == null) return;
        if (line.tick(this::takeFromLoader, this::giveToLoader, hand())) lineChanged();
        movedAt = level.getGameTime();
        var changes = line.contents().drainChanges();
        if (!changes.isEmpty()) send(new TileLineUpdate(worldPosition, List.of(), List.of(), tiers(), line.ring(), false, changes));
    }

    // A tile's items are drawn from the line update, so on the client only the ride is kept.
    public void clientTick() {
        registerRide();
    }

    private void registerRide() {
        var shape = shape();
        var pitch = pitch();
        var travel = BeltTileBlock.travel(travel());
        var centre = worldPosition.getCenter();
        BeltCollisionRegistry.registerTile(level, worldPosition, new Ride(shape, pitch, travel), progress -> {
            var point = shape.point(progress, travel);
            return centre.add(point.x(), pitch.surface(progress) - Pitch.SURFACE, point.z());
        }, tier.blocksPerTick() * 20);
    }

    private record Ride(TileShape shape, Pitch pitch, LineScan.Travel travel) {
    }

    @Override
    public void setRemoved() {
        if (level != null) BeltCollisionRegistry.unregisterTile(level, worldPosition);
        super.setRemoved();
    }

    /**
     * Makes this tile's front the player's end for its line, until they release it or it lapses.
     * Once their inventory refuses an item the hand stops taking until it is released, and the
     * line runs on past it (#350, #396).
     */
    public void holdHand(ServerPlayer player) {
        var reach = player.blockInteractionRange() + 1;
        if (player.getEyePosition().distanceToSqr(worldPosition.getCenter()) > reach * reach) return;
        var taking = heldHand == null || heldHand.player != player || heldHand.taking;
        heldHand = new HeldHand(player, level.getGameTime() + HAND_LAPSE_TICKS, taking);
    }

    public void releaseHand(Player player) {
        if (heldHand != null && heldHand.player == player) heldHand = null;
    }

    // The first live hand on the line's tiles; the line has one end, so it takes one hand.
    private BeltContents.@Nullable Hand<ItemStack> hand() {
        for (var at = 0; at < members.size(); at++) {
            var member = members.get(at);
            var held = member.heldHand;
            if (held == null) continue;
            var player = held.player;
            if (level.getGameTime() > held.until || player.isRemoved() || !player.isAlive() || player.level() != level) {
                member.heldHand = null;
                continue;
            }
            if (!held.taking) continue;
            return new BeltContents.Hand<>(TransportLine.handPoint(at), item -> {
                if (BeltEndBlockEntity.intoInventory(player, item)) return true;
                member.heldHand = new HeldHand(player, held.until, false);
                return false;
            });
        }
        return null;
    }

    private static final int HAND_LAPSE_TICKS = BeltEndBlockEntity.HAND_LAPSE_TICKS;

    private record HeldHand(ServerPlayer player, long until, boolean taking) {
    }

    public void lineChanged() {
        var holding = holder();
        if (holding == null || level == null) return;
        holding.shares = null;
        // Every chunk, since each saves its own tiles' items and a chunk not marked is skipped.
        for (var tile : holding.inChunks) level.blockEntityChanged(tile);
    }

    /**
     * The line this tile heads, as a splitter half behind it hands it items, or null where this
     * tile is not a line's head entered from {@code travel} (#394).
     */
    public Splitter.@Nullable Handoff<ItemStack> entryHandoff(Direction travel) {
        if (line == null || line.ring() || !shape().entry(BeltTileBlock.travel(travel())).equals(BeltTileBlock.travel(travel))) return null;
        var pending = movedAt == level.getGameTime() ? 0 : line.speed();
        return new Splitter.Handoff<>(line.contents(), line.length(), line.speed(), null, pending);
    }

    /** Tells this tile its run has changed under it, so it scans again on its next tick. */
    public void invalidate() {
        rescan = true;
    }

    private void invalidate(@Nullable BlockPos pos) {
        if (pos == null) return;
        tileAt(pos).ifPresent(BeltTileBlockEntity::invalidate);
    }

    // Beside it, and a block up or down beside it, where a slope's other end is (#417).
    private void invalidateAround(BlockPos pos) {
        for (var side : Direction.Plane.HORIZONTAL) {
            var beside = pos.relative(side);
            invalidate(beside);
            invalidate(beside.above());
            invalidate(beside.below());
        }
    }

    // Asked of loaded chunks only: a line stops at a chunk's edge rather than pulling the next
    // chunk in, and resumes when that chunk loads and its tiles scan.
    private Optional<BeltTileBlockEntity> tileAt(BlockPos pos) {
        if (level == null || !level.isLoaded(pos)) return Optional.empty();
        return level.getBlockEntity(pos, BlockEntitiesContent.BELT_TILE.get()).filter(tile -> !tile.unloaded);
    }

    private void rebuild() {
        rescan = false;
        if (level == null) return;
        // A tile placed against a run brings the whole run's heads and tails into question.
        var first = head == null;
        var scan = LineScan.through(spot(worldPosition), this::pieceAt);
        if (scan.spots().isEmpty()) return;
        var run = scan.spots().stream().map(BeltTileBlockEntity::pos).toList();
        var scannedHead = run.getFirst();
        if (!scannedHead.equals(head)) invalidate(head);
        head = scannedHead;
        if (first) invalidateAround(worldPosition);

        if (!head.equals(worldPosition)) {
            var handedOver = line != null && !line.contents().isEmpty();
            release();
            // Not when the head's line is this run already: every tile of a run scans when its
            // chunk loads, and a nudge each would rebuild the line once per tile.
            var headTile = tileAt(head).orElse(null);
            if (headTile != null && (handedOver || !carried.isEmpty() || !headTile.tiles.equals(run))) {
                headTile.invalidate();
            }
            return;
        }
        buildLine(run, scan.ring());
    }

    private void buildLine(List<BlockPos> run, boolean ring) {
        var tierList = new ArrayList<BeltTier>(run.size());
        var found = new ArrayList<BeltTileBlockEntity>(run.size());
        for (var pos : run) {
            var tile = tileAt(pos).orElse(null);
            // The run was scanned from the block entities, so a missing one means the world moved
            // under the scan; the next tick scans again.
            if (tile == null) {
                rescan = true;
                return;
            }
            tierList.add(tile.tier);
            found.add(tile);
        }

        // Every member lets go before any is drained: a tile joining two runs makes one of their
        // heads a member, and block entities tick in an order nothing here decides, so a member
        // still holding a line would otherwise strand its items where nothing looks again.
        for (var member : found) member.release();

        var held = new ArrayList<List<TransportLine.Share<ItemStack>>>(found.size());
        var crossed = new LinkedHashMap<ChunkPos, BlockPos>();
        for (var at = 0; at < found.size(); at++) {
            var member = found.get(at);
            held.add(member.carried);
            member.carried = new ArrayList<>();
            member.holder = this;
            member.index = at;
            member.head = worldPosition;
            member.setChanged();
            crossed.putIfAbsent(ChunkPos.containing(member.worldPosition), member.worldPosition);
        }

        members = List.copyOf(found);
        tiles = run;
        inChunks = List.copyOf(crossed.values());
        line = new TransportLine<>(tierList, ring);
        shares = null;
        for (var spilled : line.restoreShares(held)) drop(spilled);
        line.contents().drainChanges();
        send(wholeLine());
        setChanged();
    }

    /** Hands this line's items back to its tiles and stops ticking it. */
    private void release() {
        if (line == null) return;
        var held = line.shares();
        for (var at = 0; at < members.size(); at++) {
            var member = members.get(at);
            if (member.holder == this) member.holder = null;
            // An unloaded tile has just saved its own share, and loads it again with its chunk.
            if (member.unloaded) continue;
            if (member.isRemoved()) {
                for (var share : held.get(at)) drop(share.payload());
                continue;
            }
            member.carried.addAll(held.get(at));
            member.setChanged();
        }
        send(TileLineUpdate.gone(worldPosition));
        line = null;
        members = List.of();
        tiles = List.of();
        inChunks = List.of();
        shares = null;
        setChanged();
    }

    /**
     * Lets go of the tiles of a chunk that has just been saved, with each tile's share in it, and
     * is about to unload. Every tile is flagged before any line is released, so no line hands a
     * share to a tile that is going and would lose it.
     */
    public static void chunkUnloading(Iterable<BlockEntity> blockEntities) {
        var going = new ArrayList<BeltTileBlockEntity>();
        for (var blockEntity : blockEntities) {
            if (blockEntity instanceof BeltTileBlockEntity tile) {
                tile.unloaded = true;
                going.add(tile);
            }
        }
        for (var tile : going) {
            var holding = tile.holder();
            if (holding == null) continue;
            var former = holding.members;
            holding.release();
            for (var member : former) if (!member.unloaded) member.rescan = true;
        }
    }

    private TileLineUpdate wholeLine() {
        return new TileLineUpdate(worldPosition, members.stream().map(BeltTileBlockEntity::travel).toList(),
          members.stream().map(member -> member.pitch().fed()).toList(), tiers(), line.ring(), true,
          new BeltContents.Changes<>(List.of(), line.contents().snapshot()));
    }

    private List<BeltTier> tiers() {
        return members.stream().map(BeltTileBlockEntity::tier).toList();
    }

    private void send(TileLineUpdate update) {
        if (!(level instanceof ServerLevel serverLevel) || inChunks.isEmpty()) return;
        BeltSync.sendLine(serverLevel, inChunks.stream().map(ChunkPos::containing).toList(), update);
    }

    /** The whole line this tile belongs to, for a player who has just been sent one of its chunks. */
    public @Nullable TileLineUpdate lineUpdate() {
        var holding = holder();
        return holding == null || holding.line == null ? null : holding.wholeLine();
    }

    /** The tile holding this tile's line, if a line holds it. */
    public @Nullable BeltTileBlockEntity holder() {
        return line != null ? this : holder;
    }

    private void drop(ItemStack stack) {
        if (level == null || level.isClientSide()) return;
        var at = worldPosition.getCenter();
        level.addFreshEntity(new ItemEntity(level, at.x, at.y, at.z, stack));
    }

    private LineScan.@Nullable Piece pieceAt(LineScan.Spot spot) {
        return tileAt(pos(spot)).map(tile -> {
            var travel = BeltTileBlock.travel(tile.travel());
            return new LineScan.Piece(travel, tile.shape().entry(travel), tile.pitch());
        }).orElse(null);
    }

    private static LineScan.Spot spot(BlockPos pos) {
        return new LineScan.Spot(pos.getX(), pos.getY(), pos.getZ());
    }

    private static BlockPos pos(LineScan.Spot spot) {
        return new BlockPos(spot.x(), spot.y(), spot.z());
    }

    /** The loader feeding the head of the line, facing the way items enter it. */
    private @Nullable ItemStack takeFromLoader() {
        var entry = shape().entry(BeltTileBlock.travel(travel()));
        var travel = Direction.getApproximateNearest(entry.x(), 0, entry.z());
        var loader = loaderAt(worldPosition.relative(travel.getOpposite()), travel);
        return loader == null ? null : loader.extractOne();
    }

    /** The loader past the last tile, which faces back along the line, or the side of a line there. */
    private boolean giveToLoader(ItemStack item) {
        if (members.isEmpty()) return false;
        var last = tiles.getLast();
        var travel = members.getLast().travel();
        var loader = loaderAt(last.relative(travel), travel.getOpposite());
        if (loader != null) return loader.acceptFromLine(item);
        var half = splitterAt(last.relative(travel), travel);
        if (half != null) return half.offerFromLine(item, line.contents().overshoot(line.length(), line.speed()));
        return sideLoad(last.relative(travel), travel, item);
    }

    // Only into the side of a straight, level tile: a corner's side is its entry, head-on is no
    // feed (#409), and a slope takes no side-load (#417).
    private boolean sideLoad(BlockPos pos, Direction travel, ItemStack item) {
        var fed = tileAt(pos).orElse(null);
        if (fed == null || fed.shape() != TileShape.STRAIGHT || fed.pitch() != Pitch.LEVEL || fed.travel().getAxis() == travel.getAxis()) return false;
        var holding = fed.holder();
        if (holding == null || holding == this || holding.line == null || fed.index >= holding.line.tileCount()) return false;
        if (!holding.line.sideLoad(item, fed.index)) return false;
        holding.shares = null;
        for (var tile : holding.inChunks) level.blockEntityChanged(tile);
        return true;
    }

    private @Nullable BeltEndBlockEntity splitterAt(BlockPos pos, Direction travel) {
        if (level == null || !level.isLoaded(pos)) return null;
        var half = level.getBlockEntity(pos, BlockEntitiesContent.BELT_END.get()).orElse(null);
        return half != null && half.isSplitter() && half.getOwnFacing() == travel ? half : null;
    }

    private @Nullable BeltEndBlockEntity loaderAt(BlockPos pos, Direction facing) {
        if (level == null || !level.isLoaded(pos)) return null;
        var loader = level.getBlockEntity(pos, BlockEntitiesContent.BELT_END.get()).orElse(null);
        if (loader == null || loader.isSplitter()) return null;
        return loader.getOwnFacing() == facing ? loader : null;
    }

    /** The line this tile belongs to, whichever tile of the run holds it. */
    public @Nullable TransportLine<ItemStack> line() {
        var holding = holder();
        return holding == null ? null : holding.line;
    }

    /** What this tile itself carries, and where in the tile, whether or not a line holds it. */
    public List<TransportLine.Share<ItemStack>> held() {
        var holding = holder();
        if (holding == null || holding.line == null) return List.copyOf(carried);
        if (holding.shares == null) holding.shares = holding.line.shares();
        return index < holding.shares.size() ? holding.shares.get(index) : List.of();
    }

    /** Lets go of what this tile carries without dropping it, for the tile replacing it to {@link #carry} (#393). */
    public List<TransportLine.Share<ItemStack>> takeCarried() {
        var holding = holder();
        if (holding != null) holding.release();
        var taken = carried;
        carried = new ArrayList<>();
        return taken;
    }

    public void carry(List<TransportLine.Share<ItemStack>> shares) {
        carried.addAll(shares);
        setChanged();
    }

    /** What this tile itself carries, for a tooltip (#398). */
    public List<ItemStack> heldHere() {
        return held().stream().map(TransportLine.Share::payload).toList();
    }

    // A tile turned into or out of a corner is in another line; the run is cut as a break cuts it.
    @Override
    public void setBlockState(BlockState state) {
        var before = getBlockState();
        super.setBlockState(state);
        if (level == null || level.isClientSide() || before.equals(state)) return;
        var holding = holder();
        if (holding != null) holding.release();
        invalidate();
        invalidate(head);
        invalidateAround(worldPosition);
    }

    // Catches every removal, so a break, an explosion or a command leaves the run scanning again.
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        // Through the holder, so every tile of the run holds its own share before the run is cut:
        // the tiles past this one keep their items and run dry rather than losing them (#383).
        var holding = holder();
        if (holding != null) holding.release();
        for (var share : carried) drop(share.payload());
        carried = new ArrayList<>();
        invalidate(head);
        invalidateAround(pos);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        var list = output.childrenList("carried");
        for (var share : held()) {
            var child = list.addChild();
            child.putDouble("offset", share.offset());
            child.store("stack", ItemStack.OPTIONAL_CODEC, share.payload());
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        carried = new ArrayList<>();
        for (var child : input.childrenListOrEmpty("carried")) {
            carried.add(new TransportLine.Share<>(child.getDoubleOr("offset", 0),
              child.read("stack", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY)));
        }
        line = null;
        members = List.of();
        tiles = List.of();
        inChunks = List.of();
        shares = null;
        holder = null;
        head = null;
        rescan = true;
    }
}
