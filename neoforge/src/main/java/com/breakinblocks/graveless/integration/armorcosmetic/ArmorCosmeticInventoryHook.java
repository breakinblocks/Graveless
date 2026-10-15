package com.breakinblocks.graveless.integration.armorcosmetic;

import com.skd.armorcosmetic.api.CosArmorAPI;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public class ArmorCosmeticInventoryHook extends ArmorCosmeticHook {
    @Override
    protected int slots(ServerPlayer player) {
        return CosArmorAPI.getCAStacks(player.getUUID()).getSlots();
    }

    @Override
    protected ItemStack get(ServerPlayer player, int slot) {
        return CosArmorAPI.getCAStacks(player.getUUID()).getStackInSlot(slot);
    }

    @Override
    protected void set(ServerPlayer player, int slot, ItemStack stack) {
        CosArmorAPI.getCAStacks(player.getUUID()).setStackInSlot(slot, stack);
    }
}
