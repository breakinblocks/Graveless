package com.breakinblocks.graveless.client.render;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerSkin;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class GhostSkins {
    private static final Map<UUID, PlayerSkin> CACHE = new ConcurrentHashMap<>();
    private static final Set<UUID> PENDING = ConcurrentHashMap.newKeySet();

    private GhostSkins() {
    }

    public static PlayerSkin get(UUID ownerId, String ownerName) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null) {
            Player online = minecraft.level.getPlayerByUUID(ownerId);
            if (online instanceof AbstractClientPlayer clientPlayer) {
                return clientPlayer.getSkin();
            }
        }
        PlayerSkin cached = CACHE.get(ownerId);
        if (cached != null) {
            return cached;
        }
        if (PENDING.add(ownerId)) {
            String name = ownerName == null || ownerName.isBlank() ? "Ghost" : ownerName;
            GameProfile profile = new GameProfile(ownerId, name);
            minecraft.getSkinManager().get(profile)
                    .thenAccept(optional -> optional.ifPresent(skin -> CACHE.put(ownerId, skin)));
        }
        return DefaultPlayerSkin.get(ownerId);
    }

    public static void clear() {
        CACHE.clear();
        PENDING.clear();
    }
}
