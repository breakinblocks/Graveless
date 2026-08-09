package com.breakinblocks.graveless.client;

import com.breakinblocks.graveless.client.gui.GraveBrowserScreen;
import com.breakinblocks.graveless.net.GravelessNetworking;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class ClientGhostHandlers {
    private ClientGhostHandlers() {
    }

    public static void handleAdd(GravelessNetworking.GhostAddPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> GhostClientManager.add(new GhostClientManager.ClientGhost(
                payload.recordId(), payload.ownerId(), payload.ownerName(), payload.pos(), payload.itemCount())));
    }

    public static void handleRemove(GravelessNetworking.GhostRemovePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> GhostClientManager.remove(payload.recordId()));
    }

    public static void handleGraveList(GravelessNetworking.GraveListPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.screen instanceof GraveBrowserScreen screen) {
                screen.updateFrom(payload);
            } else {
                minecraft.setScreen(new GraveBrowserScreen(payload));
            }
        });
    }

    public static void handleGraveDetail(GravelessNetworking.GraveDetailPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (Minecraft.getInstance().screen instanceof GraveBrowserScreen screen) {
                screen.receiveDetail(payload);
            }
        });
    }

    public static void handleBackupList(GravelessNetworking.BackupListPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (Minecraft.getInstance().screen instanceof GraveBrowserScreen screen) {
                screen.receiveBackups(payload);
            }
        });
    }
}
