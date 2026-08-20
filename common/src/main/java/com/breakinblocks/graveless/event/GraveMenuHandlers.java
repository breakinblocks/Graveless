package com.breakinblocks.graveless.event;

import com.breakinblocks.graveless.Graveless;
import com.breakinblocks.graveless.config.GravelessConfig;
import com.breakinblocks.graveless.data.CapturedEntry;
import com.breakinblocks.graveless.data.DeathRecord;
import com.breakinblocks.graveless.data.GraveProfile;
import com.breakinblocks.graveless.data.GraveStore;
import com.breakinblocks.graveless.net.GravelessNetworking;
import com.breakinblocks.graveless.restore.RestoreEngine;
import com.breakinblocks.graveless.util.GraveBackups;
import com.breakinblocks.graveless.util.SpiritCompassManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import com.breakinblocks.graveless.platform.Services;
import com.breakinblocks.graveless.platform.PayloadContext;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class GraveMenuHandlers {
    public static final int TERRAIN_SIZE = 16;
    public static final int TERRAIN_BELOW = 8;
    public static final int TERRAIN_ABOVE = 8;
    public static final int TERRAIN_HEIGHT = TERRAIN_BELOW + TERRAIN_ABOVE + 1;

    public static void openFor(ServerPlayer viewer) {
        openFor(viewer, viewer.getUUID(), Optional.empty());
    }

    public static void openFor(ServerPlayer viewer, UUID ownerId) {
        openFor(viewer, ownerId, Optional.empty());
    }

    public static void openFor(ServerPlayer viewer, UUID ownerId, Optional<UUID> focusId) {
        boolean admin = isAdmin(viewer);
        MinecraftServer server = viewer.level().getServer();
        GraveStore store = GraveStore.get(server);
        GraveProfile profile = store.profile(ownerId);
        if (!admin && !profile.canAccess(ownerId, viewer.getUUID())) {
            return;
        }
        List<GravelessNetworking.GraveSummary> summaries = new ArrayList<>();
        List<DeathRecord> records = profile.records();
        for (int i = records.size() - 1; i >= 0; i--) {
            DeathRecord record = records.get(i);
            summaries.add(new GravelessNetworking.GraveSummary(
                    record.id(), record.pos(), record.epochMillis(), record.gameTime(),
                    record.cause(), record.itemCount(), record.xp()));
        }
        List<GravelessNetworking.PlayerEntry> players = server.getPlayerList().getPlayers().stream()
                .map(p -> new GravelessNetworking.PlayerEntry(p.getUUID(), p.getName().getString()))
                .sorted(Comparator.comparing(entry -> entry.name().toLowerCase()))
                .toList();
        List<GravelessNetworking.PlayerEntry> allowed = profile.allowed().stream()
                .map(id -> new GravelessNetworking.PlayerEntry(id, GhostSyncEvents.ownerName(server, id)))
                .toList();
        Services.NETWORK.sendToPlayer(viewer, new GravelessNetworking.GraveListPayload(
                ownerId, GhostSyncEvents.ownerName(server, ownerId), summaries, admin, profile.isEnabled(),
                Optional.ofNullable(profile.trackedRecordId()), players, allowed, focusId));
    }

    public static void handleOpenRequest(GravelessNetworking.GraveOpenPayload payload, PayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            MinecraftServer server = player.level().getServer();
            GhostSyncEvents.FoundRecord found = GhostSyncEvents.findRecord(GraveStore.get(server), payload.recordId());
            if (found == null) {
                return;
            }
            if (!isAdmin(player) && (!found.profile().canAccess(found.ownerId(), player.getUUID())
                    || !GhostSyncEvents.canReach(player, found.record()))) {
                return;
            }
            openFor(player, found.ownerId(), Optional.of(found.record().id()));
        });
    }

    public static void handleExtract(GravelessNetworking.GraveExtractPayload payload, PayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer actor)) {
                return;
            }
            MinecraftServer server = actor.level().getServer();
            GraveStore store = GraveStore.get(server);
            GraveProfile profile = store.profile(payload.ownerId());
            DeathRecord record = profile.findRecord(payload.recordId());
            if (record == null || !canTakeFrom(actor, payload.ownerId(), profile, record)) {
                return;
            }
            int seen = -1;
            int entryIndex = -1;
            for (int i = 0; i < record.entries().size(); i++) {
                if (!record.entries().get(i).stack().isEmpty()) {
                    seen++;
                    if (seen == payload.itemIndex()) {
                        entryIndex = i;
                        break;
                    }
                }
            }
            if (entryIndex < 0) {
                return;
            }
            CapturedEntry entry = record.entries().remove(entryIndex);
            ItemStack stack = entry.stack().copy();
            Graveless.LOGGER.info("{} extracted {} x{} from {}'s grave {}",
                    actor.getName().getString(), stack.getItem(), stack.getCount(),
                    GhostSyncEvents.ownerName(server, payload.ownerId()), record.id());
            if (!actor.getInventory().add(stack)) {
                actor.drop(stack, false);
            }
            if (record.isEmpty()) {
                profile.records().remove(record);
                GhostSyncEvents.broadcastRemoval(server, record.id());
                refreshOwner(server, payload.ownerId(), actor);
            }
            store.setDirty();
            openFor(actor, payload.ownerId(), Optional.of(record.id()));
        });
    }

    private static boolean canTakeFrom(ServerPlayer actor, UUID ownerId, GraveProfile profile, DeathRecord record) {
        if (isAdmin(actor)) {
            return true;
        }
        return profile.canAccess(ownerId, actor.getUUID()) && GhostSyncEvents.canReach(actor, record);
    }

    public static void handleProfileAction(GravelessNetworking.ProfileActionPayload payload, PayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            MinecraftServer server = player.level().getServer();
            GraveStore store = GraveStore.get(server);
            GraveProfile profile = store.profile(player.getUUID());
            switch (payload.action()) {
                case GravelessNetworking.ProfileActionPayload.ACTION_TOGGLE_ENABLED -> {
                    profile.setEnabled(!profile.isEnabled());
                    player.sendSystemMessage(Component.translatable(
                            profile.isEnabled() ? "graveless.command.enabled" : "graveless.command.disabled")
                            .withStyle(ChatFormatting.AQUA));
                }
                case GravelessNetworking.ProfileActionPayload.ACTION_ALLOW ->
                        payload.target().ifPresent(target -> {
                            if (!target.equals(player.getUUID())) {
                                profile.allowed().add(target);
                            }
                        });
                case GravelessNetworking.ProfileActionPayload.ACTION_DENY ->
                        payload.target().ifPresent(profile.allowed()::remove);
                case GravelessNetworking.ProfileActionPayload.ACTION_CLEAR -> profile.allowed().clear();
                default -> {
                }
            }
            store.setDirty();
            openFor(player, player.getUUID());
        });
    }

    public static void handleBackupListRequest(GravelessNetworking.BackupListRequestPayload payload, PayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer admin) || !isAdmin(admin)) {
                return;
            }
            sendBackupList(admin, payload.ownerId());
        });
    }

    public static void handleBackupRevive(GravelessNetworking.BackupRevivePayload payload, PayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer admin) || !isAdmin(admin)) {
                return;
            }
            MinecraftServer server = admin.level().getServer();
            Path match = GraveBackups.list(server, payload.ownerId()).stream()
                    .filter(path -> path.getFileName().toString().equals(payload.fileName()))
                    .findFirst().orElse(null);
            if (match == null) {
                return;
            }
            DeathRecord revived = reviveBackup(server, payload.ownerId(), match);
            if (revived != null) {
                admin.sendSystemMessage(Component.translatable("graveless.command.restorebackup.done",
                        payload.fileName(), revived.itemCount(),
                        GhostSyncEvents.ownerName(server, payload.ownerId())).withStyle(ChatFormatting.AQUA));
            }
            sendBackupList(admin, payload.ownerId());
            openFor(admin, payload.ownerId());
        });
    }

    public static DeathRecord reviveBackup(MinecraftServer server, UUID ownerId, Path file) {
        DeathRecord record = GraveBackups.read(server, file);
        if (record == null) {
            return null;
        }
        GraveStore store = GraveStore.get(server);
        GraveProfile profile = store.profile(ownerId);
        DeathRecord revived = new DeathRecord(UUID.randomUUID(), record.pos(), record.gameTime(),
                record.epochMillis(), record.cause(), record.xp(), record.entries(), record.terrain());
        profile.records().add(revived);
        int max = GravelessConfig.SERVER.maxRecordsPerPlayer.get();
        while (profile.records().size() > max) {
            profile.records().removeFirst();
        }
        store.setDirty();
        ServerPlayer online = server.getPlayerList().getPlayer(ownerId);
        if (online != null) {
            SpiritCompassManager.refresh(online);
        }
        return revived;
    }

    private static void sendBackupList(ServerPlayer admin, UUID ownerId) {
        MinecraftServer server = admin.level().getServer();
        List<GravelessNetworking.BackupEntry> entries = new ArrayList<>();
        for (Path file : GraveBackups.list(server, ownerId)) {
            DeathRecord record = GraveBackups.read(server, file);
            if (record != null) {
                entries.add(new GravelessNetworking.BackupEntry(file.getFileName().toString(),
                        record.epochMillis(), record.gameTime(), record.itemCount(), record.pos()));
            }
        }
        Services.NETWORK.sendToPlayer(admin, new GravelessNetworking.BackupListPayload(ownerId, entries));
    }

    public static void handleViewRequest(GravelessNetworking.GraveViewRequestPayload payload, PayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player && isAdmin(player)) {
                openFor(player, payload.ownerId());
            }
        });
    }

    public static void handleDetailRequest(GravelessNetworking.GraveDetailRequestPayload payload, PayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            if (!payload.ownerId().equals(player.getUUID()) && !isAdmin(player)) {
                return;
            }
            MinecraftServer server = player.level().getServer();
            GraveProfile profile = GraveStore.get(server).profile(payload.ownerId());
            DeathRecord record = profile.findRecord(payload.recordId());
            if (record == null) {
                return;
            }
            List<ItemStack> items = new ArrayList<>();
            for (CapturedEntry entry : record.entries()) {
                if (!entry.stack().isEmpty()) {
                    items.add(entry.stack().copy());
                }
            }
            int total = TERRAIN_SIZE * TERRAIN_SIZE * TERRAIN_HEIGHT;
            List<Integer> terrain;
            if (record.terrain().length == total) {
                terrain = new ArrayList<>(total);
                for (int id : record.terrain()) {
                    terrain.add(id);
                }
            } else {
                terrain = buildTerrain(server, record);
            }
            Services.NETWORK.sendToPlayer(player, new GravelessNetworking.GraveDetailPayload(
                    record.id(), items, terrain));
        });
    }

    public static int[] captureTerrainSnapshot(MinecraftServer server, DeathRecord record) {
        List<Integer> terrain = buildTerrain(server, record);
        int[] snapshot = new int[terrain.size()];
        for (int i = 0; i < snapshot.length; i++) {
            snapshot[i] = terrain.get(i);
        }
        return snapshot;
    }

    public static void handleAction(GravelessNetworking.GraveActionPayload payload, PayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            boolean admin = isAdmin(player);
            boolean own = payload.ownerId().equals(player.getUUID());
            MinecraftServer server = player.level().getServer();
            GraveStore store = GraveStore.get(server);
            GraveProfile profile = store.profile(payload.ownerId());
            if (!own && !admin && !profile.canAccess(payload.ownerId(), player.getUUID())) {
                return;
            }
            DeathRecord record = profile.findRecord(payload.recordId());
            if (record == null) {
                return;
            }
            switch (payload.action()) {
                case GravelessNetworking.GraveActionPayload.ACTION_DELETE -> {
                    if (own || admin) {
                        deleteRecord(server, store, profile, payload.ownerId(), record, player);
                    }
                }
                case GravelessNetworking.GraveActionPayload.ACTION_TELEPORT -> {
                    if (admin) {
                        teleport(server, record, player);
                    }
                }
                case GravelessNetworking.GraveActionPayload.ACTION_TRACK -> {
                    if (own) {
                        profile.setTrackedRecordId(record.id());
                        store.setDirty();
                        SpiritCompassManager.refresh(player);
                        openFor(player, payload.ownerId());
                    }
                }
                case GravelessNetworking.GraveActionPayload.ACTION_RESTORE -> {
                    if (admin) {
                        restoreToOwner(server, store, profile, payload.ownerId(), record, player);
                    }
                }
                case GravelessNetworking.GraveActionPayload.ACTION_CLAIM_XP -> {
                    if (canTakeFrom(player, payload.ownerId(), profile, record)) {
                        claimXp(server, store, profile, payload.ownerId(), record, player);
                    }
                }
                default -> {
                }
            }
        });
    }

    private static boolean isAdmin(ServerPlayer player) {
        return player.hasPermissions(2);
    }

    private static void deleteRecord(MinecraftServer server, GraveStore store, GraveProfile profile,
                                     UUID ownerId, DeathRecord record, ServerPlayer actor) {
        profile.records().remove(record);
        store.setDirty();
        GhostSyncEvents.broadcastRemoval(server, record.id());
        refreshOwner(server, ownerId, actor);
        actor.sendSystemMessage(Component.translatable("graveless.menu.deleted",
                record.itemCount()).withStyle(ChatFormatting.GRAY));
        openFor(actor, ownerId);
    }

    private static void claimXp(MinecraftServer server, GraveStore store, GraveProfile profile,
                                UUID ownerId, DeathRecord record, ServerPlayer actor) {
        int xp = record.xp();
        if (xp <= 0) {
            return;
        }
        record.setXp(0);
        actor.giveExperiencePoints(xp);
        if (record.isEmpty()) {
            profile.records().remove(record);
            GhostSyncEvents.broadcastRemoval(server, record.id());
            refreshOwner(server, ownerId, actor);
        }
        store.setDirty();
        actor.sendSystemMessage(Component.translatable("graveless.menu.xp_claimed", xp)
                .withStyle(ChatFormatting.AQUA));
        openFor(actor, ownerId, Optional.of(record.id()));
    }

    private static void restoreToOwner(MinecraftServer server, GraveStore store, GraveProfile profile,
                                       UUID ownerId, DeathRecord record, ServerPlayer actor) {
        ServerPlayer owner = server.getPlayerList().getPlayer(ownerId);
        if (owner == null) {
            actor.sendSystemMessage(Component.translatable("graveless.menu.owner_offline")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        RestoreEngine.Result result = RestoreEngine.claim(owner, profile, record, store);
        if (result.recordRemoved()) {
            GhostSyncEvents.broadcastRemoval(server, record.id());
        }
        SpiritCompassManager.refresh(owner);
        actor.sendSystemMessage(Component.translatable("graveless.command.restore.done",
                result.restored(), owner.getName().getString(), result.remaining()).withStyle(ChatFormatting.AQUA));
        if (owner != actor) {
            owner.sendSystemMessage(Component.translatable("graveless.restore.received",
                    result.restored()).withStyle(ChatFormatting.AQUA));
        }
        openFor(actor, ownerId);
    }

    private static void refreshOwner(MinecraftServer server, UUID ownerId, ServerPlayer actor) {
        if (ownerId.equals(actor.getUUID())) {
            SpiritCompassManager.refresh(actor);
            return;
        }
        ServerPlayer owner = server.getPlayerList().getPlayer(ownerId);
        if (owner != null) {
            SpiritCompassManager.refresh(owner);
        }
    }

    private static void teleport(MinecraftServer server, DeathRecord record, ServerPlayer player) {
        ServerLevel level = server.getLevel(record.pos().dimension());
        if (level == null) {
            return;
        }
        BlockPos anchor = GhostSyncEvents.anchor(level, record.pos().pos());
        player.teleportTo(level, anchor.getX() + 0.5, anchor.getY(), anchor.getZ() + 0.5,
                Set.of(), player.getYRot(), player.getXRot());
        level.playSound(null, anchor, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.6F, 1.1F);
    }

    private static List<Integer> buildTerrain(MinecraftServer server, DeathRecord record) {
        int total = TERRAIN_SIZE * TERRAIN_SIZE * TERRAIN_HEIGHT;
        List<Integer> terrain = new ArrayList<>(total);
        int air = Block.getId(Blocks.AIR.defaultBlockState());
        ServerLevel level = server.getLevel(record.pos().dimension());
        if (level == null) {
            for (int i = 0; i < total; i++) {
                terrain.add(air);
            }
            return terrain;
        }
        BlockPos anchor = GhostSyncEvents.anchor(level, record.pos().pos());
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int layer = 0; layer < TERRAIN_HEIGHT; layer++) {
            int y = anchor.getY() - TERRAIN_BELOW + layer;
            for (int dz = 0; dz < TERRAIN_SIZE; dz++) {
                for (int dx = 0; dx < TERRAIN_SIZE; dx++) {
                    if (y < level.getMinBuildHeight() || y > level.getMaxBuildHeight() - 1) {
                        terrain.add(air);
                        continue;
                    }
                    int x = anchor.getX() + dx - TERRAIN_SIZE / 2;
                    int z = anchor.getZ() + dz - TERRAIN_SIZE / 2;
                    ChunkAccess chunk = level.getChunk(x >> 4, z >> 4, ChunkStatus.FULL, false);
                    if (chunk == null) {
                        terrain.add(air);
                        continue;
                    }
                    terrain.add(Block.getId(previewState(chunk.getBlockState(cursor.set(x, y, z)))));
                }
            }
        }
        return terrain;
    }

    private static BlockState previewState(BlockState state) {
        if (state.isAir()) {
            return state;
        }
        if (state.getBlock() instanceof LiquidBlock) {
            return state.getFluidState().is(FluidTags.LAVA)
                    ? Blocks.MAGMA_BLOCK.defaultBlockState()
                    : Blocks.BLUE_STAINED_GLASS.defaultBlockState();
        }
        if (state.getRenderShape() != RenderShape.MODEL) {
            return Blocks.AIR.defaultBlockState();
        }
        return state;
    }

    private GraveMenuHandlers() {
    }
}
