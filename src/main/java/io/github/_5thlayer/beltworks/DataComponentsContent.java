// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github._5thlayer.beltworks.model.FeederArms;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class DataComponentsContent {

    public static final DeferredRegister<DataComponentType<?>> TYPES = DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, Beltworks.MOD_ID);

    /** The reach a held feeder's next placement takes (ADR 0013). A held feeder's tail never turns, so only reach is kept. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<FeederArms>> HELD_REACH =
            TYPES.register("held_reach", DataComponentsContent::heldReach);

    private static DataComponentType<FeederArms> heldReach() {
        var reach = Codec.intRange(FeederArms.MIN_REACH, FeederArms.MAX_REACH);
        return DataComponentType.<FeederArms>builder()
                .persistent(RecordCodecBuilder.create(instance -> instance.group(
                        reach.fieldOf("head").forGetter(FeederArms::headReach),
                        reach.fieldOf("tail").forGetter(FeederArms::tailReach)
                ).apply(instance, DataComponentsContent::straight)))
                .networkSynchronized(StreamCodec.composite(
                        ByteBufCodecs.VAR_INT, FeederArms::headReach,
                        ByteBufCodecs.VAR_INT, FeederArms::tailReach,
                        DataComponentsContent::straight))
                .build();
    }

    private static FeederArms straight(int head, int tail) {
        return new FeederArms(head, tail, FeederArms.Turn.STRAIGHT);
    }
}
