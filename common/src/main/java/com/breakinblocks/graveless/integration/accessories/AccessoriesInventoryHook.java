package com.breakinblocks.graveless.integration.accessories;

import com.breakinblocks.graveless.capture.InventoryHook;
import com.breakinblocks.graveless.data.CapturedEntry;
import com.breakinblocks.graveless.registry.ModDataComponents;
import io.wispforest.accessories.api.AccessoriesAPI;
import io.wispforest.accessories.api.AccessoriesCapability;
import io.wispforest.accessories.api.AccessoriesContainer;
import io.wispforest.accessories.api.DropRule;
import io.wispforest.accessories.api.slot.SlotReference;
import io.wispforest.accessories.impl.ExpandedSimpleContainer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

import java.util.ArrayList;
import java.util.List;

public class AccessoriesInventoryHook implements InventoryHook {
    public static final String ID = "accessories";
    private static final String COSMETIC_SUFFIX = "#cosmetic";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public List<CapturedEntry> capture(ServerPlayer player, DamageSource source) {
        AccessoriesCapability capability = AccessoriesCapability.get(player);
        if (capability == null) {
            return List.of();
        }
        List<CapturedEntry> entries = new ArrayList<>();
        capability.getContainers().forEach((type, container) -> {
            boolean changed = captureStacks(player, source, container, container.getAccessories(), false, entries);
            changed |= captureStacks(player, source, container, container.getCosmeticAccessories(), true, entries);
            if (changed) {
                container.markChanged();
            }
        });
        return entries;
    }

    private static boolean captureStacks(ServerPlayer player, DamageSource source, AccessoriesContainer container,
                                         ExpandedSimpleContainer stacks, boolean cosmetic,
                                         List<CapturedEntry> entries) {
        String type = container.getSlotName();
        boolean changed = false;
        for (int slot = 0; slot < stacks.getContainerSize(); slot++) {
            ItemStack stack = stacks.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            SlotReference reference = SlotReference.of(player, type, slot);
            DropRule rule = AccessoriesAPI.getOrDefaultAccessory(stack).getDropRule(stack, reference, source);
            if (rule == DropRule.KEEP) {
                continue;
            }
            stacks.setItem(slot, ItemStack.EMPTY);
            changed = true;
            if (rule == DropRule.DESTROY) {
                continue;
            }
            if (EnchantmentHelper.has(stack, EnchantmentEffectComponents.PREVENT_EQUIPMENT_DROP)) {
                continue;
            }
            stack.remove(ModDataComponents.CURIO_SLOT.get());
            entries.add(new CapturedEntry(ID, cosmetic ? type + COSMETIC_SUFFIX : type, slot, stack));
        }
        return changed;
    }

    @Override
    public ItemStack restore(ServerPlayer player, CapturedEntry entry) {
        ItemStack stack = entry.stack().copy();
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
        if (canPlace(player, stacks, type, slot, cosmetic, stack)) {
            place(container, stacks, slot, stack);
            return ItemStack.EMPTY;
        }
        for (int i = 0; i < stacks.getContainerSize(); i++) {
            if (canPlace(player, stacks, type, i, cosmetic, stack)) {
                place(container, stacks, i, stack);
                return ItemStack.EMPTY;
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
        stacks.setItem(slot, stack);
        container.markChanged();
    }
}
