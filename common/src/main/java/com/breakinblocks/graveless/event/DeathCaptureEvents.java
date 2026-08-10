package com.breakinblocks.graveless.event;

import com.breakinblocks.graveless.Graveless;
import com.breakinblocks.graveless.capture.InventoryHook;
import com.breakinblocks.graveless.capture.InventoryHooks;
import com.breakinblocks.graveless.config.GravelessConfig;
import com.breakinblocks.graveless.data.CapturedEntry;
import com.breakinblocks.graveless.data.DeathRecord;
import com.breakinblocks.graveless.data.GraveProfile;
import com.breakinblocks.graveless.data.GraveStore;
import com.breakinblocks.graveless.platform.Services;
import com.breakinblocks.graveless.registry.ModDataComponents;
import com.breakinblocks.graveless.registry.ModItems;
import com.breakinblocks.graveless.util.GraveBackups;
import com.breakinblocks.graveless.util.SpiritCompassManager;
import com.breakinblocks.graveless.util.XpMath;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.gamerules.GameRules;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class DeathCaptureEvents {
    private static final Map<UUID, DeathRecord> PENDING = new HashMap<>();

    public static void onDeath(ServerPlayer player, DamageSource source) {
        if (Services.PLATFORM.isFakePlayer(player)) {
            return;
        }
        if (player.isSpectator() || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        if (!GravelessConfig.SERVER.enabled.get()) {
            return;
        }
        if (level.getGameRules().get(GameRules.KEEP_INVENTORY)) {
            return;
        }
        GraveProfile profile = GraveStore.get(level.getServer()).profile(player.getUUID());
        if (!profile.isEnabled()) {
            return;
        }

        for (InventoryHook hook : InventoryHooks.all()) {
            try {
                hook.prepareDrops(player, source);
            } catch (Exception e) {
                Graveless.LOGGER.error("Inventory hook {} failed during drop preparation for {}", hook.id(), player.getName().getString(), e);
            }
        }
        List<CapturedEntry> entries = new ArrayList<>();
        for (InventoryHook hook : InventoryHooks.all()) {
            try {
                entries.addAll(hook.capture(player, source));
            } catch (Exception e) {
                Graveless.LOGGER.error("Inventory hook {} failed during capture for {}", hook.id(), player.getName().getString(), e);
            }
        }

        int xp = XpMath.totalPoints(player.experienceLevel, player.experienceProgress);
        player.experienceLevel = 0;
        player.experienceProgress = 0.0F;
        player.totalExperience = 0;

        DeathRecord record = new DeathRecord(
                UUID.randomUUID(),
                GlobalPos.of(level.dimension(), player.blockPosition()),
                level.getGameTime(),
                System.currentTimeMillis(),
                source.getLocalizedDeathMessage(player).getString(),
                xp,
                entries);
        PENDING.put(player.getUUID(), record);
    }

    public static boolean hasPending(ServerPlayer player) {
        return PENDING.containsKey(player.getUUID());
    }

    public static int modifyDroppedXp(ServerPlayer player, int amount) {
        return hasPending(player) ? 0 : amount;
    }

    public static boolean captureDrop(ServerPlayer player, ItemStack stack) {
        DeathRecord record = PENDING.get(player.getUUID());
        if (record == null) {
            return false;
        }
        if (stack.isEmpty() || stack.is(ModItems.SPIRIT_COMPASS.get())) {
            return false;
        }
        ItemStack copy = stack.copy();
        ModDataComponents.CurioSlot curioTag = copy.remove(ModDataComponents.CURIO_SLOT.get());
        if (curioTag != null) {
            record.entries().add(new CapturedEntry("curios",
                    curioTag.cosmetic() ? curioTag.type() + "#cosmetic" : curioTag.type(),
                    curioTag.index(), copy));
        } else {
            record.entries().add(CapturedEntry.loose(copy));
        }
        return true;
    }

    public static void finishDrops(ServerPlayer player) {
        DeathRecord record = PENDING.remove(player.getUUID());
        if (record != null) {
            finalizeRecord(player, record);
        }
    }

    public static void onLogout(ServerPlayer player) {
        PENDING.remove(player.getUUID());
    }

    public static void onRespawn(ServerPlayer player) {
        if (!Services.PLATFORM.isFakePlayer(player)) {
            SpiritCompassManager.giveIfMissing(player);
        }
    }

    public static void onLogin(ServerPlayer player) {
        if (!Services.PLATFORM.isFakePlayer(player)) {
            SpiritCompassManager.refresh(player);
        }
    }

    private static void finalizeRecord(ServerPlayer player, DeathRecord record) {
        List<CapturedEntry> sanitized = CapturedEntry.sanitize(record.entries());
        record.entries().clear();
        record.entries().addAll(sanitized);
        if (record.isEmpty()) {
            return;
        }
        MinecraftServer server = player.level().getServer();
        record.setTerrain(GraveMenuHandlers.captureTerrainSnapshot(server, record));
        GraveStore store = GraveStore.get(server);
        GraveProfile profile = store.profile(player.getUUID());
        profile.records().add(record);
        int max = GravelessConfig.SERVER.maxRecordsPerPlayer.get();
        while (profile.records().size() > max) {
            profile.records().removeFirst();
        }
        store.setDirty();
        GraveBackups.write(server, player.getUUID(), record);

        BlockPos pos = record.pos().pos();
        player.sendSystemMessage(Component.translatable("graveless.death.saved",
                record.itemCount(),
                pos.getX(), pos.getY(), pos.getZ(),
                record.pos().dimension().identifier().toString()).withStyle(ChatFormatting.AQUA));
        player.level().playSound(null, pos, SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 1.0F, 0.7F);
    }
}
