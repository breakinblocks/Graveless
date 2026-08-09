package com.breakinblocks.graveless.registry;

import com.breakinblocks.graveless.Graveless;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModEffects {
    public static final DeferredRegister<MobEffect> MOB_EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, Graveless.MOD_ID);

    public static final DeferredHolder<MobEffect, MobEffect> SPIRIT_WARD =
            MOB_EFFECTS.register("spirit_ward", () -> new SpiritWardEffect(MobEffectCategory.BENEFICIAL, 0x41E9E9));

    private static class SpiritWardEffect extends MobEffect {
        SpiritWardEffect(MobEffectCategory category, int color) {
            super(category, color);
        }
    }
}
