package com.breakinblocks.graveless.client;

import net.minecraft.core.BlockPos;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class GhostClientManager {
    public record ClientGhost(UUID recordId, UUID ownerId, String ownerName, BlockPos pos, int itemCount) {
    }

    private static final Map<UUID, ClientGhost> GHOSTS = new ConcurrentHashMap<>();

    private GhostClientManager() {
    }

    public static void add(ClientGhost ghost) {
        GHOSTS.put(ghost.recordId(), ghost);
    }

    public static void remove(UUID recordId) {
        GHOSTS.remove(recordId);
    }

    public static void clear() {
        GHOSTS.clear();
    }

    public static Iterable<ClientGhost> all() {
        return GHOSTS.values();
    }

    public static boolean isEmpty() {
        return GHOSTS.isEmpty();
    }
}
