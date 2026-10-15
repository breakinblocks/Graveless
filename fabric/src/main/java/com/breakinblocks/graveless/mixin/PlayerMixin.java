package com.breakinblocks.graveless.mixin;

import com.breakinblocks.graveless.event.DeathCaptureEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayer.class)
public abstract class PlayerMixin {

    @Inject(method = "drop(Lnet/minecraft/world/item/ItemStack;ZLnet/minecraft/util/Prediction;)Lnet/minecraft/world/entity/item/ItemEntity;",
            at = @At("HEAD"), cancellable = true)
    private void graveless$captureDeathDrop(ItemStack stack, boolean thrownFromHand, Prediction prediction,
                                            CallbackInfoReturnable<ItemEntity> cir) {
        if (DeathCaptureEvents.captureDrop((ServerPlayer) (Object) this, stack)) {
            stack.setCount(0);
            cir.setReturnValue(null);
        }
    }
}
