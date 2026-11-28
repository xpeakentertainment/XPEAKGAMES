package com.xpeak.enderite.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.xpeak.enderite.EnderiteMod;
import com.xpeak.enderite.robot.RobotArmType;
import com.xpeak.enderite.robot.RobotHelper;
import com.xpeak.enderite.robot.RobotPortalEntity;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Brazo robot: manga metalica + la pieza de cada tipo. Coordenadas relativas al hombro, como el brazo del jugador.
 * La motosierra y el lanzacohetes usan robot_arms.png; los brazos nuevos, robot_arms_extra.png.
 */
public class RobotArmModel {
    public static final ModelLayerLocation RIGHT = new ModelLayerLocation(new ResourceLocation(EnderiteMod.MODID, "robot_arm"), "right");
    public static final ModelLayerLocation LEFT = new ModelLayerLocation(new ResourceLocation(EnderiteMod.MODID, "robot_arm"), "left");
    public static final ModelLayerLocation EXTRA_RIGHT = new ModelLayerLocation(new ResourceLocation(EnderiteMod.MODID, "robot_arm_extra"), "right");
    public static final ModelLayerLocation EXTRA_LEFT = new ModelLayerLocation(new ResourceLocation(EnderiteMod.MODID, "robot_arm_extra"), "left");
    public static final ResourceLocation TEXTURE = new ResourceLocation(EnderiteMod.MODID, "textures/entity/robot_arms.png");
    public static final ResourceLocation EXTRA_TEXTURE = new ResourceLocation(EnderiteMod.MODID, "textures/entity/robot_arms_extra.png");
    private static final int FULL_BRIGHT = 0xF000F0;

    private final ModelPart sleeve, shoulder, motor, bar, chain, rocketBody, barrels;
    private final ModelPart harpoonTube, harpoonTip, harpoonSpool, flameBody, flameNozzle, flameTank, portalBody, portalProngs, portalCore,
            exoBody, exoBarrel, exoCoils, exoCore, illusionBody, illusionCrystal, catapultPalm, catapultFingers;

