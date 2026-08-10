package com.breakinblocks.graveless.platform;

import com.breakinblocks.graveless.Graveless;
import com.breakinblocks.graveless.platform.services.INetworkHelper;
import com.breakinblocks.graveless.platform.services.IPlatformHelper;
import com.breakinblocks.graveless.platform.services.IRegistryFactory;

import java.util.ServiceLoader;

public final class Services {
    public static final IPlatformHelper PLATFORM = load(IPlatformHelper.class);
    public static final IRegistryFactory REGISTRIES = load(IRegistryFactory.class);
    public static final INetworkHelper NETWORK = load(INetworkHelper.class);

    private Services() {
    }

    public static <T> T load(Class<T> clazz) {
        T loadedService = ServiceLoader.load(clazz, Services.class.getClassLoader())
                .findFirst()
                .orElseThrow(() -> new NullPointerException("Failed to load service for " + clazz.getName()));
        Graveless.LOGGER.debug("Loaded {} for service {}", loadedService, clazz);
        return loadedService;
    }
}
