package com.breakinblocks.graveless.client.render;

import com.breakinblocks.graveless.Graveless;
import com.breakinblocks.graveless.config.GravelessConfig;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.Util;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.lang.reflect.Method;
import java.util.function.Function;

public final class GhostRenderTypes extends RenderType {

    private GhostRenderTypes(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize,
                             boolean affectsCrumbling, boolean sortOnUpload, Runnable setup, Runnable clear) {
        super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setup, clear);
    }

    private static final Function<ResourceLocation, RenderType> GHOST = Util.memoize(texture ->
            create("graveless_ghost",
                    DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 1536, false, false,
                    RenderType.CompositeState.builder()
                            .setShaderState(new RenderStateShard.ShaderStateShard(GhostShaders::getGhostShader))
                            .setTextureState(new RenderStateShard.TextureStateShard(texture, false, false))
                            .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                            .setCullState(NO_CULL)
                            .createCompositeState(true)));

    private static final Function<ResourceLocation, RenderType> GHOST_PREVIEW = Util.memoize(texture ->
            create("graveless_ghost_preview",
                    DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 1536, false, true,
                    RenderType.CompositeState.builder()
                            .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER)
                            .setTextureState(new RenderStateShard.TextureStateShard(texture, false, false))
                            .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                            .setCullState(NO_CULL)
                            .setOverlayState(OVERLAY)
                            .setDepthTestState(NO_DEPTH_TEST)
                            .setWriteMaskState(COLOR_WRITE)
                            .createCompositeState(false)));

    private static final Function<ResourceLocation, RenderType> GHOST_AURA = Util.memoize(texture ->
            create("graveless_ghost_aura",
                    DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 1536, false, true,
                    RenderType.CompositeState.builder()
                            .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER)
                            .setTextureState(new RenderStateShard.TextureStateShard(texture, false, false))
                            .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                            .setCullState(NO_CULL)
                            .setOverlayState(OVERLAY)
                            .setDepthTestState(NO_DEPTH_TEST)
                            .setWriteMaskState(COLOR_WRITE)
                            .createCompositeState(false)));

    private static final RenderType THREAD = create("graveless_astral_thread",
            DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 8192, false, true,
            RenderType.CompositeState.builder()
                    .setShaderState(RENDERTYPE_LIGHTNING_SHADER)
                    .setTransparencyState(LIGHTNING_TRANSPARENCY)
                    .setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(false));

    private static Object irisApi;
    private static Method shaderPackInUse;
    private static boolean irisChecked;

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

    private static final ResourceLocation WHITE_TEXTURE = Graveless.id("textures/misc/white.png");

    public static RenderType thread() {
        return shaderPackActive() ? RenderType.entityTranslucentEmissive(WHITE_TEXTURE) : THREAD;
    }

    public static RenderType ghostPreview(ResourceLocation texture) {
        return GHOST_PREVIEW.apply(texture);
    }

    public static RenderType ghostAura(ResourceLocation texture, boolean throughWalls) {
        if (throughWalls && !shaderPackActive()) {
            return GHOST_AURA.apply(texture);
        }
        return RenderType.entityTranslucentEmissive(texture);
    }

    public static boolean shadersEnabled() {
        return GravelessConfig.CLIENT.useShaders.get() && GhostShaders.isLoaded() && !shaderPackActive();
    }

    public static RenderType ghost(ResourceLocation texture) {
        return shadersEnabled() ? GHOST.apply(texture) : RenderType.entityTranslucent(texture);
    }
}
