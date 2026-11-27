package com.xpeak.enderite.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.xpeak.enderite.entity.StuckToolEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Dibuja la herramienta clavada: la hoja/punta metida en la pared y el mango hacia fuera. */
public class StuckToolRenderer extends EntityRenderer<StuckToolEntity> {
    public static float TIP_ANGLE = 55.0F;
    private final ItemRenderer itemRenderer;

    public StuckToolRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.itemRenderer = ctx.getItemRenderer();
        this.shadowRadius = 0.0F;
    }

    @Override
    public void render(StuckToolEntity entity, float yaw, float partialTicks, PoseStack pose, MultiBufferSource buffers, int light) {
        ItemStack stack = entity.getItem();
        if (stack.isEmpty()) return;
        Direction face = entity.getFace();
        pose.pushPose();
        pose.translate(0.0D, 0.25D, 0.0D);
        // Girar para que "hacia fuera de la pared" sea +X local
        pose.mulPose(Axis.YP.rotationDegrees(-face.toYRot() - 90.0F));
        // En modo FIXED el objeto queda en el plano XY con la punta hacia (-X,+Y).
        // Girando 55 grados la punta entra en la pared (-X) un poco hacia abajo, y el mango queda fuera.
        pose.mulPose(Axis.ZP.rotationDegrees(TIP_ANGLE));
        pose.scale(0.9F, 0.9F, 0.9F);
        itemRenderer.renderStatic(stack, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY, pose, buffers, entity.level(), entity.getId());
        pose.popPose();
        super.render(entity, yaw, partialTicks, pose, buffers, light);
    }

    @Override
    @SuppressWarnings("deprecation")
    public ResourceLocation getTextureLocation(StuckToolEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
