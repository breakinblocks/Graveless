package com.breakinblocks.graveless;

import com.breakinblocks.graveless.config.GravelessConfig;
import com.breakinblocks.graveless.net.GravelessNetworking;
import com.breakinblocks.graveless.platform.ConfigType;
import com.breakinblocks.graveless.platform.Services;
import com.breakinblocks.graveless.registry.ModBlockEntities;
import com.breakinblocks.graveless.registry.ModBlocks;
import com.breakinblocks.graveless.registry.ModDataComponents;
import com.breakinblocks.graveless.registry.ModEffects;
import com.breakinblocks.graveless.registry.ModItems;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

public final class Graveless {
    public static final String MOD_ID = "graveless";
    public static final Logger LOGGER = LogUtils.getLogger();

    private Graveless() {
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    public static void init() {
        ModBlocks.BLOCKS.init();
        ModItems.ITEMS.init();
        ModBlockEntities.BLOCK_ENTITY_TYPES.init();
        ModDataComponents.DATA_COMPONENT_TYPES.init();
        ModEffects.MOB_EFFECTS.init();

        Services.PLATFORM.registerConfig(ConfigType.SERVER, GravelessConfig.SERVER_SPEC);

        GravelessNetworking.register();

        LOGGER.info("Graveless loaded on {}", Services.PLATFORM.getPlatformName());
    }

    public static void initClient() {
        Services.PLATFORM.registerConfig(ConfigType.CLIENT, GravelessConfig.CLIENT_SPEC);
    }

    public static boolean isModLoaded(String modId) {
        return Services.PLATFORM.isModLoaded(modId);
    }
}
