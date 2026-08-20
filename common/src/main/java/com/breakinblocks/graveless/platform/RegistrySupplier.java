package com.breakinblocks.graveless.platform;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Supplier;

public interface RegistrySupplier<T> extends Supplier<T> {
    ResourceLocation getId();

    Holder<T> holder();
}
