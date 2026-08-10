package com.breakinblocks.graveless.mixin;

import com.breakinblocks.graveless.event.DeathCaptureEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {

    @Inject(method = "die", at = @At("HEAD"))
    private void graveless$captureOnDeath(DamageSource source, CallbackInfo ci) {
        DeathCaptureEvents.onDeath((ServerPlayer) (Object) this, source);
    }

    @Inject(method = "die", at = @At("TAIL"))
    private void graveless$finishOnDeath(DamageSource source, CallbackInfo ci) {
        DeathCaptureEvents.finishDrops((ServerPlayer) (Object) this);
    }
}
