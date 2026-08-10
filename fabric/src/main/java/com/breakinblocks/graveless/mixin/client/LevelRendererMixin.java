package com.breakinblocks.graveless.mixin.client;

import com.breakinblocks.graveless.client.render.GhostRenderManager;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    @Shadow
    @Final
    private LevelRenderState levelRenderState;

    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void graveless$enableOutlineProcessing(GraphicsResourceAllocator resourceAllocator,
                                                   DeltaTracker deltaTracker, boolean renderOutline,
                                                   CameraRenderState cameraState, Matrix4fc modelViewMatrix,
                                                   GpuBufferSlice terrainFog, Vector4f fogColor,
                                                   boolean shouldRenderSky, ChunkSectionsToRender sections,
                                                   CallbackInfo ci) {
        if (GhostRenderManager.needsOutlinePass()) {
            levelRenderState.haveGlowingEntities = true;
        }
    }

    @Inject(method = "submitEntities", at = @At("TAIL"))
    private void graveless$submitGhosts(PoseStack poseStack, LevelRenderState renderState,
                                        SubmitNodeCollector collector, CallbackInfo ci) {
        GhostRenderManager.submitGhosts(poseStack, collector, renderState.cameraRenderState.pos);
    }
}
