// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.gametest;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github._5thlayer.beltworks.Beltworks;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInstance;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * A game test whose body is a method in the Mod. 26.1's test functions are registered during
 * bootstrap, before any mod loads, so a mod registers test instances of its own type instead.
 * The codec, which the datapack registry requires, stores the body's id and looks it up again.
 */
final class CodeGameTest extends GameTestInstance {

    private static final Map<Identifier, Consumer<GameTestHelper>> BODIES = new HashMap<>();

    static final MapCodec<CodeGameTest> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
      Identifier.CODEC.fieldOf("body").forGetter(test -> test.id),
      TestData.CODEC.forGetter(CodeGameTest::info)
    ).apply(instance, CodeGameTest::new));

    private final Identifier id;
    private final Consumer<GameTestHelper> body;

    CodeGameTest(Identifier id, TestData<Holder<TestEnvironmentDefinition<?>>> info) {
        super(info);
        this.id = id;
        this.body = BODIES.get(id);
        // A test with no body would pass without testing anything.
        if (body == null) throw new IllegalStateException("no game test body for " + id);
    }

    /** Forgets the bodies defined so far: the tests are registered again for each world a client loads. */
    static void clear() {
        BODIES.clear();
    }

    static void define(Identifier id, Consumer<GameTestHelper> body) {
        if (BODIES.putIfAbsent(id, body) != null) throw new IllegalStateException("two game test bodies for " + id);
    }

    @Override
    public void run(GameTestHelper helper) {
        body.accept(helper);
    }

    @Override
    public MapCodec<? extends GameTestInstance> codec() {
        return CODEC;
    }

    @Override
    protected MutableComponent typeDescription() {
        return Component.literal(Beltworks.MOD_ID + " code");
    }
}
