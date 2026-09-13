package com.breakinblocks.graveless.integration.accessories;

import com.breakinblocks.graveless.capture.InventoryHook;
import com.breakinblocks.graveless.data.CapturedEntry;
import com.breakinblocks.graveless.event.DeathCaptureEvents;
import io.wispforest.accessories.api.AccessoriesAPI;
import io.wispforest.accessories.api.AccessoriesCapability;
import io.wispforest.accessories.api.AccessoriesContainer;
import io.wispforest.accessories.api.slot.SlotReference;
import io.wispforest.accessories.impl.ExpandedSimpleContainer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class AccessoriesInventoryHook implements InventoryHook {
    public static final String ID = "accessories";
    private static final String COSMETIC_SUFFIX = "#cosmetic";
    private final Map<UUID, Map<ItemStack, CapturedEntry>> pendingSlots = new HashMap<>();

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void prepareDrops(ServerPlayer player, DamageSource source) {
        AccessoriesCapability capability = AccessoriesCapability.get(player);
        if (capability == null) {
            return;
        }
        Map<ItemStack, CapturedEntry> slots = new IdentityHashMap<>();
        capability.getContainers().forEach((type, container) -> {
            rememberSlots(type, container.getAccessories(), false, slots);
            rememberSlots(type, container.getCosmeticAccessories(), true, slots);
        });
        pendingSlots.put(player.getUUID(), slots);
    }

    private static void rememberSlots(String type, ExpandedSimpleContainer stacks, boolean cosmetic,
                                       Map<ItemStack, CapturedEntry> slots) {
        for (int slot = 0; slot < stacks.getContainerSize(); slot++) {
            ItemStack stack = stacks.getItem(slot);
            if (!stack.isEmpty()) {
                slots.put(stack, new CapturedEntry(ID, cosmetic ? type + COSMETIC_SUFFIX : type, slot, stack));
            }
        }
    }

    @Override
    public List<CapturedEntry> capture(ServerPlayer player, DamageSource source) {
        // Accessories owns keep rules, nested items, and death callbacks. Capture its resolved drops.
        return List.of();
    }

    public void captureDrops(ServerPlayer player, List<ItemStack> drops) {
        Map<ItemStack, CapturedEntry> slots = pendingSlots.remove(player.getUUID());
        if (slots == null || !DeathCaptureEvents.hasPending(player)) {
            return;
        }
        drops.removeIf(stack -> DeathCaptureEvents.captureDrop(player,
                slots.getOrDefault(stack, CapturedEntry.loose(stack)).withStack(stack)));
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
        AccessoriesCapability capability = AccessoriesCapability.get(player);
        if (capability == null) {
            return stack;
        }
        String context = entry.context();
        boolean cosmetic = context.endsWith(COSMETIC_SUFFIX);
        String type = cosmetic ? context.substring(0, context.length() - COSMETIC_SUFFIX.length()) : context;
        AccessoriesContainer container = capability.getContainers().get(type);
        if (container == null) {
            return stack;
        }
        ExpandedSimpleContainer stacks = cosmetic ? container.getCosmeticAccessories() : container.getAccessories();
        int slot = entry.slot();
        if (!fallback) {
            if (canPlace(player, stacks, type, slot, cosmetic, stack)) {
                place(container, stacks, slot, stack);
            }
            return stack;
        }
        for (int i = 0; i < stacks.getContainerSize(); i++) {
            if (canPlace(player, stacks, type, i, cosmetic, stack)) {
                place(container, stacks, i, stack);
                return stack;
            }
        }
        return stack;
    }

    private static boolean canPlace(ServerPlayer player, ExpandedSimpleContainer stacks, String type, int slot,
                                    boolean cosmetic, ItemStack stack) {
        if (slot < 0 || slot >= stacks.getContainerSize() || !stacks.getItem(slot).isEmpty()) {
            return false;
        }
        return cosmetic || AccessoriesAPI.canInsertIntoSlot(stack, SlotReference.of(player, type, slot));
    }

    private static void place(AccessoriesContainer container, ExpandedSimpleContainer stacks, int slot,
                              ItemStack stack) {
        ItemStack placed = stack.copyWithCount(Math.min(stack.getCount(), stacks.getMaxStackSize(stack)));
        try {
            stacks.setItem(slot, placed);
            container.markChanged();
        } finally {
            if (stacks.getItem(slot) == placed) {
                stack.shrink(placed.getCount());
            }
        }
    }
}
