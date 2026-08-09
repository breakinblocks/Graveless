package com.breakinblocks.graveless.client.render;

import com.breakinblocks.graveless.Graveless;
import com.breakinblocks.graveless.client.GhostClientManager;
import com.breakinblocks.graveless.config.GravelessConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.FrameGraphSetupEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import org.joml.Matrix4f;

@EventBusSubscriber(modid = Graveless.MOD_ID, value = Dist.CLIENT)
public class GhostRenderManager {
    private static final int FULL_BRIGHT = 0xF000F0;
    private static final int STRAND_COUNT = 1;
    private static final int STRAND_SEGMENTS = 14;
    private static final double STRAND_RANGE = 32.0;
    private static final float HEART_HEIGHT = 1.25F;
    private static final float BEAM_HEIGHT = 256.0F;
    private static final float BEAM_FADE_START = 192.0F;
    private static final float BEAM_WIDTH = 0.28F;

    private record GhostVisibility(float body, float outline) {
    }

    private static GhostModel wideModel;
    private static GhostModel slimModel;

    @SubscribeEvent
    public static void onSubmit(SubmitCustomGeometryEvent event) {
        if (GhostClientManager.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }
        ensureModels(minecraft);

        Vec3 camPos = event.getLevelRenderState().cameraRenderState.pos;
        float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Vec3 look = minecraft.player.getViewVector(partialTick);
        float time = minecraft.level.getGameTime() + partialTick;
        Vec3 playerChest = minecraft.player.getPosition(partialTick).add(0.0, 1.1, 0.0);

        PoseStack poseStack = event.getPoseStack();
        SubmitNodeCollector collector = event.getSubmitNodeCollector();

        for (GhostClientManager.ClientGhost ghost : GhostClientManager.all()) {
            BlockPos pos = ghost.pos();
            Vec3 base = new Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
            float phase = (ghost.recordId().hashCode() & 0xFF) * 0.13F;
            float ghostTime = time + phase;
            float bob = 0.18F + Mth.sin(ghostTime * 0.05F) * 0.07F;
            Vec3 heart = base.add(0.0, HEART_HEIGHT + bob, 0.0);
            double dist = heart.distanceTo(camPos);
            float farFade = farFade(dist);
            GhostVisibility visibility = visibility(camPos, look, heart, dist, farFade);

            submitBeam(poseStack, collector, camPos, base, ghostTime, dist, farFade);
            if (visibility.body() >= 0.01F || visibility.outline() >= 0.01F) {
                submitGhostModel(poseStack, collector, camPos, base, ghost, ghostTime, bob, visibility);
                submitStrands(poseStack, collector, camPos, heart, playerChest, ghostTime, visibility.body());
            }
        }
    }

    @SubscribeEvent
    public static void onFrameGraphSetup(FrameGraphSetupEvent event) {
        if (!GhostClientManager.isEmpty()) {
            event.enableOutlineProcessing();
        }
    }

    private static void ensureModels(Minecraft minecraft) {
        if (wideModel == null) {
            wideModel = new GhostModel(minecraft.getEntityModels().bakeLayer(ModelLayers.PLAYER), false);
            slimModel = new GhostModel(minecraft.getEntityModels().bakeLayer(ModelLayers.PLAYER_SLIM), true);
        }
    }

    private static float farFade(double dist) {
        double fadeEnd = GravelessConfig.SERVER.visibilityRadius.get();
        double fadeStart = Math.max(fadeEnd - 32.0, 16.0);
        return Mth.clamp((float) ((fadeEnd - dist) / (fadeEnd - fadeStart)), 0.0F, 1.0F);
    }

    private static GhostVisibility visibility(Vec3 camPos, Vec3 look, Vec3 heart, double dist, float farFade) {
        if (dist < 0.5) {
            return new GhostVisibility(1.0F, 1.0F);
        }
        Vec3 toGhost = heart.subtract(camPos).scale(1.0 / dist);
        double dot = look.dot(toGhost);
        float focus = smoothstep((float) dot, 0.60F, 0.94F);
        float near = Mth.clamp((float) (1.0 - (dist - 2.5) / 4.0), 0.0F, 1.0F);
        float body = Math.max(focus, near * 0.35F);
        float outlineFocus = smoothstep((float) dot, 0.20F, 0.60F);
        float outline = Math.max(outlineFocus, near);
        return new GhostVisibility(body * farFade, outline * farFade);
    }

