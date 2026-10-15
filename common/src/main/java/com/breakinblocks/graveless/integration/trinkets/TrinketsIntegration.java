package com.breakinblocks.graveless.integration.trinkets;

import com.breakinblocks.graveless.Graveless;
import com.breakinblocks.graveless.capture.InventoryHooks;

public final class TrinketsIntegration {
    public static final String MOD_ID = "trinkets_updated";

    private TrinketsIntegration() {}

    public static void init() {
        InventoryHooks.register(new TrinketsInventoryHook());
        Graveless.LOGGER.info("Graveless Trinkets integration active");
    }
}
