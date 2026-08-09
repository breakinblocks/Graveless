package com.breakinblocks.graveless.registry;

import com.breakinblocks.graveless.Graveless;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Function;

public class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Graveless.MOD_ID);

    public static <B extends Block> DeferredBlock<B> registerWithItem(String name,
                                                                     Function<Identifier, ? extends B> factory) {
        DeferredBlock<B> block = BLOCKS.register(name, factory);
        ModItems.ITEMS.register(name, registryName -> new BlockItem(block.get(), new Item.Properties()
                .setId(ResourceKey.create(Registries.ITEM, registryName))));
        return block;
    }
}
