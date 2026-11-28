package com.xpeak.enderite.robot;

import com.xpeak.enderite.EnderiteMod;
import com.xpeak.enderite.network.ModNetwork;
import com.xpeak.enderite.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingSwapItemsEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Logica de la armadura robot en el servidor (y reglas comunes):
 * - con brazos puestos, click izquierdo = brazo izquierdo, click derecho = brazo derecho (la hotbar no se usa);
 * - con el torso puesto, la mano secundaria no se puede usar;
 * - el agua la va estropeando.
 */
@Mod.EventBusSubscriber(modid = EnderiteMod.MODID)
public final class RobotServer {
    public static final class ArmState {
        public boolean leftDown, rightDown;
        boolean leftPrev, rightPrev;
        int leftCooldown, rightCooldown;
        boolean leftActive, rightActive;
        boolean warnedBroken;
    }

    private static final Map<UUID, ArmState> STATES = new HashMap<>();

    public static ArmState state(Player p) {
        return STATES.computeIfAbsent(p.getUUID(), k -> new ArmState());
    }

    public static void setInput(ServerPlayer player, boolean left, boolean right) {
        ArmState s = state(player);
        s.leftDown = left;
        s.rightDown = right;
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        tick(player);
    }

    /** Un tick de la armadura robot de un jugador (publico para las pruebas). */
    public static void tick(ServerPlayer player) {
        ItemStack torso = RobotHelper.getTorso(player);
        ArmState s = state(player);
        if (torso.isEmpty()) {
            setActive(player, s, false, false);
            return;
        }
        ServerLevel level = player.serverLevel();

        // El agua estropea la armadura robot
        if (player.isInWaterOrBubble() && player.tickCount % 20 == 0) {
            int t = Math.max(4, torso.getMaxDamage() / 50);
            RobotHelper.wear(torso, t);
            for (HumanoidArm side : HumanoidArm.values()) {
                ItemStack arm = RobotHelper.getArm(torso, side);
                if (!arm.isEmpty()) {
                    RobotHelper.wear(arm, Math.max(3, arm.getMaxDamage() / 50));
                    RobotHelper.setArm(torso, side, arm);
                }
            }
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getY() + 1.2D, player.getZ(), 8, 0.4D, 0.4D, 0.4D, 0.15D);
            level.playSound(null, player.blockPosition(), ModSounds.ROBOT_SHORT_CIRCUIT.get(), SoundSource.PLAYERS, 0.7F, 1.0F);
        }
        boolean broken = RobotHelper.isBroken(torso);
        if (broken && !s.warnedBroken) {
            player.displayClientMessage(Component.translatable("item.enderite.robot.broken").withStyle(ChatFormatting.RED), true);
            s.warnedBroken = true;
        } else if (!broken) {
            s.warnedBroken = false;
        }

