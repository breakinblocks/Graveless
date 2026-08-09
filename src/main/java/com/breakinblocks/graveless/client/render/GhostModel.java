package com.breakinblocks.graveless.client.render;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;

public class GhostModel extends PlayerModel {

    public GhostModel(ModelPart root, boolean slim) {
        super(root, slim);
    }

    @Override
    public void setupAnim(AvatarRenderState state) {
        super.setupAnim(state);
        float t = state.ageInTicks * 0.06F;
        float sway = Mth.sin(t) * 0.06F;
        float drift = Mth.cos(t * 0.7F) * 0.05F;

        this.head.xRot = 0.12F + Mth.sin(t * 0.5F) * 0.05F;
        this.head.zRot = drift * 0.4F;
        this.head.yRot = 0.0F;

        this.body.xRot = 0.04F;
        this.body.zRot = drift * 0.2F;

        this.rightArm.xRot = -0.35F + sway;
        this.rightArm.zRot = -0.5F - drift;
        this.rightArm.yRot = 0.0F;
        this.leftArm.xRot = -0.35F - sway;
        this.leftArm.zRot = 0.5F + drift;
        this.leftArm.yRot = 0.0F;

        this.rightLeg.xRot = 0.14F + sway * 0.5F;
        this.rightLeg.zRot = -0.08F;
        this.leftLeg.xRot = -0.12F - sway * 0.5F;
        this.leftLeg.zRot = 0.08F;
    }
}
