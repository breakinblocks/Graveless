package com.breakinblocks.graveless.mixin.client;

import com.breakinblocks.graveless.client.render.GhostRenderManager;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    @Inject(method = "submitEntities", at = @At("TAIL"))
    private void graveless$submitGhosts(PoseStack poseStack, LevelRenderState renderState,
                                        SubmitNodeCollector collector, CallbackInfo ci) {
        GhostRenderManager.submitGhosts(poseStack, collector, renderState.cameraRenderState.pos);
    }
}
