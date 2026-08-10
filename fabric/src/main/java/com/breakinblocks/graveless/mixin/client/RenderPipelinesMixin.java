package com.breakinblocks.graveless.mixin.client;

import com.breakinblocks.graveless.client.render.GhostRenderTypes;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.renderer.RenderPipelines;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(RenderPipelines.class)
public abstract class RenderPipelinesMixin {

    @Inject(method = "getStaticPipelines", at = @At("RETURN"), cancellable = true)
    private static void graveless$addPipelines(CallbackInfoReturnable<List<RenderPipeline>> cir) {
        List<RenderPipeline> pipelines = new ArrayList<>(cir.getReturnValue());
        pipelines.addAll(GhostRenderTypes.pipelines());
        cir.setReturnValue(List.copyOf(pipelines));
    }
}
