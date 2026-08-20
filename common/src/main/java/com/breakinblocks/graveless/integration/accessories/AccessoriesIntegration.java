package com.breakinblocks.graveless.integration.accessories;

import com.breakinblocks.graveless.Graveless;
import com.breakinblocks.graveless.capture.InventoryHooks;

public final class AccessoriesIntegration {
    private AccessoriesIntegration() {
    }

    public static void init() {
        InventoryHooks.register(new AccessoriesInventoryHook());
        Graveless.LOGGER.info("Graveless Accessories integration active");
    }
}
