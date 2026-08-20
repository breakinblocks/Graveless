package com.breakinblocks.graveless;

import com.breakinblocks.graveless.client.ClientGhostHandlers;
import com.breakinblocks.graveless.client.CompassClientProperties;
import com.breakinblocks.graveless.client.GhostEffects;
import com.breakinblocks.graveless.client.render.GhostRenderManager;
import com.breakinblocks.graveless.client.render.GhostShaders;
import com.breakinblocks.graveless.platform.FabricNetworkHelper;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;

public class GravelessFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        Graveless.initClient();
        ClientGhostHandlers.bindAll();
        FabricNetworkHelper.registerClientReceivers();
        CompassClientProperties.register();

        CoreShaderRegistrationCallback.EVENT.register(context ->
                context.register(Graveless.id("ghost"), DefaultVertexFormat.NEW_ENTITY,
                        GhostShaders::setGhostShader));

        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            GhostRenderManager.renderGhosts(context.matrixStack(), context.consumers(),
                    context.camera().getPosition());
            Minecraft.getInstance().renderBuffers().bufferSource().endBatch();
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> GhostEffects.onClientTick());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> GhostEffects.onDisconnect());
    }
}
