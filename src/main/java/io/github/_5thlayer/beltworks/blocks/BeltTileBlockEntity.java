// SPDX-FileCopyrightText: Rearth
// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: CC-BY-4.0 AND MIT

package io.github._5thlayer.beltworks.blocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
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
import io.github._5thlayer.beltworks.model.HeldHand;
import io.github._5thlayer.beltworks.model.LineMembership;
import io.github._5thlayer.beltworks.model.LineScan;
import io.github._5thlayer.beltworks.model.Pitch;
import io.github._5thlayer.beltworks.model.Splitter;
import io.github._5thlayer.beltworks.model.TileShape;
import io.github._5thlayer.beltworks.model.TransportLine;

import java.util.ArrayList;
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

    private final LineMembership.Member<ItemStack, BeltTileBlockEntity> membership;
    private final LineMembership.World<ItemStack, BeltTileBlockEntity> world = new LevelLines();

    // The game time the line this tile holds last moved, so a splitter handing it an item knows
    // whether the line is still to move this tick (#394).
    private long movedAt = Long.MIN_VALUE;

    // On the tile rather than the line, so a rebuilt line keeps it (#396).
    private final HeldHand<ServerPlayer> heldHand = new HeldHand<>();

    public BeltTileBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntitiesContent.BELT_TILE.get(), pos, state);
        tier = ((BeltTileBlock) state.getBlock()).tier();
        membership = new LineMembership.Member<>(spot(pos), tier, this);
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
        if (membership.unloaded()) return;
        registerRide();
        var line = membership.tick(world);
        if (line == null) return;
        if (line.tick(this::takeFromLoader, this::handOn, hand())) lineChanged();
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
        heldHand.hold(player, level.getGameTime());
    }

    public void releaseHand(ServerPlayer player) {
        heldHand.release(player);
    }

    // The first live hand on the line's tiles; the line has one end, so it takes one hand.
    private BeltContents.@Nullable Hand<ItemStack> hand() {
        var members = membership.members();
        var now = level.getGameTime();
        var alive = PlayerHand.alive(level);
        for (var at = 0; at < members.size(); at++) {
            var hand = members.get(at).owner().heldHand.hand(TransportLine.handPoint(at), now, alive, PlayerHand::intoInventory);
            if (hand != null) return hand;
        }
        return null;
    }

    public void lineChanged() {
        if (level != null) membership.lineChanged(world);
    }

    /**
     * The line this tile heads, as a splitter half behind it hands it items, or null where this
     * tile is not a line's head entered from {@code travel} (#394).
     */
    public Splitter.@Nullable Handoff<ItemStack> entryHandoff(Direction travel) {
        var line = membership.headed();
        if (line == null || line.ring() || !shape().entry(BeltTileBlock.travel(travel())).equals(BeltTileBlock.travel(travel))) return null;
        var pending = movedAt == level.getGameTime() ? 0 : line.speed();
        return new Splitter.Handoff<>(line.contents(), line.length(), line.speed(), null, pending);
    }

    /** Tells this tile its run has changed under it, so it scans again on its next tick. */
    public void invalidate() {
        membership.invalidate();
    }

    /**
     * Lets go of the tiles of a chunk that has just been saved, with each tile's share in it, and
     * is about to unload.
     */
    public static void chunkUnloading(Iterable<BlockEntity> blockEntities) {
        var going = new ArrayList<LineMembership.Member<ItemStack, BeltTileBlockEntity>>();
        LineMembership.@Nullable World<ItemStack, BeltTileBlockEntity> world = null;
        for (var blockEntity : blockEntities) {
            if (blockEntity instanceof BeltTileBlockEntity tile) {
                going.add(tile.membership);
                world = tile.world;
            }
        }
        if (world != null) LineMembership.Member.chunkUnloading(going, world);
    }

    private TileLineUpdate wholeLine() {
        var line = membership.headed();
        var members = owners();
        return new TileLineUpdate(worldPosition, members.stream().map(BeltTileBlockEntity::travel).toList(),
          members.stream().map(member -> member.pitch().fed()).toList(), tiers(), line.ring(), true,
          new BeltContents.Changes<>(List.of(), line.contents().snapshot()));
    }

    private List<BeltTileBlockEntity> owners() {
        return membership.members().stream().map(LineMembership.Member::owner).toList();
    }

    private List<BeltTier> tiers() {
        return membership.members().stream().map(LineMembership.Member::tier).toList();
    }

    private void send(TileLineUpdate update) {
        var inChunks = membership.inChunks();
        if (!(level instanceof ServerLevel serverLevel) || inChunks.isEmpty()) return;
        BeltSync.sendLine(serverLevel, inChunks.stream().map(spot -> ChunkPos.containing(pos(spot))).toList(), update);
    }

    /** The whole line this tile belongs to, for a player who has just been sent one of its chunks. */
    public @Nullable TileLineUpdate lineUpdate() {
        var holding = holder();
        return holding == null ? null : holding.wholeLine();
    }

    /** The tile holding this tile's line, if a line holds it. */
    public @Nullable BeltTileBlockEntity holder() {
        var holding = membership.holder();
        return holding == null ? null : holding.owner();
    }

    private void drop(ItemStack stack) {
        world.drop(membership.spot(), stack);
    }

    private static void drop(Level level, BlockPos pos, ItemStack stack) {
        var at = pos.getCenter();
        level.addFreshEntity(new ItemEntity(level, at.x, at.y, at.z, stack));
    }

    // Asked of loaded chunks only: a line stops at a chunk's edge rather than pulling the next
    // chunk in, and resumes when that chunk loads and its tiles scan.
    private Optional<BeltTileBlockEntity> tileAt(BlockPos pos) {
        if (level == null || !level.isLoaded(pos)) return Optional.empty();
        return level.getBlockEntity(pos, BlockEntitiesContent.BELT_TILE.get()).filter(tile -> !tile.membership.unloaded());
    }

    /** The level as the line lifecycle sees it. */
    private final class LevelLines implements LineMembership.World<ItemStack, BeltTileBlockEntity> {

        @Override
        public LineMembership.@Nullable Member<ItemStack, BeltTileBlockEntity> at(LineScan.Spot spot) {
            return tileAt(pos(spot)).map(tile -> tile.membership).orElse(null);
        }

        @Override
        public LineScan.@Nullable Piece pieceAt(LineScan.Spot spot) {
            return tileAt(pos(spot)).map(tile -> {
                var travel = BeltTileBlock.travel(tile.travel());
                return new LineScan.Piece(travel, tile.shape().entry(travel), tile.pitch());
            }).orElse(null);
        }

        @Override
        public Object chunk(LineScan.Spot spot) {
            return ChunkPos.containing(pos(spot));
        }

        @Override
        public boolean removed(LineMembership.Member<ItemStack, BeltTileBlockEntity> member) {
            return member.owner().isRemoved();
        }

        @Override
        public void drop(LineScan.Spot at, ItemStack item) {
            if (level == null || level.isClientSide()) return;
            BeltTileBlockEntity.drop(level, pos(at), item);
        }

        @Override
        public void changed(LineMembership.Member<ItemStack, BeltTileBlockEntity> member) {
            member.owner().setChanged();
        }

        @Override
        public void chunkChanged(LineScan.Spot spot) {
            if (level != null) level.blockEntityChanged(pos(spot));
        }

        @Override
        public void lineBuilt(LineMembership.Member<ItemStack, BeltTileBlockEntity> head) {
            head.owner().send(head.owner().wholeLine());
        }

        @Override
        public void lineGone(LineMembership.Member<ItemStack, BeltTileBlockEntity> head) {
            head.owner().send(TileLineUpdate.gone(head.owner().worldPosition));
        }
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

    /** Hands the item at the line's end to the piece past its last tile, if one takes it. */
    private boolean handOn(ItemStack item) {
        var line = membership.headed();
        if (line == null || membership.members().isEmpty()) return false;
        var travel = membership.members().getLast().owner().travel();
        var outlet = BeltOutlet.pastLineEnd(level, pos(membership.tiles().getLast()).relative(travel), travel, this);
        return outlet != null && outlet.offer(item, line.contents().overshoot(line.length(), line.speed()));
    }

    /**
     * Merges an item from the end of the line {@code from} heads, or from a splitter half when
     * null, into this tile's line here.
     */
    boolean sideLoadFrom(@Nullable BeltTileBlockEntity from, ItemStack item) {
        return membership.sideLoad(item, from == null ? null : from.membership, world);
    }

    /**
     * Puts an item dropped onto this tile into its line, {@code offset} of the way along the tile or
     * in the nearest gap on it, answering whether the tile had room (#91).
     */
    public boolean dropOnto(ItemStack item, double offset) {
        return level != null && membership.insert(item, offset, world);
    }

    /**
     * How far, in blocks, the items under a loose item {@code offset} of the way along this tile
     * moved in the last tick, or NaN where no line holds the tile (#92).
     */
    public double movedAt(double offset) {
        return membership.movedAt(offset);
    }

    private @Nullable BeltEndBlockEntity loaderAt(BlockPos pos, Direction facing) {
        if (level == null || !level.isLoaded(pos)) return null;
        var loader = level.getBlockEntity(pos, BlockEntitiesContent.BELT_END.get()).orElse(null);
        if (loader == null || loader.isSplitter()) return null;
        return loader.getOwnFacing() == facing ? loader : null;
    }

    /** The line this tile belongs to, whichever tile of the run holds it. */
    public @Nullable TransportLine<ItemStack> line() {
        return membership.line();
    }

    /** Where this tile is in its line, counted from the line's first tile. */
    public int index() {
        return membership.index();
    }

    /** What this tile itself carries, and where in the tile, whether or not a line holds it. */
    public List<TransportLine.Share<ItemStack>> held() {
        return membership.held();
    }

    /** Lets go of what this tile carries without dropping it, for the tile replacing it to {@link #carry} (#393). */
    public List<TransportLine.Share<ItemStack>> takeCarried() {
        return membership.takeCarried(world);
    }

    public void carry(List<TransportLine.Share<ItemStack>> shares) {
        membership.carry(shares, world);
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
        membership.releaseLine(world);
        invalidate();
        membership.invalidateAround(world);
    }

    // Catches every removal, so a break, an explosion or a command leaves the run scanning again.
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        // Through the holder, so every tile of the run holds its own share before the run is cut:
        // the tiles past this one keep their items and run dry rather than losing them (#383).
        var carried = membership.takeCarried(world);
        // A tile of another tier put in its place, as a stretch's Fast Replace puts one, takes what
        // this one carried as it joins the level (#393, #27).
        if (level != null && !carried.isEmpty() && TierSwap.swaps(state, level.getBlockState(pos))) {
            TierSwap.park(level, pos, BeltTileBlockEntity.class, tile -> tile.carry(carried), () -> carried.forEach(share -> drop(share.payload())));
        } else {
            for (var share : carried) drop(share.payload());
        }
        membership.invalidateAround(world);
    }

    @Override
    public void setLevel(Level level) {
        super.setLevel(level);
        TierSwap.take(this);
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
        var carried = new ArrayList<TransportLine.Share<ItemStack>>();
        for (var child : input.childrenListOrEmpty("carried")) {
            carried.add(new TransportLine.Share<>(child.getDoubleOr("offset", 0),
              child.read("stack", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY)));
        }
        membership.loaded(carried);
    }
}
