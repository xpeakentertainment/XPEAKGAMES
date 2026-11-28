package com.xpeak.enderite.magnet;

import com.xpeak.enderite.registry.ModBlockEntities;
import com.xpeak.enderite.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class MagnetBlockEntity extends BlockEntity {
    public MagnetBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MAGNET.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, MagnetBlockEntity be) {
        if (!state.getValue(MagnetBlock.POWERED) || !(level instanceof ServerLevel sl)) return;
        long t = level.getGameTime();
        Vec3 center = Vec3.atCenterOf(pos);
        MagnetLogic.ambientParticles(sl, center, 0.8D, t);
        if (t % 80 == 0) level.playSound(null, pos, ModSounds.MAGNET_HUM.get(), SoundSource.BLOCKS, 0.5F, 1.0F);
        if (t % 2 == 0) {
            MagnetLogic.pull(sl, center, null, hit -> hit.getBlockPos().equals(pos), t);
        }
    }
}
