package com.xpeak.enderite.cling;

import com.xpeak.enderite.EnderiteConfig;
import com.xpeak.enderite.EnderiteMod;
import com.xpeak.enderite.entity.StuckToolEntity;
import com.xpeak.enderite.limbo.Limbo;
import com.xpeak.enderite.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;

/**
 * Saltas y, en el aire, haces click derecho con una espada, hacha, pico, pala, azada o tridente (la "lanza")
 * pegado a una pared: la herramienta se clava, te quedas colgando y tienes un salto extra.
 * Click derecho a la herramienta clavada para recuperarla. Gasta bastante durabilidad.
 */
@Mod.EventBusSubscriber(modid = EnderiteMod.MODID)
public final class WallCling {
    /** Distancia maxima entre tu cuerpo y la pared. */
    private static final double MAX_GAP = 0.65D;

    public static boolean isClingTool(ItemStack stack) {
        Item item = stack.getItem();
        return !stack.isEmpty() && (item instanceof SwordItem || item instanceof DiggerItem || item instanceof TridentItem);
    }

    public static boolean canTryCling(Player player, ItemStack stack) {
        return isClingTool(stack)
                && !player.onGround()
                && !player.isPassenger()
                && !player.isInWater() && !player.isInLava()
                && !player.isFallFlying()
                && !player.getAbilities().flying
                && !player.isSpectator()
                && !player.onClimbable()
                && !Limbo.isLimbo(player.level());
    }

    /** Resultado de buscar donde clavar la herramienta. */
    public record Spot(BlockPos wall, Direction face, Vec3 toolCenter) {}

