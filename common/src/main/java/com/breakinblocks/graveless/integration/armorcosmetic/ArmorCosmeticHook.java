package com.breakinblocks.graveless.integration.armorcosmetic;

import com.breakinblocks.graveless.capture.InventoryHook;
import com.breakinblocks.graveless.data.CapturedEntry;
import com.breakinblocks.graveless.event.DeathCaptureEvents;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.ItemStack;

public abstract class ArmorCosmeticHook implements InventoryHook {
    public static final String ID = "armor_cosmetic";

    protected abstract int slots(ServerPlayer player);

    protected abstract ItemStack get(ServerPlayer player, int slot);

    protected abstract void set(ServerPlayer player, int slot, ItemStack stack);

    @Override
    public String id() {
        return ID;
    }

    @Override
    public List<CapturedEntry> capture(ServerPlayer player, DamageSource source) {
        return List.of();
    }

    public boolean moveToGrave(ServerPlayer player) {
        if (!DeathCaptureEvents.hasPending(player)) {
            return false;
        }
        for (int slot = 0; slot < slots(player); slot++) {
            ItemStack stack = get(player, slot);
            if (stack.isEmpty()) {
                continue;
            }
            if (DeathCaptureEvents.captureEntry(player, new CapturedEntry(ID, "", slot, stack.copy()))) {
                set(player, slot, ItemStack.EMPTY);
            }
        }
        return true;
    }

    @Override
    public ItemStack restore(ServerPlayer player, CapturedEntry entry) {
        ItemStack stack = entry.stack();
        int slot = entry.slot();
        if (slot < 0 || slot >= slots(player) || !get(player, slot).isEmpty()) {
            return stack;
        }
        ItemStack placed = stack.copy();
        try {
            set(player, slot, placed);
        } finally {
            if (get(player, slot) == placed) {
                stack.setCount(0);
            }
        }
        return stack;
    }
}
