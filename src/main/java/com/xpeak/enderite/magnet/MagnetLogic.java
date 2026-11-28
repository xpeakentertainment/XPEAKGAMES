package com.xpeak.enderite.magnet;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Predicate;

/** Atrae todos los objetos sueltos a 50 bloques que no tengan una pared en medio. */
public final class MagnetLogic {
    public static final double RANGE = 50.0D;
    private static final DustParticleOptions BLUE = new DustParticleOptions(new Vector3f(0.25F, 0.65F, 1.0F), 0.8F);
    private static final DustParticleOptions RED = new DustParticleOptions(new Vector3f(1.0F, 0.2F, 0.2F), 0.8F);

    /**
     * @param center punto al que se atraen los objetos
     * @param clearAt devuelve true si un bloque golpeado por el rayo no cuenta como pared (el propio iman)
     * @return cuantos objetos esta atrayendo
     */
    public static int pull(ServerLevel level, Vec3 center, @Nullable Entity self, @Nullable Predicate<BlockHitResult> clearAt, long tick) {
        List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, new AABB(center, center).inflate(RANGE),
                e -> e.isAlive() && e.position().distanceToSqr(center) <= RANGE * RANGE);
        int pulled = 0;
        for (ItemEntity item : items) {
            Vec3 from = item.position().add(0.0D, 0.15D, 0.0D);
            double dist = from.distanceTo(center);
            if (dist < 0.6D) {
                item.setDeltaMovement(item.getDeltaMovement().scale(0.5D));
                continue;
            }
            BlockHitResult hit = level.clip(new ClipContext(from, center, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, self != null ? self : item));
            if (hit.getType() == HitResult.Type.BLOCK && (clearAt == null || !clearAt.test(hit))) continue; // hay una pared
            Vec3 dir = center.subtract(from).normalize();
            // Vuelan en linea recta hacia el iman (se compensa la gravedad), frenando al llegar
            double speed = Math.min(0.65D, 0.12D + dist * 0.06D);
            Vec3 v = item.getDeltaMovement().scale(0.55D).add(dir.scale(speed * 0.45D)).add(0.0D, 0.04D, 0.0D);
            if (dist < 1.6D) v = dir.scale(dist * 0.25D).add(0.0D, 0.04D, 0.0D);
            item.setDeltaMovement(v);
            item.hasImpulse = true;
            pulled++;
            if ((tick + item.getId()) % 6 == 0) {
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, item.getX(), item.getY() + 0.3D, item.getZ(), 1, 0.05D, 0.05D, 0.05D, 0.02D);
                Vec3 mid = from.add(center).scale(0.5D);
                level.sendParticles((tick / 6) % 2 == 0 ? BLUE : RED, mid.x, mid.y, mid.z, 1, 0.1D, 0.1D, 0.1D, 0.0D);
            }
        }
        return pulled;
    }

    public static void ambientParticles(ServerLevel level, Vec3 center, double radius, long tick) {
        if (tick % 4 != 0) return;
        double a = (tick * 0.35D) % (Math.PI * 2.0D);
        for (int i = 0; i < 2; i++) {
            double ang = a + i * Math.PI;
            level.sendParticles(i == 0 ? BLUE : RED, center.x + Math.cos(ang) * radius, center.y, center.z + Math.sin(ang) * radius,
                    1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
        if (tick % 12 == 0) {
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, center.x, center.y, center.z, 2, radius * 0.6D, 0.3D, radius * 0.6D, 0.05D);
        }
    }

    private MagnetLogic() {}
}
