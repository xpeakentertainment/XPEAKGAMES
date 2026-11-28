package com.xpeak.enderite.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.xpeak.enderite.EnderiteMod;
import com.xpeak.enderite.robot.*;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.HashSet;
import java.util.Set;

/** Efectos de los brazos en el cliente: el tiron del arpon (lo mueve tu propio cliente) y el rayo azul del exobrazo. */
@Mod.EventBusSubscriber(modid = EnderiteMod.MODID, value = Dist.CLIENT)
public final class RobotEffects {
    private static final Set<Integer> ARRIVED = new HashSet<>();

    // ------------------------------------------------------------------ arpon

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) {
            ARRIVED.clear();
            return;
        }
        HarpoonEntity pulling = null;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof HarpoonEntity h && h.getOwner() == p && h.isPulling()) {
                pulling = h;
                break;
            }
        }
        if (pulling == null || ARRIVED.contains(pulling.getId())) return;
        Vec3 anchor = pulling.anchor();
        Vec3 center = p.position().add(0.0D, p.getBbHeight() * 0.5D, 0.0D);
        if (center.distanceTo(anchor) < 1.7D || p.isShiftKeyDown()) {
            // al llegar: pequeno salto para subirte al borde
            ARRIVED.add(pulling.getId());
            Vec3 dir = anchor.subtract(center).normalize();
            p.setDeltaMovement(dir.x * 0.3D, 0.48D, dir.z * 0.3D);
            return;
        }
        Vec3 v = HarpoonEntity.pullVelocity(p, anchor);
        p.setDeltaMovement(v.x, v.y + 0.08D, v.z); // +0.08: compensa la gravedad de este tick
        p.resetFallDistance();
    }

    // ------------------------------------------------------------------ rayo del exobrazo

    /** Punto del que sale el rayo (el brazo), suavizado entre ticks. */
    public static Vec3 armOrigin(Player player, HumanoidArm side, float partial) {
        Vec3 look = player.getViewVector(partial);
        Vec3 right = new Vec3(-look.z, 0, look.x).normalize();
        double sign = side == HumanoidArm.RIGHT ? 1.0D : -1.0D;
        return player.getEyePosition(partial).add(right.scale(0.38D * sign)).add(0, -0.35D, 0).add(look.scale(0.6D));
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float partial = event.getPartialTick();
        Camera cam = event.getCamera();
        Vec3 camPos = cam.getPosition();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        boolean drew = false;
        for (Player p : mc.level.players()) {
            ItemStack torso = RobotHelper.getTorso(p);
            if (torso.isEmpty()) continue;
            for (HumanoidArm side : HumanoidArm.values()) {
                ItemStack arm = RobotHelper.getArm(torso, side);
                if (RobotHelper.armType(arm) != RobotArmType.EXO || !RobotClient.isFiring(p, side)) continue;
                Vec3 eye = p.getEyePosition(partial);
                Vec3 end = ExoBeam.trace(mc.level, eye, p.getViewVector(partial), p);
                Vec3 start = armOrigin(p, side, partial);
                if (p == mc.player && mc.options.getCameraType().isFirstPerson()) {
                    // en primera persona sale de la parte de abajo de la pantalla, donde se ve el brazo
                    Vec3 look = p.getViewVector(partial);
                    Vec3 right = new Vec3(-look.z, 0, look.x).normalize();
                    start = eye.add(look.scale(0.55D)).add(right.scale(side == HumanoidArm.RIGHT ? 0.3D : -0.3D)).add(0.0D, -0.28D, 0.0D);
                }
                drawBeam(event.getPoseStack(), buffers, camPos, start, end, p.tickCount + partial);
                drew = true;
            }
        }
        if (drew) buffers.endBatch(RenderType.lightning());
    }

    private static void drawBeam(PoseStack pose, MultiBufferSource buffers, Vec3 camPos, Vec3 a, Vec3 b, float time) {
        VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
        Matrix4f m = pose.last().pose();
        float flicker = 0.85F + 0.15F * (float) Math.sin(time * 1.7F);
        ribbon(vc, m, camPos, a, b, 0.16F * flicker, 0.25F, 0.55F, 1.0F, 0.35F);
        ribbon(vc, m, camPos, a, b, 0.07F, 0.55F, 0.85F, 1.0F, 0.75F);
        ribbon(vc, m, camPos, a, b, 0.025F, 0.95F, 0.98F, 1.0F, 1.0F);
    }

    /** Cinta mirando a la camara entre a y b. */
    private static void ribbon(VertexConsumer vc, Matrix4f m, Vec3 cam, Vec3 a, Vec3 b, float width, float r, float g, float bl, float alpha) {
        Vec3 dir = b.subtract(a);
        Vec3 mid = a.add(b).scale(0.5D);
        Vec3 toCam = cam.subtract(mid);
        Vec3 side = dir.cross(toCam);
        if (side.lengthSqr() < 1.0E-8) side = dir.cross(new Vec3(0, 1, 0));
        if (side.lengthSqr() < 1.0E-8) side = new Vec3(1, 0, 0);
        side = side.normalize().scale(width);
        Vec3 a1 = a.add(side).subtract(cam), a2 = a.subtract(side).subtract(cam);
        Vec3 b1 = b.add(side).subtract(cam), b2 = b.subtract(side).subtract(cam);
        vc.vertex(m, (float) a1.x, (float) a1.y, (float) a1.z).color(r, g, bl, alpha).endVertex();
        vc.vertex(m, (float) b1.x, (float) b1.y, (float) b1.z).color(r, g, bl, alpha).endVertex();
        vc.vertex(m, (float) b2.x, (float) b2.y, (float) b2.z).color(r, g, bl, alpha).endVertex();
        vc.vertex(m, (float) a2.x, (float) a2.y, (float) a2.z).color(r, g, bl, alpha).endVertex();
        // y por detras (sin depender del sentido de la cara)
        vc.vertex(m, (float) a2.x, (float) a2.y, (float) a2.z).color(r, g, bl, alpha).endVertex();
        vc.vertex(m, (float) b2.x, (float) b2.y, (float) b2.z).color(r, g, bl, alpha).endVertex();
        vc.vertex(m, (float) b1.x, (float) b1.y, (float) b1.z).color(r, g, bl, alpha).endVertex();
        vc.vertex(m, (float) a1.x, (float) a1.y, (float) a1.z).color(r, g, bl, alpha).endVertex();
    }

    private RobotEffects() {}
}
