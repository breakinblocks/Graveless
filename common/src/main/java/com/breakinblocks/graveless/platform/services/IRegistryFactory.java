package com.breakinblocks.graveless.platform.services;

import com.breakinblocks.graveless.platform.Registrar;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;

public interface IRegistryFactory {
    <T> Registrar<T> create(ResourceKey<Registry<T>> registry, String modId);
}
