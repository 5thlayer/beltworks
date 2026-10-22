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
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * Sets the server config's loader power for a batch, and any tier's loader and feeder joules per
 * item, and puts them back after. A batch runs its tests side by side, so a test that needs one setting is in the
 * batch of that setting rather than changing a value its neighbours read.
 */
record LoaderPowerEnvironment(boolean loadersNeedPower, Map<BeltTier, Long> loaderJoulesPerItem,
                              Map<BeltTier, Long> feederJoulesPerItem)
  implements TestEnvironmentDefinition<LoaderPowerEnvironment> {

    private static final Codec<BeltTier> TIER = Codec.STRING.xmap(
      name -> BeltTier.valueOf(name.toUpperCase(Locale.ROOT)), tier -> tier.name().toLowerCase(Locale.ROOT));

    static final MapCodec<LoaderPowerEnvironment> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
      Codec.BOOL.fieldOf("loaders_need_power").forGetter(LoaderPowerEnvironment::loadersNeedPower),
      Codec.unboundedMap(TIER, Codec.LONG).optionalFieldOf("loader_joules_per_item", Map.of())
        .forGetter(LoaderPowerEnvironment::loaderJoulesPerItem),
      Codec.unboundedMap(TIER, Codec.LONG).optionalFieldOf("feeder_joules_per_item", Map.of())
        .forGetter(LoaderPowerEnvironment::feederJoulesPerItem)
    ).apply(instance, LoaderPowerEnvironment::new));

    LoaderPowerEnvironment(boolean loadersNeedPower) {
        this(loadersNeedPower, Map.of(), Map.of());
    }

    /** Returns what it replaced, for teardown to put back. */
    @Override
    public LoaderPowerEnvironment setup(ServerLevel level) {
        var before = new LoaderPowerEnvironment(BeltworksConfig.LOADERS_NEED_POWER.get(),
          read(loaderJoulesPerItem, BeltworksConfig.LOADER_JOULES_PER_ITEM), read(feederJoulesPerItem, BeltworksConfig.FEEDER_JOULES_PER_ITEM));
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
        setting.feederJoulesPerItem.forEach((tier, joules) -> BeltworksConfig.FEEDER_JOULES_PER_ITEM.get(tier).set(joules));
    }

    // The config's figures for the tiers a setting changes.
    private static Map<BeltTier, Long> read(Map<BeltTier, Long> changed, Map<BeltTier, ModConfigSpec.LongValue> config) {
        var joules = new EnumMap<BeltTier, Long>(BeltTier.class);
        changed.keySet().forEach(tier -> joules.put(tier, config.get(tier).get()));
        return joules;
    }

    @Override
    public MapCodec<LoaderPowerEnvironment> codec() {
        return CODEC;
    }
}
