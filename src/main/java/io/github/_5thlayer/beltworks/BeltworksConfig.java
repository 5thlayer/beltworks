// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks;

import io.github._5thlayer.beltworks.model.BeltTier;
import io.github._5thlayer.beltworks.model.LoaderEnergy;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/** The server config, {@code beltworks-server.toml}. */
public final class BeltworksConfig {

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue LOADERS_NEED_POWER = BUILDER
      .comment("Whether loaders of tiers 2 to 4 need FE for each item they move. Off, every loader moves items as tier 1 does.")
      .define("loadersNeedPower", LoaderEnergy.Setting.DEFAULT.loadersNeedPower());

    public static final ModConfigSpec.LongValue JOULES_PER_FE = BUILDER
      .comment("How many joules of a loader's Factorio energy cost one FE pays for.")
      .defineInRange("joulesPerFe", LoaderEnergy.Setting.DEFAULT.joulesPerFe(), 1, Long.MAX_VALUE);

    /** What a feeder of each tier pays for an item, in the same joules, whatever LOADERS_NEED_POWER says. */
    public static final Map<BeltTier, ModConfigSpec.LongValue> FEEDER_JOULES_PER_ITEM = feederJoulesPerItem();

    public static final ModConfigSpec SPEC = BUILDER.build();

    private BeltworksConfig() {
    }

    private static Map<BeltTier, ModConfigSpec.LongValue> feederJoulesPerItem() {
        var values = new EnumMap<BeltTier, ModConfigSpec.LongValue>(BeltTier.class);
        BUILDER.comment("What a feeder pays in joules for each item it moves, by tier. A feeder always pays, whatever loadersNeedPower says.")
          .translation("beltworks.configuration.feederJoulesPerItem")
          .push("feederJoulesPerItem");
        for (var tier : BeltTier.values()) {
            var name = tier.name().toLowerCase(Locale.ROOT);
            values.put(tier, BUILDER.translation("beltworks.configuration.feederJoulesPerItem." + name)
              .defineInRange(name, tier.feederJoulesPerItem(), 1, Long.MAX_VALUE));
        }
        BUILDER.pop();
        return values;
    }

    /** The default until the server config loads, as on a client before it has joined a world. */
    public static LoaderEnergy.Setting loaderPower() {
        if (!SPEC.isLoaded()) return LoaderEnergy.Setting.DEFAULT;
        var feeders = new EnumMap<BeltTier, Long>(BeltTier.class);
        FEEDER_JOULES_PER_ITEM.forEach((tier, joules) -> feeders.put(tier, joules.get()));
        return new LoaderEnergy.Setting(LOADERS_NEED_POWER.get(), JOULES_PER_FE.get(), feeders);
    }
}
