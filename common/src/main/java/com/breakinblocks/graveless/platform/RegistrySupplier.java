package com.breakinblocks.graveless.platform;

import java.util.function.Supplier;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;

public interface RegistrySupplier<T> extends Supplier<T> {
    ResourceLocation getId();

    Holder<T> holder();
}
