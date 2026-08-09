package com.breakinblocks.graveless.data;

import com.breakinblocks.graveless.Graveless;
import com.mojang.serialization.Codec;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class GraveStore extends SavedData {
    private static final Codec<GraveStore> CODEC = Codec.unboundedMap(UUIDUtil.STRING_CODEC, GraveProfile.CODEC)
            .xmap(GraveStore::new, store -> Map.copyOf(store.profiles));

    public static final SavedDataType<GraveStore> TYPE =
            new SavedDataType<>(Graveless.id("graves"), GraveStore::new, CODEC);

    private final Map<UUID, GraveProfile> profiles = new HashMap<>();

    public GraveStore() {
    }

    private GraveStore(Map<UUID, GraveProfile> profiles) {
        this.profiles.putAll(profiles);
    }

    public static GraveStore get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public GraveProfile profile(UUID playerId) {
        return profiles.computeIfAbsent(playerId, id -> new GraveProfile());
    }

    public Map<UUID, GraveProfile> profiles() {
        return profiles;
    }
}
