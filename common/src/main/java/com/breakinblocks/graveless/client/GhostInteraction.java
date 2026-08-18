package com.breakinblocks.graveless.client;

import com.breakinblocks.graveless.config.GravelessConfig;
import com.breakinblocks.graveless.net.GravelessNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import com.breakinblocks.graveless.platform.Services;

public class GhostInteraction {
    private static final int CLAIM_COOLDOWN_TICKS = 10;
    private static long lastClaimTime = Long.MIN_VALUE;

    public static boolean onAttackPressed(Player player) {
        return attemptInteract(player);
    }

    public static boolean onUsePressed(Player player, InteractionHand hand) {
        return hand == InteractionHand.MAIN_HAND && attemptInteract(player);
    }

    private static boolean attemptInteract(Player player) {
        if (GhostClientManager.isEmpty()) {
            return false;
        }
        GhostClientManager.ClientGhost target = aimedGhost(player, Minecraft.getInstance().hitResult);
        if (target == null) {
            return false;
        }
        long time = player.level().getGameTime();
        if (lastClaimTime == Long.MIN_VALUE || time < lastClaimTime
                || time - lastClaimTime >= CLAIM_COOLDOWN_TICKS) {
            lastClaimTime = time;
            Services.NETWORK.sendToServer(player.isShiftKeyDown()
                    ? new GravelessNetworking.GraveOpenPayload(target.recordId())
                    : new GravelessNetworking.ClaimRequestPayload(target.recordId()));
        }
        return true;
    }

    private static GhostClientManager.ClientGhost aimedGhost(Player player, HitResult hit) {
        boolean obstructed = hit != null && hit.getType() != HitResult.Type.MISS;
        Vec3 eye = player.getEyePosition();
        double hitDist = obstructed ? hit.getLocation().distanceTo(eye) : Double.MAX_VALUE;
        int range = GravelessConfig.SERVER.claimRange.get();
        Vec3 look = player.getViewVector(1.0F);
        GhostClientManager.ClientGhost best = null;
        double bestScore = Double.MAX_VALUE;
        for (GhostClientManager.ClientGhost ghost : GhostClientManager.all()) {
            BlockPos pos = ghost.pos();
            Vec3 heart = new Vec3(pos.getX() + 0.5, pos.getY() + 1.3, pos.getZ() + 0.5);
            Vec3 toGhost = heart.subtract(eye);
            double dist = toGhost.length();
            if (dist > range) {
                continue;
            }
            double along = toGhost.dot(look);
            if (along <= 0.0) {
                continue;
            }
            if (obstructed && along > hitDist + 0.5) {
                continue;
            }
            double offAxis = eye.add(look.scale(along)).distanceTo(heart);
            double tolerance = obstructed ? Math.max(0.6, along * 0.04) : Math.max(1.2, along * 0.06);
            if (offAxis > tolerance) {
                continue;
            }
            if (GravelessConfig.SERVER.requireLineOfSight.get() && !hasLineOfSight(player, eye, heart)) {
                continue;
            }
            double score = offAxis / tolerance + (dist / range) * 0.25;
            if (score < bestScore) {
                bestScore = score;
                best = ghost;
            }
        }
        return best;
    }

    private static boolean hasLineOfSight(Player player, Vec3 eye, Vec3 heart) {
        BlockHitResult hit = player.level().clip(new ClipContext(
                eye, heart, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        return hit.getType() != HitResult.Type.BLOCK || hit.getLocation().distanceToSqr(heart) < 1.5;
    }
}
