package com.breakinblocks.graveless.client.render;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;

public class GhostModel extends PlayerModel<AbstractClientPlayer> {

    public GhostModel(ModelPart root, boolean slim) {
        super(root, slim);
    }

    public void setupGhostAnim(float ageInTicks) {
        float t = ageInTicks * 0.06F;
        float sway = Mth.sin(t) * 0.06F;
        float drift = Mth.cos(t * 0.7F) * 0.05F;

        this.setAllVisible(true);
        this.crouching = false;
        this.young = false;
        this.attackTime = 0.0F;
        this.swimAmount = 0.0F;

        this.head.xRot = 0.12F + Mth.sin(t * 0.5F) * 0.05F;
        this.head.zRot = drift * 0.4F;
        this.head.yRot = 0.0F;

        this.body.xRot = 0.04F;
        this.body.yRot = 0.0F;
        this.body.zRot = drift * 0.2F;

        this.rightArm.xRot = -0.35F + sway;
        this.rightArm.zRot = -0.5F - drift;
        this.rightArm.yRot = 0.0F;
        this.leftArm.xRot = -0.35F - sway;
        this.leftArm.zRot = 0.5F + drift;
        this.leftArm.yRot = 0.0F;

        this.rightLeg.xRot = 0.14F + sway * 0.5F;
        this.rightLeg.yRot = 0.0F;
        this.rightLeg.zRot = -0.08F;
        this.leftLeg.xRot = -0.12F - sway * 0.5F;
        this.leftLeg.yRot = 0.0F;
        this.leftLeg.zRot = 0.08F;

        this.hat.copyFrom(this.head);
        this.jacket.copyFrom(this.body);
        this.rightSleeve.copyFrom(this.rightArm);
        this.leftSleeve.copyFrom(this.leftArm);
        this.rightPants.copyFrom(this.rightLeg);
        this.leftPants.copyFrom(this.leftLeg);
    }
}
