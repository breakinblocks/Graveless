package com.breakinblocks.graveless.client.render;

import net.minecraft.client.renderer.ShaderInstance;
import org.jetbrains.annotations.Nullable;

public final class GhostShaders {
    @Nullable
    private static ShaderInstance ghostShader;

    private GhostShaders() {
    }

    public static void setGhostShader(@Nullable ShaderInstance shader) {
        ghostShader = shader;
    }

    @Nullable
    public static ShaderInstance getGhostShader() {
        return ghostShader;
    }

    public static boolean isLoaded() {
        return ghostShader != null;
    }
}
