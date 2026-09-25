// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.compat;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;
import io.github._5thlayer.beltworks.Beltworks;
import io.github._5thlayer.beltworks.blocks.BeltTileBlock;
import io.github._5thlayer.beltworks.blocks.BeltTileBlockEntity;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;

import java.util.LinkedHashMap;

/**
 * A tile's Jade tooltip (PlanetaryFactory #398): its tier's rate, the length and rate of the line
 * through it, and what the tile itself carries. Jade finds it by its annotation, and nothing else
 * names it, so the Mod loads without Jade (ADR 0002).
 *
 * <p>The items come from the server: a line's items are kept by its tiles there, and the client is
 * not sent them (PlanetaryFactory #395).
 */
@WailaPlugin
public class BeltTileJadePlugin implements IWailaPlugin {

    private static final Identifier UID = Beltworks.id("belt_tile");

    private static final String LINE_TILES = "LineTiles";
    private static final String LINE_RATE = "LineRate";
    private static final String HELD = "Held";
    private static final String STACK = "Stack";
    private static final String KINDS = "Kinds";

    private static final IServerDataProvider<BlockAccessor> DATA = new IServerDataProvider<>() {
        @Override
        public void appendServerData(CompoundTag tag, BlockAccessor accessor) {
            if (!(accessor.getBlockEntity() instanceof BeltTileBlockEntity tile)) return;
            var line = tile.line();
            if (line != null) {
                tag.putInt(LINE_TILES, line.tileCount());
                tag.putInt(LINE_RATE, (int) Math.round(line.itemsPerSecond()));
            }
            var kinds = new LinkedHashMap<String, ItemStack>();
            var held = 0;
            for (var stack : tile.heldHere()) {
                held += stack.getCount();
                kinds.merge(stack.getItem().toString(), stack.copy(), (first, next) -> {
                    first.grow(next.getCount());
                    return first;
                });
            }
            tag.putInt(HELD, held);
            tag.putInt(KINDS, kinds.size());
            // A stack that won't encode is left out, and the tooltip counts the items without a name.
            kinds.values().stream().findFirst().flatMap(stack -> ItemStack.CODEC.encodeStart(ops(accessor), stack).result())
              .ifPresent(encoded -> tag.put(STACK, encoded));
        }

        @Override
        public Identifier getUid() {
            return UID;
        }
    };

    private static final IBlockComponentProvider TOOLTIP = new IBlockComponentProvider() {
        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            if (!(accessor.getBlock() instanceof BeltTileBlock belt)) return;
            tooltip.add(Component.translatable("gui.beltworks.belt_tile.tier", belt.tier().number(),
              (int) Math.round(belt.tier().itemsPerSecond())).withStyle(ChatFormatting.GRAY));

            var data = accessor.getServerData();
            if (data.contains(LINE_TILES)) {
                tooltip.add(Component.translatable("gui.beltworks.belt_tile.line", data.getIntOr(LINE_TILES, 0),
                  data.getIntOr(LINE_RATE, 0)).withStyle(ChatFormatting.GRAY));
            }
            var held = data.getIntOr(HELD, 0);
            if (held == 0) {
                tooltip.add(Component.translatable("gui.beltworks.belt_tile.empty").withStyle(ChatFormatting.DARK_GRAY));
                return;
            }
            var encoded = data.get(STACK);
            var first = encoded == null ? ItemStack.EMPTY : ItemStack.CODEC.parse(ops(accessor), encoded).result().orElse(ItemStack.EMPTY);
            // A tile holds eight items at most, so one name and a count says the whole of it.
            if (data.getIntOr(KINDS, 0) == 1 && !first.isEmpty()) {
                tooltip.add(Component.translatable("gui.beltworks.belt_tile.holds_one", held, first.getHoverName()));
            } else {
                tooltip.add(Component.translatable("gui.beltworks.belt_tile.holds", held));
            }
        }

        @Override
        public Identifier getUid() {
            return UID;
        }
    };

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(DATA, BeltTileBlockEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(TOOLTIP, BeltTileBlock.class);
    }

    // 26.1 has no ItemStack save and parse, and Jade's sync is a CompoundTag, so a stack goes
    // through its codec against registry-aware NBT.
    private static RegistryOps<Tag> ops(BlockAccessor accessor) {
        return accessor.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
    }
}
