package com.breakinblocks.graveless.net;

import com.breakinblocks.graveless.Graveless;
import com.breakinblocks.graveless.event.GhostSyncEvents;
import com.breakinblocks.graveless.event.GraveMenuHandlers;
import com.breakinblocks.graveless.platform.PayloadContext;
import com.breakinblocks.graveless.platform.Services;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class GravelessNetworking {

    public static void register() {
        Services.NETWORK.registerToClient(GhostAddPayload.TYPE, GhostAddPayload.STREAM_CODEC,
                ClientPayloadDispatch::dispatch);
        Services.NETWORK.registerToClient(GhostRemovePayload.TYPE, GhostRemovePayload.STREAM_CODEC,
                ClientPayloadDispatch::dispatch);
        Services.NETWORK.registerToClient(GraveListPayload.TYPE, GraveListPayload.STREAM_CODEC,
                ClientPayloadDispatch::dispatch);
        Services.NETWORK.registerToClient(GraveDetailPayload.TYPE, GraveDetailPayload.STREAM_CODEC,
                ClientPayloadDispatch::dispatch);
        Services.NETWORK.registerToClient(BackupListPayload.TYPE, BackupListPayload.STREAM_CODEC,
                ClientPayloadDispatch::dispatch);

        Services.NETWORK.registerToServer(ClaimRequestPayload.TYPE, ClaimRequestPayload.STREAM_CODEC,
                GhostSyncEvents::handleClaimRequest);
        Services.NETWORK.registerToServer(GraveDetailRequestPayload.TYPE, GraveDetailRequestPayload.STREAM_CODEC,
                GraveMenuHandlers::handleDetailRequest);
        Services.NETWORK.registerToServer(GraveActionPayload.TYPE, GraveActionPayload.STREAM_CODEC,
                GraveMenuHandlers::handleAction);
        Services.NETWORK.registerToServer(GraveViewRequestPayload.TYPE, GraveViewRequestPayload.STREAM_CODEC,
                GraveMenuHandlers::handleViewRequest);
        Services.NETWORK.registerToServer(GraveOpenPayload.TYPE, GraveOpenPayload.STREAM_CODEC,
                GraveMenuHandlers::handleOpenRequest);
        Services.NETWORK.registerToServer(GraveExtractPayload.TYPE, GraveExtractPayload.STREAM_CODEC,
                GraveMenuHandlers::handleExtract);
        Services.NETWORK.registerToServer(ProfileActionPayload.TYPE, ProfileActionPayload.STREAM_CODEC,
                GraveMenuHandlers::handleProfileAction);
        Services.NETWORK.registerToServer(BackupListRequestPayload.TYPE, BackupListRequestPayload.STREAM_CODEC,
                GraveMenuHandlers::handleBackupListRequest);
        Services.NETWORK.registerToServer(BackupRevivePayload.TYPE, BackupRevivePayload.STREAM_CODEC,
                GraveMenuHandlers::handleBackupRevive);
    }

    public record GhostAddPayload(UUID recordId, UUID ownerId, String ownerName, BlockPos pos, int itemCount)
            implements CustomPacketPayload {
        public static final Type<GhostAddPayload> TYPE = new Type<>(Graveless.id("ghost_add"));

        public static final StreamCodec<FriendlyByteBuf, GhostAddPayload> STREAM_CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, GhostAddPayload::recordId,
                UUIDUtil.STREAM_CODEC, GhostAddPayload::ownerId,
                ByteBufCodecs.STRING_UTF8, GhostAddPayload::ownerName,
                BlockPos.STREAM_CODEC, GhostAddPayload::pos,
                ByteBufCodecs.VAR_INT, GhostAddPayload::itemCount,
                GhostAddPayload::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record GhostRemovePayload(UUID recordId) implements CustomPacketPayload {
        public static final Type<GhostRemovePayload> TYPE = new Type<>(Graveless.id("ghost_remove"));

        public static final StreamCodec<FriendlyByteBuf, GhostRemovePayload> STREAM_CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, GhostRemovePayload::recordId,
                GhostRemovePayload::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record ClaimRequestPayload(UUID recordId) implements CustomPacketPayload {
        public static final Type<ClaimRequestPayload> TYPE = new Type<>(Graveless.id("claim_request"));

        public static final StreamCodec<FriendlyByteBuf, ClaimRequestPayload> STREAM_CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, ClaimRequestPayload::recordId,
                ClaimRequestPayload::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record GraveSummary(UUID recordId, GlobalPos pos, long epochMillis, long gameTime, String cause,
                               int itemCount, int xp) {
        public static final StreamCodec<FriendlyByteBuf, GraveSummary> STREAM_CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, GraveSummary::recordId,
                GlobalPos.STREAM_CODEC, GraveSummary::pos,
                ByteBufCodecs.VAR_LONG, GraveSummary::epochMillis,
                ByteBufCodecs.VAR_LONG, GraveSummary::gameTime,
                ByteBufCodecs.STRING_UTF8, GraveSummary::cause,
                ByteBufCodecs.VAR_INT, GraveSummary::itemCount,
                ByteBufCodecs.VAR_INT, GraveSummary::xp,
                GraveSummary::new
        );
    }

    public record PlayerEntry(UUID id, String name) {
        public static final StreamCodec<FriendlyByteBuf, PlayerEntry> STREAM_CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, PlayerEntry::id,
                ByteBufCodecs.STRING_UTF8, PlayerEntry::name,
                PlayerEntry::new
        );
    }

    public record GraveListPayload(UUID ownerId, String ownerName, List<GraveSummary> graves, boolean admin,
                                   boolean ownerEnabled, Optional<UUID> trackedId,
                                   List<PlayerEntry> players, List<PlayerEntry> allowed, Optional<UUID> focusId)
            implements CustomPacketPayload {
        public static final Type<GraveListPayload> TYPE = new Type<>(Graveless.id("grave_list"));

        public static final StreamCodec<FriendlyByteBuf, GraveListPayload> STREAM_CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, GraveListPayload::ownerId,
                ByteBufCodecs.STRING_UTF8, GraveListPayload::ownerName,
                GraveSummary.STREAM_CODEC.apply(ByteBufCodecs.list()), GraveListPayload::graves,
                ByteBufCodecs.BOOL, GraveListPayload::admin,
                ByteBufCodecs.BOOL, GraveListPayload::ownerEnabled,
                UUIDUtil.STREAM_CODEC.apply(ByteBufCodecs::optional), GraveListPayload::trackedId,
                PlayerEntry.STREAM_CODEC.apply(ByteBufCodecs.list()), GraveListPayload::players,
                PlayerEntry.STREAM_CODEC.apply(ByteBufCodecs.list()), GraveListPayload::allowed,
                UUIDUtil.STREAM_CODEC.apply(ByteBufCodecs::optional), GraveListPayload::focusId,
                GraveListPayload::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record ProfileActionPayload(int action, Optional<UUID> target) implements CustomPacketPayload {
        public static final int ACTION_TOGGLE_ENABLED = 0;
        public static final int ACTION_ALLOW = 1;
        public static final int ACTION_DENY = 2;
        public static final int ACTION_CLEAR = 3;

        public static final Type<ProfileActionPayload> TYPE = new Type<>(Graveless.id("profile_action"));

        public static final StreamCodec<FriendlyByteBuf, ProfileActionPayload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, ProfileActionPayload::action,
                UUIDUtil.STREAM_CODEC.apply(ByteBufCodecs::optional), ProfileActionPayload::target,
                ProfileActionPayload::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record BackupEntry(String fileName, long epochMillis, long gameTime, int itemCount, GlobalPos pos) {
        public static final StreamCodec<FriendlyByteBuf, BackupEntry> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, BackupEntry::fileName,
                ByteBufCodecs.VAR_LONG, BackupEntry::epochMillis,
                ByteBufCodecs.VAR_LONG, BackupEntry::gameTime,
                ByteBufCodecs.VAR_INT, BackupEntry::itemCount,
                GlobalPos.STREAM_CODEC, BackupEntry::pos,
                BackupEntry::new
        );
    }

    public record BackupListRequestPayload(UUID ownerId) implements CustomPacketPayload {
        public static final Type<BackupListRequestPayload> TYPE = new Type<>(Graveless.id("backup_list_request"));

        public static final StreamCodec<FriendlyByteBuf, BackupListRequestPayload> STREAM_CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, BackupListRequestPayload::ownerId,
                BackupListRequestPayload::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record BackupListPayload(UUID ownerId, List<BackupEntry> backups) implements CustomPacketPayload {
        public static final Type<BackupListPayload> TYPE = new Type<>(Graveless.id("backup_list"));

        public static final StreamCodec<FriendlyByteBuf, BackupListPayload> STREAM_CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, BackupListPayload::ownerId,
                BackupEntry.STREAM_CODEC.apply(ByteBufCodecs.list()), BackupListPayload::backups,
                BackupListPayload::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record BackupRevivePayload(UUID ownerId, String fileName) implements CustomPacketPayload {
        public static final Type<BackupRevivePayload> TYPE = new Type<>(Graveless.id("backup_revive"));

        public static final StreamCodec<FriendlyByteBuf, BackupRevivePayload> STREAM_CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, BackupRevivePayload::ownerId,
                ByteBufCodecs.STRING_UTF8, BackupRevivePayload::fileName,
                BackupRevivePayload::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record GraveDetailRequestPayload(UUID ownerId, UUID recordId) implements CustomPacketPayload {
        public static final Type<GraveDetailRequestPayload> TYPE = new Type<>(Graveless.id("grave_detail_request"));

        public static final StreamCodec<FriendlyByteBuf, GraveDetailRequestPayload> STREAM_CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, GraveDetailRequestPayload::ownerId,
                UUIDUtil.STREAM_CODEC, GraveDetailRequestPayload::recordId,
                GraveDetailRequestPayload::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record GraveViewRequestPayload(UUID ownerId) implements CustomPacketPayload {
        public static final Type<GraveViewRequestPayload> TYPE = new Type<>(Graveless.id("grave_view_request"));

        public static final StreamCodec<FriendlyByteBuf, GraveViewRequestPayload> STREAM_CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, GraveViewRequestPayload::ownerId,
                GraveViewRequestPayload::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record GraveOpenPayload(UUID recordId) implements CustomPacketPayload {
        public static final Type<GraveOpenPayload> TYPE = new Type<>(Graveless.id("grave_open"));

        public static final StreamCodec<FriendlyByteBuf, GraveOpenPayload> STREAM_CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, GraveOpenPayload::recordId,
                GraveOpenPayload::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record GraveDetailPayload(UUID recordId, List<ItemStack> items, List<Integer> terrain)
            implements CustomPacketPayload {
        public static final Type<GraveDetailPayload> TYPE = new Type<>(Graveless.id("grave_detail"));

        public static final StreamCodec<RegistryFriendlyByteBuf, GraveDetailPayload> STREAM_CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, GraveDetailPayload::recordId,
                ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list()), GraveDetailPayload::items,
                ByteBufCodecs.INT.apply(ByteBufCodecs.list()), GraveDetailPayload::terrain,
                GraveDetailPayload::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record GraveExtractPayload(UUID ownerId, UUID recordId, int itemIndex) implements CustomPacketPayload {
        public static final Type<GraveExtractPayload> TYPE = new Type<>(Graveless.id("grave_extract"));

        public static final StreamCodec<FriendlyByteBuf, GraveExtractPayload> STREAM_CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, GraveExtractPayload::ownerId,
                UUIDUtil.STREAM_CODEC, GraveExtractPayload::recordId,
                ByteBufCodecs.VAR_INT, GraveExtractPayload::itemIndex,
                GraveExtractPayload::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record GraveActionPayload(UUID ownerId, UUID recordId, int action) implements CustomPacketPayload {
        public static final int ACTION_DELETE = 0;
        public static final int ACTION_TELEPORT = 1;
        public static final int ACTION_TRACK = 2;
        public static final int ACTION_RESTORE = 3;
        public static final int ACTION_CLAIM_XP = 4;

        public static final Type<GraveActionPayload> TYPE = new Type<>(Graveless.id("grave_action"));

        public static final StreamCodec<FriendlyByteBuf, GraveActionPayload> STREAM_CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, GraveActionPayload::ownerId,
                UUIDUtil.STREAM_CODEC, GraveActionPayload::recordId,
                ByteBufCodecs.VAR_INT, GraveActionPayload::action,
                GraveActionPayload::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
