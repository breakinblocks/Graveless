package com.breakinblocks.graveless.platform;

import com.breakinblocks.graveless.platform.services.IRegistryFactory;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Function;

public class NeoForgeRegistryFactory implements IRegistryFactory {
    private static IEventBus modBus;

    public static void setModBus(IEventBus bus) {
        modBus = bus;
    }

    @Override
    public <T> Registrar<T> create(ResourceKey<Registry<T>> registry, String modId) {
        return new NeoForgeRegistrar<>(DeferredRegister.create(registry, modId));
    }

    private record NeoForgeRegistrar<T>(DeferredRegister<T> deferred) implements Registrar<T> {
        @Override
        public <I extends T> RegistrySupplier<I> register(String name, Function<Identifier, I> factory) {
            return new NeoForgeRegistrySupplier<>(deferred.register(name, factory::apply));
        }

        @Override
        public void init() {
            deferred.register(modBus);
        }
    }

    private record NeoForgeRegistrySupplier<T, I extends T>(DeferredHolder<T, I> deferred)
            implements RegistrySupplier<I> {
        @Override
        public I get() {
            return deferred.get();
        }

        @Override
        public Identifier getId() {
            return deferred.getId();
        }

        @SuppressWarnings("unchecked")
        @Override
        public Holder<I> holder() {
            return (Holder<I>) deferred;
        }
    }
}
