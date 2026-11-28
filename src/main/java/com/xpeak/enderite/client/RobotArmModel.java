package com.xpeak.enderite.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.xpeak.enderite.EnderiteMod;
import com.xpeak.enderite.robot.RobotArmType;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/** Brazo robot (manga metalica + motosierra o lanzacohetes). Coordenadas relativas al hombro, como el brazo del jugador. */
public class RobotArmModel {
    public static final ModelLayerLocation RIGHT = new ModelLayerLocation(new ResourceLocation(EnderiteMod.MODID, "robot_arm"), "right");
    public static final ModelLayerLocation LEFT = new ModelLayerLocation(new ResourceLocation(EnderiteMod.MODID, "robot_arm"), "left");
    public static final ResourceLocation TEXTURE = new ResourceLocation(EnderiteMod.MODID, "textures/entity/robot_arms.png");

    private final ModelPart sleeve, shoulder, motor, bar, chain, rocketBody, barrels;

    public RobotArmModel(ModelPart root) {
        sleeve = root.getChild("sleeve");
        shoulder = root.getChild("shoulder");
        motor = root.getChild("motor");
        bar = root.getChild("bar");
        chain = root.getChild("chain");
        rocketBody = root.getChild("rocket_body");
        barrels = root.getChild("barrels");
    }

    public static LayerDefinition create(boolean right) {
        float cx = right ? -1.0F : 1.0F;
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("sleeve", CubeListBuilder.create().texOffs(0, 0).addBox(cx - 2.5F, -2.5F, -2.5F, 5, 13, 5), PartPose.ZERO);
        root.addOrReplaceChild("shoulder", CubeListBuilder.create().texOffs(20, 0).addBox(cx - 3.0F, -3.5F, -3.0F, 6, 4, 6), PartPose.ZERO);
        root.addOrReplaceChild("motor", CubeListBuilder.create().texOffs(0, 18).addBox(cx - 3.0F, 7.5F, -3.0F, 6, 5, 6), PartPose.ZERO);
        root.addOrReplaceChild("bar", CubeListBuilder.create().texOffs(24, 18).addBox(cx - 1.0F, 12.0F, -2.5F, 2, 12, 5), PartPose.ZERO);
        root.addOrReplaceChild("chain", CubeListBuilder.create().texOffs(0, 36).addBox(cx - 1.5F, 11.5F, -3.0F, 3, 13, 6), PartPose.ZERO);
        root.addOrReplaceChild("rocket_body", CubeListBuilder.create().texOffs(36, 36).addBox(cx - 3.5F, 3.0F, -3.5F, 7, 8, 7), PartPose.ZERO);
        root.addOrReplaceChild("barrels", CubeListBuilder.create()
                .texOffs(44, 0).addBox(cx - 2.5F, 11.0F, -2.5F, 2, 5, 2)
                .texOffs(52, 0).addBox(cx + 0.5F, 11.0F, -2.5F, 2, 5, 2)
                .texOffs(44, 8).addBox(cx - 1.0F, 11.0F, 0.5F, 2, 5, 2), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 64);
    }

    /** Dibuja el brazo. {@code active}: motosierra girando / lanzacohetes disparando. */
    public void render(PoseStack pose, VertexConsumer vc, int light, RobotArmType type, boolean active, float time) {
        int ov = OverlayTexture.NO_OVERLAY;
        sleeve.render(pose, vc, light, ov);
        shoulder.render(pose, vc, light, ov);
        if (type == RobotArmType.CHAINSAW) {
            motor.render(pose, vc, light, ov);
            pose.pushPose();
            if (active) {
                // La motosierra se acerca hacia delante y la cadena vibra
                pose.translate(0.0D, 2.5D / 16.0D, 0.0D);
                float jitter = ((int) (time * 3.0F) % 2 == 0) ? 0.35F : -0.35F;
                pose.translate(jitter / 16.0F * 0.4F, jitter / 16.0F, 0.0D);
            }
            bar.render(pose, vc, light, ov);
            chain.render(pose, vc, light, ov);
            pose.popPose();
        } else if (type == RobotArmType.ROCKET) {
            rocketBody.render(pose, vc, light, ov);
            pose.pushPose();
            if (active) pose.translate(0.0D, -1.0D / 16.0D, 0.0D); // retroceso
            barrels.render(pose, vc, light, ov);
            pose.popPose();
        }
    }
}
