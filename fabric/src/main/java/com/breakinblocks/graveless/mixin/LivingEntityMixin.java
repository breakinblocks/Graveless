package com.breakinblocks.graveless.mixin;

import com.breakinblocks.graveless.event.DeathCaptureEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

    @Inject(method = "dropExperience", at = @At("HEAD"), cancellable = true)
    private void graveless$suppressExperience(ServerLevel level, Entity killer, CallbackInfo ci) {
        if ((Object) this instanceof ServerPlayer player && DeathCaptureEvents.hasPending(player)) {
            ci.cancel();
        }
    }

    @Inject(
            method = "drop(Lnet/minecraft/world/item/ItemStack;ZZ)Lnet/minecraft/world/entity/item/ItemEntity;",
            at = @At("HEAD"),
            cancellable = true)
    private void graveless$captureDeathDrop(
            ItemStack stack, boolean randomly, boolean thrownFromHand, CallbackInfoReturnable<ItemEntity> cir) {
        if ((Object) this instanceof ServerPlayer player && DeathCaptureEvents.captureDrop(player, stack)) {
            stack.setCount(0);
            cir.setReturnValue(null);
        }
    }
}
