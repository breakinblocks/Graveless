package com.breakinblocks.graveless.integration.curios;

import com.breakinblocks.graveless.Graveless;
import com.breakinblocks.graveless.capture.InventoryHooks;

public final class CuriosIntegration {
    private CuriosIntegration() {
    }

    public static void init() {
        InventoryHooks.register(new CuriosInventoryHook());
        Graveless.LOGGER.info("Graveless Curios integration active");
    }
}
