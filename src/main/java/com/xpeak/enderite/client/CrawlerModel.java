package com.xpeak.enderite.client;

import com.xpeak.enderite.EnderiteMod;
import com.xpeak.enderite.entity.CrawlerEntity;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Modelo del Crawler: cuerpo de enderman encorvado, brazos y piernas larguisimos y una cabeza
 * sin ojos que es practicamente una boca enorme (la mandibula se abre cuando te persigue).
 */
public class CrawlerModel extends HierarchicalModel<CrawlerEntity> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(new ResourceLocation(EnderiteMod.MODID, "crawler"), "main");

    private final ModelPart root;
    private final ModelPart body;
    private final ModelPart neck;
    private final ModelPart head;
    private final ModelPart jaw;
    private final ModelPart rightArm;
    private final ModelPart leftArm;
    private final ModelPart rightLeg;
    private final ModelPart leftLeg;

    public CrawlerModel(ModelPart root) {
        this.root = root;
        this.body = root.getChild("body");
        this.neck = body.getChild("neck");
        this.head = neck.getChild("head");
        this.jaw = head.getChild("jaw");
        this.rightArm = body.getChild("right_arm");
        this.leftArm = body.getChild("left_arm");
        this.rightLeg = root.getChild("right_leg");
        this.leftLeg = root.getChild("left_leg");
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // Piernas: 28 px de largo, del suelo (y=24) hasta la cadera (y=-4)
        root.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(56, 16).addBox(-1.0F, 0.0F, -1.0F, 2, 28, 2),
                PartPose.offset(-2.0F, -4.0F, 0.0F));
        root.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(24, 32).mirror().addBox(-1.0F, 0.0F, -1.0F, 2, 28, 2),
                PartPose.offset(2.0F, -4.0F, 0.0F));

        // Cuerpo: pivota en la cadera, crece hacia arriba
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 32).addBox(-4.0F, -12.0F, -2.0F, 8, 12, 4),
                PartPose.offset(0.0F, -4.0F, 0.0F));

        // Brazos larguisimos colgando de los hombros
        body.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(40, 16).addBox(-1.0F, -1.0F, -1.0F, 2, 28, 2),
                PartPose.offset(-5.0F, -11.0F, 0.0F));
        body.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(48, 16).mirror().addBox(-1.0F, -1.0F, -1.0F, 2, 28, 2),
                PartPose.offset(5.0F, -11.0F, 0.0F));

        // Cuello y cabeza
        PartDefinition neck = body.addOrReplaceChild("neck", CubeListBuilder.create().texOffs(48, 0).addBox(-1.0F, -2.0F, -1.0F, 2, 2, 2),
                PartPose.offset(0.0F, -12.0F, 0.0F));
        PartDefinition head = neck.addOrReplaceChild("head", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-4.0F, -8.0F, -4.0F, 8, 5, 8)        // craneo (sin ojos)
                        .texOffs(32, 0).addBox(-4.0F, -3.0F, -4.05F, 8, 2, 0),    // dientes de arriba
                PartPose.offset(0.0F, -2.0F, 0.0F));
        head.addOrReplaceChild("jaw", CubeListBuilder.create()
                        .texOffs(0, 16).addBox(-4.0F, 0.0F, -8.0F, 8, 3, 8)        // mandibula
                        .texOffs(32, 4).addBox(-4.0F, -2.0F, -8.05F, 8, 2, 0),    // dientes de abajo
                PartPose.offset(0.0F, -3.0F, 4.0F));

        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(CrawlerEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        this.root().getAllParts().forEach(ModelPart::resetPose);

        boolean aggressive = entity.isAggressive();
        boolean climbing = entity.isClimbing();
        boolean digging = entity.isDigging();

        // Encorvado hacia delante
        body.xRot = 0.32F;
        neck.xRot = -0.15F;

        // Cabeza que sigue a la presa, con tics nerviosos
        head.yRot = netHeadYaw * ((float) Math.PI / 180F);
        head.xRot = headPitch * ((float) Math.PI / 180F) - 0.17F;
        float twitchPhase = (ageInTicks + entity.getId() * 13) % 37.0F;
        if (twitchPhase < 2.0F) {
            head.zRot = (entity.getId() % 2 == 0 ? 1 : -1) * 0.45F;
            head.yRot += 0.3F;
        } else {
            head.zRot = Mth.sin(ageInTicks * 0.13F) * 0.06F;
        }

        // Mandibula: entreabierta en reposo, abierta del todo cuando te persigue (y "masticando")
        float open = aggressive ? 0.75F + Mth.sin(ageInTicks * 0.55F) * 0.2F : 0.18F + Mth.sin(ageInTicks * 0.09F) * 0.06F;
        jaw.xRot = open;

        // Piernas
        float walk = limbSwing * 0.55F;
        rightLeg.xRot = Mth.cos(walk) * 0.9F * limbSwingAmount;
        leftLeg.xRot = Mth.cos(walk + (float) Math.PI) * 0.9F * limbSwingAmount;

        // Brazos
        if (climbing) {
            rightArm.xRot = -2.9F + Mth.sin(ageInTicks * 0.6F) * 0.4F;
            leftArm.xRot = -2.9F - Mth.sin(ageInTicks * 0.6F) * 0.4F;
        } else if (aggressive || digging) {
            rightArm.xRot = -1.55F + Mth.sin(ageInTicks * 0.35F) * 0.12F;
            leftArm.xRot = -1.45F - Mth.sin(ageInTicks * 0.35F) * 0.12F;
            rightArm.zRot = 0.08F;
            leftArm.zRot = -0.08F;
        } else {
            rightArm.xRot = Mth.cos(walk + (float) Math.PI) * 0.5F * limbSwingAmount - 0.25F;
            leftArm.xRot = Mth.cos(walk) * 0.5F * limbSwingAmount - 0.25F;
            rightArm.zRot = 0.05F + Mth.sin(ageInTicks * 0.05F) * 0.03F;
            leftArm.zRot = -0.05F - Mth.sin(ageInTicks * 0.05F) * 0.03F;
        }

        // Golpe
        if (this.attackTime > 0.0F) {
            float a = Mth.sin(this.attackTime * (float) Math.PI);
            rightArm.xRot -= a * 1.2F;
            leftArm.xRot -= a * 0.9F;
            body.xRot += a * 0.2F;
        }
    }
}
