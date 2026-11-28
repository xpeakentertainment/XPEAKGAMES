package com.xpeak.enderite.robot;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Rayo azul del exobrazo: alcance "infinito" (hasta chocar con un bloque o salir de la zona cargada). */
public final class ExoBeam {
    public static final double MAX = 512.0D;

    /** Punto donde acaba el rayo. Nunca carga chunks: se para donde el mundo deja de estar cargado. */
    public static Vec3 trace(Level level, Vec3 from, Vec3 dir, Entity source) {
        Vec3 to = from.add(dir.normalize().scale(MAX));
        ClipContext ctx = new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, source);
        BlockHitResult r = BlockGetter.traverseBlocks(from, to, ctx, (c, pos) -> {
            if (level.isOutsideBuildHeight(pos)) return null;
            if (!level.hasChunkAt(pos)) return BlockHitResult.miss(Vec3.atCenterOf(pos), Direction.UP, pos);
            BlockState state = level.getBlockState(pos);
            VoxelShape shape = c.getBlockShape(state, level, pos);
            return level.clipWithInteractionOverride(from, to, pos, shape, state);
        }, c -> BlockHitResult.miss(to, Direction.UP, BlockPos.containing(to)));
        return r.getLocation();
    }

    /** Criaturas que atraviesa el rayo entre {@code from} y {@code end}. */
    public static List<LivingEntity> hits(Level level, Vec3 from, Vec3 end, Entity source) {
        Set<LivingEntity> out = new LinkedHashSet<>();
        Vec3 d = end.subtract(from);
        double len = d.length();
        int steps = Math.max(1, (int) Math.ceil(len / 8.0D));
        for (int i = 0; i < steps; i++) {
            Vec3 a = from.add(d.scale(i / (double) steps));
            Vec3 b = from.add(d.scale((i + 1) / (double) steps));
            AABB box = new AABB(a, b).inflate(1.0D);
            for (Entity e : level.getEntities(source, box, e -> e instanceof LivingEntity && e.isAlive() && e.isPickable())) {
                if (e.getBoundingBox().inflate(0.3D).clip(a, b).isPresent() || e.getBoundingBox().contains(a)) out.add((LivingEntity) e);
            }
        }
        return new ArrayList<>(out);
    }

    private ExoBeam() {}
}
