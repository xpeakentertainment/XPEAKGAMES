package com.xpeak.enderite.client;

import com.xpeak.enderite.EnderiteMod;
import com.xpeak.enderite.entity.CrawlerEntity;
import com.xpeak.enderite.entity.StuckToolEntity;
import com.xpeak.enderite.network.ModNetwork;
import com.xpeak.enderite.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Efectos del Limbo en el cliente: distorsion, niebla, sonido, latidos, no poder correr, y el salto desde la pared. */
@Mod.EventBusSubscriber(modid = EnderiteMod.MODID, value = Dist.CLIENT)
public final class LimboClientEvents {
    public static final ResourceLocation SHADER = new ResourceLocation(EnderiteMod.MODID, "shaders/post/limbo.json");

    public static double nearestCrawler = 999.0D;
    private static boolean shaderActive;
    private static int shaderRetry;
    private static LimboAmbientSound ambient;
    private static int heartbeatCooldown;
    private static boolean jumpWasDown;
    private static int clingTicks;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        boolean inLimbo = player != null && ClientLimboState.inLimbo();

        if (event.phase == TickEvent.Phase.START) {
            if (inLimbo) {
                player.setSprinting(false);
                if (mc.options.keySprint.isDown()) mc.options.keySprint.setDown(false);
                if (player.isFallFlying()) player.stopFallFlying();
            }
            return;
        }

        if (player == null || mc.level == null) {
            disableShader(mc);
            ClientLimboState.reset();
            return;
        }

        // Distorsion de pantalla
        if (inLimbo) {
            PostChain current = mc.gameRenderer.currentEffect();
            if ((current == null || !SHADER.toString().equals(current.getName())) && --shaderRetry <= 0) {
                mc.gameRenderer.loadEffect(SHADER);
                shaderRetry = 100;
            }
            shaderActive = true;
        } else {
            disableShader(mc);
        }

        // Sonido ambiente (y nada de musica)
        if (inLimbo) {
            mc.getMusicManager().stopPlaying();
            if (ambient == null || ambient.isDone()) {
                ambient = new LimboAmbientSound();
                mc.getSoundManager().play(ambient);
            }
        }

        // Latidos cuando el Crawler esta cerca
        nearestCrawler = 999.0D;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof CrawlerEntity) {
                nearestCrawler = Math.min(nearestCrawler, e.distanceTo(player));
            }
        }
        if (inLimbo && nearestCrawler < 22.0D) {
            if (--heartbeatCooldown <= 0) {
                float vol = (float) Mth.clamp(1.2D - nearestCrawler / 22.0D, 0.25D, 1.0D);
                mc.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.LIMBO_HEARTBEAT.get(), 1.0F, vol));
                heartbeatCooldown = (int) (7 + nearestCrawler * 0.9D);
            }
        }

        // Salto extra desde la pared (pulsa saltar mientras cuelgas)
        boolean jumpDown = mc.screen == null && (mc.options.keyJump.isDown() || player.input.jumping);
        if (player.getVehicle() instanceof StuckToolEntity) {
            clingTicks++;
            if (jumpDown && !jumpWasDown && clingTicks > 3) ModNetwork.sendClingJump();
        } else {
            clingTicks = 0;
        }
        jumpWasDown = jumpDown;
    }

    /** El servidor confirma el salto desde la pared: soltarse y saltar ya en el cliente. */
    public static void applyClingJump(double x, double y, double z) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) return;
        if (p.getVehicle() instanceof StuckToolEntity) p.stopRiding();
        p.setDeltaMovement(x, y, z);
        p.resetFallDistance();
    }

    private static void disableShader(Minecraft mc) {
        if (!shaderActive) return;
        PostChain current = mc.gameRenderer.currentEffect();
        if (current != null && SHADER.toString().equals(current.getName())) mc.gameRenderer.shutdownEffect();
        shaderActive = false;
        shaderRetry = 0;
    }

    /** En el Limbo no se puede correr: el impulso nunca llega al minimo necesario para esprintar. */
    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (!ClientLimboState.inLimbo()) return;
        Input input = event.getInput();
        input.forwardImpulse = Mth.clamp(input.forwardImpulse, -0.79F, 0.79F);
        input.leftImpulse = Mth.clamp(input.leftImpulse, -0.79F, 0.79F);
    }

    @SubscribeEvent
    public static void onFog(ViewportEvent.RenderFog event) {
        if (!ClientLimboState.inLimbo()) return;
        if (event.getMode() == FogRenderer.FogMode.FOG_TERRAIN) {
            event.setNearPlaneDistance(1.5F);
            event.setFarPlaneDistance(Math.min(event.getFarPlaneDistance(), 46.0F));
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        if (!ClientLimboState.inLimbo()) return;
        Minecraft mc = Minecraft.getInstance();
        float t = mc.level != null ? mc.level.getGameTime() + (float) event.getPartialTick() : 0.0F;
        float pulse = 0.03F * Mth.sin(t * 0.05F);
        event.setRed(0.13F + pulse);
        event.setGreen(0.018F);
        event.setBlue(0.025F);
    }

    @SubscribeEvent
    public static void onCamera(ViewportEvent.ComputeCameraAngles event) {
        if (!ClientLimboState.inLimbo()) return;
        Minecraft mc = Minecraft.getInstance();
        float t = mc.level != null ? mc.level.getGameTime() + (float) event.getPartialTick() : 0.0F;
        float roll = Mth.sin(t * 0.045F) * 2.2F + Mth.sin(t * 0.13F) * 0.6F;
        if (nearestCrawler < 12.0D) roll += Mth.sin(t * 0.9F) * (float) (1.5D * (1.0D - nearestCrawler / 12.0D));
        event.setRoll(event.getRoll() + roll);
    }

    @SubscribeEvent
    public static void onFov(ComputeFovModifierEvent event) {
        if (!ClientLimboState.inLimbo()) return;
        Minecraft mc = Minecraft.getInstance();
        float t = mc.level != null ? mc.level.getGameTime() : 0.0F;
        event.setNewFovModifier(event.getNewFovModifier() * (1.0F + 0.03F * Mth.sin(t * 0.07F)));
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientLimboState.reset();
        disableShader(Minecraft.getInstance());
    }

    private LimboClientEvents() {}
}
