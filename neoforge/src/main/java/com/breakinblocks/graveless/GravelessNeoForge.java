package com.breakinblocks.graveless;

import com.breakinblocks.graveless.client.GravelessClient;
import com.breakinblocks.graveless.integration.curios.CuriosIntegration;
import com.breakinblocks.graveless.platform.NeoForgePlatformHelper;
import com.breakinblocks.graveless.platform.NeoForgeRegistryFactory;
import com.breakinblocks.graveless.registry.ModItems;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

@Mod(Graveless.MOD_ID)
public class GravelessNeoForge {

    public GravelessNeoForge(IEventBus eventBus, ModContainer container, Dist dist) {
        NeoForgeRegistryFactory.setModBus(eventBus);
        NeoForgePlatformHelper.setContainer(container);

        Graveless.init();

        eventBus.addListener(this::commonSetup);
        eventBus.addListener(this::buildCreativeContents);

        if (dist.isClient()) {
            Graveless.initClient();
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
            if (Graveless.isModLoaded("curios")) {
                CuriosIntegration.init();
            }
        });
    }
}
