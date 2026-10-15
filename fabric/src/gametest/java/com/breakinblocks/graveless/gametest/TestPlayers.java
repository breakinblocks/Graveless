package com.breakinblocks.graveless.gametest;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

final class TestPlayers {
    private static final AtomicInteger COUNTER = new AtomicInteger();

    private TestPlayers() {}

    static ServerPlayer join(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        GameProfile profile = new GameProfile(UUID.randomUUID(), "gl_test_" + COUNTER.incrementAndGet());
        ServerPlayer player = new ServerPlayer(server, level, profile, ClientInformation.createDefault());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        player.connection =
                new ServerGamePacketListenerImpl(
                        server, connection, player, CommonListenerCookie.createInitial(profile, false)) {
                    @Override
                    public void send(Packet<?> packet, PacketSendListener listener) {}
                };
        Vec3 pos = helper.absoluteVec(new Vec3(1.5, 2, 1.5));
        player.moveTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
        player.setNoGravity(true);
        level.addNewPlayer(player);
        return player;
    }

    static void leave(ServerPlayer player) {
        if (!player.isRemoved()) {
            player.serverLevel().removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
        }
    }
}
