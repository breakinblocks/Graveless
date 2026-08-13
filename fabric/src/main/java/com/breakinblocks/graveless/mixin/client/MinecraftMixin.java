package com.breakinblocks.graveless.mixin.client;

import com.breakinblocks.graveless.client.GhostInteraction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
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

    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void graveless$onAttack(CallbackInfoReturnable<Boolean> cir) {
        if (player != null && GhostInteraction.onAttackPressed(player)) {
            player.swing(InteractionHand.MAIN_HAND);
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void graveless$onUse(CallbackInfo ci) {
        if (player != null && GhostInteraction.onUsePressed(player, InteractionHand.MAIN_HAND)) {
            player.swing(InteractionHand.MAIN_HAND);
            ci.cancel();
        }
    }
}