    public RobotArmModel(ModelPart root, ModelPart extra) {
        sleeve = root.getChild("sleeve");
        shoulder = root.getChild("shoulder");
        motor = root.getChild("motor");
        bar = root.getChild("bar");
        chain = root.getChild("chain");
        rocketBody = root.getChild("rocket_body");
        barrels = root.getChild("barrels");
        harpoonTube = extra.getChild("harpoon_tube");
        harpoonTip = extra.getChild("harpoon_tip");
        harpoonSpool = extra.getChild("harpoon_spool");
        flameBody = extra.getChild("flame_body");
        flameNozzle = extra.getChild("flame_nozzle");
        flameTank = extra.getChild("flame_tank");
        portalBody = extra.getChild("portal_body");
        portalProngs = extra.getChild("portal_prongs");
        portalCore = extra.getChild("portal_core");
        exoBody = extra.getChild("exo_body");
        exoBarrel = extra.getChild("exo_barrel");
        exoCoils = extra.getChild("exo_coils");
        exoCore = extra.getChild("exo_core");
        illusionBody = extra.getChild("illusion_body");
        illusionCrystal = extra.getChild("illusion_crystal");
        catapultPalm = extra.getChild("catapult_palm");
        catapultFingers = extra.getChild("catapult_fingers");
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

    /** Piezas de los brazos nuevos (arpon, lanzallamas, portales, exobrazo, ilusiones, catapulta). */
    public static LayerDefinition createExtra(boolean right) {
        float cx = right ? -1.0F : 1.0F;
        float outer = right ? cx - 4.5F : cx + 2.5F;
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("harpoon_tube", CubeListBuilder.create().texOffs(0, 0).addBox(cx - 1.5F, 9.0F, -1.5F, 3, 6, 3), PartPose.ZERO);
        root.addOrReplaceChild("harpoon_tip", CubeListBuilder.create()
                .texOffs(12, 0).addBox(cx - 0.5F, 15.0F, -0.5F, 1, 4, 1)
                .texOffs(16, 0).addBox(cx - 1.5F, 16.0F, -0.5F, 3, 1, 1), PartPose.ZERO);
        root.addOrReplaceChild("harpoon_spool", CubeListBuilder.create().texOffs(24, 0).addBox(outer, 4.0F, -2.0F, 2, 4, 4), PartPose.ZERO);
        root.addOrReplaceChild("flame_body", CubeListBuilder.create().texOffs(0, 10).addBox(cx - 2.5F, 8.0F, -2.5F, 5, 5, 5), PartPose.ZERO);
        root.addOrReplaceChild("flame_nozzle", CubeListBuilder.create().texOffs(20, 10).addBox(cx - 1.0F, 13.0F, -1.0F, 2, 4, 2), PartPose.ZERO);
        root.addOrReplaceChild("flame_tank", CubeListBuilder.create().texOffs(28, 10).addBox(cx - 1.5F, 1.0F, 2.5F, 3, 7, 3), PartPose.ZERO);
        root.addOrReplaceChild("portal_body", CubeListBuilder.create().texOffs(0, 20).addBox(cx - 3.0F, 7.0F, -3.0F, 6, 5, 6), PartPose.ZERO);
        root.addOrReplaceChild("portal_prongs", CubeListBuilder.create()
                .texOffs(24, 20).addBox(cx - 2.5F, 12.0F, -2.5F, 1, 4, 1)
                .texOffs(24, 20).addBox(cx + 1.5F, 12.0F, -2.5F, 1, 4, 1)
                .texOffs(24, 20).addBox(cx - 0.5F, 12.0F, 1.5F, 1, 4, 1), PartPose.ZERO);
        root.addOrReplaceChild("portal_core", CubeListBuilder.create().texOffs(28, 20).addBox(cx - 1.0F, 12.5F, -1.0F, 2, 2, 2), PartPose.ZERO);
        root.addOrReplaceChild("exo_body", CubeListBuilder.create().texOffs(0, 31).addBox(cx - 3.0F, 6.0F, -3.0F, 6, 7, 6), PartPose.ZERO);
        root.addOrReplaceChild("exo_barrel", CubeListBuilder.create().texOffs(24, 31).addBox(cx - 1.5F, 13.0F, -1.5F, 3, 5, 3), PartPose.ZERO);
        root.addOrReplaceChild("exo_coils", CubeListBuilder.create()
                .texOffs(36, 20).addBox(cx - 3.5F, 7.5F, -3.5F, 7, 1, 7)
                .texOffs(36, 20).addBox(cx - 3.5F, 10.5F, -3.5F, 7, 1, 7), PartPose.ZERO);
        root.addOrReplaceChild("exo_core", CubeListBuilder.create().texOffs(36, 31).addBox(cx - 1.0F, 17.6F, -1.0F, 2, 1, 2), PartPose.ZERO);
        root.addOrReplaceChild("illusion_body", CubeListBuilder.create().texOffs(0, 44).addBox(cx - 2.5F, 8.0F, -2.5F, 5, 5, 5), PartPose.ZERO);
        PartDefinition crystal = root.addOrReplaceChild("illusion_crystal", CubeListBuilder.create(),
                PartPose.offsetAndRotation(cx, 13.0F, 0.0F, 0.0F, (float) Math.PI / 4.0F, 0.0F));
        crystal.addOrReplaceChild("gem", CubeListBuilder.create().texOffs(20, 44).addBox(-1.0F, 0.0F, -1.0F, 2, 4, 2), PartPose.ZERO);
        root.addOrReplaceChild("catapult_palm", CubeListBuilder.create().texOffs(28, 44).addBox(cx - 3.0F, 10.5F, -3.0F, 6, 2, 6), PartPose.ZERO);
        root.addOrReplaceChild("catapult_fingers", CubeListBuilder.create()
                .texOffs(52, 44).addBox(cx - 3.0F, 12.5F, -3.0F, 1, 4, 1)
                .texOffs(52, 44).addBox(cx + 2.0F, 12.5F, -3.0F, 1, 4, 1)
                .texOffs(52, 44).addBox(cx - 3.0F, 12.5F, 2.0F, 1, 4, 1)
                .texOffs(52, 44).addBox(cx + 2.0F, 12.5F, 2.0F, 1, 4, 1), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 64);
    }

    /** Dibuja el brazo. {@code active}: el brazo esta funcionando (motosierra girando, disparando, rayo...). */
    public void render(PoseStack pose, MultiBufferSource buffers, int light, RobotArmType type, boolean active, float time, ItemStack arm) {
        int ov = OverlayTexture.NO_OVERLAY;
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
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
            return;
        }
        if (type == RobotArmType.ROCKET) {
            rocketBody.render(pose, vc, light, ov);
            pose.pushPose();
            if (active) pose.translate(0.0D, -1.0D / 16.0D, 0.0D); // retroceso
            barrels.render(pose, vc, light, ov);
            pose.popPose();
            return;
        }
        // Brazos nuevos: otra textura (se pide el buffer despues de terminar con el anterior)
        VertexConsumer ex = buffers.getBuffer(RenderType.entityCutoutNoCull(EXTRA_TEXTURE));
        switch (type) {
            case HARPOON -> {
                harpoonSpool.render(pose, ex, light, ov);
                harpoonTube.render(pose, ex, light, ov);
                if (!active) harpoonTip.render(pose, ex, light, ov); // la punta va volando
            }
            case FLAMETHROWER -> {
                flameTank.render(pose, ex, light, ov);
                flameBody.render(pose, ex, light, ov);
                pose.pushPose();
                if (active) pose.translate(((int) (time * 4.0F) % 2 == 0 ? 0.2F : -0.2F) / 16.0F, 0.0D, 0.0D);
                flameNozzle.render(pose, ex, active ? FULL_BRIGHT : light, ov);
                pose.popPose();
            }
            case PORTAL -> {
                portalBody.render(pose, ex, light, ov);
                portalProngs.render(pose, ex, light, ov);
                int c = RobotPortalEntity.rgb(RobotHelper.nextPortalColor(arm));
                float pulse = 0.75F + 0.25F * (float) Math.sin(time * 0.3F);
                portalCore.render(pose, ex, FULL_BRIGHT, ov, ((c >> 16) & 255) / 255.0F * pulse, ((c >> 8) & 255) / 255.0F * pulse, (c & 255) / 255.0F * pulse, 1.0F);
            }
            case EXO -> {
                exoBody.render(pose, ex, light, ov);
                pose.pushPose();
                if (active) pose.translate(0.0D, ((int) (time * 5.0F) % 2 == 0 ? 0.25F : 0.0F) / 16.0F, 0.0D);
                exoCoils.render(pose, ex, active ? FULL_BRIGHT : light, ov);
                pose.popPose();
                exoBarrel.render(pose, ex, light, ov);
                exoCore.render(pose, ex, FULL_BRIGHT, ov);
            }
            case ILLUSION -> {
                illusionBody.render(pose, ex, light, ov);
                pose.pushPose();
                illusionCrystal.yRot = (float) Math.PI / 4.0F + time * (active ? 0.4F : 0.05F);
                illusionCrystal.render(pose, ex, FULL_BRIGHT, ov);
                pose.popPose();
            }
            case CATAPULT -> {
                catapultPalm.render(pose, ex, light, ov);
                catapultFingers.render(pose, ex, light, ov);
            }
            default -> {
            }
        }
    }
}
