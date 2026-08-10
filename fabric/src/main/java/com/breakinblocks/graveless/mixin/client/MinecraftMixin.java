package com.breakinblocks.graveless.mixin.client;

import com.breakinblocks.graveless.client.GhostInteraction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.jspecify.annotations.Nullable;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Shadow
    public @Nullable LocalPlayer player;

    @Shadow
    public @Nullable HitResult hitResult;

    @Inject(method = "startAttack", at = @At("HEAD"))
    private void graveless$onLeftClickEmpty(CallbackInfoReturnable<Boolean> cir) {
        if (player != null && graveless$missedEverything()) {
            GhostInteraction.onLeftClickEmpty(player);
        }
    }

    @Inject(method = "startUseItem", at = @At("HEAD"))
    private void graveless$onRightClickEmpty(CallbackInfo ci) {
        if (player != null && graveless$missedEverything()) {
            GhostInteraction.onRightClickEmpty(player, InteractionHand.MAIN_HAND);
        }
    }

    private boolean graveless$missedEverything() {
        return hitResult == null || hitResult.getType() == HitResult.Type.MISS;
    }
}
