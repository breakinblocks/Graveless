package com.breakinblocks.graveless.net;

import com.breakinblocks.graveless.platform.PayloadContext;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

public final class ClientPayloadDispatch {
    private static final Map<Identifier, BiConsumer<?, PayloadContext>> HANDLERS = new HashMap<>();

    private ClientPayloadDispatch() {
    }

    public static <T extends CustomPacketPayload> void bind(CustomPacketPayload.Type<T> type,
                                                            BiConsumer<T, PayloadContext> handler) {
        HANDLERS.put(type.id(), handler);
    }

    @SuppressWarnings("unchecked")
    public static <T extends CustomPacketPayload> void dispatch(T payload, PayloadContext context) {
        BiConsumer<T, PayloadContext> handler = (BiConsumer<T, PayloadContext>) HANDLERS.get(payload.type().id());
        if (handler != null) {
            handler.accept(payload, context);
        }
    }
}
