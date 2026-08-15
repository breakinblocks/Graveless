package com.breakinblocks.graveless.gametest;

import com.breakinblocks.graveless.Graveless;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;

@EventBusSubscriber(modid = Graveless.MOD_ID)
public final class GravelessGameTests {
    private GravelessGameTests() {
    }

    @SubscribeEvent
    public static void register(RegisterGameTestsEvent event) {
        TestRegistrar tests = new TestRegistrar(event);
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
        Graveless.LOGGER.info("Registered Graveless game tests");
    }
}
