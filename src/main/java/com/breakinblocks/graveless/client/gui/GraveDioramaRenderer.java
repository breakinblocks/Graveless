package com.breakinblocks.graveless.client.gui;

import com.breakinblocks.graveless.client.render.GhostRenderTypes;
import com.breakinblocks.graveless.event.GraveMenuHandlers;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.CardinalLighting;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

public class GraveDioramaRenderer extends PictureInPictureRenderer<GraveDioramaRenderState> {
    private static final int FULL_BRIGHT = 0xF000F0;
    private static final int SIZE = GraveMenuHandlers.TERRAIN_SIZE;
    private static final int HEIGHT = GraveMenuHandlers.TERRAIN_HEIGHT;

    private BlockState[] lastBlocks;
    private Identifier lastTexture;
    private float lastScale;
    private float lastYaw;

    public GraveDioramaRenderer(MultiBufferSource.BufferSource bufferSource) {
        super(bufferSource);
    }

    @Override
    public Class<GraveDioramaRenderState> getRenderStateClass() {
        return GraveDioramaRenderState.class;
    }

    @Override
    protected String getTextureLabel() {
        return "graveless diorama";
    }

    @Override
    protected boolean textureIsReadyToBlit(GraveDioramaRenderState state) {
        return state.blocks() == lastBlocks && state.scale() == lastScale && state.yaw() == lastYaw
                && state.skin().body().texturePath().equals(lastTexture);
    }

    @Override
    protected float getTranslateY(int height, int guiScale) {
        return height / 2.0F;
    }

    @Override
    protected void renderToTexture(GraveDioramaRenderState state, PoseStack poseStack) {
        Minecraft minecraft = Minecraft.getInstance();
        DioramaView view = new DioramaView(state.blocks());
        ModelBlockRenderer renderer = new ModelBlockRenderer(false, true, minecraft.getBlockColors());
        BlockStateModelSet models = minecraft.getModelManager().getBlockStateModelSet();

        float ghostFeet = groundLayer(state.blocks());
        poseStack.scale(1.0F, -1.0F, 1.0F);
        poseStack.mulPose(new Quaternionf().rotationXYZ(
                (float) Math.toRadians(30.0), (float) Math.toRadians(state.yaw()), 0.0F));
        poseStack.translate(-(SIZE / 2.0F + 0.5F), -(ghostFeet + 0.9F), -(SIZE / 2.0F + 0.5F));

        renderPass(renderer, models, view, poseStack, false);
        this.bufferSource.endBatch();
        renderPass(renderer, models, view, poseStack, true);
        this.bufferSource.endBatch();
        renderGhost(state, poseStack);
        lastBlocks = state.blocks();
        lastTexture = state.skin().body().texturePath();
        lastScale = state.scale();
        lastYaw = state.yaw();
    }

    private void renderGhost(GraveDioramaRenderState state, PoseStack poseStack) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.gameRenderer.getLighting().setupFor(Lighting.Entry.ENTITY_IN_UI);
        FeatureRenderDispatcher dispatcher = minecraft.gameRenderer.getFeatureRenderDispatcher();
        AvatarRenderState avatar = new AvatarRenderState();
        avatar.skin = state.skin();
        avatar.ageInTicks = 0.0F;
        avatar.showCape = false;
        avatar.lightCoords = FULL_BRIGHT;
        poseStack.pushPose();
        poseStack.translate(SIZE / 2.0F + 0.5F, groundLayer(state.blocks()) + 0.05F, SIZE / 2.0F + 0.5F);
        poseStack.mulPose(Axis.YP.rotationDegrees(205.0F));
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        poseStack.translate(0.0F, -1.501F, 0.0F);
        Identifier texture = state.skin().body().texturePath();
        dispatcher.getSubmitNodeStorage().submitModel(state.playerModel(), avatar, poseStack,
                RenderTypes.entityTranslucentEmissive(texture), FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY, ARGB.color(245, 255, 255, 255), null, 0, null);
        dispatcher.getSubmitNodeStorage().submitModel(state.playerModel(), avatar, poseStack,
                GhostRenderTypes.ghostPreview(texture), FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY, ARGB.color(80, 160, 235, 255), null, 0, null);
        poseStack.popPose();
        dispatcher.renderAllFeatures();
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

    private void renderPass(ModelBlockRenderer renderer, BlockStateModelSet models, DioramaView view,
                            PoseStack poseStack, boolean translucent) {
        BlockQuadOutput output = (x, y, z, quad, instance) -> {
            boolean quadTranslucent = quad.materialInfo().layer() == ChunkSectionLayer.TRANSLUCENT;
            if (quadTranslucent != translucent) {
                return;
            }
            instance.setLightCoords(FULL_BRIGHT);
            VertexConsumer buffer = this.bufferSource.getBuffer(switch (quad.materialInfo().layer()) {
                case SOLID -> RenderTypes.solidMovingBlock();
                case CUTOUT -> RenderTypes.cutoutMovingBlock();
                case TRANSLUCENT -> RenderTypes.translucentMovingBlock();
            });
            poseStack.pushPose();
            poseStack.translate(x, y, z);
            buffer.putBakedQuad(poseStack.last(), quad, instance);
            poseStack.popPose();
        };

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int y = 0; y < HEIGHT; y++) {
            for (int z = 0; z < SIZE; z++) {
                for (int x = 0; x < SIZE; x++) {
                    BlockState blockState = view.getBlockState(cursor.set(x, y, z));
                    if (blockState.isAir()) {
                        continue;
                    }
                    BlockStateModel model = models.get(blockState);
                    renderer.tesselateBlock(output, x, y, z, view, cursor.immutable(), blockState, model,
                            blockState.getSeed(cursor));
                }
            }
        }
    }

    private static final class DioramaView implements BlockAndTintGetter {
        private final BlockState[] blocks;

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
        public CardinalLighting cardinalLighting() {
            return CardinalLighting.DEFAULT;
        }

        @Override
        public LevelLightEngine getLightEngine() {
            return LevelLightEngine.EMPTY;
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
        public int getMinY() {
            return 0;
        }
    }
}
