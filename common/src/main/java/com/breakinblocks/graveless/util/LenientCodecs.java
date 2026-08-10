package com.breakinblocks.graveless.util;

import com.breakinblocks.graveless.Graveless;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.ListBuilder;

import java.util.ArrayList;
import java.util.List;

public final class LenientCodecs {
    private LenientCodecs() {
    }

    public static <A> Codec<List<A>> lenientList(Codec<A> elementCodec, String label) {
        return new Codec<>() {
            @Override
            public <T> DataResult<Pair<List<A>, T>> decode(DynamicOps<T> ops, T input) {
                return ops.getList(input).map(consumer -> {
                    List<A> out = new ArrayList<>();
                    consumer.accept(element -> elementCodec.parse(ops, element)
                            .resultOrPartial(error -> Graveless.LOGGER.error(
                                    "Dropped unreadable {} while loading grave data: {}", label, error))
                            .ifPresent(out::add));
                    return Pair.of(List.copyOf(out), input);
                });
            }

            @Override
            public <T> DataResult<T> encode(List<A> input, DynamicOps<T> ops, T prefix) {
                ListBuilder<T> builder = ops.listBuilder();
                for (A element : input) {
                    DataResult<T> encoded = elementCodec.encodeStart(ops, element);
                    if (encoded.result().isPresent()) {
                        builder.add(encoded);
                    } else {
                        Graveless.LOGGER.error("Dropped unwritable {} while saving grave data: {}",
                                label, encoded.error().map(Object::toString).orElse("unknown error"));
                    }
                }
                return builder.build(prefix);
            }

            @Override
            public String toString() {
                return "LenientList[" + elementCodec + "]";
            }
        };
    }
}
