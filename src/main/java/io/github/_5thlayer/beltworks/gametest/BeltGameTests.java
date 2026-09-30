// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.beltworks.gametest;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInstance;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import io.github._5thlayer.beltworks.Beltworks;

import java.util.List;
import java.util.function.Consumer;

/**
 * The Mod's game tests, run by the {@code gameTestServer} Gradle run. Each test stands on the
 * {@code gametest/platform} structure, a stone floor that {@code scripts/build-gametest-structures.py}
 * writes, or on its {@code gametest/long_platform} for a 64-tile line, and places what it needs
 * itself, so the setup is in the diff.
 */
public final class BeltGameTests {

    private static final Identifier PLATFORM = Beltworks.id("gametest/platform");

    /** 66 blocks long: a 64-tile line with a chest at each end. */
    static final Identifier LONG_PLATFORM = Beltworks.id("gametest/long_platform");

    private static final DeferredRegister<MapCodec<? extends GameTestInstance>> TEST_TYPES =
      DeferredRegister.create(Registries.TEST_INSTANCE_TYPE, Beltworks.MOD_ID);

    private static final DeferredRegister<MapCodec<? extends TestEnvironmentDefinition<?>>> ENVIRONMENT_TYPES =
      DeferredRegister.create(Registries.TEST_ENVIRONMENT_DEFINITION_TYPE, Beltworks.MOD_ID);

    static {
        TEST_TYPES.register("code", () -> CodeGameTest.CODEC);
        ENVIRONMENT_TYPES.register("loader_power", () -> LoaderPowerEnvironment.CODEC);
    }

    private BeltGameTests() {
    }

    public static void register(IEventBus modBus) {
        TEST_TYPES.register(modBus);
        ENVIRONMENT_TYPES.register(modBus);
        // Posted only when game tests are enabled, so a production server never registers the tests.
        modBus.addListener(BeltGameTests::registerTests);
    }

    private static void registerTests(RegisterGameTestsEvent event) {
        CodeGameTest.clear();
        // Registered rather than borrowed, since the event hands out no lookup for vanilla's.
        var environment = event.registerEnvironment(Beltworks.id("default"), new TestEnvironmentDefinition.AllOf(List.of()));
        var powered = event.registerEnvironment(Beltworks.id("loaders_need_power"), new LoaderPowerEnvironment(true));
        var unpowered = event.registerEnvironment(Beltworks.id("loaders_need_no_power"), new LoaderPowerEnvironment(false));
        var tests = new Registrar(event, environment, powered, unpowered);
        LineSmokeTest.register(tests);
        BeltTileTests.register(tests);
        BeltCornerTests.register(tests);
        BeltSideLoadTests.register(tests);
        BeltSlopeEdgeTests.register(tests);
        BeltWedgeTests.register(tests);
        BeltTileSyncTests.register(tests);
        BeltTileHandTests.register(tests);
        BeltDropTests.register(tests);
        BeltFilterTests.register(tests);
        LoaderMouthTests.register(tests);
        SplitterTileTests.register(tests);
        SplitterPlanTests.register(tests);
        PlanTranslationTests.register(tests);
        StretchTests.register(tests);
        DismantleTests.register(tests);
        SupportTests.register(tests);
        RotateTests.register(tests);
        FeederTests.register(tests);
        RecipeTests.register(tests);
    }

    /**
     * What a test class is handed: a name, a tick budget and a body. A test in the default environment
     * holds whatever the config says about loader power, and one that doesn't asks for a setting.
     */
    record Registrar(RegisterGameTestsEvent event, Holder<TestEnvironmentDefinition<?>> environment,
                     Holder<TestEnvironmentDefinition<?>> powered, Holder<TestEnvironmentDefinition<?>> unpowered) {

        Registrar withLoaderPower(boolean loadersNeedPower) {
            return new Registrar(event, loadersNeedPower ? powered : unpowered, powered, unpowered);
        }

        void test(String name, int maxTicks, Consumer<GameTestHelper> body) {
            test(name, maxTicks, PLATFORM, body);
        }

        void test(String name, int maxTicks, Identifier structure, Consumer<GameTestHelper> body) {
            var id = Beltworks.id(name);
            CodeGameTest.define(id, body);
            event.registerTest(id, new CodeGameTest(id, new TestData<>(environment, structure, maxTicks, 0, true, Rotation.NONE)));
        }
    }
}
