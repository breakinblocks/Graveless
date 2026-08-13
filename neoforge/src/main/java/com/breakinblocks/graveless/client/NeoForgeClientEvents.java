package com.breakinblocks.graveless.client;

import com.breakinblocks.graveless.Graveless;
import com.breakinblocks.graveless.client.render.GhostRenderManager;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.FrameGraphSetupEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;

@EventBusSubscriber(modid = Graveless.MOD_ID, value = Dist.CLIENT)
public class NeoForgeClientEvents {

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        GhostEffects.onClientTick();
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        GhostEffects.onDisconnect();
    }

    @SubscribeEvent
    public static void onClone(ClientPlayerNetworkEvent.Clone event) {
        GhostEffects.onRespawnClone();
    }

    @SubscribeEvent
    public static void onInteractionKey(InputEvent.InteractionKeyMappingTriggered event) {
        Player player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        if (event.isAttack()) {
            if (GhostInteraction.onAttackPressed(player)) {
                event.setCanceled(true);
            }
        } else if (event.isUseItem() && GhostInteraction.onUsePressed(player, event.getHand())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onSubmit(SubmitCustomGeometryEvent event) {
        GhostRenderManager.submitGhosts(event.getPoseStack(), event.getSubmitNodeCollector(),
                event.getLevelRenderState().cameraRenderState.pos);
    }

    @SubscribeEvent
    public static void onFrameGraphSetup(FrameGraphSetupEvent event) {
        if (GhostRenderManager.needsOutlinePass()) {
            event.enableOutlineProcessing();
        }
    }
}
