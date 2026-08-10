package com.breakinblocks.graveless.registry;

import com.breakinblocks.graveless.Graveless;
import com.breakinblocks.graveless.platform.Registrar;
import com.breakinblocks.graveless.platform.RegistrySupplier;
import com.breakinblocks.graveless.platform.Services;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import java.util.function.Function;

public class ModBlocks {
    public static final Registrar<Block> BLOCKS = Services.REGISTRIES.create(Registries.BLOCK, Graveless.MOD_ID);

    public static <B extends Block> RegistrySupplier<B> registerWithItem(String name,
                                                                         Function<Identifier, ? extends B> factory) {
        RegistrySupplier<B> block = BLOCKS.register(name, factory::apply);
        ModItems.ITEMS.register(name, registryName -> new BlockItem(block.get(), new Item.Properties()
                .setId(ResourceKey.create(Registries.ITEM, registryName))));
        return block;
    }
}
