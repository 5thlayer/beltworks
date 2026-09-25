// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks;

import io.github._5thlayer.beltworks.model.LoaderEnergy;
import net.neoforged.neoforge.common.ModConfigSpec;

/** The server config, {@code beltworks-server.toml}. */
public final class BeltworksConfig {

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue LOADERS_NEED_POWER = BUILDER
      .comment("Whether loaders of tiers 2 to 4 need FE for each item they move. Off, every loader moves items as tier 1 does.")
      .define("loadersNeedPower", LoaderEnergy.Setting.DEFAULT.loadersNeedPower());

    public static final ModConfigSpec.LongValue JOULES_PER_FE = BUILDER
      .comment("How many joules of a loader's Factorio energy cost one FE pays for.")
      .defineInRange("joulesPerFe", LoaderEnergy.Setting.DEFAULT.joulesPerFe(), 1, Long.MAX_VALUE);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private BeltworksConfig() {
    }

    /** The default until the server config loads, as on a client before it has joined a world. */
    public static LoaderEnergy.Setting loaderPower() {
        if (!SPEC.isLoaded()) return LoaderEnergy.Setting.DEFAULT;
        return new LoaderEnergy.Setting(LOADERS_NEED_POWER.get(), JOULES_PER_FE.get());
    }
}
