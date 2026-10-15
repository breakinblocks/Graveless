package com.breakinblocks.graveless.integration.trinkets;

import com.breakinblocks.graveless.capture.InventoryHook;
import com.breakinblocks.graveless.data.CapturedEntry;
import com.mojang.serialization.JavaOps;
import eu.pb4.trinkets.api.TrinketAttachment;
import eu.pb4.trinkets.api.TrinketInventory;
import eu.pb4.trinkets.api.TrinketSlotAccess;
import eu.pb4.trinkets.api.TrinketSlotReference;
import eu.pb4.trinkets.api.TrinketsApi;
import eu.pb4.trinkets.impl.slots.TrinketSlot;
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
    private final Map<UUID, Map<ItemStack, CapturedEntry>> pendingSlots = new HashMap<>();

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void prepareDrops(ServerPlayer player, DamageSource source) {
        TrinketAttachment attachment = TrinketsApi.getAttachment(player);
        if (attachment == null) {
            return;
        }
        Map<ItemStack, CapturedEntry> slots = new IdentityHashMap<>();
        attachment.forEach((access, stack) -> {
            if (!stack.isEmpty()) {
                slots.put(stack, new CapturedEntry(ID, access.getSerializedName(), access.index(), stack));
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
        TrinketAttachment attachment = TrinketsApi.getAttachment(player);
        TrinketSlotReference reference = TrinketSlotReference.CODEC
                .parse(JavaOps.INSTANCE, entry.context())
                .result()
                .orElse(null);
        if (attachment == null || reference == null) {
            return stack;
        }
        if (!fallback) {
            TrinketSlotAccess access = attachment.getSlotAccess(reference);
            if (access != null && access.isValid() && access.get().isEmpty()) {
                place(access, stack);
            }
            return stack;
        }
        TrinketInventory inventory = attachment.getInventory(reference.slot());
        if (inventory == null) {
            return stack;
        }
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            TrinketSlotAccess access = attachment.getSlotAccess(reference.slot(), i, reference.cosmetic());
            if (access == null || !access.isValid() || !access.get().isEmpty()) {
                continue;
            }
            if (!reference.cosmetic() && !TrinketSlot.canInsert(stack, access, player)) {
                continue;
            }
            place(access, stack);
            return stack;
        }
        return stack;
    }

    private static void place(TrinketSlotAccess access, ItemStack stack) {
        ItemStack placed = stack.copy();
        try {
            access.set(placed);
        } finally {
            if (access.get() == placed) {
                stack.setCount(0);
            }
        }
    }
}
