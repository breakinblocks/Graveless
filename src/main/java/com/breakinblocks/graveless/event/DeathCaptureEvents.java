package com.breakinblocks.graveless.event;

import com.breakinblocks.graveless.Graveless;
import com.breakinblocks.graveless.capture.InventoryHook;
import com.breakinblocks.graveless.capture.InventoryHooks;
import com.breakinblocks.graveless.config.GravelessConfig;
import com.breakinblocks.graveless.data.CapturedEntry;
import com.breakinblocks.graveless.data.DeathRecord;
import com.breakinblocks.graveless.data.GraveProfile;
import com.breakinblocks.graveless.data.GraveStore;
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
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.gamerules.GameRules;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = Graveless.MOD_ID)
public class DeathCaptureEvents {
    private static final Map<UUID, DeathRecord> PENDING = new HashMap<>();

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player instanceof FakePlayer) {
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
                hook.prepareDrops(player, event.getSource());
            } catch (Exception e) {
                Graveless.LOGGER.error("Inventory hook {} failed during drop preparation for {}", hook.id(), player.getName().getString(), e);
            }
        }
        List<CapturedEntry> entries = new ArrayList<>();
        for (InventoryHook hook : InventoryHooks.all()) {
            try {
                entries.addAll(hook.capture(player, event.getSource()));
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
                event.getSource().getLocalizedDeathMessage(player).getString(),
                xp,
                entries);
        PENDING.put(player.getUUID(), record);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onExperienceDrop(LivingExperienceDropEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && PENDING.containsKey(player.getUUID())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        DeathRecord record = PENDING.remove(player.getUUID());
        if (record == null) {
            return;
        }
        if (!event.isCanceled()) {
            for (ItemEntity drop : event.getDrops()) {
                ItemStack stack = drop.getItem();
                if (stack.isEmpty() || stack.is(ModItems.SPIRIT_COMPASS.get())) {
                    continue;
                }
                stack = stack.copy();
                ModDataComponents.CurioSlot curioTag = stack.remove(ModDataComponents.CURIO_SLOT.get());
                if (curioTag != null) {
                    record.entries().add(new CapturedEntry("curios",
                            curioTag.cosmetic() ? curioTag.type() + "#cosmetic" : curioTag.type(),
                            curioTag.index(), stack));
                } else {
                    record.entries().add(CapturedEntry.loose(stack));
                }
            }
            event.setCanceled(true);
        }
        finalizeRecord(player, record);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        PENDING.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !(player instanceof FakePlayer)) {
            SpiritCompassManager.giveIfMissing(player);
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !(player instanceof FakePlayer)) {
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
