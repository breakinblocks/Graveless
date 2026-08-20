package com.breakinblocks.graveless.platform;

import com.breakinblocks.graveless.platform.services.INetworkHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class NeoForgeNetworkHelper implements INetworkHelper {
    private static final List<Consumer<PayloadRegistrar>> PENDING = new ArrayList<>();

    public static void flush(PayloadRegistrar registrar) {
        PENDING.forEach(action -> action.accept(registrar));
        PENDING.clear();
    }

    @Override
    public <T extends CustomPacketPayload> void registerToClient(CustomPacketPayload.Type<T> type,
                                                                 StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
                                                                 BiConsumer<T, PayloadContext> handler) {
        PENDING.add(registrar -> registrar.playToClient(type, codec,
                (payload, context) -> handler.accept(payload, wrap(context))));
    }

    @Override
    public <T extends CustomPacketPayload> void registerToServer(CustomPacketPayload.Type<T> type,
                                                                 StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
                                                                 BiConsumer<T, PayloadContext> handler) {
        PENDING.add(registrar -> registrar.playToServer(type, codec,
                (payload, context) -> handler.accept(payload, wrap(context))));
    }

    @Override
    public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }

    @Override
    public void sendToServer(CustomPacketPayload payload) {
        PacketDistributor.sendToServer(payload);
    }

    private static PayloadContext wrap(IPayloadContext context) {
        return new PayloadContext() {
            @Override
            public Player player() {
                return context.player();
            }

            @Override
            public void enqueueWork(Runnable task) {
                context.enqueueWork(task);
            }
        };
    }
}
