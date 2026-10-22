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
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLConstructModEvent;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import io.github._5thlayer.beltworks.Beltworks;
import io.github._5thlayer.beltworks.BlockUpdatesSent;
import io.github._5thlayer.beltworks.FeederArmsSent;
import io.github._5thlayer.beltworks.model.BeltTier;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The Mod's game tests, run by the {@code gameTestServer} Gradle run. Each test stands on the
 * {@code gametest/platform} structure, a 23x7x7 stone floor with room for two lines side by side, a
 * splitter's belts and a three-block climb, or on its {@code gametest/long_platform}, 66x3x3, for a
 * 64-tile line, and places what it needs itself, so the setup is in the diff. Both are committed .nbt
 * files of the Mod's own: the libworks plugin's {@code buildGametestStructures} writes only its 9x5x9
 * default platform, and refuses to replace this one. It lives in the {@code gametest} source set, which
 * only the Mod's own runs load, and hooks itself in, so the {@code @Mod} class names no game-test code
 * and nothing gates it (libworks ADR 0011).
 */
@EventBusSubscriber(modid = Beltworks.MOD_ID)
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

    @SubscribeEvent
    static void construct(FMLConstructModEvent event) {
        register(event.getContainer().getEventBus());
    }

    public static void register(IEventBus modBus) {
        TEST_TYPES.register(modBus);
        ENVIRONMENT_TYPES.register(modBus);
        // Posted only when game tests are enabled, which a production game never is.
        modBus.addListener(BeltGameTests::registerTests);
        // The proving set's stand-in for a fickle target answers at a lodestone.
        modBus.addListener(FickleInventory::registerCapabilities);
        BlockUpdatesSent.listen(BlockUpdateWatch::saw);
        FeederArmsSent.listen(FeederArmWatch::saw);
    }

    private static void registerTests(RegisterGameTestsEvent event) {
        CodeGameTest.clear();
        FickleInventory.clear();
        // Registered rather than borrowed, since the event hands out no lookup for vanilla's.
        var environment = event.registerEnvironment(Beltworks.id("default"), new TestEnvironmentDefinition.AllOf(List.of()));
        var powered = event.registerEnvironment(Beltworks.id("loaders_need_power"), new LoaderPowerEnvironment(true));
        var unpowered = event.registerEnvironment(Beltworks.id("loaders_need_no_power"), new LoaderPowerEnvironment(false));
        // Tier 1 paying what tier 2 does by default, and tier 2 nothing: the defaults the other way about.
        var repriced = event.registerEnvironment(Beltworks.id("loaders_repriced"), new LoaderPowerEnvironment(true,
          Map.of(BeltTier.BELT, BeltTier.IMPROVED.loaderJoulesPerItem(), BeltTier.IMPROVED, 0L),
          Map.of(BeltTier.BELT, BeltTier.IMPROVED.feederJoulesPerItem(), BeltTier.IMPROVED, 0L)));
        var tests = new Registrar(event, environment, powered, unpowered, repriced);
        LineSmokeTest.register(tests);
        BeltTileTests.register(tests);
        BeltCornerTests.register(tests);
        PlaysAloneTests.register(tests);
        BeltSideLoadTests.register(tests);
        BeltSlopeEdgeTests.register(tests);
        BeltWedgeTests.register(tests);
        BeltTileSyncTests.register(tests);
        BeltTileHandTests.register(tests);
        BeltDropTests.register(tests);
        BeltFilterTests.register(tests);
        LoaderMouthTests.register(tests);
        ProvingSetTests.register(tests);
        SplitterTileTests.register(tests);
        SplitterPlanTests.register(tests);
        PlanTranslationTests.register(tests);
        StretchTests.register(tests);
        DismantleTests.register(tests);
        SupportTests.register(tests);
        RotateTests.register(tests);
        FeederTests.register(tests);
        FeederHandTests.register(tests);
        TierSwapTests.register(tests);
        FastReplaceTests.register(tests);
        CreativeTabTests.register(tests);
        RecipeTests.register(tests);
        GalleryTests.register(tests);
    }

    /**
     * What a test class is handed: a name, a tick budget and a body. A test in the default environment
     * holds whatever the config says about loader power, and one that doesn't asks for a setting.
     */
    record Registrar(RegisterGameTestsEvent event, Holder<TestEnvironmentDefinition<?>> environment,
                     Holder<TestEnvironmentDefinition<?>> powered, Holder<TestEnvironmentDefinition<?>> unpowered,
                     Holder<TestEnvironmentDefinition<?>> repriced) {

        Registrar withLoaderPower(boolean loadersNeedPower) {
            return new Registrar(event, loadersNeedPower ? powered : unpowered, powered, unpowered, repriced);
        }

        /**
         * Loaders need power, a tier-1 loader and feeder pay what tier-2 ones do by default, and tier-2
         * ones pay nothing.
         */
        Registrar withLoadersRepriced() {
            return new Registrar(event, repriced, powered, unpowered, repriced);
        }

        void test(String name, int maxTicks, Consumer<GameTestHelper> body) {
            test(name, maxTicks, PLATFORM, body);
        }

        void test(String name, int maxTicks, Identifier structure, Consumer<GameTestHelper> body) {
            register(name, maxTicks, structure, body, environment);
        }

        /**
         * A test in a batch of its own, for one that reloads the server's data, as turning the Gallery pack
         * on does, under every test running beside it (libworks ADR 0010).
         */
        void testOnItsOwn(String name, int maxTicks, Consumer<GameTestHelper> body) {
            register(name, maxTicks, PLATFORM, body, event.registerEnvironment(Beltworks.id("on_its_own/" + name), new TestEnvironmentDefinition.AllOf(List.of())));
        }

        private void register(String name, int maxTicks, Identifier structure, Consumer<GameTestHelper> body,
                              Holder<TestEnvironmentDefinition<?>> batch) {
            var id = Beltworks.id(name);
            CodeGameTest.define(id, body);
            event.registerTest(id, new CodeGameTest(id, new TestData<>(batch, structure, maxTicks, 0, true, Rotation.NONE)));
        }
    }
}