    private static float smoothstep(float x, float edge0, float edge1) {
        float t = Mth.clamp((x - edge0) / (edge1 - edge0), 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    private static void submitGhostModel(PoseStack poseStack, SubmitNodeCollector collector, Vec3 camPos,
                                         Vec3 base, GhostClientManager.ClientGhost ghost, float time,
                                         float bob, GhostVisibility visibility) {
        PlayerSkin skin = GhostSkins.get(ghost.ownerId(), ghost.ownerName());
        GhostModel model = skin.model() == PlayerModelType.SLIM ? slimModel : wideModel;
        Identifier texture = skin.body().texturePath();

        float bodyRot = (float) -Math.toDegrees(Math.atan2(camPos.x - base.x, camPos.z - base.z));

        AvatarRenderState state = new AvatarRenderState();
        state.skin = skin;
        state.ageInTicks = time;
        state.showCape = false;
        state.lightCoords = FULL_BRIGHT;

        poseStack.pushPose();
        poseStack.translate(base.x - camPos.x, base.y - camPos.y + bob, base.z - camPos.z);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - bodyRot));
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        poseStack.translate(0.0F, -1.501F, 0.0F);

        int bodyTint = ARGB.color((int) (visibility.body() * 255.0F), 210, 235, 255);
        int outlineColor = ARGB.color((int) (visibility.outline() * 255.0F), 120, 235, 255);
        collector.submitModel(model, state, poseStack, GhostRenderTypes.ghost(texture),
                FULL_BRIGHT, OverlayTexture.NO_OVERLAY, bodyTint, null, outlineColor, null);

        if (!GhostRenderTypes.shadersEnabled() && visibility.body() >= 0.01F) {
            poseStack.pushPose();
            poseStack.scale(1.05F, 1.03F, 1.05F);
            int auraTint = ARGB.color((int) (visibility.body() * 70.0F), 150, 200, 255);
            collector.submitModel(model, state, poseStack, RenderTypes.entityTranslucentEmissive(texture),
                    FULL_BRIGHT, OverlayTexture.NO_OVERLAY, auraTint, null, 0, null);
            poseStack.popPose();
        }

        poseStack.popPose();
    }

    private static void submitBeam(PoseStack poseStack, SubmitNodeCollector collector, Vec3 camPos,
                                   Vec3 base, float time, double dist, float farFade) {
        float distRamp = Mth.clamp((float) ((dist - 8.0) / 16.0), 0.0F, 1.0F);
        float pulse = 0.8F + 0.2F * Mth.sin(time * 0.08F);
        float beamAlpha = 0.22F * distRamp * farFade * pulse;
        if (beamAlpha < 0.01F) {
            return;
        }
        poseStack.pushPose();
        poseStack.translate(base.x - camPos.x, base.y - camPos.y, base.z - camPos.z);
        float finalAlpha = beamAlpha;
        collector.submitCustomGeometry(poseStack, GhostRenderTypes.thread(), (pose, buffer) ->
                renderBeam(pose.pose(), buffer, finalAlpha));
        poseStack.popPose();
    }

    private static void renderBeam(Matrix4f matrix, VertexConsumer buffer, float alpha) {
        for (int i = 0; i < 4; i++) {
            float angle = (float) (i * Math.PI / 4.0);
            float x = Mth.cos(angle) * BEAM_WIDTH;
            float z = Mth.sin(angle) * BEAM_WIDTH;
            beamQuad(matrix, buffer, -x, -z, x, z, alpha);
            beamQuad(matrix, buffer, x, z, -x, -z, alpha);
        }
    }

    private static void beamQuad(Matrix4f matrix, VertexConsumer buffer,
                                 float x1, float z1, float x2, float z2, float alpha) {
        buffer.addVertex(matrix, x1, 0.0F, z1).setColor(0.78F, 0.9F, 1.0F, alpha);
        buffer.addVertex(matrix, x2, 0.0F, z2).setColor(0.78F, 0.9F, 1.0F, alpha);
        buffer.addVertex(matrix, x2, BEAM_FADE_START, z2).setColor(0.78F, 0.9F, 1.0F, alpha);
        buffer.addVertex(matrix, x1, BEAM_FADE_START, z1).setColor(0.78F, 0.9F, 1.0F, alpha);

        buffer.addVertex(matrix, x1, BEAM_FADE_START, z1).setColor(0.78F, 0.9F, 1.0F, alpha);
        buffer.addVertex(matrix, x2, BEAM_FADE_START, z2).setColor(0.78F, 0.9F, 1.0F, alpha);
        buffer.addVertex(matrix, x2, BEAM_HEIGHT, z2).setColor(0.78F, 0.9F, 1.0F, 0.0F);
        buffer.addVertex(matrix, x1, BEAM_HEIGHT, z1).setColor(0.78F, 0.9F, 1.0F, 0.0F);
    }

