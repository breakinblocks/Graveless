package com.breakinblocks.graveless.capture;

import com.breakinblocks.graveless.data.CapturedEntry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public interface InventoryHook {
    String id();

    default void prepareDrops(ServerPlayer player, DamageSource source) {
    }

    List<CapturedEntry> capture(ServerPlayer player, DamageSource source);

    ItemStack restore(ServerPlayer player, CapturedEntry entry);
}
