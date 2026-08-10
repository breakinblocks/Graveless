package com.breakinblocks.graveless.platform.services;

import com.breakinblocks.graveless.platform.ConfigType;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.ModConfigSpec;

public interface IPlatformHelper {
    String getPlatformName();

    boolean isModLoaded(String modId);

    boolean isDevelopmentEnvironment();

    boolean isFakePlayer(Player player);

    void registerConfig(ConfigType type, ModConfigSpec spec);
}
