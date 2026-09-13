package com.breakinblocks.graveless.integration.accessories;

import com.breakinblocks.graveless.Graveless;
import com.breakinblocks.graveless.capture.InventoryHooks;
import io.wispforest.accessories.api.events.OnDeathCallback;
import net.minecraft.server.level.ServerPlayer;

public final class AccessoriesIntegration {
    private AccessoriesIntegration() {
    }

    public static void init() {
        AccessoriesInventoryHook hook = new AccessoriesInventoryHook();
        InventoryHooks.register(hook);
        OnDeathCallback.EVENT.register((state, entity, capability, source, drops) -> {
            if (state.orElse(true) && entity instanceof ServerPlayer player) {
                hook.captureDrops(player, drops);
            }
            return state;
        });
        Graveless.LOGGER.info("Graveless Accessories integration active");
    }
}
