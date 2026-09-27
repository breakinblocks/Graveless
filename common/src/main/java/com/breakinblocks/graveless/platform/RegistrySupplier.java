package com.breakinblocks.graveless.platform;

import java.util.function.Supplier;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;

public interface RegistrySupplier<T> extends Supplier<T> {
    Identifier getId();

    Holder<T> holder();
}
