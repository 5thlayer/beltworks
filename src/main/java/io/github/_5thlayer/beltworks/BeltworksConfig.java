// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks;

import io.github._5thlayer.beltworks.model.BeltTier;
import io.github._5thlayer.beltworks.model.LoaderEnergy;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.ToLongFunction;

/** The server config, {@code beltworks-server.toml}. */
public final class BeltworksConfig {

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue LOADERS_NEED_POWER = BUILDER
      .comment("Whether loaders and feeders need FE for each item they move, at the figures loaderJoulesPerItem and feederJoulesPerItem set. Off, every loader and feeder moves items for free.")
      .define("loadersNeedPower", LoaderEnergy.Setting.DEFAULT.loadersNeedPower());

    public static final ModConfigSpec.LongValue JOULES_PER_FE = BUILDER
      .comment("How many joules of a loader's Factorio energy cost one FE pays for.")
      .defineInRange("joulesPerFe", LoaderEnergy.Setting.DEFAULT.joulesPerFe(), 1, Long.MAX_VALUE);

    /** What a feeder of each tier pays for an item, in the same joules, when LOADERS_NEED_POWER is on. */
    public static final Map<BeltTier, ModConfigSpec.LongValue> FEEDER_JOULES_PER_ITEM = feederJoulesPerItem();

    /** What a loader of each tier pays for an item, in the same joules, when LOADERS_NEED_POWER is on; 0 is unpowered. */
    public static final Map<BeltTier, ModConfigSpec.LongValue> LOADER_JOULES_PER_ITEM = loaderJoulesPerItem();

    public static final ModConfigSpec SPEC = BUILDER.build();

    private BeltworksConfig() {
    }

    private static Map<BeltTier, ModConfigSpec.LongValue> feederJoulesPerItem() {
        return perTier("feederJoulesPerItem", "What a feeder pays in joules for each item it moves, by tier. A feeder pays only when loadersNeedPower is on, and then at every tier.",
          BeltTier::feederJoulesPerItem, 1);
    }

    private static Map<BeltTier, ModConfigSpec.LongValue> loaderJoulesPerItem() {
        return perTier("loaderJoulesPerItem", "What a loader pays in joules for each item it moves, by tier, when loadersNeedPower is on. 0 runs that tier unpowered, with no energy face.",
          BeltTier::loaderJoulesPerItem, 0);
    }

    private static Map<BeltTier, ModConfigSpec.LongValue> perTier(String section, String comment,
                                                                  ToLongFunction<BeltTier> fallback, long least) {
        var values = new EnumMap<BeltTier, ModConfigSpec.LongValue>(BeltTier.class);
        BUILDER.comment(comment).translation("beltworks.configuration." + section).push(section);
        for (var tier : BeltTier.values()) {
            var name = tier.name().toLowerCase(Locale.ROOT);
            values.put(tier, BUILDER.translation("beltworks.configuration." + section + "." + name)
              .defineInRange(name, fallback.applyAsLong(tier), least, LoaderEnergy.Setting.MAX_JOULES_PER_ITEM));
        }
        BUILDER.pop();
        return values;
    }

    /** The default until the server config loads, as on a client before it has joined a world. */
    public static LoaderEnergy.Setting loaderPower() {
        if (!SPEC.isLoaded()) return LoaderEnergy.Setting.DEFAULT;
        return new LoaderEnergy.Setting(LOADERS_NEED_POWER.get(), JOULES_PER_FE.get(), read(FEEDER_JOULES_PER_ITEM),
          read(LOADER_JOULES_PER_ITEM));
    }

    private static Map<BeltTier, Long> read(Map<BeltTier, ModConfigSpec.LongValue> values) {
        var read = new EnumMap<BeltTier, Long>(BeltTier.class);
        values.forEach((tier, value) -> read.put(tier, value.get()));
        return read;
    }
}
