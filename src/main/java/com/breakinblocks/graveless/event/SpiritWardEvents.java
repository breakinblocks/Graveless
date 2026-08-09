package com.breakinblocks.graveless.event;

import com.breakinblocks.graveless.Graveless;
import com.breakinblocks.graveless.config.GravelessConfig;
import com.breakinblocks.graveless.data.DeathRecord;
import com.breakinblocks.graveless.data.GraveProfile;
import com.breakinblocks.graveless.data.GraveStore;
import com.breakinblocks.graveless.registry.ModEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@EventBusSubscriber(modid = Graveless.MOD_ID)
public class SpiritWardEvents {
    private static final Set<UUID> IN_RANGE = new HashSet<>();

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player instanceof FakePlayer) {
            return;
        }
        if ((player.tickCount + player.getId()) % 20 != 0) {
            return;
        }
        if (!GravelessConfig.SERVER.protectionEnabled.get()) {
            IN_RANGE.remove(player.getUUID());
            return;
        }
        boolean near = nearOwnGrave(player);
        boolean was = IN_RANGE.contains(player.getUUID());
        if (near && !was) {
            apply(player, GravelessConfig.SERVER.protectionDuration.get() * 20);
        } else if (!near && was && player.hasEffect(ModEffects.SPIRIT_WARD)) {
            for (Holder<MobEffect> effect : wardEffects()) {
                player.removeEffect(effect);
            }
        }
        if (near) {
            IN_RANGE.add(player.getUUID());
        } else {
            IN_RANGE.remove(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        if (event.getNewAboutToBeSetTarget() instanceof Player player
                && player.hasEffect(ModEffects.SPIRIT_WARD)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        IN_RANGE.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        IN_RANGE.remove(event.getEntity().getUUID());
    }

    public static void beginWearOff(ServerPlayer player) {
        if (!player.hasEffect(ModEffects.SPIRIT_WARD)) {
            return;
        }
        int linger = GravelessConfig.SERVER.protectionLinger.get() * 20;
        for (Holder<MobEffect> effect : wardEffects()) {
            player.removeEffect(effect);
            if (linger > 0) {
                player.addEffect(new MobEffectInstance(effect, linger, 0, true, false, true), null);
            }
        }
        IN_RANGE.add(player.getUUID());
    }

    private static void apply(ServerPlayer player, int durationTicks) {
        for (Holder<MobEffect> effect : wardEffects()) {
            player.addEffect(new MobEffectInstance(effect, durationTicks, 0, true, false, true), null);
        }
        AABB area = player.getBoundingBox().inflate(40.0);
        List<Mob> mobs = player.level().getEntitiesOfClass(Mob.class, area,
                mob -> mob.getTarget() == player);
        for (Mob mob : mobs) {
            mob.setTarget(null);
        }
    }

    private static List<Holder<MobEffect>> wardEffects() {
        return List.of(ModEffects.SPIRIT_WARD, MobEffects.INVISIBILITY, MobEffects.NIGHT_VISION);
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
