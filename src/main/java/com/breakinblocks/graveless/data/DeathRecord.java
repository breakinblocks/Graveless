package com.breakinblocks.graveless.data;

import com.breakinblocks.graveless.util.LenientCodecs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class DeathRecord {
    public static final Codec<DeathRecord> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(DeathRecord::id),
            GlobalPos.CODEC.fieldOf("pos").forGetter(DeathRecord::pos),
            Codec.LONG.fieldOf("game_time").forGetter(DeathRecord::gameTime),
            Codec.LONG.optionalFieldOf("epoch_millis", 0L).forGetter(DeathRecord::epochMillis),
            Codec.STRING.fieldOf("cause").forGetter(DeathRecord::cause),
            Codec.INT.optionalFieldOf("xp", 0).forGetter(DeathRecord::xp),
            LenientCodecs.lenientList(CapturedEntry.CODEC, "grave item entry")
                    .fieldOf("entries").forGetter(DeathRecord::entries)
    ).apply(instance, DeathRecord::new));

    private final UUID id;
    private final GlobalPos pos;
    private final long gameTime;
    private final long epochMillis;
    private final String cause;
    private int xp;
    private final List<CapturedEntry> entries;

    public DeathRecord(UUID id, GlobalPos pos, long gameTime, long epochMillis, String cause, int xp, List<CapturedEntry> entries) {
        this.id = id;
        this.pos = pos;
        this.gameTime = gameTime;
        this.epochMillis = epochMillis;
        this.cause = cause;
        this.xp = xp;
        this.entries = new ArrayList<>(entries);
    }

    public UUID id() {
        return id;
    }

    public GlobalPos pos() {
        return pos;
    }

    public long gameTime() {
        return gameTime;
    }

    public long epochMillis() {
        return epochMillis;
    }

    public String cause() {
        return cause;
    }

    public int xp() {
        return xp;
    }

    public void setXp(int xp) {
        this.xp = xp;
    }

    public List<CapturedEntry> entries() {
        return entries;
    }

    public int itemCount() {
        int count = 0;
        for (CapturedEntry entry : entries) {
            count += entry.stack().getCount();
        }
        return count;
    }

    public boolean isEmpty() {
        return entries.isEmpty() && xp <= 0;
    }
}
