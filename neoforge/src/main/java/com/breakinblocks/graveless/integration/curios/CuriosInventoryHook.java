package com.breakinblocks.graveless.integration.curios;

import com.breakinblocks.graveless.capture.InventoryHook;
import com.breakinblocks.graveless.data.CapturedEntry;
import com.breakinblocks.graveless.registry.ModDataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

import java.util.List;

public class CuriosInventoryHook implements InventoryHook {
    public static final String ID = "curios";
    private static final String COSMETIC_SUFFIX = "#cosmetic";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void prepareDrops(ServerPlayer player, DamageSource source) {
        ICuriosItemHandler handler = CuriosApi.getCuriosInventory(player).orElse(null);
        if (handler == null) {
            return;
        }
        handler.getCurios().forEach((type, stacksHandler) -> {
            tagStacks(type, stacksHandler.getStacks(), false);
            tagStacks(type, stacksHandler.getCosmeticStacks(), true);
        });
    }

    private static void tagStacks(String type, IDynamicStackHandler stacks, boolean cosmetic) {
        for (int i = 0; i < stacks.getSlots(); i++) {
            ItemStack stack = stacks.getStackInSlot(i);
            if (!stack.isEmpty()) {
                stack.set(ModDataComponents.CURIO_SLOT.get(),
                        new ModDataComponents.CurioSlot(type, i, cosmetic));
            }
        }
    }

    @Override
    public List<CapturedEntry> capture(ServerPlayer player, DamageSource source) {
        return List.of();
    }

    @Override
    public ItemStack restore(ServerPlayer player, CapturedEntry entry) {
        ItemStack stack = entry.stack().copy();
        ICuriosItemHandler handler = CuriosApi.getCuriosInventory(player).orElse(null);
        if (handler == null) {
            return stack;
        }
        String context = entry.context();
        boolean cosmetic = context.endsWith(COSMETIC_SUFFIX);
        String type = cosmetic ? context.substring(0, context.length() - COSMETIC_SUFFIX.length()) : context;
        ICurioStacksHandler stacksHandler = handler.getStacksHandler(type).orElse(null);
        if (stacksHandler == null) {
            return stack;
        }
        IDynamicStackHandler stacks = cosmetic ? stacksHandler.getCosmeticStacks() : stacksHandler.getStacks();
        int slot = entry.slot();
        if (slot >= 0 && slot < stacks.getSlots() && stacks.getStackInSlot(slot).isEmpty()) {
            place(handler, stacks, type, slot, cosmetic, stack);
            return ItemStack.EMPTY;
        }
        for (int i = 0; i < stacks.getSlots(); i++) {
            if (!stacks.getStackInSlot(i).isEmpty()) {
                continue;
            }
            if (!cosmetic && !CuriosApi.isStackValid(new SlotContext(type, player, i, false, false), stack)) {
                continue;
            }
            place(handler, stacks, type, i, cosmetic, stack);
            return ItemStack.EMPTY;
        }
        return stack;
    }

    private static void place(ICuriosItemHandler handler, IDynamicStackHandler stacks,
                              String type, int slot, boolean cosmetic, ItemStack stack) {
        if (cosmetic) {
            stacks.setStackInSlot(slot, stack);
        } else {
            handler.setEquippedCurio(type, slot, stack);
        }
    }
}
