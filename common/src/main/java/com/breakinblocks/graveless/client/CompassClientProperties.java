package com.breakinblocks.graveless.client;

import com.breakinblocks.graveless.registry.ModItems;
import net.minecraft.client.renderer.item.CompassItemPropertyFunction;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.component.LodestoneTracker;

public final class CompassClientProperties {
    private CompassClientProperties() {
    }

    public static void register() {
        ItemProperties.register(ModItems.SPIRIT_COMPASS.get(), ResourceLocation.withDefaultNamespace("angle"),
                new CompassItemPropertyFunction((level, stack, entity) -> {
                    LodestoneTracker tracker = stack.get(DataComponents.LODESTONE_TRACKER);
                    return tracker == null ? null : tracker.target().orElse(null);
                }));
    }
}
