package com.xpeak.enderite.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.xpeak.enderite.EnderiteMod;
import com.xpeak.enderite.limbo.Limbo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.client.gui.overlay.ForgeGui;

/** HUD del Limbo: pantalla negra mientras "despiertas", vinyeta roja que late y la cuenta atras hasta el amanecer. */
public final class LimboOverlay {
    private static final ResourceLocation VIGNETTE = new ResourceLocation(EnderiteMod.MODID, "textures/misc/limbo_vignette.png");

    public static void render(ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) {
        // Cabeza dentro de arenas movedizas: todo se ve arena
        Minecraft m = Minecraft.getInstance();
        if (m.player != null && m.level != null && m.options.getCameraType().isFirstPerson()) {
            net.minecraft.core.BlockPos eyes = net.minecraft.core.BlockPos.containing(m.player.getEyePosition(partialTick));
            if (m.level.getBlockState(eyes).is(com.xpeak.enderite.registry.ModBlocks.QUICKSAND.get())) {
                g.fill(0, 0, width, height, 0xF0A89A78);
            }
        }
        int phase = ClientLimboState.phase();
        if (phase == Limbo.PHASE_NONE) return;
        Minecraft mc = Minecraft.getInstance();

        if (phase == Limbo.PHASE_LOADING) {
            g.fill(0, 0, width, height, 0xFF000000);
            long t = mc.level != null ? mc.level.getGameTime() : 0;
            int dots = (int) ((t / 10) % 4);
            g.drawCenteredString(mc.font, Component.translatable("enderite.limbo.loading").append(".".repeat(dots)),
                    width / 2, height / 2 - 4, 0x8A1010);
            return;
        }

        float time = (mc.level != null ? mc.level.getGameTime() : 0) + partialTick;
        double near = LimboClientEvents.nearestCrawler;
        float danger = near < 24.0D ? (float) (1.0D - near / 24.0D) : 0.0F;
        float pulse = 0.55F + 0.25F * Mth.sin(time * (0.08F + danger * 0.25F)) + danger * 0.3F;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, Mth.clamp(pulse, 0.0F, 1.0F));
        g.blit(VIGNETTE, 0, 0, width, height, 0.0F, 0.0F, 256, 256, 256, 256);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        // Fundido desde negro al llegar
        long since = ClientLimboState.ticksSincePhaseChange();
        if (since < 60) {
            int a = (int) (255 * (1.0F - since / 60.0F));
            g.fill(0, 0, width, height, (Mth.clamp(a, 0, 255) << 24));
        }

        // Cuenta atras hasta el amanecer
        int ticks = ClientLimboState.ticksToDawn();
        int secs = ticks / 20;
        String clock = String.format("%d:%02d", secs / 60, secs % 60);
        Component text = Component.translatable("enderite.limbo.dawn_in", clock);
        g.drawCenteredString(mc.font, text, width / 2, 6, 0xB01818);
        RenderSystem.disableBlend();
    }

    private LimboOverlay() {}
}
