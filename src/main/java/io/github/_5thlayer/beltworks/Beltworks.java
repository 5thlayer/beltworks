// SPDX-FileCopyrightText: Rearth
// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: CC-BY-4.0 AND MIT

package io.github._5thlayer.beltworks;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Predicate;

import io.github._5thlayer.groundworks.Dismantles;
import io.github._5thlayer.groundworks.Placements;
import io.github._5thlayer.groundworks.Rotate;
import io.github._5thlayer.groundworks.Stretches;

import io.github._5thlayer.beltworks.api.item.ItemApi;
import io.github._5thlayer.beltworks.blocks.BeltFamily;
import io.github._5thlayer.beltworks.blocks.BeltTileBlockEntity;
import io.github._5thlayer.beltworks.blocks.SplitterBlock;
import io.github._5thlayer.beltworks.collision.BeltCollisionRegistry;
import io.github._5thlayer.beltworks.gametest.BeltGameTests;
import io.github._5thlayer.beltworks.items.BeltLegs;
import io.github._5thlayer.beltworks.neoforge.BeltChangesPayload;
import io.github._5thlayer.beltworks.neoforge.BeltHandPayload;
import io.github._5thlayer.beltworks.neoforge.SplitterSettingsPayload;
import io.github._5thlayer.beltworks.neoforge.FeederReachPayload;
import io.github._5thlayer.beltworks.neoforge.FeederSuckedPayload;
import io.github._5thlayer.beltworks.neoforge.BeltLinePayload;
import io.github._5thlayer.beltworks.neoforge.LoaderEnergyHandler;
import io.github._5thlayer.beltworks.neoforge.NeoforgeItemApiImpl;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.ChunkWatchEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(Beltworks.MOD_ID)
public final class Beltworks {
    public static final String MOD_ID = "beltworks";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    // One face per loader: two journals on one buffer in one transaction would revert out of order.
    private static final Map<BlockEntity, LoaderEnergyHandler> ENERGY_FACES =
      Collections.synchronizedMap(new WeakHashMap<>());

    /** The Mod's own blocks: what it opts in to previews and states Rotate in Place turns. */
    public static final Predicate<Block> OURS = block -> BuiltInRegistries.BLOCK.getKey(block).getNamespace().equals(MOD_ID);

    public Beltworks(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, BeltworksConfig.SPEC);
        ItemApi.BLOCK = new NeoforgeItemApiImpl();
        // Every block of the Mod's that places as vanilla does, such as a loader, gets a preview (ADR 0010).
        Placements.optIn(OURS);
        // Rotate in Place turns the Mod's own blocks and never another mod's, each answering for itself.
        Rotate.turnsInPlace(OURS);
        // Groundworks runs the Dismantle, and the Mod supplies the belt's part (ADR 0011).
        Dismantles.register(BeltFamily.INSTANCE);
        // Groundworks runs the Stretch too, and the Mod builds a belt's legs (ADR 0011).
        Stretches.register(new BeltLegs());
        modBus.addListener(Beltworks::registerCapabilities);
        modBus.addListener(Beltworks::registerPayloads);
        NeoForge.EVENT_BUS.addListener(Beltworks::sendLinesOfChunk);
        NeoForge.EVENT_BUS.addListener(Beltworks::releaseLinesOfChunk);

        BlockContent.BLOCKS.register(modBus);
        ItemContent.ITEMS.register(modBus);
        DataComponentsContent.TYPES.register(modBus);
        BlockEntitiesContent.TYPES.register(modBus);
        ItemGroupContent.GROUPS.register(modBus);
        NeoForge.EVENT_BUS.addListener(LevelTickEvent.Pre.class, event -> {
            if (!event.getLevel().isClientSide()) BeltCollisionRegistry.moveServerEntities(event.getLevel());
        });

        // Ahead of Groundworks' Dismantle, which passes a tool with nothing stored on a splitter.
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, PlayerInteractEvent.RightClickBlock.class, event -> {
            if (event.getHand() != InteractionHand.MAIN_HAND) return;
            var result = SplitterBlock.useOn(event.getEntity(), event.getItemStack(), event.getPos());
            if (result == InteractionResult.PASS) return;
            event.setCanceled(true);
            event.setCancellationResult(result);
        });

        BeltGameTests.register(modBus);
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("4")
          .playToServer(BeltHandPayload.TYPE, BeltHandPayload.STREAM_CODEC, BeltHandPayload::handle)
          .playToServer(FeederReachPayload.TYPE, FeederReachPayload.STREAM_CODEC, FeederReachPayload::handle)
          .playToServer(SplitterSettingsPayload.TYPE, SplitterSettingsPayload.STREAM_CODEC, SplitterSettingsPayload::handle)
          .playToClient(BeltChangesPayload.TYPE, BeltChangesPayload.STREAM_CODEC, BeltChangesPayload::handle)
          .playToClient(BeltLinePayload.TYPE, BeltLinePayload.STREAM_CODEC, BeltLinePayload::handle)
          .playToClient(FeederSuckedPayload.TYPE, FeederSuckedPayload.STREAM_CODEC, FeederSuckedPayload::handle);
    }

    // Posted after the chunk is saved and before any of its block entities is removed (#395).
    private static void releaseLinesOfChunk(ChunkEvent.Unload event) {
        if (event.getLevel().isClientSide() || !(event.getChunk() instanceof LevelChunk chunk)) return;
        BeltTileBlockEntity.chunkUnloading(List.copyOf(chunk.getBlockEntities().values()));
    }

    // A line is otherwise sent whole only when it is built, so a player who comes into sight of
    // one later is sent it here, after the chunk that shows its tiles (#395).
    private static void sendLinesOfChunk(ChunkWatchEvent.Sent event) {
        var holders = new LinkedHashSet<BeltTileBlockEntity>();
        for (var blockEntity : event.getChunk().getBlockEntities().values()) {
            if (blockEntity instanceof BeltTileBlockEntity tile && tile.holder() != null) holders.add(tile.holder());
        }
        for (var holder : holders) {
            var update = holder.lineUpdate();
            if (update != null) BeltSync.sendLine(event.getPlayer(), update);
        }
    }

    // A tier-1 loader has no face, so a pole does not count it as a machine.
    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.Energy.BLOCK, BlockEntitiesContent.BELT_END.get(),
          (loader, side) -> loader.getEnergy().powered()
            ? ENERGY_FACES.computeIfAbsent(loader, owner -> new LoaderEnergyHandler(owner, loader.getEnergy())) : null);
        // A feeder draws power only when loaders need it, and then at every tier (ADR 0014).
        event.registerBlockEntity(Capabilities.Energy.BLOCK, BlockEntitiesContent.FEEDER.get(),
          (feeder, side) -> feeder.getEnergy().powered()
            ? ENERGY_FACES.computeIfAbsent(feeder, owner -> new LoaderEnergyHandler(owner, feeder.getEnergy())) : null);
    }
}