    private static void submitStrands(PoseStack poseStack, SubmitNodeCollector collector, Vec3 camPos,
                                      Vec3 heart, Vec3 playerChest, float time, float alpha) {
        Vec3 toPlayer = playerChest.subtract(heart);
        double dist = toPlayer.length();
        if (dist < 1.0 || dist > STRAND_RANGE) {
            return;
        }
        float proximity = Mth.clamp((float) (1.0 - (dist - 1.5) / (STRAND_RANGE - 1.5)), 0.0F, 1.0F);
        float intensity = alpha * proximity;
        if (intensity < 0.02F) {
            return;
        }

        Vec3 dir = toPlayer.scale(1.0 / dist);
        Vec3 side = dir.cross(new Vec3(0.0, 1.0, 0.0));
        if (side.lengthSqr() < 1.0E-4) {
            side = new Vec3(1.0, 0.0, 0.0);
        } else {
            side = side.normalize();
        }
        Vec3 up = side.cross(dir);

        poseStack.pushPose();
        poseStack.translate(heart.x - camPos.x, heart.y - camPos.y, heart.z - camPos.z);

        for (int s = 0; s < STRAND_COUNT; s++) {
            float strandPhase = time * 0.09F + s * ((float) Math.PI * 2.0F / STRAND_COUNT);
            float pulse = 0.7F + 0.3F * Mth.sin(time * 0.13F + s * 1.7F);
            float strandAlpha = intensity * pulse * 0.8F;
            if (strandAlpha < 0.015F) {
                continue;
            }
            Vec3 bend = side.scale(Mth.sin(strandPhase) * 0.3)
                    .add(up.scale(Mth.cos(strandPhase * 0.8F) * 0.2))
                    .add(0.0, 0.3, 0.0);
            Vec3 mid = toPlayer.scale(0.5).add(bend);

            float[][] points = new float[STRAND_SEGMENTS + 1][3];
            for (int i = 0; i <= STRAND_SEGMENTS; i++) {
                float t = (float) i / STRAND_SEGMENTS;
                double omt = 1.0 - t;
                double bx = 2.0 * omt * t * mid.x + t * t * toPlayer.x;
                double by = 2.0 * omt * t * mid.y + t * t * toPlayer.y;
                double bz = 2.0 * omt * t * mid.z + t * t * toPlayer.z;
                float wobble = Mth.sin(strandPhase * 2.0F + t * 9.0F) * 0.05F * (1.0F - t);
                points[i][0] = (float) (bx + side.x * wobble);
                points[i][1] = (float) (by + up.y * wobble);
                points[i][2] = (float) (bz + side.z * wobble);
            }

            Vec3 camToHeart = heart.subtract(camPos);
            float finalAlpha = strandAlpha;
            collector.submitCustomGeometry(poseStack, GhostRenderTypes.thread(), (pose, buffer) ->
                    renderRibbon(pose.pose(), buffer, points, camToHeart, finalAlpha));
        }
        poseStack.popPose();
    }

    private static void renderRibbon(Matrix4f matrix, VertexConsumer buffer, float[][] points,
                                     Vec3 camToHeart, float alpha) {
        int last = points.length - 1;
        for (int i = 0; i < last; i++) {
            float t0 = (float) i / last;
            float t1 = (float) (i + 1) / last;
            float w0 = Mth.lerp(t0, 0.07F, 0.016F);
            float w1 = Mth.lerp(t1, 0.07F, 0.016F);
            float a0 = alpha * (1.0F - t0 * 0.55F);
            float a1 = alpha * (1.0F - t1 * 0.55F);

            float dx = points[i + 1][0] - points[i][0];
            float dy = points[i + 1][1] - points[i][1];
            float dz = points[i + 1][2] - points[i][2];
            float cx = (float) camToHeart.x + points[i][0];
            float cy = (float) camToHeart.y + points[i][1];
            float cz = (float) camToHeart.z + points[i][2];
            float sx = dy * cz - dz * cy;
            float sy = dz * cx - dx * cz;
            float sz = dx * cy - dy * cx;
            float len = Mth.sqrt(sx * sx + sy * sy + sz * sz);
            if (len < 1.0E-5F) {
                continue;
            }
            sx /= len;
            sy /= len;
            sz /= len;

            addRibbonVertex(matrix, buffer, points[i][0] + sx * w0, points[i][1] + sy * w0, points[i][2] + sz * w0, a0);
            addRibbonVertex(matrix, buffer, points[i][0] - sx * w0, points[i][1] - sy * w0, points[i][2] - sz * w0, a0);
            addRibbonVertex(matrix, buffer, points[i + 1][0] - sx * w1, points[i + 1][1] - sy * w1, points[i + 1][2] - sz * w1, a1);
            addRibbonVertex(matrix, buffer, points[i + 1][0] + sx * w1, points[i + 1][1] + sy * w1, points[i + 1][2] + sz * w1, a1);
        }
    }

    private static void addRibbonVertex(Matrix4f matrix, VertexConsumer buffer, float x, float y, float z, float alpha) {
        buffer.addVertex(matrix, x, y, z)
                .setColor(0.88F, 0.94F, 1.0F, alpha);
    }

    public static void resetModels() {
        wideModel = null;
        slimModel = null;
    }
}
