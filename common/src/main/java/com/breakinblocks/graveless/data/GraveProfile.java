package com.breakinblocks.graveless.data;

import com.breakinblocks.graveless.util.LenientCodecs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;

public class GraveProfile {
    public static final Codec<GraveProfile> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    Codec.BOOL.optionalFieldOf("enabled", true).forGetter(p -> p.enabled),
                    UUIDUtil.CODEC
                            .listOf()
                            .optionalFieldOf("allowed", List.of())
                            .forGetter(p -> List.copyOf(p.allowed)),
                    LenientCodecs.lenientList(DeathRecord.CODEC, "death record")
                            .optionalFieldOf("records", List.of())
                            .forGetter(p -> p.records),
                    UUIDUtil.CODEC.optionalFieldOf("tracked").forGetter(p -> Optional.ofNullable(p.trackedRecordId)),
                    Codec.unboundedMap(UUIDUtil.STRING_CODEC, BackupState.CODEC)
                            .lenientOptionalFieldOf("backup_states", Map.of())
                            .forGetter(p -> Map.copyOf(p.backupStates)))
            .apply(instance, GraveProfile::new));

    public record BackupStatus(BackupState state, int liveItems) {}

    private boolean enabled;
    private final Set<UUID> allowed;
    private final List<DeathRecord> records;
    private UUID trackedRecordId;
    private final Map<UUID, BackupState> backupStates;

    public GraveProfile() {
        this(true, List.of(), List.of(), Optional.empty(), Map.of());
    }

    public GraveProfile(
            boolean enabled,
            List<UUID> allowed,
            List<DeathRecord> records,
            Optional<UUID> tracked,
            Map<UUID, BackupState> backupStates) {
        this.enabled = enabled;
        this.allowed = new LinkedHashSet<>(allowed);
        this.records = new ArrayList<>(records);
        this.trackedRecordId = tracked.orElse(null);
        this.backupStates = new HashMap<>(backupStates);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Set<UUID> allowed() {
        return allowed;
    }

    public List<DeathRecord> records() {
        return records;
    }

    public UUID trackedRecordId() {
        return trackedRecordId;
    }

    public void setTrackedRecordId(UUID trackedRecordId) {
        this.trackedRecordId = trackedRecordId;
    }

    public boolean canAccess(UUID owner, UUID viewer) {
        return owner.equals(viewer) || allowed.contains(viewer);
    }

    public DeathRecord findRecord(UUID recordId) {
        for (DeathRecord record : records) {
            if (record.id().equals(recordId)) {
                return record;
            }
        }
        return null;
    }

    public void removeRecord(DeathRecord record, BackupState outcome) {
        if (records.remove(record)) {
            backupStates.put(record.sourceId(), outcome);
        }
    }

    public void trimRecords(int max) {
        while (records.size() > max) {
            backupStates.put(records.removeFirst().sourceId(), BackupState.OVERFLOW);
        }
    }

    public BackupStatus backupStatus(UUID backupId) {
        boolean live = false;
        int liveItems = 0;
        for (DeathRecord record : records) {
            if (record.sourceId().equals(backupId)) {
                live = true;
                liveItems += record.itemCount();
            }
        }
        if (live) {
            return new BackupStatus(BackupState.LIVE, liveItems);
        }
        return new BackupStatus(backupStates.getOrDefault(backupId, BackupState.UNKNOWN), 0);
    }

    public boolean forgetBackupStates(Collection<UUID> backupIds) {
        return backupStates.keySet().removeAll(backupIds);
    }
}
