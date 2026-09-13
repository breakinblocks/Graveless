package com.breakinblocks.graveless.restore;

import com.breakinblocks.graveless.Graveless;
import com.breakinblocks.graveless.capture.InventoryHook;
import com.breakinblocks.graveless.capture.InventoryHooks;
import com.breakinblocks.graveless.capture.VanillaInventoryHook;
import com.breakinblocks.graveless.data.CapturedEntry;
import com.breakinblocks.graveless.data.DeathRecord;
import com.breakinblocks.graveless.data.GraveProfile;
import com.breakinblocks.graveless.data.GraveStore;
import com.breakinblocks.graveless.util.SpiritCompassManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

public final class RestoreEngine {
    private RestoreEngine() {
    }

    public record Result(int restored, int remaining, int xpRestored, boolean recordRemoved) {
    }

    public static Result claim(ServerPlayer player, GraveProfile profile, DeathRecord record, GraveStore store) {
        var compasses = SpiritCompassManager.suspend(player);
        int before = record.itemCount();
        int xpRestored = 0;
        try {
            record.entries().sort(Comparator.comparingInt(RestoreEngine::restoreOrder));
            Set<Integer> failed = new HashSet<>();
            // Hooks only restore their own destinations. General inventory space is used afterward.
            for (int i = 0; i < record.entries().size(); i++) {
                CapturedEntry entry = record.entries().get(i);
                InventoryHook hook = InventoryHooks.byId(entry.handler());
                if (hook == null || entry.stack().isEmpty()) {
                    continue;
                }
                try {
                    ItemStack leftover = hook.restore(player, entry);
                    record.entries().set(i, entry.withStack(leftover));
                } catch (Exception e) {
                    failed.add(i);
                    Graveless.LOGGER.error("Inventory hook {} failed restoring grave {} for {}",
                            entry.handler(), record.id(), player.getUUID(), e);
                }
            }
            for (int i = 0; i < record.entries().size(); i++) {
                CapturedEntry entry = record.entries().get(i);
                if (failed.contains(i) || entry.stack().isEmpty()) {
                    continue;
                }
                InventoryHook hook = InventoryHooks.byId(entry.handler());
                try {
                    ItemStack remaining = hook == null ? entry.stack() : hook.restoreFallback(player, entry);
                    record.entries().set(i, entry.withStack(remaining));
                    if (!remaining.isEmpty()) {
                        player.getInventory().add(remaining);
                    }
                } catch (Exception e) {
                    Graveless.LOGGER.error("Failed restoring remaining items from grave {} for {}",
                            record.id(), player.getUUID(), e);
                }
            }
            xpRestored = record.xp();
            if (xpRestored > 0) {
                player.giveExperiencePoints(xpRestored);
                record.setXp(0);
            }
        } finally {
            record.entries().removeIf(entry -> entry.stack().isEmpty());
            if (record.isEmpty()) {
                profile.records().remove(record);
            }
            store.setDirty();
            SpiritCompassManager.resume(player, compasses);
            player.inventoryMenu.broadcastChanges();
        }
        return new Result(before - record.itemCount(), record.itemCount(), xpRestored, record.isEmpty());
    }

    private static int restoreOrder(CapturedEntry entry) {
        if (VanillaInventoryHook.ID.equals(entry.handler())) {
            return entry.slot() >= Inventory.INVENTORY_SIZE ? 0 : 2;
        }
        if (CapturedEntry.LOOSE_HANDLER.equals(entry.handler())) {
            return 3;
        }
        return 1;
    }
}
