package com.breakinblocks.graveless.platform;

import com.breakinblocks.graveless.platform.services.IPlatformHelper;
import net.minecraft.world.entity.player.Player;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.util.FakePlayer;

public class NeoForgePlatformHelper implements IPlatformHelper {
    private static ModContainer container;

    public static void setContainer(ModContainer modContainer) {
        container = modContainer;
    }

    @Override
    public String getPlatformName() {
        return "NeoForge";
    }

    @Override
    public boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    @Override
    public boolean isDevelopmentEnvironment() {
        return !FMLLoader.getCurrent().isProduction();
    }

    @Override
    public boolean isFakePlayer(Player player) {
        return player instanceof FakePlayer;
    }

    @Override
    public void registerConfig(ConfigType type, ModConfigSpec spec) {
        container.registerConfig(type == ConfigType.CLIENT ? ModConfig.Type.CLIENT : ModConfig.Type.SERVER, spec);
    }
}
