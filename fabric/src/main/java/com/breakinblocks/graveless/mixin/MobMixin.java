package com.breakinblocks.graveless.mixin;

import com.breakinblocks.graveless.event.SpiritWardEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mob.class)
public abstract class MobMixin {

    @Inject(method = "setTarget", at = @At("HEAD"), cancellable = true)
    private void graveless$respectSpiritWard(LivingEntity target, CallbackInfo ci) {
        if (target != null && !SpiritWardEvents.allowTarget(target)) {
            ci.cancel();
        }
    }
}
