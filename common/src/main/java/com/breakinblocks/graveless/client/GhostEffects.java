package com.breakinblocks.graveless.client;

import com.breakinblocks.graveless.client.render.GhostRenderManager;
import com.breakinblocks.graveless.client.render.GhostSkins;
import com.breakinblocks.graveless.config.GravelessConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class GhostEffects {

    private static final Set<UUID> ANNOUNCED = new HashSet<>();

    public static void onClientTick() {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || minecraft.isPaused() || minecraft.player == null || GhostClientManager.isEmpty()) {
            return;
        }
        announceClaimRange(minecraft);
        long gameTime = level.getGameTime();
        RandomSource random = level.getRandom();
        for (GhostClientManager.ClientGhost ghost : GhostClientManager.all()) {
            BlockPos pos = ghost.pos();
            double distSqr = minecraft.player.blockPosition().distSqr(pos);
            if (distSqr > 96 * 96) {
                continue;
            }
            double cx = pos.getX() + 0.5;
            double cy = pos.getY();
            double cz = pos.getZ() + 0.5;
            float phase = (ghost.recordId().hashCode() & 0xFF) * 0.13F;

            if (gameTime % 3 == 0) {
                float angle = (gameTime * 0.045F) + phase;
                double radius = 0.5 + Mth.sin(gameTime * 0.02F + phase) * 0.15;
                double x = cx + Mth.cos(angle) * radius;
                double z = cz + Mth.sin(angle) * radius;
                double y = cy + 0.15 + ((gameTime % 60) / 60.0) * 1.9;
                level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, x, y, z,
                        -Mth.sin(angle) * 0.012, 0.014, Mth.cos(angle) * 0.012);
            }

            if (gameTime % 2 == 0) {
                double swirl = gameTime * 0.09 + phase;
                for (int i = 0; i < 2; i++) {
                    double angle = swirl + i * Math.PI;
                    double radius = 1.1 + 0.4 * Mth.sin(gameTime * 0.03F + i * 1.7F);
                    double targetX = cx + Math.cos(angle) * 0.15;
                    double targetY = cy + 0.4 + random.nextDouble() * 1.6;
                    double targetZ = cz + Math.sin(angle) * 0.15;
                    double offsetX = Math.cos(angle + 0.9) * radius;
                    double offsetY = 0.4 - random.nextDouble() * 0.8;
                    double offsetZ = Math.sin(angle + 0.9) * radius;
                    level.addParticle(ParticleTypes.ENCHANT, targetX, targetY, targetZ,
                            offsetX, offsetY, offsetZ);
                }
            }

            if (distSqr < 24 * 24 && random.nextInt(160) == 0) {
                level.playLocalSound(cx, cy + 1.2, cz, SoundEvents.SOUL_ESCAPE.value(), SoundSource.AMBIENT,
                        0.7F, 0.7F + random.nextFloat() * 0.25F, false);
            }
            if (distSqr < 12 * 12 && random.nextInt(200) == 0) {
                level.playLocalSound(cx, cy + 1.2, cz, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.AMBIENT,
                        0.45F, 0.65F, false);
            }
        }
    }

    private static void announceClaimRange(Minecraft minecraft) {
        if (minecraft.player.isDeadOrDying()) {
            return;
        }
        int range = GravelessConfig.SERVER.claimRange.get();
        double rangeSqr = (double) range * range;
        Vec3 playerPos = minecraft.player.position();
        List<Component> messages = new ArrayList<>();
        double nearest = Double.MAX_VALUE;
        Component nearestMessage = null;
        for (GhostClientManager.ClientGhost ghost : GhostClientManager.all()) {
            Vec3 ghostPos = Vec3.atCenterOf(ghost.pos());
            double distSqr = playerPos.distanceToSqr(ghostPos);
            if (distSqr <= rangeSqr && ANNOUNCED.add(ghost.recordId())) {
                Component message = Component.translatable("graveless.hud.in_range",
                        (int) Math.sqrt(distSqr), directionPhrase(playerPos, ghostPos));
                messages.add(message);
                if (distSqr < nearest) {
                    nearest = distSqr;
                    nearestMessage = message;
                }
            }
        }
        if (messages.isEmpty()) {
            return;
        }
        minecraft.gui.setOverlayMessage(nearestMessage, false);
        for (Component message : messages) {
            minecraft.getChatListener().handleSystemMessage(
                    message.copy().withStyle(ChatFormatting.AQUA), false);
        }
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 0.8F));
    }

    private static Component directionPhrase(Vec3 from, Vec3 to) {
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double dz = to.z - from.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        if (horizontal < 4.0 && Math.abs(dy) > horizontal) {
            return Component.translatable(dy > 0 ? "graveless.direction.above" : "graveless.direction.below");
        }
        double angle = Math.toDegrees(Math.atan2(dx, dz));
        String[] octants = {"south", "southeast", "east", "northeast", "north", "northwest", "west", "southwest"};
        int index = Math.floorMod((int) Math.floor((angle + 22.5) / 45.0), 8);
        return Component.translatable("graveless.direction." + octants[index]);
    }

    public static void onDisconnect() {
        GhostClientManager.clear();
        GhostSkins.clear();
        GhostRenderManager.resetModels();
        ANNOUNCED.clear();
    }

    public static void onRespawnClone() {
        GhostClientManager.clear();
    }
}
