// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.mixin;

import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** An item entity's pickup delay, which vanilla only answers as whether it has one. */
@Mixin(ItemEntity.class)
public interface ItemEntityAccessor {

    @Accessor("pickupDelay")
    int beltworks$pickupDelay();
}
