package com.breakinblocks.graveless.platform;

import com.breakinblocks.graveless.Graveless;
import com.breakinblocks.graveless.platform.services.IPlatformHelper;
import fuzs.forgeconfigapiport.fabric.api.neoforge.v4.NeoForgeConfigRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

public class FabricPlatformHelper implements IPlatformHelper {

    @Override
    public String getPlatformName() {
        return "Fabric";
    }

    @Override
    public boolean isModLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }

    @Override
    public boolean isDevelopmentEnvironment() {
        return FabricLoader.getInstance().isDevelopmentEnvironment();
    }

    @Override
    public boolean isFakePlayer(Player player) {
        return player instanceof ServerPlayer serverPlayer && serverPlayer.connection == null;
    }

    @Override
    public void registerConfig(ConfigType type, ModConfigSpec spec) {
        NeoForgeConfigRegistry.INSTANCE.register(Graveless.MOD_ID,
                type == ConfigType.CLIENT ? ModConfig.Type.CLIENT : ModConfig.Type.SERVER, spec);
    }
}
