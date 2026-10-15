package com.breakinblocks.graveless.integration.trinkets;

import com.breakinblocks.graveless.capture.InventoryHook;
import com.breakinblocks.graveless.data.CapturedEntry;
import dev.emi.trinkets.api.SlotReference;
import dev.emi.trinkets.api.SlotType;
import dev.emi.trinkets.api.TrinketComponent;
import dev.emi.trinkets.api.TrinketInventory;
import dev.emi.trinkets.api.TrinketsApi;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.ItemStack;

public class TrinketsInventoryHook implements InventoryHook {
    public static final String ID = "trinkets";
    private static final char SEPARATOR = '/';
    private final Map<UUID, Map<ItemStack, CapturedEntry>> pendingSlots = new HashMap<>();

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void prepareDrops(ServerPlayer player, DamageSource source) {
        TrinketComponent component = TrinketsApi.getTrinketComponent(player).orElse(null);
        if (component == null) {
            return;
        }
        Map<ItemStack, CapturedEntry> slots = new IdentityHashMap<>();
        component.forEach((ref, stack) -> {
            if (!stack.isEmpty()) {
                SlotType type = ref.inventory().getSlotType();
                slots.put(
                        stack, new CapturedEntry(ID, type.getGroup() + SEPARATOR + type.getName(), ref.index(), stack));
            }
        });
        pendingSlots.put(player.getUUID(), slots);
    }

    @Override
    public List<CapturedEntry> capture(ServerPlayer player, DamageSource source) {
        return List.of();
    }

    @Override
    public CapturedEntry dropEntry(ServerPlayer player, ItemStack stack) {
        Map<ItemStack, CapturedEntry> slots = pendingSlots.get(player.getUUID());
        return slots == null ? null : slots.remove(stack);
    }

    @Override
    public void finishDrops(ServerPlayer player) {
        pendingSlots.remove(player.getUUID());
    }

    @Override
    public ItemStack restore(ServerPlayer player, CapturedEntry entry) {
        return restore(player, entry, false);
    }

    @Override
    public ItemStack restoreFallback(ServerPlayer player, CapturedEntry entry) {
        return restore(player, entry, true);
    }

    private ItemStack restore(ServerPlayer player, CapturedEntry entry, boolean fallback) {
        ItemStack stack = entry.stack();
        TrinketInventory inventory = inventory(player, entry.context());
        if (inventory == null) {
            return stack;
        }
        if (!fallback) {
            int slot = entry.slot();
            if (slot >= 0
                    && slot < inventory.getContainerSize()
                    && inventory.getItem(slot).isEmpty()) {
                place(inventory, slot, stack);
            }
            return stack;
        }
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (inventory.getItem(i).isEmpty() && canEquip(player, inventory, i, stack)) {
                place(inventory, i, stack);
                return stack;
            }
        }
        return stack;
    }

    private static TrinketInventory inventory(ServerPlayer player, String context) {
        int split = context.indexOf(SEPARATOR);
        if (split < 0) {
            return null;
        }
        String group = context.substring(0, split);
        String slot = context.substring(split + 1);
        return TrinketsApi.getTrinketComponent(player)
                .map(component ->
                        component.getInventory().getOrDefault(group, Map.of()).get(slot))
                .orElse(null);
    }

    private static boolean canEquip(ServerPlayer player, TrinketInventory inventory, int slot, ItemStack stack) {
        SlotReference ref = new SlotReference(inventory, slot);
        return TrinketsApi.evaluatePredicateSet(inventory.getSlotType().getValidatorPredicates(), stack, ref, player)
                && TrinketsApi.getTrinket(stack.getItem()).canEquip(stack, ref, player);
    }

    private static void place(TrinketInventory inventory, int slot, ItemStack stack) {
        ItemStack placed = stack.copy();
        try {
            inventory.setItem(slot, placed);
        } finally {
            if (inventory.getItem(slot) == placed) {
                stack.setCount(0);
            }
        }
    }
}
