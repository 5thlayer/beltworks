// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;

/** A player of its own, keeping the translation key of every message it is sent. */
final class ListeningPlayer extends FakePlayer {
    final List<String> heard = new ArrayList<>();

    ListeningPlayer(GameTestHelper helper) {
        super(helper.getLevel(), new GameProfile(UUID.randomUUID(), "beltworks_belt_listener"));
    }

    /** A survival player standing on the block at {@code at}, within reach of the blocks near it. */
    ListeningPlayer(GameTestHelper helper, BlockPos at) {
        this(helper);
        setGameMode(GameType.SURVIVAL);
        Vec3 feet = Vec3.atBottomCenterOf(helper.absolutePos(at));
        setPos(feet.x, feet.y, feet.z);
    }

    @Override
    public void sendSystemMessage(Component message, boolean actionBar) {
        if (message.getContents() instanceof TranslatableContents translatable) heard.add(translatable.getKey());
    }
}
