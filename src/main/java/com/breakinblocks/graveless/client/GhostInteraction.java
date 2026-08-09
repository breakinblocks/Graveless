package com.breakinblocks.graveless.client;

import com.breakinblocks.graveless.Graveless;
import com.breakinblocks.graveless.config.GravelessConfig;
import com.breakinblocks.graveless.net.GravelessNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@EventBusSubscriber(modid = Graveless.MOD_ID, value = Dist.CLIENT)
public class GhostInteraction {

    @SubscribeEvent
    public static void onRightClickEmpty(PlayerInteractEvent.RightClickEmpty event) {
        if (event.getHand() == InteractionHand.MAIN_HAND) {
            attemptClaim(event.getEntity());
        }
    }

    @SubscribeEvent
    public static void onLeftClickEmpty(PlayerInteractEvent.LeftClickEmpty event) {
        attemptClaim(event.getEntity());
    }

    private static void attemptClaim(Player player) {
        if (GhostClientManager.isEmpty()) {
            return;
        }
        GhostClientManager.ClientGhost target = aimedGhost(player);
        if (target != null) {
            ClientPacketDistributor.sendToServer(new GravelessNetworking.ClaimRequestPayload(target.recordId()));
        }
    }

    private static GhostClientManager.ClientGhost aimedGhost(Player player) {
        int range = GravelessConfig.SERVER.claimRange.get();
        Vec3 eye = player.getEyePosition();
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
            double offAxis = eye.add(look.scale(along)).distanceTo(heart);
            double tolerance = Math.max(1.2, along * 0.06);
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
