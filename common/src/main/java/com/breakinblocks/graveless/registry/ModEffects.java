package com.breakinblocks.graveless.registry;

import com.breakinblocks.graveless.Graveless;
import com.breakinblocks.graveless.platform.Registrar;
import com.breakinblocks.graveless.platform.RegistrySupplier;
import com.breakinblocks.graveless.platform.Services;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

public class ModEffects {
    public static final Registrar<MobEffect> MOB_EFFECTS =
            Services.REGISTRIES.create(Registries.MOB_EFFECT, Graveless.MOD_ID);

    public static final RegistrySupplier<MobEffect> SPIRIT_WARD =
            MOB_EFFECTS.register("spirit_ward", () -> new SpiritWardEffect(MobEffectCategory.BENEFICIAL, 0x41E9E9));

    private static class SpiritWardEffect extends MobEffect {
        SpiritWardEffect(MobEffectCategory category, int color) {
            super(category, color);
        }
    }
}
