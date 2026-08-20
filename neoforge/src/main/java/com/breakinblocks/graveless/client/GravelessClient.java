package com.breakinblocks.graveless.client;

import com.breakinblocks.graveless.Graveless;
import com.breakinblocks.graveless.client.render.GhostShaders;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.ShaderInstance;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

import java.io.IOException;

public class GravelessClient {

    public static void init(IEventBus eventBus) {
        eventBus.addListener(GravelessClient::registerShaders);
        eventBus.addListener(GravelessClient::clientSetup);
        ClientGhostHandlers.bindAll();
    }

    private static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(CompassClientProperties::register);
    }

    private static void registerShaders(RegisterShadersEvent event) {
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(),
                    Graveless.id("ghost"), DefaultVertexFormat.NEW_ENTITY), GhostShaders::setGhostShader);
        } catch (IOException e) {
            Graveless.LOGGER.error("Failed to load ghost shader; falling back to plain translucent rendering", e);
        }
    }
}
