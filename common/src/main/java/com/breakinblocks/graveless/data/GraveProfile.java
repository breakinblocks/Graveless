package com.breakinblocks.graveless.data;

import com.breakinblocks.graveless.util.LenientCodecs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class GraveProfile {
    public static final Codec<GraveProfile> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.optionalFieldOf("enabled", true).forGetter(p -> p.enabled),
            UUIDUtil.CODEC.listOf().optionalFieldOf("allowed", List.of()).forGetter(p -> List.copyOf(p.allowed)),
            LenientCodecs.lenientList(DeathRecord.CODEC, "death record")
                    .optionalFieldOf("records", List.of()).forGetter(p -> p.records),
            UUIDUtil.CODEC.optionalFieldOf("tracked").forGetter(p -> Optional.ofNullable(p.trackedRecordId))
    ).apply(instance, GraveProfile::new));

    private boolean enabled;
    private final Set<UUID> allowed;
    private final List<DeathRecord> records;
    private UUID trackedRecordId;

    public GraveProfile() {
        this(true, List.of(), List.of(), Optional.empty());
    }

    public GraveProfile(boolean enabled, List<UUID> allowed, List<DeathRecord> records, Optional<UUID> tracked) {
        this.enabled = enabled;
        this.allowed = new LinkedHashSet<>(allowed);
        this.records = new ArrayList<>(records);
        this.trackedRecordId = tracked.orElse(null);
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
}
