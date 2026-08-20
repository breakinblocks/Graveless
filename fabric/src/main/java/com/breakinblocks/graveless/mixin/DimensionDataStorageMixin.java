package com.breakinblocks.graveless.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.datafixers.DataFixer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.storage.DimensionDataStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(DimensionDataStorage.class)
public abstract class DimensionDataStorageMixin {

    @WrapOperation(method = "readTagFromDisk",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/util/datafix/DataFixTypes;update(Lcom/mojang/datafixers/DataFixer;Lnet/minecraft/nbt/CompoundTag;II)Lnet/minecraft/nbt/CompoundTag;"))
    private CompoundTag graveless$allowMissingDataFixType(DataFixTypes type, DataFixer fixer, CompoundTag tag,
                                                          int version, int newVersion, Operation<CompoundTag> original) {
        return type == null ? tag : original.call(type, fixer, tag, version, newVersion);
    }
}
