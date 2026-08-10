package com.breakinblocks.graveless.client;

import com.breakinblocks.graveless.Graveless;
import com.breakinblocks.graveless.client.render.GhostRenderManager;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.FrameGraphSetupEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

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
    public static void onRightClickEmpty(PlayerInteractEvent.RightClickEmpty event) {
        GhostInteraction.onRightClickEmpty(event.getEntity(), event.getHand());
    }

    @SubscribeEvent
    public static void onLeftClickEmpty(PlayerInteractEvent.LeftClickEmpty event) {
        GhostInteraction.onLeftClickEmpty(event.getEntity());
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
