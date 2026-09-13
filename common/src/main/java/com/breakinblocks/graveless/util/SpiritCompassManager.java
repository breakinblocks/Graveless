package com.breakinblocks.graveless.util;

import com.breakinblocks.graveless.data.DeathRecord;
import com.breakinblocks.graveless.data.GraveProfile;
import com.breakinblocks.graveless.data.GraveStore;
import com.breakinblocks.graveless.event.GhostSyncEvents;
import com.breakinblocks.graveless.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.LodestoneTracker;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class SpiritCompassManager {
    private SpiritCompassManager() {
    }

    public record StoredCompass(int slot, ItemStack stack) {
    }

    public static List<StoredCompass> suspend(ServerPlayer player) {
        List<StoredCompass> compasses = new ArrayList<>();
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (inventory.getItem(slot).is(ModItems.SPIRIT_COMPASS.get())) {
                compasses.add(new StoredCompass(slot, inventory.getItem(slot)));
                inventory.setItem(slot, ItemStack.EMPTY);
            }
        }
        return compasses;
    }

    public static void resume(ServerPlayer player, List<StoredCompass> compasses) {
        refresh(player);
        GraveProfile profile = GraveStore.get(player.level().getServer()).profile(player.getUUID());
        if (profile.records().isEmpty()) {
            return;
        }
        Inventory inventory = player.getInventory();
        LodestoneTracker tracker = trackerFor(player, profile);
        for (StoredCompass compass : compasses) {
            ItemStack stack = compass.stack();
            stack.set(DataComponents.LODESTONE_TRACKER, tracker);
            if (inventory.getItem(compass.slot()).isEmpty()) {
                inventory.setItem(compass.slot(), stack);
            } else {
                inventory.add(stack);
                if (!stack.isEmpty()) {
                    player.drop(stack, false);
                }
            }
        }
    }

    public static void refresh(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        GraveStore store = GraveStore.get(server);
        GraveProfile profile = store.profile(player.getUUID());
        List<DeathRecord> records = profile.records();
        if (records.isEmpty()) {
            if (profile.trackedRecordId() != null) {
                profile.setTrackedRecordId(null);
                store.setDirty();
            }
            removeAll(player);
            return;
        }
        LodestoneTracker tracker = trackerFor(player, profile);
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(ModItems.SPIRIT_COMPASS.get()) && !tracker.equals(stack.get(DataComponents.LODESTONE_TRACKER))) {
                stack.set(DataComponents.LODESTONE_TRACKER, tracker);
                inventory.setChanged();
            }
        }
    }

    private static LodestoneTracker trackerFor(ServerPlayer player, GraveProfile profile) {
        MinecraftServer server = player.level().getServer();
        GraveStore store = GraveStore.get(server);
        DeathRecord target = null;
        UUID tracked = profile.trackedRecordId();
        if (tracked != null) {
            target = profile.findRecord(tracked);
            if (target == null) {
                profile.setTrackedRecordId(null);
                store.setDirty();
            }
        }
        if (target == null) {
            target = profile.records().getLast();
        }
        ServerLevel level = server.getLevel(target.pos().dimension());
        BlockPos pos = level == null ? target.pos().pos() : GhostSyncEvents.anchor(level, target.pos().pos());
        return new LodestoneTracker(
                Optional.of(GlobalPos.of(target.pos().dimension(), pos)), false);
    }

    public static void giveIfMissing(ServerPlayer player) {
        GraveProfile profile = GraveStore.get(player.level().getServer()).profile(player.getUUID());
        if (profile.records().isEmpty()) {
            removeAll(player);
            return;
        }
        if (!has(player)) {
            ItemStack stack = new ItemStack(ModItems.SPIRIT_COMPASS.get());
            stack.set(DataComponents.LODESTONE_TRACKER, trackerFor(player, profile));
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        }
        refresh(player);
    }

    private static boolean has(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (inventory.getItem(slot).is(ModItems.SPIRIT_COMPASS.get())) {
                return true;
            }
        }
        return false;
    }

    private static void removeAll(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (inventory.getItem(slot).is(ModItems.SPIRIT_COMPASS.get())) {
                inventory.setItem(slot, ItemStack.EMPTY);
            }
        }
    }
}
