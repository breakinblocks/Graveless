package com.breakinblocks.graveless;

import com.breakinblocks.graveless.client.GravelessClient;
import com.breakinblocks.graveless.config.GravelessConfig;
import com.breakinblocks.graveless.integration.curios.CuriosIntegration;
import com.breakinblocks.graveless.registry.ModEffects;
import com.breakinblocks.graveless.registry.ModBlockEntities;
import com.breakinblocks.graveless.registry.ModBlocks;
import com.breakinblocks.graveless.registry.ModDataComponents;
import com.breakinblocks.graveless.registry.ModItems;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import org.slf4j.Logger;

@Mod(Graveless.MOD_ID)
public class Graveless {
    public static final String MOD_ID = "graveless";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    public Graveless(IEventBus eventBus, ModContainer container, Dist dist) {
        ModBlocks.BLOCKS.register(eventBus);
        ModItems.ITEMS.register(eventBus);
        ModBlockEntities.BLOCK_ENTITY_TYPES.register(eventBus);
        ModDataComponents.DATA_COMPONENT_TYPES.register(eventBus);
        ModEffects.MOB_EFFECTS.register(eventBus);

        container.registerConfig(ModConfig.Type.SERVER, GravelessConfig.SERVER_SPEC);

        eventBus.addListener(this::commonSetup);
        eventBus.addListener(this::buildCreativeContents);

        if (dist.isClient()) {
            container.registerConfig(ModConfig.Type.CLIENT, GravelessConfig.CLIENT_SPEC);
            container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);

            GravelessClient.init(eventBus);
        }
    }

    private void buildCreativeContents(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(ModItems.SPIRIT_COMPASS.get());
        }
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            if (isModLoaded("curios")) {
                CuriosIntegration.init();
            }
        });
    }

    public static boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }
}
