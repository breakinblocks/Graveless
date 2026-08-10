package com.breakinblocks.graveless.platform;

import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;

import java.util.function.Supplier;

public interface RegistrySupplier<T> extends Supplier<T> {
    Identifier getId();

    Holder<T> holder();
}
