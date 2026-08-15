package com.breakinblocks.graveless.event;

import com.breakinblocks.graveless.config.GravelessConfig;
import com.breakinblocks.graveless.data.DeathRecord;
import com.breakinblocks.graveless.data.GraveProfile;
import com.breakinblocks.graveless.data.GraveStore;
import com.breakinblocks.graveless.net.GravelessNetworking;
import com.breakinblocks.graveless.restore.RestoreEngine;
import com.breakinblocks.graveless.util.SpiritCompassManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import com.breakinblocks.graveless.platform.Services;
import com.breakinblocks.graveless.platform.PayloadContext;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class GhostSyncEvents {
    private static final Map<UUID, Set<UUID>> KNOWN = new HashMap<>();

    public record FoundRecord(UUID ownerId, GraveProfile profile, DeathRecord record) {
    }

    public static void onPlayerTick(ServerPlayer player) {
        if ((player.tickCount + player.getId()) % 20 == 0) {
            syncGhosts(player);
        }
    }

    public static void forget(ServerPlayer player) {
        KNOWN.remove(player.getUUID());
    }

    public static void handleClaimRequest(GravelessNetworking.ClaimRequestPayload payload, PayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            MinecraftServer server = player.level().getServer();
            GraveStore store = GraveStore.get(server);
            FoundRecord found = findRecord(store, payload.recordId());
            if (found == null || !found.profile().canAccess(found.ownerId(), player.getUUID())) {
                return;
            }
            DeathRecord record = found.record();
            if (!inClaimRange(player, record)) {
                return;
            }
            BlockPos anchor = anchor(player.level(), record.pos().pos());
            if (GravelessConfig.SERVER.requireLineOfSight.get() && !hasLineOfSight(player, anchor)) {
                player.sendSystemMessage(Component.translatable("graveless.claim.blocked")
                        .withStyle(ChatFormatting.GRAY));
                return;
            }
            completeClaim(player, found);
        });
    }

    private static void completeClaim(ServerPlayer player, FoundRecord found) {
        MinecraftServer server = player.level().getServer();
        GraveStore store = GraveStore.get(server);
        RestoreEngine.Result result = RestoreEngine.claim(player, found.profile(), found.record(), store);
        ServerLevel level = player.level();
        BlockPos anchor = anchor(level, found.record().pos().pos());
        if (result.recordRemoved()) {
            player.sendSystemMessage(Component.translatable("graveless.restore.complete",
                    result.restored()).withStyle(ChatFormatting.AQUA));
            broadcastRemoval(server, found.record().id());
            level.playSound(null, anchor, SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6F, 1.3F);
        } else {
            player.sendSystemMessage(Component.translatable("graveless.restore.partial",
                    result.restored(), result.remaining()).withStyle(ChatFormatting.YELLOW));
            level.playSound(null, anchor, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.8F, 0.6F);
        }
        Vec3 center = Vec3.atCenterOf(anchor);
        level.sendParticles(ParticleTypes.SOUL, center.x, center.y + 0.5, center.z, 30, 0.4, 0.8, 0.4, 0.02);
        SpiritWardEvents.beginWearOff(player);
        SpiritCompassManager.refresh(player);
        if (!found.ownerId().equals(player.getUUID())) {
            ServerPlayer owner = server.getPlayerList().getPlayer(found.ownerId());
            if (owner != null) {
                SpiritCompassManager.refresh(owner);
            }
        }
    }

    private static void syncGhosts(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        GraveStore store = GraveStore.get(server);
        int radius = GravelessConfig.SERVER.visibilityRadius.get();
        double radiusSqr = (double) radius * radius;
        UUID viewerId = player.getUUID();

        Map<UUID, GravelessNetworking.GhostAddPayload> visible = new HashMap<>();
        store.profiles().forEach((ownerId, profile) -> {
            if (!profile.canAccess(ownerId, viewerId)) {
                return;
            }
            for (DeathRecord record : profile.records()) {
                if (!record.pos().dimension().equals(player.level().dimension())) {
                    continue;
                }
                BlockPos anchor = anchor(player.level(), record.pos().pos());
                if (Vec3.atCenterOf(anchor).distanceToSqr(player.position()) > radiusSqr) {
                    continue;
                }
                String ownerName = ownerName(server, ownerId);
                visible.put(record.id(), new GravelessNetworking.GhostAddPayload(
                        record.id(), ownerId, ownerName, anchor, record.itemCount()));
            }
        });

        Set<UUID> known = KNOWN.computeIfAbsent(viewerId, id -> new HashSet<>());
        for (UUID recordId : Set.copyOf(known)) {
            if (!visible.containsKey(recordId)) {
                known.remove(recordId);
                Services.NETWORK.sendToPlayer(player, new GravelessNetworking.GhostRemovePayload(recordId));
            }
        }
        visible.forEach((recordId, payload) -> {
            if (known.add(recordId)) {
                Services.NETWORK.sendToPlayer(player, payload);
            }
        });
    }

    public static void broadcastRemoval(MinecraftServer server, UUID recordId) {
        KNOWN.forEach((viewerId, records) -> {
            if (records.remove(recordId)) {
                ServerPlayer viewer = server.getPlayerList().getPlayer(viewerId);
                if (viewer != null) {
                    Services.NETWORK.sendToPlayer(viewer, new GravelessNetworking.GhostRemovePayload(recordId));
                }
            }
        });
    }

    public static boolean canReach(ServerPlayer player, DeathRecord record) {
        if (!inClaimRange(player, record)) {
            return false;
        }
        return !GravelessConfig.SERVER.requireLineOfSight.get()
                || hasLineOfSight(player, anchor(player.level(), record.pos().pos()));
    }

    private static boolean inClaimRange(ServerPlayer player, DeathRecord record) {
        if (!record.pos().dimension().equals(player.level().dimension())) {
            return false;
        }
        BlockPos anchor = anchor(player.level(), record.pos().pos());
        int range = GravelessConfig.SERVER.claimRange.get();
        return Vec3.atCenterOf(anchor).distanceToSqr(player.position()) <= (double) (range + 2) * (range + 2);
    }

    public static FoundRecord findRecord(GraveStore store, UUID recordId) {
        for (Map.Entry<UUID, GraveProfile> entry : store.profiles().entrySet()) {
            DeathRecord record = entry.getValue().findRecord(recordId);
            if (record != null) {
                return new FoundRecord(entry.getKey(), entry.getValue(), record);
            }
        }
        return null;
    }

    public static String ownerName(MinecraftServer server, UUID ownerId) {
        ServerPlayer online = server.getPlayerList().getPlayer(ownerId);
        if (online != null) {
            return online.getName().getString();
        }
        return server.services().nameToIdCache().get(ownerId)
                .map(profile -> profile.name())
                .orElse("");
    }

    public static BlockPos anchor(ServerLevel level, BlockPos pos) {
        int y = pos.getY();
        if (y < level.getMinY() + 1) {
            y = Math.min(level.getMinY() + 48, level.getMaxY() - 2);
        } else if (y > level.getMaxY() - 2) {
            y = level.getMaxY() - 2;
        }
        return y == pos.getY() ? pos : new BlockPos(pos.getX(), y, pos.getZ());
    }

    private static boolean hasLineOfSight(ServerPlayer player, BlockPos anchor) {
        Vec3 from = player.getEyePosition();
        Vec3 to = Vec3.atLowerCornerOf(anchor).add(0.5, 1.3, 0.5);
        BlockHitResult hit = player.level().clip(new ClipContext(
                from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        return hit.getType() != HitResult.Type.BLOCK || hit.getLocation().distanceToSqr(to) < 1.5;
    }
}
