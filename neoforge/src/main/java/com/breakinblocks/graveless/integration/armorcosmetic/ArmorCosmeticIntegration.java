package com.breakinblocks.graveless.integration.armorcosmetic;

import com.breakinblocks.graveless.Graveless;
import com.breakinblocks.graveless.capture.InventoryHooks;
import com.skd.armorcosmetic.api.event.CosArmorDeathDrops;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;

public final class ArmorCosmeticIntegration {
    private static final ArmorCosmeticInventoryHook HOOK = new ArmorCosmeticInventoryHook();

    private ArmorCosmeticIntegration() {}

    public static void init() {
        InventoryHooks.register(HOOK);
        NeoForge.EVENT_BUS.addListener(
                EventPriority.LOWEST, CosArmorDeathDrops.class, ArmorCosmeticIntegration::onDeathDrops);
        Graveless.LOGGER.info("Graveless Armor Cosmetic integration active");
    }

    private static void onDeathDrops(CosArmorDeathDrops event) {
        if (event.getEntityPlayer() instanceof ServerPlayer player && HOOK.moveToGrave(player)) {
            event.setCanceled(true);
        }
    }
}
