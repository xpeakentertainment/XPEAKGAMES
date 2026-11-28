package com.xpeak.enderite.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.xpeak.enderite.robot.IllusionBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraftforge.client.model.data.ModelData;

/** Dibuja el bloque que imita la ilusion (por fuera es identico; por dentro no tiene colision). */
public class IllusionBlockRenderer implements BlockEntityRenderer<IllusionBlockEntity> {
    public IllusionBlockRenderer(BlockEntityRendererProvider.Context ctx) {
    }

    @Override
    public void render(IllusionBlockEntity be, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(be.getMimic(), pose, buffers, light, overlay, ModelData.EMPTY, null);
    }

    @Override
    public int getViewDistance() {
        return 96;
    }
}
