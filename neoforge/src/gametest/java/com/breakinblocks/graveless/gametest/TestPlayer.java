package com.breakinblocks.graveless.gametest;

import com.breakinblocks.graveless.Graveless;
import com.breakinblocks.graveless.data.DeathRecord;
import com.breakinblocks.graveless.data.GraveProfile;
import com.breakinblocks.graveless.data.GraveStore;
import com.breakinblocks.graveless.event.DeathCaptureEvents;
import com.breakinblocks.graveless.event.GhostSyncEvents;
import com.breakinblocks.graveless.net.GravelessNetworking;
import com.breakinblocks.graveless.platform.PayloadContext;
import com.breakinblocks.graveless.registry.ModItems;
import com.breakinblocks.graveless.util.XpMath;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.network.registration.ChannelAttributes;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public final class TestPlayer implements GameTestListener {
    private static final AtomicInteger COUNTER = new AtomicInteger();

    private static final List<Identifier> CLIENTBOUND_CHANNELS = List.of(
            GravelessNetworking.GhostAddPayload.TYPE.id(),
            GravelessNetworking.GhostRemovePayload.TYPE.id(),
            GravelessNetworking.GraveListPayload.TYPE.id(),
            GravelessNetworking.GraveDetailPayload.TYPE.id(),
            GravelessNetworking.BackupListPayload.TYPE.id());

    private final MinecraftServer server;
    private final EmbeddedChannel channel;
    private final UUID id;
    private ServerPlayer player;
    private boolean removed;

    private TestPlayer(MinecraftServer server, EmbeddedChannel channel, ServerPlayer player) {
        this.server = server;
        this.channel = channel;
        this.player = player;
        this.id = player.getUUID();
    }

    public static TestPlayer join(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        String name = "gl_test_" + COUNTER.incrementAndGet();
        GameProfile profile = new GameProfile(UUID.randomUUID(), name);
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(profile, false);
        ServerPlayer player = new ServerPlayer(server, level, cookie.gameProfile(), cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        EmbeddedChannel channel = new EmbeddedChannel(connection);
        Set<Identifier> adHoc = ChannelAttributes.getOrCreateAdHocChannels(connection);
        adHoc.addAll(CLIENTBOUND_CHANNELS);
        server.getPlayerList().placeNewPlayer(connection, player, cookie);
        player.connection.markClientLoaded();

        TestPlayer test = new TestPlayer(server, channel, player);
        helper.testInfo.addListener(test);
        helper.onEachTick(test::driveConnection);
        test.moveTo(helper, new BlockPos(0, 1, 0));
        player.setGameMode(GameType.SURVIVAL);
        player.setNoGravity(true);
        player.getInventory().clearContent();
        test.clearOutbound();
        return test;
    }

    public ServerPlayer player() {
        return this.player;
    }

    public UUID id() {
        return this.id;
    }

    public String name() {
        return this.player.getName().getString();
    }

    public MinecraftServer server() {
        return this.server;
    }

    public GraveStore store() {
        return GraveStore.get(this.server);
    }

    public GraveProfile profile() {
        return store().profile(this.id);
    }

    public List<DeathRecord> records() {
        return profile().records();
    }

    public DeathRecord newestRecord() {
        List<DeathRecord> records = records();
        return records.isEmpty() ? null : records.getLast();
    }

    public void moveTo(GameTestHelper helper, BlockPos relative) {
        BlockPos target = helper.absolutePos(relative);
        this.player.snapTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5);
    }

    public void moveToAbsolute(double x, double y, double z) {
        this.player.snapTo(x, y, z);
    }

    public void op() {
        this.server.getPlayerList().op(this.player.nameAndId(),
                Optional.of(LevelBasedPermissionSet.OWNER), Optional.empty());
    }

    public int tickCount() {
        return this.player.tickCount;
    }

    private void driveConnection() {
        if (this.removed || this.player.hasDisconnected()) {
            return;
        }
        try {
            this.player.connection.tick();
        } catch (Exception e) {
            Graveless.LOGGER.error("Failed to tick game test player {}", this.id, e);
        }
    }

    public void killForReal() {
        this.player.hurtServer((ServerLevel) this.player.level(),
                this.player.damageSources().fellOutOfWorld(), Float.MAX_VALUE);
    }

    public void give(int slot, ItemStack stack) {
        this.player.getInventory().setItem(slot, stack);
    }

    public ItemStack itemAt(int slot) {
        return this.player.getInventory().getItem(slot);
    }

    public void fillInventory(ItemStack filler) {
        Inventory inventory = this.player.getInventory();
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
            inventory.setItem(slot, filler.copy());
        }
    }

    public int countItems() {
        Inventory inventory = this.player.getInventory();
        int count = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            count += inventory.getItem(slot).getCount();
        }
        return count;
    }

    public int countOf(Item item) {
        Inventory inventory = this.player.getInventory();
        int count = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    public int storedXp() {
        return XpMath.totalPoints(this.player.experienceLevel, this.player.experienceProgress);
    }

    public LodestoneTracker compassTarget() {
        Inventory inventory = this.player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(ModItems.SPIRIT_COMPASS.get())) {
                return stack.get(DataComponents.LODESTONE_TRACKER);
            }
        }
        return null;
    }

    public BlockPos anchorOf(DeathRecord record) {
        return GhostSyncEvents.anchor(this.player.level(), record.pos().pos());
    }

    public void moveToRecord(DeathRecord record) {
        BlockPos anchor = anchorOf(record);
        this.player.snapTo(anchor.getX() + 0.5, anchor.getY(), anchor.getZ() + 0.5);
    }

    public void simulateDeath() {
        beginDeath();
        finishDeath();
    }

    public void beginDeath() {
        DamageSource source = this.player.damageSources().generic();
        DeathCaptureEvents.onDeath(this.player, source);
    }

    public void finishDeath() {
        DeathCaptureEvents.finishDrops(this.player);
    }

    public PayloadContext context() {
        ServerPlayer target = this.player;
        return new PayloadContext() {
            @Override
            public Player player() {
                return target;
            }

            @Override
            public void enqueueWork(Runnable task) {
                task.run();
            }
        };
    }

    public <T extends CustomPacketPayload> List<T> outbound(Class<T> type) {
        this.channel.flush();
        List<T> found = new ArrayList<>();
        for (Object message : this.channel.outboundMessages()) {
            if (message instanceof ClientboundCustomPayloadPacket packet && type.isInstance(packet.payload())) {
                found.add(type.cast(packet.payload()));
            }
        }
        return found;
    }

    public void clearOutbound() {
        this.channel.flush();
        this.channel.outboundMessages().clear();
    }

    public void leave() {
        if (this.removed) {
            return;
        }
        this.removed = true;
        try {
            this.server.getPlayerList().remove(this.player);
        } catch (Exception e) {
            Graveless.LOGGER.error("Failed to remove game test player {}", this.id, e);
        }
        this.channel.outboundMessages().clear();
    }

    @Override
    public void testStructureLoaded(GameTestInfo testInfo) {
    }

    @Override
    public void testPassed(GameTestInfo testInfo, GameTestRunner runner) {
        leave();
    }

    @Override
    public void testFailed(GameTestInfo testInfo, GameTestRunner runner) {
        leave();
    }

    @Override
    public void testAddedForRerun(GameTestInfo original, GameTestInfo copy, GameTestRunner runner) {
    }
}
