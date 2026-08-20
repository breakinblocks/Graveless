package com.breakinblocks.graveless;

import com.breakinblocks.graveless.commands.GravelessCommands;
import com.breakinblocks.graveless.event.DeathCaptureEvents;
import com.breakinblocks.graveless.integration.accessories.AccessoriesIntegration;
import com.breakinblocks.graveless.event.GhostSyncEvents;
import com.breakinblocks.graveless.event.SpiritWardEvents;
import com.breakinblocks.graveless.registry.ModItems;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.CreativeModeTabs;

public class GravelessFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        Graveless.init();

        if (Graveless.isModLoaded("accessories")) {
            AccessoriesIntegration.init();
        }

        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.TOOLS_AND_UTILITIES)
                .register(entries -> entries.accept(ModItems.SPIRIT_COMPASS.get()));

        CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) ->
                GravelessCommands.register(dispatcher));

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                GhostSyncEvents.onPlayerTick(player);
                SpiritWardEvents.onPlayerTick(player);
            }
        });

        ServerPlayerEvents.JOIN.register(DeathCaptureEvents::onLogin);

        ServerPlayerEvents.LEAVE.register(player -> {
            DeathCaptureEvents.onLogout(player);
            GhostSyncEvents.forget(player);
            SpiritWardEvents.onLogout(player);
        });

        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            DeathCaptureEvents.onRespawn(newPlayer);
            GhostSyncEvents.forget(newPlayer);
            SpiritWardEvents.onRespawn(newPlayer);
        });

        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) ->
                GhostSyncEvents.forget(player));
    }
}
