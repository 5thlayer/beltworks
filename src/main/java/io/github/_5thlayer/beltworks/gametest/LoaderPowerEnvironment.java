// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.gametest;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import io.github._5thlayer.beltworks.BeltworksConfig;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.server.level.ServerLevel;

/**
 * Sets the server config's loader power for a batch and puts it back after. A batch runs its tests
 * side by side, so a test that needs one setting is in the batch of that setting rather than
 * changing a value its neighbours read.
 */
record LoaderPowerEnvironment(boolean loadersNeedPower) implements TestEnvironmentDefinition<Boolean> {

    static final MapCodec<LoaderPowerEnvironment> CODEC =
      Codec.BOOL.fieldOf("loaders_need_power").xmap(LoaderPowerEnvironment::new, LoaderPowerEnvironment::loadersNeedPower);

    @Override
    public Boolean setup(ServerLevel level) {
        boolean before = BeltworksConfig.LOADERS_NEED_POWER.get();
        BeltworksConfig.LOADERS_NEED_POWER.set(loadersNeedPower);
        return before;
    }

    @Override
    public void teardown(ServerLevel level, Boolean before) {
        BeltworksConfig.LOADERS_NEED_POWER.set(before);
    }

    @Override
    public MapCodec<LoaderPowerEnvironment> codec() {
        return CODEC;
    }
}
