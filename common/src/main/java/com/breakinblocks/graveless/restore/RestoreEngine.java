package com.breakinblocks.graveless.restore;

import com.breakinblocks.graveless.capture.InventoryHook;
import com.breakinblocks.graveless.capture.InventoryHooks;
import com.breakinblocks.graveless.capture.VanillaInventoryHook;
import com.breakinblocks.graveless.data.CapturedEntry;
import com.breakinblocks.graveless.data.DeathRecord;
import com.breakinblocks.graveless.data.GraveProfile;
import com.breakinblocks.graveless.data.GraveStore;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class RestoreEngine {
    private RestoreEngine() {
    }

    public record Result(int restored, int remaining, int xpRestored, boolean recordRemoved) {
    }

    public static Result claim(ServerPlayer player, GraveProfile profile, DeathRecord record, GraveStore store) {
        List<CapturedEntry> pending = new ArrayList<>(record.entries());
        pending.sort(Comparator.comparingInt(RestoreEngine::restoreOrder));
        record.entries().clear();

        int restored = 0;
        for (CapturedEntry entry : pending) {
            ItemStack leftover = restoreEntry(player, entry);
            if (leftover.isEmpty()) {
                restored += entry.stack().getCount();
            } else {
                restored += entry.stack().getCount() - leftover.getCount();
                record.entries().add(entry.withStack(leftover));
            }
        }

        int xpRestored = record.xp();
        if (xpRestored > 0) {
            player.giveExperiencePoints(xpRestored);
            record.setXp(0);
        }

        boolean removed = record.entries().isEmpty();
        if (removed) {
            profile.records().remove(record);
        }
        store.setDirty();
        player.inventoryMenu.broadcastChanges();

        int remaining = record.itemCount();
        return new Result(restored, remaining, xpRestored, removed);
    }

    private static ItemStack restoreEntry(ServerPlayer player, CapturedEntry entry) {
        InventoryHook hook = InventoryHooks.byId(entry.handler());
        ItemStack leftover = hook != null ? hook.restore(player, entry) : entry.stack().copy();
        if (leftover.isEmpty()) {
            return ItemStack.EMPTY;
        }
        Inventory inventory = player.getInventory();
        inventory.add(leftover);
        return leftover.isEmpty() ? ItemStack.EMPTY : leftover;
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
