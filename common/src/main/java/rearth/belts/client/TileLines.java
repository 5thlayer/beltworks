package rearth.belts.client;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import rearth.belts.TileLineUpdate;
import rearth.belts.model.TransportLine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The client's copies of the lines of tiles it has been sent, by head, each advancing itself
 * between the server's changes (PlanetaryFactory #395). Kept apart from the tiles' block entities
 * because a player may see a line's tiles without its head.
 *
 * <p>Touched only on the client's main thread, and names no client-only class, so the payload's
 * handler can reach it on either side.
 */
public final class TileLines {

    private static @Nullable Level level;
    private static final Map<BlockPos, Copy> BY_HEAD = new HashMap<>();
    private static final Map<BlockPos, Place> BY_TILE = new HashMap<>();

    private TileLines() {
    }

    /** A line's copy, and the tiles it runs over, head first. */
    public static final class Copy {
        private final TransportLine<ItemStack> line;
        private final List<BlockPos> tiles;
        // One tile in each chunk the line crosses, since a player can stand in its middle.
        private final List<BlockPos> inChunks;
        // Every tile of a line draws in the same frame, so the line is laid out once a frame.
        private int version;
        private int drawnVersion = -1;
        private float drawnPartial;
        private List<List<TransportLine.Drawn<ItemStack>>> drawn = List.of();

        private Copy(TransportLine<ItemStack> line, List<BlockPos> tiles) {
            this.line = line;
            this.tiles = tiles;
            var crossed = new LinkedHashMap<Long, BlockPos>();
            for (var tile : tiles) crossed.putIfAbsent(ChunkPos.pack(tile), tile);
            this.inChunks = List.copyOf(crossed.values());
        }

        private List<TransportLine.Drawn<ItemStack>> drawnOn(int tile, float partialTicks) {
            if (drawnVersion != version || drawnPartial != partialTicks) {
                var byTile = new ArrayList<List<TransportLine.Drawn<ItemStack>>>(tiles.size());
                for (var at = 0; at < tiles.size(); at++) byTile.add(new ArrayList<>());
                for (var item : line.drawn(partialTicks)) byTile.get(item.tile()).add(item);
                drawn = byTile;
                drawnVersion = version;
                drawnPartial = partialTicks;
            }
            return drawn.get(tile);
        }
    }

    /** A tile's place on the copy of its line. */
    public record Place(Copy copy, int tile) {

        /** The items drawn on this tile this far into the next tick. */
        public List<TransportLine.Drawn<ItemStack>> drawn(float partialTicks) {
            return copy.drawnOn(tile, partialTicks);
        }
    }

    public static void accept(Level at, TileLineUpdate update) {
        if (at != level) {
            BY_HEAD.clear();
            BY_TILE.clear();
            level = at;
        }
        if (!update.reset()) {
            var copy = BY_HEAD.get(update.head());
            // A change to a line whose whole this client was never sent waits for the next.
            if (copy != null) {
                copy.line.contents().apply(update.changes());
                copy.version++;
            }
            return;
        }

        forget(update.head());
        if (update.isGone()) return;
        var line = new TransportLine<ItemStack>(update.tiers(), update.ring());
        line.contents().reset(update.changes().added());
        var tiles = new ArrayList<BlockPos>(update.tiers().size());
        var next = update.head();
        for (var travel : update.travels()) {
            tiles.add(next);
            next = next.relative(travel);
        }
        var copy = new Copy(line, List.copyOf(tiles));
        BY_HEAD.put(update.head(), copy);
        // The server sends a line's end before any of its tiles joins another, so a tile is on one copy.
        for (var tile = 0; tile < tiles.size(); tile++) BY_TILE.put(tiles.get(tile), new Place(copy, tile));
    }

    private static void forget(BlockPos head) {
        var copy = BY_HEAD.remove(head);
        if (copy == null) return;
        for (var tile : copy.tiles) {
            var place = BY_TILE.get(tile);
            if (place != null && place.copy == copy) BY_TILE.remove(tile);
        }
    }

    /** Moves every copy on one tick, and drops those none of whose chunks this client still has. */
    public static void tick(Level at) {
        if (at != level) return;
        for (var copy : List.copyOf(BY_HEAD.values())) {
            if (copy.inChunks.stream().noneMatch(at::isLoaded)) {
                forget(copy.tiles.getFirst());
                continue;
            }
            copy.line.advance();
            copy.version++;
        }
    }

    public static @Nullable Place at(Level at, BlockPos tile) {
        if (at != level) return null;
        return BY_TILE.get(tile);
    }
}
