package com.breakinblocks.graveless.client;

import com.breakinblocks.graveless.Graveless;
import com.breakinblocks.graveless.client.gui.GraveDioramaRenderState;
import com.breakinblocks.graveless.client.gui.GraveDioramaRenderer;
import com.breakinblocks.graveless.client.render.GhostRenderTypes;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterPictureInPictureRenderersEvent;

@EventBusSubscriber(modid = Graveless.MOD_ID, value = Dist.CLIENT)
public class GravelessClient {

    public static void init(IEventBus eventBus) {
        eventBus.addListener(GravelessClient::registerRenderers);
        eventBus.addListener(GhostRenderTypes::registerPipelines);
        eventBus.addListener(GravelessClient::registerPipRenderers);
    }

    private static void registerPipRenderers(RegisterPictureInPictureRenderersEvent event) {
        event.register(GraveDioramaRenderState.class, GraveDioramaRenderer::new);
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
    }
}
