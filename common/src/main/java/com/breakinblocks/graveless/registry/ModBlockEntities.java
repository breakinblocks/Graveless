package com.breakinblocks.graveless.registry;

import com.breakinblocks.graveless.Graveless;
import com.breakinblocks.graveless.platform.Registrar;
import com.breakinblocks.graveless.platform.Services;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;

public class ModBlockEntities {
    public static final Registrar<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            Services.REGISTRIES.create(Registries.BLOCK_ENTITY_TYPE, Graveless.MOD_ID);
}
