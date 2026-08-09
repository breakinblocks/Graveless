package com.breakinblocks.graveless.registry;

import com.breakinblocks.graveless.Graveless;
import com.breakinblocks.graveless.item.SpiritCompassItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Graveless.MOD_ID);

    public static final DeferredItem<Item> SPIRIT_COMPASS = ITEMS.register("spirit_compass",
            registryName -> new SpiritCompassItem(new Item.Properties()
                    .setId(ResourceKey.create(Registries.ITEM, registryName))
                    .stacksTo(1)
                    .rarity(Rarity.UNCOMMON)));
}
