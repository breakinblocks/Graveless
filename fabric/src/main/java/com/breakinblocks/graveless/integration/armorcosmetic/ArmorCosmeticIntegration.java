package com.breakinblocks.graveless.integration.armorcosmetic;

import com.breakinblocks.graveless.Graveless;
import com.breakinblocks.graveless.capture.InventoryHooks;
import com.skd.armorcosmetic.api.event.CosArmorDeathDrops;
import net.minecraft.server.level.ServerPlayer;

public final class ArmorCosmeticIntegration {
    private static final ArmorCosmeticInventoryHook HOOK = new ArmorCosmeticInventoryHook();

    private ArmorCosmeticIntegration() {}

    public static void init() {
        InventoryHooks.register(HOOK);
        CosArmorDeathDrops.EVENT.register(
                (player, stacks) -> !(player instanceof ServerPlayer serverPlayer && HOOK.moveToGrave(serverPlayer)));
        Graveless.LOGGER.info("Graveless Armor Cosmetic integration active");
    }
}
