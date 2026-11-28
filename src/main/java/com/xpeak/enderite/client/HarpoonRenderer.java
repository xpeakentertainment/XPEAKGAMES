package com.xpeak.enderite.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.xpeak.enderite.EnderiteMod;
import com.xpeak.enderite.robot.HarpoonEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Punta del arpon + la cuerda que la une a tu brazo. */
public class HarpoonRenderer extends EntityRenderer<HarpoonEntity> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(new ResourceLocation(EnderiteMod.MODID, "harpoon"), "main");
    private static final ResourceLocation TEXTURE = new ResourceLocation(EnderiteMod.MODID, "textures/entity/harpoon.png");
    private final ModelPart model;

    public HarpoonRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.model = ctx.bakeLayer(LAYER);
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild("shaft", CubeListBuilder.create().texOffs(0, 0).addBox(-0.5F, -0.5F, -8.0F, 1, 1, 10), PartPose.ZERO);
        mesh.getRoot().addOrReplaceChild("tip", CubeListBuilder.create().texOffs(0, 12).addBox(-1.5F, -1.5F, 2.0F, 3, 3, 3)
                .texOffs(12, 12).addBox(-2.5F, -0.5F, 1.0F, 5, 1, 1), PartPose.ZERO);
        return LayerDefinition.create(mesh, 32, 32);
    }

    @Override
    public void render(HarpoonEntity h, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(Mth.lerp(partial, h.yRotO, h.getYRot())));
        pose.mulPose(Axis.XP.rotationDegrees(-Mth.lerp(partial, h.xRotO, h.getXRot())));
        model.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)), light, OverlayTexture.NO_OVERLAY);
        pose.popPose();

        Entity owner = h.getOwner();
        if (owner instanceof Player p) {
            HumanoidArm side = h.isLeft() ? HumanoidArm.LEFT : HumanoidArm.RIGHT;
            Vec3 from = RobotEffects.armOrigin(p, side, partial);
            Minecraft mc = Minecraft.getInstance();
            if (p == mc.player && mc.options.getCameraType().isFirstPerson()) {
                Vec3 look = p.getViewVector(partial);
                Vec3 right = new Vec3(-look.z, 0, look.x).normalize();
                from = p.getEyePosition(partial).add(look.scale(0.55D)).add(right.scale(h.isLeft() ? -0.3D : 0.3D)).add(0.0D, -0.28D, 0.0D);
            }
            Vec3 here = new Vec3(Mth.lerp(partial, h.xo, h.getX()), Mth.lerp(partial, h.yo, h.getY()), Mth.lerp(partial, h.zo, h.getZ()));
            Vec3 d = from.subtract(here);
            VertexConsumer vc = buffers.getBuffer(RenderType.lineStrip());
            PoseStack.Pose last = pose.last();
            float sag = h.isPulling() ? 0.0F : (float) Math.min(1.2D, d.length() * 0.04D);
            int n = 16;
            for (int i = 0; i <= n; i++) {
                float t = i / (float) n;
                float x = (float) (d.x * t), y = (float) (d.y * t - Math.sin(Math.PI * t) * sag), z = (float) (d.z * t);
                float t2 = (i + 1) / (float) n;
                float nx = (float) (d.x * t2) - x, ny = (float) (d.y * t2 - Math.sin(Math.PI * t2) * sag) - y, nz = (float) (d.z * t2) - z;
                float len = Mth.sqrt(nx * nx + ny * ny + nz * nz);
                if (len < 1.0E-5F) len = 1.0F;
                vc.vertex(last.pose(), x, y, z).color(0.18F, 0.16F, 0.14F, 1.0F).normal(last.normal(), nx / len, ny / len, nz / len).endVertex();
            }
            // cada cuerda es su propia linea (si no, dos arpones se unirian entre si)
            if (buffers instanceof MultiBufferSource.BufferSource bs) bs.endBatch(RenderType.lineStrip());
        }
        super.render(h, yaw, partial, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(HarpoonEntity entity) {
        return TEXTURE;
    }
}
