package com.breakinblocks.graveless.event;

import com.breakinblocks.graveless.config.GravelessConfig;
import com.breakinblocks.graveless.data.DeathRecord;
import com.breakinblocks.graveless.data.GraveProfile;
import com.breakinblocks.graveless.data.GraveStore;
import com.breakinblocks.graveless.platform.Services;
import com.breakinblocks.graveless.registry.ModEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class SpiritWardEvents {
    private static final Set<UUID> IN_RANGE = new HashSet<>();
    private static final Set<UUID> LINGERING = new HashSet<>();
    private static final Map<UUID, Map<Holder<MobEffect>, MobEffectInstance>> OWNED_EFFECTS = new HashMap<>();
    // Keep Night Vision above its 200-tick fading threshold while the ward is active.
    private static final int AUXILIARY_DURATION = 240;

    public static void onPlayerTick(ServerPlayer player) {
        if (Services.PLATFORM.isFakePlayer(player)) {
            return;
        }
        if (!GravelessConfig.SERVER.protectionEnabled.get()) {
            IN_RANGE.remove(player.getUUID());
            LINGERING.remove(player.getUUID());
            player.removeEffect(ModEffects.SPIRIT_WARD.holder());
            clearAuxiliaryEffects(player);
            return;
        }
        if (!player.hasEffect(ModEffects.SPIRIT_WARD.holder())) {
            LINGERING.remove(player.getUUID());
            clearAuxiliaryEffects(player);
        }
        if ((player.tickCount + player.getId()) % 20 != 0) {
            if (player.hasEffect(ModEffects.SPIRIT_WARD.holder())) {
                maintainAuxiliaryEffects(player);
            }
            return;
        }
        boolean near = nearOwnGrave(player);
        boolean was = IN_RANGE.contains(player.getUUID());
        if (near && !was && !LINGERING.contains(player.getUUID())) {
            apply(player, GravelessConfig.SERVER.protectionDuration.get() * 20);
        } else if (!near && was && !LINGERING.contains(player.getUUID())) {
            player.removeEffect(ModEffects.SPIRIT_WARD.holder());
            clearAuxiliaryEffects(player);
        }
        if (near) {
            IN_RANGE.add(player.getUUID());
        } else {
            IN_RANGE.remove(player.getUUID());
        }
        if (player.hasEffect(ModEffects.SPIRIT_WARD.holder())) {
            maintainAuxiliaryEffects(player);
        }
    }

    public static boolean allowTarget(LivingEntity target) {
        return !(target instanceof Player player && player.hasEffect(ModEffects.SPIRIT_WARD.holder()));
    }

    public static void onLogout(ServerPlayer player) {
        IN_RANGE.remove(player.getUUID());
        LINGERING.remove(player.getUUID());
        player.removeEffect(ModEffects.SPIRIT_WARD.holder());
        clearAuxiliaryEffects(player);
    }

    public static void onRespawn(ServerPlayer player) {
        onLogout(player);
    }

    public static void beginWearOff(ServerPlayer player) {
        if (!player.hasEffect(ModEffects.SPIRIT_WARD.holder())) {
            return;
        }
        int linger = Math.min(GravelessConfig.SERVER.protectionLinger.get() * 20,
                player.getEffect(ModEffects.SPIRIT_WARD.holder()).getDuration());
        player.removeEffect(ModEffects.SPIRIT_WARD.holder());
        if (linger > 0) {
            player.addEffect(new MobEffectInstance(ModEffects.SPIRIT_WARD.holder(), linger, 0, true, false, true), null);
            LINGERING.add(player.getUUID());
        } else {
            LINGERING.remove(player.getUUID());
            clearAuxiliaryEffects(player);
        }
        IN_RANGE.add(player.getUUID());
    }

    private static void apply(ServerPlayer player, int durationTicks) {
        player.addEffect(new MobEffectInstance(ModEffects.SPIRIT_WARD.holder(), durationTicks, 0, true, false, true), null);
        maintainAuxiliaryEffects(player);
        AABB area = player.getBoundingBox().inflate(40.0);
        List<Mob> mobs = player.level().getEntitiesOfClass(Mob.class, area,
                mob -> mob.getTarget() == player);
        for (Mob mob : mobs) {
            mob.setTarget(null);
        }
    }

    private static void maintainAuxiliaryEffects(ServerPlayer player) {
        Map<Holder<MobEffect>, MobEffectInstance> owned = OWNED_EFFECTS.computeIfAbsent(player.getUUID(), id -> new HashMap<>());
        for (Holder<MobEffect> effect : List.of(MobEffects.INVISIBILITY, MobEffects.NIGHT_VISION)) {
            MobEffectInstance current = player.getEffect(effect);
            if (!isOwned(current, owned.get(effect))) {
                owned.remove(effect);
            }
            // Short leases avoid extending or deleting effects granted by potions and other mods.
            if (current == null || (owned.containsKey(effect) && current.getDuration() <= AUXILIARY_DURATION - 20)) {
                player.addEffect(new MobEffectInstance(effect, AUXILIARY_DURATION, 0, true, false, true), null);
                owned.put(effect, player.getEffect(effect));
            }
        }
    }

    private static boolean isOwned(MobEffectInstance current, MobEffectInstance owned) {
        return current != null && current == owned && current.getAmplifier() == 0
                && current.isAmbient() && !current.isVisible() && current.showIcon()
                && current.getDuration() >= 0 && current.getDuration() <= AUXILIARY_DURATION;
    }

    private static void clearAuxiliaryEffects(ServerPlayer player) {
        Map<Holder<MobEffect>, MobEffectInstance> owned = OWNED_EFFECTS.remove(player.getUUID());
        if (owned != null) {
            owned.forEach((effect, instance) -> {
                if (isOwned(player.getEffect(effect), instance)) {
                    player.removeEffect(effect);
                }
            });
        }
    }

    private static boolean nearOwnGrave(ServerPlayer player) {
        GraveProfile profile = GraveStore.get(player.level().getServer()).profile(player.getUUID());
        if (profile.records().isEmpty()) {
            return false;
        }
        int radius = GravelessConfig.SERVER.protectionRange.get();
        double radiusSqr = (double) radius * radius;
        for (DeathRecord record : profile.records()) {
            if (!record.pos().dimension().equals(player.level().dimension())) {
                continue;
            }
            BlockPos anchor = GhostSyncEvents.anchor(player.level(), record.pos().pos());
            if (Vec3.atCenterOf(anchor).distanceToSqr(player.position()) <= radiusSqr) {
                return true;
            }
        }
        return false;
    }
}
