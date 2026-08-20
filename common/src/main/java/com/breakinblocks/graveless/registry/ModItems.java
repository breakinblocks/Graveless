package com.breakinblocks.graveless.registry;

import com.breakinblocks.graveless.Graveless;
import com.breakinblocks.graveless.item.SpiritCompassItem;
import com.breakinblocks.graveless.platform.Registrar;
import com.breakinblocks.graveless.platform.RegistrySupplier;
import com.breakinblocks.graveless.platform.Services;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

public class ModItems {
    public static final Registrar<Item> ITEMS = Services.REGISTRIES.create(Registries.ITEM, Graveless.MOD_ID);

    public static final RegistrySupplier<Item> SPIRIT_COMPASS = ITEMS.register("spirit_compass",
            registryName -> new SpiritCompassItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.UNCOMMON)));
}
