package com.breakinblocks.graveless.client.gui;

import com.breakinblocks.graveless.client.render.GhostModel;
import com.breakinblocks.graveless.client.render.GhostRenderTypes;
import com.breakinblocks.graveless.event.GraveMenuHandlers;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LightChunk;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;

public final class GraveDioramaRenderer {
    private static final int FULL_BRIGHT = 0xF000F0;
    private static final int SIZE = GraveMenuHandlers.TERRAIN_SIZE;
    private static final int HEIGHT = GraveMenuHandlers.TERRAIN_HEIGHT;

    private GraveDioramaRenderer() {
    }

    public static void render(GuiGraphics graphics, BlockState[] blocks, GhostModel playerModel, PlayerSkin skin,
                              float yaw, int x0, int y0, int x1, int y1, float scale) {
        Minecraft minecraft = Minecraft.getInstance();
        MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();
        DioramaView view = new DioramaView(blocks);
        int ghostFeet = groundLayer(blocks);

        graphics.flush();
        graphics.enableScissor(x0, y0, x1, y1);
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate((x0 + x1) / 2.0F, (y0 + y1) / 2.0F, 250.0F);
        pose.scale(scale, -scale, scale);
        pose.mulPose(new Quaternionf().rotationXYZ(
                (float) Math.toRadians(30.0), (float) Math.toRadians(yaw), 0.0F));
        pose.translate(-(SIZE / 2.0F + 0.5F), -(ghostFeet + 0.9F), -(SIZE / 2.0F + 0.5F));

        Lighting.setupFor3DItems();
        renderPass(minecraft.getBlockRenderer(), view, pose, bufferSource, false);
        bufferSource.endBatch();
        renderPass(minecraft.getBlockRenderer(), view, pose, bufferSource, true);
        bufferSource.endBatch();
        renderGhost(pose, bufferSource, playerModel, skin, ghostFeet);
        Lighting.setupFor3DItems();

        pose.popPose();
        graphics.disableScissor();
    }

    private static void renderGhost(PoseStack pose, MultiBufferSource.BufferSource bufferSource,
                                    GhostModel model, PlayerSkin skin, int ghostFeet) {
        Lighting.setupForEntityInInventory();
        model.setupGhostAnim(0.0F);
        pose.pushPose();
        pose.translate(SIZE / 2.0F + 0.5F, ghostFeet + 0.05F, SIZE / 2.0F + 0.5F);
        pose.mulPose(Axis.YP.rotationDegrees(205.0F));
        pose.scale(-1.0F, -1.0F, 1.0F);
        pose.translate(0.0F, -1.501F, 0.0F);
        ResourceLocation texture = skin.texture();
        VertexConsumer body = bufferSource.getBuffer(RenderType.entityTranslucentEmissive(texture));
        model.renderToBuffer(pose, body, FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                FastColor.ARGB32.color(245, 255, 255, 255));
        bufferSource.endBatch();
        VertexConsumer glow = bufferSource.getBuffer(GhostRenderTypes.ghostPreview(texture));
        model.renderToBuffer(pose, glow, FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                FastColor.ARGB32.color(80, 160, 235, 255));
        bufferSource.endBatch();
        pose.popPose();
    }

    private static int groundLayer(BlockState[] blocks) {
        int center = SIZE / 2;
        int feet = GraveMenuHandlers.TERRAIN_BELOW;
        while (feet > 0) {
            BlockState below = blocks[((feet - 1) * SIZE + center) * SIZE + center];
            if (!below.isAir()) {
                break;
            }
            feet--;
        }
        return feet;
    }

    private static void renderPass(BlockRenderDispatcher dispatcher, DioramaView view, PoseStack pose,
                                   MultiBufferSource.BufferSource bufferSource, boolean translucent) {
        RandomSource random = RandomSource.create();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int y = 0; y < HEIGHT; y++) {
            for (int z = 0; z < SIZE; z++) {
                for (int x = 0; x < SIZE; x++) {
                    BlockState blockState = view.getBlockState(cursor.set(x, y, z));
                    if (blockState.isAir()) {
                        continue;
                    }
                    RenderType layer = ItemBlockRenderTypes.getMovingBlockRenderType(blockState);
                    boolean layerTranslucent = layer == RenderType.translucentMovingBlock();
                    if (layerTranslucent != translucent) {
                        continue;
                    }
                    VertexConsumer buffer = new FullBrightVertexConsumer(bufferSource.getBuffer(layer));
                    pose.pushPose();
                    pose.translate(x, y, z);
                    dispatcher.renderBatched(blockState, cursor.immutable(), view, pose, buffer, false, random);
                    pose.popPose();
                }
            }
        }
    }

    private record FullBrightVertexConsumer(VertexConsumer delegate) implements VertexConsumer {
        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            delegate.addVertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            delegate.setColor(red, green, blue, alpha);
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            delegate.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            delegate.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            delegate.setUv2(FULL_BRIGHT & 0xFFFF, (FULL_BRIGHT >> 16) & 0xFFFF);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            delegate.setNormal(x, y, z);
            return this;
        }
    }

    private static final class DioramaView implements BlockAndTintGetter {
        private final BlockState[] blocks;
        private LevelLightEngine lightEngine;

        DioramaView(BlockState[] blocks) {
            this.blocks = blocks;
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            int x = pos.getX();
            int y = pos.getY();
            int z = pos.getZ();
            if (x < 0 || x >= SIZE || z < 0 || z >= SIZE || y < 0 || y >= HEIGHT) {
                return Blocks.AIR.defaultBlockState();
            }
            return blocks[(y * SIZE + z) * SIZE + x];
        }

        @Override
        public FluidState getFluidState(BlockPos pos) {
            return getBlockState(pos).getFluidState();
        }

        @Override
        public float getShade(Direction direction, boolean shaded) {
            if (!shaded) {
                return 1.0F;
            }
            return switch (direction) {
                case DOWN -> 0.5F;
                case UP -> 1.0F;
                case NORTH, SOUTH -> 0.8F;
                case WEST, EAST -> 0.6F;
            };
        }

        @Override
        public int getBrightness(LightLayer layer, BlockPos pos) {
            return 15;
        }

        @Override
        public int getRawBrightness(BlockPos pos, int amount) {
            return 15;
        }

        @Override
        public LevelLightEngine getLightEngine() {
            if (lightEngine == null) {
                lightEngine = new LevelLightEngine(new LightChunkGetter() {
                    @Override
                    public @Nullable LightChunk getChunkForLighting(int chunkX, int chunkZ) {
                        return null;
                    }

                    @Override
                    public BlockGetter getLevel() {
                        return DioramaView.this;
                    }
                }, false, false);
            }
            return lightEngine;
        }

        @Override
        public int getBlockTint(BlockPos pos, ColorResolver color) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.level == null || minecraft.player == null) {
                return -1;
            }
            Holder<Biome> biome = minecraft.level.getBiome(minecraft.player.blockPosition());
            return color.getColor(biome.value(), pos.getX(), pos.getZ());
        }

        @Override
        public @Nullable BlockEntity getBlockEntity(BlockPos pos) {
            return null;
        }

        @Override
        public int getHeight() {
            return HEIGHT;
        }

        @Override
        public int getMinBuildHeight() {
            return 0;
        }
    }
}
