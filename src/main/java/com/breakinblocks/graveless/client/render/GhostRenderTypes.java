package com.breakinblocks.graveless.client.render;

import com.breakinblocks.graveless.Graveless;
import com.breakinblocks.graveless.config.GravelessConfig;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.util.Util;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;

import java.lang.reflect.Method;
import java.util.function.Function;

public final class GhostRenderTypes {
    public static final RenderPipeline GHOST_PIPELINE = RenderPipeline.builder(
                    RenderPipelines.MATRICES_FOG_SNIPPET, RenderPipelines.GLOBALS_SNIPPET)
            .withLocation(Graveless.id("pipeline/ghost"))
            .withVertexShader(Graveless.id("core/ghost"))
            .withFragmentShader(Graveless.id("core/ghost"))
            .withSampler("Sampler0")
            .withVertexFormat(DefaultVertexFormat.ENTITY, VertexFormat.Mode.QUADS)
            .withDepthStencilState(DepthStencilState.DEFAULT)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withCull(false)
            .build();

    public static final RenderPipeline GHOST_PREVIEW_PIPELINE = RenderPipeline.builder(
                    RenderPipelines.ENTITY_EMISSIVE_SNIPPET)
            .withLocation(Graveless.id("pipeline/ghost_preview"))
            .withShaderDefine("ALPHA_CUTOUT", 0.1F)
            .withShaderDefine("PER_FACE_LIGHTING")
            .withSampler("Sampler1")
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withCull(false)
            .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
            .build();

    private static final Function<Identifier, RenderType> GHOST_PREVIEW = Util.memoize(texture ->
            RenderType.create("graveless_ghost_preview", RenderSetup.builder(GHOST_PREVIEW_PIPELINE)
                    .withTexture("Sampler0", texture)
                    .useOverlay()
                    .sortOnUpload()
                    .createRenderSetup()));

    public static final RenderPipeline THREAD_PIPELINE = RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
            .withLocation(Graveless.id("pipeline/astral_thread"))
            .withVertexShader("core/rendertype_lightning")
            .withFragmentShader("core/rendertype_lightning")
            .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
            .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false))
            .build();

    private static final Function<Identifier, RenderType> GHOST = Util.memoize(texture ->
            RenderType.create("graveless_ghost", RenderSetup.builder(GHOST_PIPELINE)
                    .withTexture("Sampler0", texture)
                    .bufferSize(1536)
                    .setOutline(RenderSetup.OutlineProperty.AFFECTS_OUTLINE)
                    .createRenderSetup()));

    private static final RenderType THREAD = RenderType.create("graveless_astral_thread",
            RenderSetup.builder(THREAD_PIPELINE)
                    .bufferSize(1536)
                    .sortOnUpload()
                    .createRenderSetup());

    private static Object irisApi;
    private static Method shaderPackInUse;
    private static boolean irisChecked;

    private GhostRenderTypes() {
    }

    public static boolean shaderPackActive() {
        if (!irisChecked) {
            irisChecked = true;
            try {
                Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                irisApi = api.getMethod("getInstance").invoke(null);
                shaderPackInUse = api.getMethod("isShaderPackInUse");
            } catch (Throwable t) {
                irisApi = null;
            }
        }
        if (irisApi == null) {
            return false;
        }
        try {
            return (Boolean) shaderPackInUse.invoke(irisApi);
        } catch (Throwable t) {
            return false;
        }
    }

    private static final Identifier WHITE_TEXTURE = Graveless.id("textures/misc/white.png");

    public static RenderType thread() {
        return shaderPackActive() ? RenderTypes.entityTranslucentEmissive(WHITE_TEXTURE) : THREAD;
    }

    public static RenderType ghostPreview(Identifier texture) {
        return GHOST_PREVIEW.apply(texture);
    }

    public static boolean shadersEnabled() {
        return GravelessConfig.CLIENT.useShaders.get() && !shaderPackActive();
    }

    public static RenderType ghost(Identifier texture) {
        return shadersEnabled() ? GHOST.apply(texture) : RenderTypes.entityTranslucent(texture);
    }

    public static void registerPipelines(RegisterRenderPipelinesEvent event) {
        event.registerPipeline(GHOST_PIPELINE);
        event.registerPipeline(THREAD_PIPELINE);
        event.registerPipeline(GHOST_PREVIEW_PIPELINE);
    }
}
