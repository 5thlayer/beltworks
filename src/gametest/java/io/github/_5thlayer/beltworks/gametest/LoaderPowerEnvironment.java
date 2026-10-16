// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.gametest;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github._5thlayer.beltworks.BeltworksConfig;
import io.github._5thlayer.beltworks.model.BeltTier;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.server.level.ServerLevel;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * Sets the server config's loader power for a batch, and any tier's loader joules per item, and puts
 * them back after. A batch runs its tests side by side, so a test that needs one setting is in the
 * batch of that setting rather than changing a value its neighbours read.
 */
record LoaderPowerEnvironment(boolean loadersNeedPower, Map<BeltTier, Long> loaderJoulesPerItem)
  implements TestEnvironmentDefinition<LoaderPowerEnvironment> {

    private static final Codec<BeltTier> TIER = Codec.STRING.xmap(
      name -> BeltTier.valueOf(name.toUpperCase(Locale.ROOT)), tier -> tier.name().toLowerCase(Locale.ROOT));

    static final MapCodec<LoaderPowerEnvironment> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
      Codec.BOOL.fieldOf("loaders_need_power").forGetter(LoaderPowerEnvironment::loadersNeedPower),
      Codec.unboundedMap(TIER, Codec.LONG).optionalFieldOf("loader_joules_per_item", Map.of())
        .forGetter(LoaderPowerEnvironment::loaderJoulesPerItem)
    ).apply(instance, LoaderPowerEnvironment::new));

    LoaderPowerEnvironment(boolean loadersNeedPower) {
        this(loadersNeedPower, Map.of());
    }

    /** Returns what it replaced, for teardown to put back. */
    @Override
    public LoaderPowerEnvironment setup(ServerLevel level) {
        var joules = new EnumMap<BeltTier, Long>(BeltTier.class);
        loaderJoulesPerItem.keySet().forEach(tier -> joules.put(tier, BeltworksConfig.LOADER_JOULES_PER_ITEM.get(tier).get()));
        var before = new LoaderPowerEnvironment(BeltworksConfig.LOADERS_NEED_POWER.get(), joules);
        apply(this);
        return before;
    }

    @Override
    public void teardown(ServerLevel level, LoaderPowerEnvironment before) {
        apply(before);
    }

    private static void apply(LoaderPowerEnvironment setting) {
        BeltworksConfig.LOADERS_NEED_POWER.set(setting.loadersNeedPower);
        setting.loaderJoulesPerItem.forEach((tier, joules) -> BeltworksConfig.LOADER_JOULES_PER_ITEM.get(tier).set(joules));
    }

    @Override
    public MapCodec<LoaderPowerEnvironment> codec() {
        return CODEC;
    }
}
