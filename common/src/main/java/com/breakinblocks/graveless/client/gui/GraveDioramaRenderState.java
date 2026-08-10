package com.breakinblocks.graveless.client.gui;

import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

public record GraveDioramaRenderState(
        UUID recordId,
        BlockState[] blocks,
        PlayerModel playerModel,
        PlayerSkin skin,
        float yaw,
        int x0,
        int y0,
        int x1,
        int y1,
        float scale,
        @Nullable ScreenRectangle scissorArea,
        @Nullable ScreenRectangle bounds
) implements PictureInPictureRenderState {
    public GraveDioramaRenderState(UUID recordId, BlockState[] blocks, PlayerModel playerModel, PlayerSkin skin,
                                   float yaw, int x0, int y0, int x1, int y1, float scale,
                                   @Nullable ScreenRectangle scissorArea) {
        this(recordId, blocks, playerModel, skin, yaw, x0, y0, x1, y1, scale, scissorArea,
                PictureInPictureRenderState.getBounds(x0, y0, x1, y1, scissorArea));
    }
}
