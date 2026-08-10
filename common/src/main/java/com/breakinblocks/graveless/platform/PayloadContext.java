package com.breakinblocks.graveless.platform;

import net.minecraft.world.entity.player.Player;

public interface PayloadContext {
    Player player();

    void enqueueWork(Runnable task);
}
