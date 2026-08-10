package com.breakinblocks.graveless.platform.services;

import com.breakinblocks.graveless.platform.PayloadContext;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.BiConsumer;

public interface INetworkHelper {
    <T extends CustomPacketPayload> void registerToClient(CustomPacketPayload.Type<T> type,
                                                          StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
                                                          BiConsumer<T, PayloadContext> handler);

    <T extends CustomPacketPayload> void registerToServer(CustomPacketPayload.Type<T> type,
                                                          StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
                                                          BiConsumer<T, PayloadContext> handler);

    void sendToPlayer(ServerPlayer player, CustomPacketPayload payload);

    void sendToServer(CustomPacketPayload payload);
}
