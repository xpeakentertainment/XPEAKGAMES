package com.xpeak.enderite.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.xpeak.enderite.EnderiteMod;
import com.xpeak.enderite.entity.CrawlerEntity;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public class CrawlerRenderer extends MobRenderer<CrawlerEntity, CrawlerModel> {
    private static final ResourceLocation TEXTURE = new ResourceLocation(EnderiteMod.MODID, "textures/entity/crawler.png");
    private static final ResourceLocation GLOW = new ResourceLocation(EnderiteMod.MODID, "textures/entity/crawler_glow.png");
    private static final RenderType GLOW_TYPE = RenderType.eyes(GLOW);

    public CrawlerRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new CrawlerModel(ctx.bakeLayer(CrawlerModel.LAYER)), 0.6F);
        // La boca y la sangre brillan en la oscuridad
        this.addLayer(new EyesLayer<>(this) {
            @Override
            public RenderType renderType() {
                return GLOW_TYPE;
            }
        });
    }

    @Override
    public ResourceLocation getTextureLocation(CrawlerEntity entity) {
        return TEXTURE;
    }

    @Override
    protected void setupRotations(CrawlerEntity entity, PoseStack pose, float ageInTicks, float rotationYaw, float partialTicks) {
        super.setupRotations(entity, pose, ageInTicks, rotationYaw, partialTicks);
        // Temblor inquietante cuando no lo miras
        if (!entity.isWatched() && entity.isAggressive()) {
            float shake = Mth.sin(ageInTicks * 2.7F) * 0.6F;
            pose.translate(shake * 0.02F, 0.0F, 0.0F);
        }
    }
}
