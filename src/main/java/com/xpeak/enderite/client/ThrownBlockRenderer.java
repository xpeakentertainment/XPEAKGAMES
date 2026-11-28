package com.xpeak.enderite.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.xpeak.enderite.robot.ThrownBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.model.data.ModelData;

/** Bloque lanzado por la catapulta, dando vueltas. */
public class ThrownBlockRenderer extends EntityRenderer<ThrownBlockEntity> {
    public ThrownBlockRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0.4F;
    }

    @Override
    public void render(ThrownBlockEntity e, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        pose.pushPose();
        float t = e.tickCount + partial;
        pose.translate(0.0D, 0.35D, 0.0D);
        pose.mulPose(Axis.XP.rotationDegrees(t * 25.0F));
        pose.mulPose(Axis.YP.rotationDegrees(t * 13.0F));
        pose.scale(0.75F, 0.75F, 0.75F);
        pose.translate(-0.5D, -0.5D, -0.5D);
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(e.blockState(), pose, buffers, light, OverlayTexture.NO_OVERLAY,
                ModelData.EMPTY, null);
        pose.popPose();
        super.render(e, yaw, partial, pose, buffers, light);
    }

    @Override
    @SuppressWarnings("deprecation")
    public ResourceLocation getTextureLocation(ThrownBlockEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
