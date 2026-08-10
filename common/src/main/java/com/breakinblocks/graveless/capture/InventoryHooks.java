package com.breakinblocks.graveless.capture;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class InventoryHooks {
    private static final Map<String, InventoryHook> HOOKS = new LinkedHashMap<>();

    static {
        register(new VanillaInventoryHook());
    }

    private InventoryHooks() {
    }

    public static synchronized void register(InventoryHook hook) {
        HOOKS.put(hook.id(), hook);
    }

    public static InventoryHook byId(String id) {
        return HOOKS.get(id);
    }

    public static Collection<InventoryHook> all() {
        return Collections.unmodifiableCollection(HOOKS.values());
    }
}
