package com.xpeak.enderite.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Arenas movedizas: te hundes poco a poco y te mueves muy lento. Para salir hay que mantener saltar
 * (como si treparas). Si te cubre la cabeza, te asfixias.
 */
public class QuicksandBlock extends Block {
    public QuicksandBlock(Properties props) {
        super(props);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return Shapes.empty();
    }

    @Override
    public VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return Shapes.empty();
    }

    @Override
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        entity.makeStuckInBlock(state, new Vec3(0.35D, 0.45D, 0.35D));
        entity.resetFallDistance();
        if (!level.isClientSide && entity instanceof LivingEntity living && entity.tickCount % 20 == 0) {
            BlockPos eyes = BlockPos.containing(entity.getEyePosition());
            if (level.getBlockState(eyes).is(this)) {
                living.hurt(level.damageSources().inWall(), 1.5F);
            }
        }
    }

    /** Se comporta como una escalera: hundirse despacio, y mantener saltar para salir. */
    @Override
    public boolean isLadder(BlockState state, LevelReader level, BlockPos pos, LivingEntity entity) {
        return true;
    }

    @Override
    public boolean isPathfindable(BlockState state, BlockGetter level, BlockPos pos, PathComputationType type) {
        return false;
    }
}