    /**
     * Busca la pared. {@code face} es la cara de la pared que mira hacia el jugador (o null para elegir la
     * pared mas cercana en la direccion en la que miras).
     */
    @Nullable
    public static Spot findSpot(Player player, @Nullable Direction face, @Nullable BlockPos clicked) {
        if (face != null) {
            if (face.getAxis().isVertical()) return null;
            return spotOnFace(player, face, clicked);
        }
        Vec3 look = player.getLookAngle();
        Spot best = null;
        double bestScore = -10.0D;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            // la pared esta en el lado -d del jugador... es decir, la cara "d" mira hacia el jugador
            Spot s = spotOnFace(player, d, null);
            if (s == null) continue;
            double score = -(look.x * d.getStepX() + look.z * d.getStepZ()); // mirar hacia la pared puntua mas
            if (score > bestScore) {
                bestScore = score;
                best = s;
            }
        }
        return bestScore > -0.3D ? best : null;
    }

    @Nullable
    private static Spot spotOnFace(Player player, Direction face, @Nullable BlockPos clicked) {
        Level level = player.level();
        AABB box = player.getBoundingBox();
        boolean xAxis = face.getAxis() == Direction.Axis.X;
        int sign = face.getAxisDirection().getStep();
        // Borde del jugador que da a la pared (la pared esta en el lado -face)
        double edge = xAxis ? (sign > 0 ? box.minX : box.maxX) : (sign > 0 ? box.minZ : box.maxZ);
        int[] candidates;
        if (clicked != null) {
            candidates = new int[]{xAxis ? clicked.getX() : clicked.getZ()};
        } else if (sign > 0) {
            int c0 = (int) Math.floor(edge - 0.001D);
            candidates = new int[]{c0, c0 - 1};
        } else {
            int c0 = (int) Math.floor(edge + 0.001D);
            candidates = new int[]{c0, c0 + 1};
        }
        int other = (int) Math.floor(xAxis ? player.getZ() : player.getX());
        double[] heights = {player.getY() + StuckToolEntity.HANG_OFFSET, player.getY() + 1.55D, player.getY() + 1.15D};

        for (int c : candidates) {
            double plane = sign > 0 ? c + 1.0D : c;
            double gap = sign > 0 ? edge - plane : plane - edge;
            if (gap < -0.05D || gap > MAX_GAP) continue;
            for (double toolY : heights) {
                BlockPos wall = xAxis ? new BlockPos(c, (int) Math.floor(toolY), other) : new BlockPos(other, (int) Math.floor(toolY), c);
                BlockState state = level.getBlockState(wall);
                if (!state.isFaceSturdy(level, wall, face)) continue;
                // si la herramienta queda mas abajo, el jugador baja un poco: comprobar que cabe
                double drop = (player.getY() + StuckToolEntity.HANG_OFFSET) - toolY;
                if (drop > 0.01D && !level.noCollision(player, box.move(0, -drop, 0))) continue;
                double cx = xAxis ? plane + sign * 0.12D : player.getX();
                double cz = xAxis ? player.getZ() : plane + sign * 0.12D;
                return new Spot(wall, face, new Vec3(cx, toolY, cz));
            }
        }
        return null;
    }

    /** Hace el agarre en el servidor. */
    public static boolean doCling(ServerPlayer player, InteractionHand hand, Spot spot) {
        ItemStack stack = player.getItemInHand(hand);
        if (!canTryCling(player, stack)) return false;
        int max = Math.max(1, stack.getMaxDamage());
        int cost = stack.isDamageableItem() ? Math.max(12, max * EnderiteConfig.CLING_DURABILITY_PERCENT.get() / 100) : 0;
        ItemStack tool = stack.copy();
        if (cost > 0) {
            tool.hurtAndBreak(cost, player, p -> p.broadcastBreakEvent(hand));
            if (tool.isEmpty()) {
                player.setItemInHand(hand, ItemStack.EMPTY);
                return true; // se ha roto al clavarla
            }
        }
        player.setItemInHand(hand, ItemStack.EMPTY);
        StuckToolEntity entity = StuckToolEntity.create(player.level(), tool, spot.face(), spot.wall(), spot.toolCenter(), player.getUUID());
        player.level().addFreshEntity(entity);
        player.resetFallDistance();
        player.setDeltaMovement(Vec3.ZERO);
        if (!player.startRiding(entity, true)) {
            // no deberia pasar, pero por si acaso devolvemos la herramienta
            entity.discard();
            player.setItemInHand(hand, tool);
            return false;
        }
        player.level().playSound(null, spot.toolCenter().x, spot.toolCenter().y, spot.toolCenter().z,
                ModSounds.TOOL_STICK.get(), SoundSource.PLAYERS, 1.0F, 0.9F + player.getRandom().nextFloat() * 0.2F);
        return true;
    }

    /** Salto extra desde la pared (lo manda el cliente al pulsar saltar mientras cuelgas). */
    public static void wallJump(ServerPlayer player) {
        if (!(player.getVehicle() instanceof StuckToolEntity tool)) return;
        Direction face = tool.getFace();
        player.stopRiding();
        Vec3 look = player.getLookAngle();
        Vec3 horizontal = new Vec3(look.x, 0, look.z);
        if (horizontal.lengthSqr() > 1.0E-4) horizontal = horizontal.normalize().scale(0.30D);
        // nunca hacia dentro de la pared
        double into = horizontal.x * face.getStepX() + horizontal.z * face.getStepZ();
        if (into < 0) horizontal = horizontal.subtract(face.getStepX() * into, 0, face.getStepZ() * into);
        Vec3 vel = horizontal.add(face.getStepX() * 0.18D, 0.62D, face.getStepZ() * 0.18D);
        player.setDeltaMovement(vel);
        player.hurtMarked = true;
        com.xpeak.enderite.network.ModNetwork.sendClingJumped(player, vel);
        if (com.xpeak.enderite.debug.SelfTest.ENABLED || Boolean.getBoolean("enderite.clienttest")) {
            EnderiteMod.LOGGER.info("[DEBUG] wallJump servidor pos={} vel={}", player.position(), vel);
        }
        player.resetFallDistance();
        player.causeFoodExhaustion(0.2F);
    }

    // ------------------------------------------------------------------ Eventos (cliente y servidor)

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        ItemStack stack = event.getItemStack();
        if (!canTryCling(player, stack)) return;
        Direction face = event.getFace();
        if (face == null || face.getAxis().isVertical()) return;
        Spot spot = findSpot(player, face, event.getPos());
        if (spot == null) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (player instanceof ServerPlayer sp) doCling(sp, event.getHand(), spot);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        Player player = event.getEntity();
        ItemStack stack = event.getItemStack();
        if (!canTryCling(player, stack)) return;
        Spot spot = findSpot(player, null, null);
        if (spot == null) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (player instanceof ServerPlayer sp) doCling(sp, event.getHand(), spot);
    }

    private WallCling() {}
}
