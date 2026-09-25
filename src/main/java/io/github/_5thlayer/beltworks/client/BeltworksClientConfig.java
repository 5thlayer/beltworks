// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.client;

import io.github._5thlayer.beltworks.model.Support;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The client config, {@code beltworks-client.toml}: each player's own view of the Mod. It is read
 * every frame, so an edit, in game or to the file, shows at once.
 */
public final class BeltworksClientConfig {

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue SHOW_SUPPORTS = BUILDER
      .comment("Whether a raised line shows the supports that hold it up. They are only drawn, and never limit a line.")
      .define("showSupports", Support.Setting.DEFAULT.shown());

    public static final ModConfigSpec.IntValue SUPPORT_SPACING = BUILDER
      .comment("How many blocks apart a straight floating line, or a slope mid-line, shows its supports.")
      .defineInRange("supportSpacing", Support.Setting.DEFAULT.spacing(), 1, 256);

    public static final ModConfigSpec.IntValue SUPPORT_REACH = BUILDER
      .comment("How many blocks a support's leg reaches down before it runs on out of sight.")
      // No further than the overworld is tall.
      .defineInRange("supportReach", Support.Setting.DEFAULT.reach(), 1, 384);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private BeltworksClientConfig() {
    }

    /** The default until the client config loads. */
    public static Support.Setting supports() {
        if (!SPEC.isLoaded()) return Support.Setting.DEFAULT;
        return new Support.Setting(SHOW_SUPPORTS.get(), SUPPORT_SPACING.get(), SUPPORT_REACH.get());
    }
}
