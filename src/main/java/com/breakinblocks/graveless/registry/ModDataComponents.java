package com.breakinblocks.graveless.registry;

import com.breakinblocks.graveless.Graveless;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModDataComponents {
    public static final DeferredRegister<DataComponentType<?>> DATA_COMPONENT_TYPES =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, Graveless.MOD_ID);

    public record CurioSlot(String type, int index, boolean cosmetic) {
        public static final Codec<CurioSlot> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("type").forGetter(CurioSlot::type),
                Codec.INT.fieldOf("index").forGetter(CurioSlot::index),
                Codec.BOOL.optionalFieldOf("cosmetic", false).forGetter(CurioSlot::cosmetic)
        ).apply(instance, CurioSlot::new));

        public static final StreamCodec<FriendlyByteBuf, CurioSlot> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, CurioSlot::type,
                ByteBufCodecs.VAR_INT, CurioSlot::index,
                ByteBufCodecs.BOOL, CurioSlot::cosmetic,
                CurioSlot::new
        );
    }

    public static final Supplier<DataComponentType<CurioSlot>> CURIO_SLOT =
            DATA_COMPONENT_TYPES.register("curio_slot", () ->
                    DataComponentType.<CurioSlot>builder()
                            .persistent(CurioSlot.CODEC)
                            .networkSynchronized(CurioSlot.STREAM_CODEC)
                            .build()
            );
}
