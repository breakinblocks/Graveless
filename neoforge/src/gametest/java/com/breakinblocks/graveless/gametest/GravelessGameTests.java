package com.breakinblocks.graveless.gametest;

import com.breakinblocks.graveless.Graveless;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.TestFunction;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;

import java.util.Collection;

@EventBusSubscriber(modid = Graveless.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class GravelessGameTests {

    public GravelessGameTests() {
    }

    @SubscribeEvent
    public static void register(RegisterGameTestsEvent event) {
        event.register(GravelessGameTests.class);
        Graveless.LOGGER.info("Registered Graveless game tests");
    }

    @GameTestGenerator
    public static Collection<TestFunction> generateTests() {
        TestRegistrar tests = new TestRegistrar();
        HarnessTests.register(tests);
        DataTests.register(tests);
        CaptureTests.register(tests);
        RestoreTests.register(tests);
        GhostTests.register(tests);
        CompassTests.register(tests);
        WardTests.register(tests);
        BackupTests.register(tests);
        AdminTests.register(tests);
        ExtractTests.register(tests);
        CommandTests.register(tests);
        AccessoriesTests.register(tests);
        return tests.functions();
    }
}
