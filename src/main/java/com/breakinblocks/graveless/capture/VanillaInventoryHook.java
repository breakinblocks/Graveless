package com.breakinblocks.graveless.capture;

import com.breakinblocks.graveless.data.CapturedEntry;
import com.breakinblocks.graveless.registry.ModItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

import java.util.ArrayList;
import java.util.List;

public class VanillaInventoryHook implements InventoryHook {
    public static final String ID = "inventory";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public List<CapturedEntry> capture(ServerPlayer player, DamageSource source) {
        List<CapturedEntry> entries = new ArrayList<>();
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            inventory.setItem(slot, ItemStack.EMPTY);
            if (stack.is(ModItems.SPIRIT_COMPASS.get())) {
                continue;
            }
            if (EnchantmentHelper.has(stack, EnchantmentEffectComponents.PREVENT_EQUIPMENT_DROP)) {
                continue;
            }
            entries.add(new CapturedEntry(ID, "", slot, stack));
        }
        return entries;
    }

    @Override
    public ItemStack restore(ServerPlayer player, CapturedEntry entry) {
        Inventory inventory = player.getInventory();
        ItemStack stack = entry.stack().copy();
        int slot = entry.slot();
        if (slot >= 0 && slot < inventory.getContainerSize()) {
            ItemStack existing = inventory.getItem(slot);
            if (existing.isEmpty()) {
                inventory.setItem(slot, stack);
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
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        inventory.add(stack);
        return stack.isEmpty() ? ItemStack.EMPTY : stack;
    }
}
