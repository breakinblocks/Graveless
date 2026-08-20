package com.breakinblocks.graveless.data;

import com.breakinblocks.graveless.Graveless;
import com.mojang.serialization.Codec;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class GraveStore extends SavedData {
    private static final String DATA_NAME = Graveless.MOD_ID + "_graves";
    private static final String PROFILES_KEY = "profiles";

    private static final Codec<Map<UUID, GraveProfile>> CODEC =
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, GraveProfile.CODEC);

    public static final SavedData.Factory<GraveStore> FACTORY =
            new SavedData.Factory<>(GraveStore::new, GraveStore::load, null);

    private final Map<UUID, GraveProfile> profiles = new HashMap<>();

    public GraveStore() {
    }

    private GraveStore(Map<UUID, GraveProfile> profiles) {
        this.profiles.putAll(profiles);
    }

    private static GraveStore load(CompoundTag tag, HolderLookup.Provider registries) {
        RegistryOps<Tag> ops = registries.createSerializationContext(NbtOps.INSTANCE);
        return CODEC.parse(ops, tag.getCompound(PROFILES_KEY))
                .resultOrPartial(error -> Graveless.LOGGER.error("Failed to load grave store: {}", error))
                .map(GraveStore::new)
                .orElseGet(GraveStore::new);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        RegistryOps<Tag> ops = registries.createSerializationContext(NbtOps.INSTANCE);
        CODEC.encodeStart(ops, Map.copyOf(profiles))
                .resultOrPartial(error -> Graveless.LOGGER.error("Failed to save grave store: {}", error))
                .ifPresent(encoded -> tag.put(PROFILES_KEY, encoded));
        return tag;
    }

    public static GraveStore get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public GraveProfile profile(UUID playerId) {
        return profiles.computeIfAbsent(playerId, id -> new GraveProfile());
    }

    public Map<UUID, GraveProfile> profiles() {
        return profiles;
    }
}
