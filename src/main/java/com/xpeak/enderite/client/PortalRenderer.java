package com.xpeak.enderite.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.xpeak.enderite.EnderiteMod;
import com.xpeak.enderite.robot.RobotPortalEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/** Portal ovalado de 1x2 pegado a la cara del bloque, brillando con su color. */
public class PortalRenderer extends EntityRenderer<RobotPortalEntity> {
    private static final ResourceLocation[] RING = {
            new ResourceLocation(EnderiteMod.MODID, "textures/entity/portal_cyan.png"),
            new ResourceLocation(EnderiteMod.MODID, "textures/entity/portal_violet.png")};
    private static final ResourceLocation GLOW = new ResourceLocation(EnderiteMod.MODID, "textures/entity/portal_glow.png");

    public PortalRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public void render(RobotPortalEntity portal, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        Vec3 n = RobotPortalEntity.vec(portal.facing()), u = RobotPortalEntity.vec(portal.up()), w = n.cross(u);
        int color = RobotPortalEntity.rgb(portal.color());
        float r = ((color >> 16) & 255) / 255.0F, g = ((color >> 8) & 255) / 255.0F, b = (color & 255) / 255.0F;
        float time = portal.tickCount + partial;
        PoseStack.Pose last = pose.last();
        quad(buffers.getBuffer(RenderType.entityTranslucentEmissive(RING[portal.color() & 1])), last.pose(), last.normal(), n, u, w,
                0.5F, 1.0F, 0.002F, 1.0F, 1.0F, 1.0F, 1.0F);
        float pulse = 0.45F + 0.25F * (float) Math.sin(time * 0.18F);
        quad(buffers.getBuffer(RenderType.entityTranslucentEmissive(GLOW)), last.pose(), last.normal(), n, u, w,
                0.42F, 0.9F, 0.004F, r, g, b, pulse);
        super.render(portal, yaw, partial, pose, buffers, light);
    }

    private static void quad(VertexConsumer vc, Matrix4f m, Matrix3f nm, Vec3 n, Vec3 u, Vec3 w, float hw, float hu, float off,
                             float r, float g, float b, float a) {
        Vec3 o = n.scale(off);
        Vec3 rb = o.subtract(w.scale(hw)).subtract(u.scale(hu));
        Vec3 rt = o.subtract(w.scale(hw)).add(u.scale(hu));
        Vec3 lt = o.add(w.scale(hw)).add(u.scale(hu));
        Vec3 lb = o.add(w.scale(hw)).subtract(u.scale(hu));
        v(vc, m, nm, rb, 1.0F, 1.0F, n, r, g, b, a);
        v(vc, m, nm, rt, 1.0F, 0.0F, n, r, g, b, a);
        v(vc, m, nm, lt, 0.0F, 0.0F, n, r, g, b, a);
        v(vc, m, nm, lb, 0.0F, 1.0F, n, r, g, b, a);
    }

    private static void v(VertexConsumer vc, Matrix4f m, Matrix3f nm, Vec3 p, float tu, float tv, Vec3 n, float r, float g, float b, float a) {
        vc.vertex(m, (float) p.x, (float) p.y, (float) p.z).color(r, g, b, a).uv(tu, tv).overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(0xF000F0).normal(nm, (float) n.x, (float) n.y, (float) n.z).endVertex();
    }

    @Override
    public ResourceLocation getTextureLocation(RobotPortalEntity entity) {
        return RING[entity.color() & 1];
    }
}
