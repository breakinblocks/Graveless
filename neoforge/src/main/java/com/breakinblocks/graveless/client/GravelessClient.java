package com.breakinblocks.graveless.client;

import com.breakinblocks.graveless.client.gui.GraveDioramaRenderState;
import com.breakinblocks.graveless.client.gui.GraveDioramaRenderer;
import com.breakinblocks.graveless.client.render.GhostRenderTypes;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterPictureInPictureRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;

public class GravelessClient {

    public static void init(IEventBus eventBus) {
        eventBus.addListener(GravelessClient::registerPipelines);
        eventBus.addListener(GravelessClient::registerPipRenderers);
        ClientGhostHandlers.bindAll();
    }

    private static void registerPipelines(RegisterRenderPipelinesEvent event) {
        for (RenderPipeline pipeline : GhostRenderTypes.pipelines()) {
            event.registerPipeline(pipeline);
        }
    }

    private static void registerPipRenderers(RegisterPictureInPictureRenderersEvent event) {
        event.register(GraveDioramaRenderState.class, GraveDioramaRenderer::new);
    }
}
