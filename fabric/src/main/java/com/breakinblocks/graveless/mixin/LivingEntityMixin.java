package com.breakinblocks.graveless.mixin;

import com.breakinblocks.graveless.event.DeathCaptureEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

    @Inject(method = "dropExperience", at = @At("HEAD"), cancellable = true)
    private void graveless$suppressExperience(CallbackInfo ci) {
        if ((Object) this instanceof ServerPlayer player && DeathCaptureEvents.hasPending(player)) {
            ci.cancel();
        }
    }
}
