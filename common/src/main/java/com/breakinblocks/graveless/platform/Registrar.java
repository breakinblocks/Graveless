package com.breakinblocks.graveless.platform;

import net.minecraft.resources.ResourceLocation;

import java.util.function.Function;
import java.util.function.Supplier;

public interface Registrar<T> {
    <I extends T> RegistrySupplier<I> register(String name, Function<ResourceLocation, I> factory);

    default <I extends T> RegistrySupplier<I> register(String name, Supplier<I> factory) {
        return register(name, registryName -> factory.get());
    }

    void init();
}
