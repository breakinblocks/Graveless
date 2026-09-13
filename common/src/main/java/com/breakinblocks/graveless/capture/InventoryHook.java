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

    /**
     * Restores into the original destination, leaving fallback until all original slots are tried.
     * The entry stack is the live remainder: shrink it as transfers succeed, including before
     * invoking callbacks that may throw. Never install that mutable remainder in an inventory;
     * insert a copy. On failure the engine retains the remainder and retries it on a later claim.
     */
    ItemStack restore(ServerPlayer player, CapturedEntry entry);

    /** Tries alternative hook destinations with the same live-remainder contract as restore. */
    default ItemStack restoreFallback(ServerPlayer player, CapturedEntry entry) {
        return entry.stack();
    }
}
