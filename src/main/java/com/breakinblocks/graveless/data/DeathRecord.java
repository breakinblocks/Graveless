package com.breakinblocks.graveless.data;

import com.breakinblocks.graveless.util.LenientCodecs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

public class DeathRecord {
    public static final Codec<DeathRecord> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(DeathRecord::id),
            GlobalPos.CODEC.fieldOf("pos").forGetter(DeathRecord::pos),
            Codec.LONG.fieldOf("game_time").forGetter(DeathRecord::gameTime),
            Codec.LONG.optionalFieldOf("epoch_millis", 0L).forGetter(DeathRecord::epochMillis),
            Codec.STRING.fieldOf("cause").forGetter(DeathRecord::cause),
            Codec.INT.optionalFieldOf("xp", 0).forGetter(DeathRecord::xp),
            LenientCodecs.lenientList(CapturedEntry.CODEC, "grave item entry")
                    .fieldOf("entries").forGetter(DeathRecord::entries),
            Codec.INT_STREAM.xmap(IntStream::toArray, stream -> IntStream.of(stream))
                    .optionalFieldOf("terrain", new int[0]).forGetter(DeathRecord::terrain)
    ).apply(instance, DeathRecord::new));

    private final UUID id;
    private final GlobalPos pos;
    private final long gameTime;
    private final long epochMillis;
    private final String cause;
    private int xp;
    private final List<CapturedEntry> entries;
    private int[] terrain;

    public DeathRecord(UUID id, GlobalPos pos, long gameTime, long epochMillis, String cause, int xp, List<CapturedEntry> entries) {
        this(id, pos, gameTime, epochMillis, cause, xp, entries, new int[0]);
    }

    public DeathRecord(UUID id, GlobalPos pos, long gameTime, long epochMillis, String cause, int xp,
                       List<CapturedEntry> entries, int[] terrain) {
        this.id = id;
        this.pos = pos;
        this.gameTime = gameTime;
        this.epochMillis = epochMillis;
        this.cause = cause;
        this.xp = xp;
        this.entries = new ArrayList<>(entries);
        this.terrain = terrain;
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

    public int[] terrain() {
        return terrain;
    }

    public void setTerrain(int[] terrain) {
        this.terrain = terrain;
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
