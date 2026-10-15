package com.breakinblocks.graveless.capture;

import com.breakinblocks.graveless.data.CapturedEntry;
import com.breakinblocks.graveless.platform.Services;
import com.breakinblocks.graveless.registry.ModItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public class VanillaInventoryHook implements InventoryHook {
    public static final String ID = "inventory";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public List<CapturedEntry> capture(ServerPlayer player, DamageSource source) {
        boolean fromDrops = Services.PLATFORM.capturesInventoryFromDrops();
        List<CapturedEntry> entries = new ArrayList<>();
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            if (isDiscarded(stack)) {
                inventory.setItem(slot, ItemStack.EMPTY);
                continue;
            }
            if (fromDrops) {
                continue;
            }
            inventory.setItem(slot, ItemStack.EMPTY);
            entries.add(new CapturedEntry(ID, "", slot, stack));
        }
        return entries;
    }

    public static Map<ItemStack, Integer> pendingSlots(Inventory inventory) {
        Map<ItemStack, Integer> slots = new IdentityHashMap<>();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty()) {
                slots.put(stack, slot);
            }
        }
        return slots;
    }

    private static boolean isDiscarded(ItemStack stack) {
        return stack.is(ModItems.SPIRIT_COMPASS.get())
                || EnchantmentHelper.has(stack, EnchantmentEffectComponents.PREVENT_EQUIPMENT_DROP);
    }

    @Override
    public ItemStack restore(ServerPlayer player, CapturedEntry entry) {
        Inventory inventory = player.getInventory();
        ItemStack stack = entry.stack();
        int slot = entry.slot();
        if (slot >= 0 && slot < inventory.getContainerSize()) {
            ItemStack existing = inventory.getItem(slot);
            if (existing.isEmpty()) {
                ItemStack placed = stack.copy();
                try {
                    inventory.setItem(slot, placed);
                } finally {
                    if (inventory.getItem(slot) == placed) {
                        stack.setCount(0);
                    }
                }
                return ItemStack.EMPTY;
            }
            if (ItemStack.isSameItemSameComponents(existing, stack)) {
                int space = existing.getMaxStackSize() - existing.getCount();
                if (space > 0) {
                    int moved = Math.min(space, stack.getCount());
                    existing.grow(moved);
                    stack.shrink(moved);
                    inventory.setChanged();
                }
            }
        }
        return stack.isEmpty() ? ItemStack.EMPTY : stack;
    }
}