        boolean leftActive = false, rightActive = false;
        if (!broken && !player.isSpectator()) {
            leftActive = tickArm(player, torso, HumanoidArm.LEFT, s.leftDown, s.leftPrev, s, true);
            rightActive = tickArm(player, torso, HumanoidArm.RIGHT, s.rightDown, s.rightPrev, s, false);
        }
        s.leftPrev = s.leftDown;
        s.rightPrev = s.rightDown;
        if (s.leftCooldown > 0) s.leftCooldown--;
        if (s.rightCooldown > 0) s.rightCooldown--;
        setActive(player, s, leftActive, rightActive);
    }

    private static boolean tickArm(ServerPlayer player, ItemStack torso, HumanoidArm side, boolean down, boolean prev, ArmState s, boolean left) {
        ItemStack arm = RobotHelper.getArm(torso, side);
        RobotArmType type = RobotHelper.armType(arm);
        if (type == null || !down) return false;
        if (RobotHelper.isBroken(arm)) {
            if (!prev) player.displayClientMessage(Component.translatable("item.enderite.robot.arm_broken").withStyle(ChatFormatting.RED), true);
            return false;
        }
        float mult = (torso.getItem() instanceof RobotTorsoItem t && t.core == RobotCore.RED) ? 1.5F : 1.0F;
        ServerLevel level = player.serverLevel();
        switch (type) {
            case CHAINSAW -> {
                if (player.tickCount % 4 == 0) {
                    if (chainsawHit(player, side, 4.0F * mult)) {
                        RobotHelper.wear(arm, 1);
                        RobotHelper.setArm(torso, side, arm);
                    }
                }
                if (player.tickCount % 6 == 0) {
                    level.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.ROBOT_CHAINSAW.get(), SoundSource.PLAYERS, 0.8F, 1.0F);
                }
                return true;
            }
            case ROCKET -> {
                int cd = left ? s.leftCooldown : s.rightCooldown;
                if (prev || cd > 0) return false; // un disparo por click
                int ammo = RobotHelper.getAmmo(arm);
                if (ammo < 3 && !player.getAbilities().instabuild) {
                    level.playSound(null, player.blockPosition(), ModSounds.ROBOT_NO_AMMO.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
                    player.displayClientMessage(Component.translatable("item.enderite.robot.no_ammo").withStyle(ChatFormatting.GOLD), true);
                    return false;
                }
                fireRockets(player, side, mult);
                if (!player.getAbilities().instabuild) RobotHelper.setAmmo(arm, ammo - 3);
                RobotHelper.wear(arm, 2);
                RobotHelper.setArm(torso, side, arm);
                if (left) s.leftCooldown = 24;
                else s.rightCooldown = 24;
                return true;
            }
        }
        return false;
    }

    /** Punto de salida del brazo (a la altura del hombro, desplazado hacia su lado). */
    public static Vec3 armOrigin(Player player, HumanoidArm side) {
        Vec3 look = player.getLookAngle();
        Vec3 right = new Vec3(-look.z, 0, look.x).normalize();
        double sign = side == HumanoidArm.RIGHT ? 1.0D : -1.0D;
        return player.getEyePosition().add(right.scale(0.38D * sign)).add(0, -0.35D, 0).add(look.scale(0.6D));
    }

    public static void fireRockets(ServerPlayer player, HumanoidArm side, float mult) {
        Vec3 look = player.getLookAngle();
        Vec3 right = new Vec3(-look.z, 0, look.x).normalize();
        Vec3 up = right.cross(look).normalize();
        Vec3 origin = armOrigin(player, side);
        double[][] spread = {{0.0D, 0.0D}, {-0.06D, -0.03D}, {0.06D, -0.03D}};
        for (double[] sp : spread) {
            Vec3 dir = look.add(right.scale(sp[0])).add(up.scale(sp[1]));
            RobotRocketEntity.shoot(player.level(), player, origin.add(right.scale(sp[0] * 2.0D)), dir, mult);
        }
        player.level().playSound(null, origin.x, origin.y, origin.z, ModSounds.ROBOT_ROCKET_LAUNCH.get(), SoundSource.PLAYERS, 1.2F, 1.0F);
    }

    private static boolean chainsawHit(ServerPlayer player, HumanoidArm side, float damage) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        AABB box = player.getBoundingBox().expandTowards(look.scale(2.8D)).inflate(0.8D);
        boolean hit = false;
        for (Entity e : player.level().getEntities(player, box, e -> e instanceof LivingEntity && e.isAlive() && e.isPickable())) {
            Vec3 to = e.position().add(0, e.getBbHeight() * 0.5D, 0).subtract(eye);
            double dist = to.length();
            if (dist > 3.4D || to.normalize().dot(look) < 0.45D) continue;
            int invul = e.invulnerableTime;
            e.invulnerableTime = 0;
            if (e.hurt(player.damageSources().playerAttack(player), damage)) {
                hit = true;
                ((ServerLevel) player.level()).sendParticles(ParticleTypes.DAMAGE_INDICATOR, e.getX(), e.getY() + e.getBbHeight() * 0.6D, e.getZ(),
                        2, 0.2D, 0.2D, 0.2D, 0.1D);
                ((ServerLevel) player.level()).sendParticles(ParticleTypes.CRIT, e.getX(), e.getY() + e.getBbHeight() * 0.5D, e.getZ(),
                        4, 0.3D, 0.3D, 0.3D, 0.2D);
            } else {
                e.invulnerableTime = invul;
            }
        }
        Vec3 tip = armOrigin(player, side).add(look.scale(1.2D));
        ((ServerLevel) player.level()).sendParticles(ParticleTypes.SMOKE, tip.x, tip.y, tip.z, 1, 0.05D, 0.05D, 0.05D, 0.01D);
        return hit;
    }

    private static void setActive(ServerPlayer player, ArmState s, boolean left, boolean right) {
        if (s.leftActive == left && s.rightActive == right) return;
        s.leftActive = left;
        s.rightActive = right;
        ModNetwork.sendArmAnim(player, left, right);
    }

    // ------------------------------------------------------------------ Reglas (cliente y servidor)

    /** Con brazos robot no se usa la hotbar (los clicks son de los brazos), y la mano secundaria esta bloqueada. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (blocked(event.getEntity(), event.getHand())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (blocked(event.getEntity(), event.getHand())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (blocked(event.getEntity(), event.getHand())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (RobotHelper.hasArms(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onAttack(AttackEntityEvent event) {
        if (RobotHelper.hasArms(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        if (RobotHelper.hasArms(event.getEntity())) event.setNewSpeed(0.0F);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onSwap(LivingSwapItemsEvent.Hands event) {
        if (RobotHelper.wearsTorso(event.getEntity())) event.setCanceled(true);
    }

    private static boolean blocked(Player player, InteractionHand hand) {
        if (!RobotHelper.wearsTorso(player)) return false;
        if (hand == InteractionHand.OFF_HAND) return true;
        return RobotHelper.hasArms(player);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        STATES.remove(event.getEntity().getUUID());
    }

    private RobotServer() {}
}
