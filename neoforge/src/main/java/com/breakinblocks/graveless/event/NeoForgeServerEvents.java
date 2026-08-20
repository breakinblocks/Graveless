package com.breakinblocks.graveless.event;

import com.breakinblocks.graveless.Graveless;
import com.breakinblocks.graveless.commands.GravelessCommands;
import com.breakinblocks.graveless.platform.NeoForgeNetworkHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@EventBusSubscriber(modid = Graveless.MOD_ID)
public class NeoForgeServerEvents {

    @EventBusSubscriber(modid = Graveless.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
    public static class ModBusEvents {
        @SubscribeEvent
        public static void registerPayloads(RegisterPayloadHandlersEvent event) {
            NeoForgeNetworkHelper.flush(event.registrar(Graveless.MOD_ID));
        }
    }

    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {
        GravelessCommands.register(event.getDispatcher());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            DeathCaptureEvents.onDeath(player, event.getSource());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onExperienceDrop(LivingExperienceDropEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && DeathCaptureEvents.hasPending(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !DeathCaptureEvents.hasPending(player)) {
            return;
        }
        if (!event.isCanceled()) {
            for (ItemEntity drop : event.getDrops()) {
                DeathCaptureEvents.captureDrop(player, drop.getItem());
            }
            event.setCanceled(true);
        }
        DeathCaptureEvents.finishDrops(player);
    }

    @SubscribeEvent
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        if (!SpiritWardEvents.allowTarget(event.getNewAboutToBeSetTarget())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            GhostSyncEvents.onPlayerTick(player);
            SpiritWardEvents.onPlayerTick(player);
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            DeathCaptureEvents.onLogin(player);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            DeathCaptureEvents.onLogout(player);
            GhostSyncEvents.forget(player);
            SpiritWardEvents.onLogout(player);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            DeathCaptureEvents.onRespawn(player);
            GhostSyncEvents.forget(player);
            SpiritWardEvents.onRespawn(player);
        }
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            GhostSyncEvents.forget(player);
        }
    }
}
