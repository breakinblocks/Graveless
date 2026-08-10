package com.breakinblocks.graveless.platform;

import com.breakinblocks.graveless.platform.services.IRegistryFactory;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class FabricRegistryFactory implements IRegistryFactory {

    @Override
    public <T> Registrar<T> create(ResourceKey<Registry<T>> registry, String modId) {
        return new FabricRegistrar<>(registry, modId);
    }

    @SuppressWarnings("unchecked")
    private static <T> Registry<T> lookup(ResourceKey<Registry<T>> key) {
        return (Registry<T>) BuiltInRegistries.REGISTRY.getValue(key.identifier());
    }

    private static final class FabricRegistrar<T> implements Registrar<T> {
        private final ResourceKey<Registry<T>> registryKey;
        private final String modId;
        private final List<FabricRegistrySupplier<T, ?>> entries = new ArrayList<>();

        private FabricRegistrar(ResourceKey<Registry<T>> registryKey, String modId) {
            this.registryKey = registryKey;
            this.modId = modId;
        }

        @Override
        public <I extends T> RegistrySupplier<I> register(String name, Function<Identifier, I> factory) {
            Identifier id = Identifier.fromNamespaceAndPath(modId, name);
            FabricRegistrySupplier<T, I> entry = new FabricRegistrySupplier<>(registryKey, id, factory.apply(id));
            entries.add(entry);
            return entry;
        }

        @Override
        public void init() {
            Registry<T> registry = lookup(registryKey);
            for (FabricRegistrySupplier<T, ?> entry : entries) {
                entry.bind(registry);
            }
        }
    }

    private static final class FabricRegistrySupplier<T, I extends T> implements RegistrySupplier<I> {
        private final ResourceKey<Registry<T>> registryKey;
        private final Identifier id;
        private final I value;
        private Holder<I> holder;

        private FabricRegistrySupplier(ResourceKey<Registry<T>> registryKey, Identifier id, I value) {
            this.registryKey = registryKey;
            this.id = id;
            this.value = value;
        }

        @SuppressWarnings("unchecked")
        private void bind(Registry<T> registry) {
            Registry.register(registry, id, value);
            holder = (Holder<I>) registry.wrapAsHolder(value);
        }

        @Override
        public I get() {
            return value;
        }

        @Override
        public Identifier getId() {
            return id;
        }

        @SuppressWarnings("unchecked")
        @Override
        public Holder<I> holder() {
            if (holder == null) {
                holder = (Holder<I>) lookup(registryKey).wrapAsHolder(value);
            }
            return holder;
        }
    }
}
