package com.breakinblocks.graveless.gametest;

import com.breakinblocks.graveless.Graveless;
import com.breakinblocks.graveless.event.GraveMenuHandlers;
import com.breakinblocks.graveless.net.GravelessNetworking.GraveListPayload;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.concurrent.atomic.AtomicInteger;

@EventBusSubscriber(modid = Graveless.MOD_ID)
public final class HarnessTests {
    private static final AtomicInteger PLAYER_TICKS = new AtomicInteger();

    private HarnessTests() {
    }

    @SubscribeEvent
    public static void countPlayerTicks(PlayerTickEvent.Post event) {
        PLAYER_TICKS.incrementAndGet();
    }

    static void register(TestRegistrar tests) {
        tests.add("harness_player_tick_events_fire", helper -> {
            TestPlayer player = TestPlayer.join(helper);
            int eventsBefore = PLAYER_TICKS.get();
            int ticksBefore = player.tickCount();
            helper.startSequence()
                    .thenWaitUntil(() -> Check.isTrue(helper, player.tickCount() >= ticksBefore + 30,
                            "game test players must be ticked by the level"))
                    .thenExecute(() -> Check.isTrue(helper, PLAYER_TICKS.get() > eventsBefore,
                            "PlayerTickEvent never fired, so every tick-driven test would pass vacuously"))
                    .thenSucceed();
        });
        tests.add("harness_captures_outbound_payloads", helper -> {
            TestPlayer player = TestPlayer.join(helper);
            player.clearOutbound();
            Check.equal(helper, 0, player.outbound(GraveListPayload.class).size(),
                    "outbound payloads before opening the browser");
            GraveMenuHandlers.openFor(player.player());
            Check.equal(helper, 1, player.outbound(GraveListPayload.class).size(),
                    "the grave browser payload must reach the test channel");
            helper.succeed();
        });
    }
}
