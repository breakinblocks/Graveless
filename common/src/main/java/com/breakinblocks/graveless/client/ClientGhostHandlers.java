package com.breakinblocks.graveless.client;

import com.breakinblocks.graveless.client.gui.GraveBrowserScreen;
import com.breakinblocks.graveless.net.ClientPayloadDispatch;
import com.breakinblocks.graveless.net.GravelessNetworking;
import com.breakinblocks.graveless.platform.PayloadContext;
import net.minecraft.client.Minecraft;

public final class ClientGhostHandlers {
    private ClientGhostHandlers() {
    }

    public static void bindAll() {
        ClientPayloadDispatch.bind(GravelessNetworking.GhostAddPayload.TYPE, ClientGhostHandlers::handleAdd);
        ClientPayloadDispatch.bind(GravelessNetworking.GhostRemovePayload.TYPE, ClientGhostHandlers::handleRemove);
        ClientPayloadDispatch.bind(GravelessNetworking.GraveListPayload.TYPE, ClientGhostHandlers::handleGraveList);
        ClientPayloadDispatch.bind(GravelessNetworking.GraveDetailPayload.TYPE, ClientGhostHandlers::handleGraveDetail);
        ClientPayloadDispatch.bind(GravelessNetworking.BackupListPayload.TYPE, ClientGhostHandlers::handleBackupList);
    }

    public static void handleAdd(GravelessNetworking.GhostAddPayload payload, PayloadContext context) {
        context.enqueueWork(() -> GhostClientManager.add(new GhostClientManager.ClientGhost(
                payload.recordId(), payload.ownerId(), payload.ownerName(), payload.pos(), payload.itemCount())));
    }

    public static void handleRemove(GravelessNetworking.GhostRemovePayload payload, PayloadContext context) {
        context.enqueueWork(() -> GhostClientManager.remove(payload.recordId()));
    }

    public static void handleGraveList(GravelessNetworking.GraveListPayload payload, PayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.screen instanceof GraveBrowserScreen screen) {
                screen.updateFrom(payload);
            } else {
                minecraft.setScreen(new GraveBrowserScreen(payload));
            }
        });
    }

    public static void handleGraveDetail(GravelessNetworking.GraveDetailPayload payload, PayloadContext context) {
        context.enqueueWork(() -> {
            if (Minecraft.getInstance().screen instanceof GraveBrowserScreen screen) {
                screen.receiveDetail(payload);
            }
        });
    }

    public static void handleBackupList(GravelessNetworking.BackupListPayload payload, PayloadContext context) {
        context.enqueueWork(() -> {
            if (Minecraft.getInstance().screen instanceof GraveBrowserScreen screen) {
                screen.receiveBackups(payload);
            }
        });
    }
}
