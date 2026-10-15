package com.breakinblocks.graveless.data;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import java.util.function.IntFunction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.StringRepresentable;

public enum BackupState implements StringRepresentable {
    UNKNOWN("unknown"),
    LIVE("live"),
    CLAIMED("claimed"),
    DELETED("deleted"),
    OVERFLOW("overflow");

    public static final Codec<BackupState> CODEC = StringRepresentable.fromEnum(BackupState::values);

    private static final IntFunction<BackupState> BY_ID =
            ByIdMap.continuous(BackupState::ordinal, values(), ByIdMap.OutOfBoundsStrategy.ZERO);

    public static final StreamCodec<ByteBuf, BackupState> STREAM_CODEC =
            ByteBufCodecs.idMapper(BY_ID, BackupState::ordinal);

    private final String name;

    BackupState(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    public Component label(int backupItems, int liveItems) {
        if (this == LIVE && liveItems < backupItems) {
            return Component.translatable("graveless.backup.state.live_partial", liveItems);
        }
        return Component.translatable("graveless.backup.state." + name);
    }
}
