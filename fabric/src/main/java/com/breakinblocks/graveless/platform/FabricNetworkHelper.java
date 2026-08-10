package com.breakinblocks.graveless.platform;

import com.breakinblocks.graveless.platform.services.INetworkHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

public class FabricNetworkHelper implements INetworkHelper {
    private static final List<Runnable> CLIENT_RECEIVERS = new ArrayList<>();

    public static void registerClientReceivers() {
        CLIENT_RECEIVERS.forEach(Runnable::run);
        CLIENT_RECEIVERS.clear();
    }

    @Override
    public <T extends CustomPacketPayload> void registerToClient(CustomPacketPayload.Type<T> type,
                                                                 StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
                                                                 BiConsumer<T, PayloadContext> handler) {
        PayloadTypeRegistry.clientboundPlay().register(type, codec);
        CLIENT_RECEIVERS.add(() -> ClientPlayNetworking.registerGlobalReceiver(type,
                (payload, context) -> handler.accept(payload, new PayloadContext() {
                    @Override
                    public Player player() {
                        return context.player();
                    }

                    @Override
                    public void enqueueWork(Runnable task) {
                        context.client().execute(task);
                    }
                })));
    }

    @Override
    public <T extends CustomPacketPayload> void registerToServer(CustomPacketPayload.Type<T> type,
                                                                 StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
                                                                 BiConsumer<T, PayloadContext> handler) {
        PayloadTypeRegistry.serverboundPlay().register(type, codec);
        ServerPlayNetworking.registerGlobalReceiver(type,
                (payload, context) -> handler.accept(payload, new PayloadContext() {
                    @Override
                    public Player player() {
                        return context.player();
                    }

                    @Override
                    public void enqueueWork(Runnable task) {
                        context.server().execute(task);
                    }
                }));
    }

    @Override
    public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        ServerPlayNetworking.send(player, payload);
    }

    @Override
    public void sendToServer(CustomPacketPayload payload) {
        ClientPlayNetworking.send(payload);
    }
}
