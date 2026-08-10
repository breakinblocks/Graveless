package com.breakinblocks.graveless;

import com.breakinblocks.graveless.client.ClientGhostHandlers;
import com.breakinblocks.graveless.client.GhostEffects;
import com.breakinblocks.graveless.client.gui.GraveDioramaRenderer;
import com.breakinblocks.graveless.platform.FabricNetworkHelper;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.PictureInPictureRendererRegistry;

public class GravelessFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        Graveless.initClient();
        ClientGhostHandlers.bindAll();
        FabricNetworkHelper.registerClientReceivers();

        PictureInPictureRendererRegistry.register(context -> new GraveDioramaRenderer(context.bufferSource()));

        ClientTickEvents.END_CLIENT_TICK.register(client -> GhostEffects.onClientTick());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> GhostEffects.onDisconnect());
    }
}
