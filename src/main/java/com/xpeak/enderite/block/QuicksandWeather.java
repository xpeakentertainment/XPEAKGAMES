package com.xpeak.enderite.block;

import com.xpeak.enderite.EnderiteMod;
import com.xpeak.enderite.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashSet;
import java.util.Set;

/**
 * Cuando llueve, cada gota que cae sobre arena tiene un 10% de convertirla en arenas movedizas.
 * Se comprueba al mismo ritmo que la lluvia normal de Minecraft (1 columna por chunk cada ~16 ticks).
 */
@Mod.EventBusSubscriber(modid = EnderiteMod.MODID)
public final class QuicksandWeather {
    public static final float CHANCE = 0.10F;

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level) || !level.isRaining()) return;
        RandomSource random = level.random;
        Set<Long> done = new HashSet<>();
        int radius = 8;
        for (ServerPlayer player : level.players()) {
            ChunkPos center = player.chunkPosition();
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    long key = ChunkPos.asLong(center.x + dx, center.z + dz);
                    if (!done.add(key) || random.nextInt(16) != 0) continue;
                    if (!level.hasChunk(center.x + dx, center.z + dz)) continue;
                    int x = ((center.x + dx) << 4) + random.nextInt(16);
                    int z = ((center.z + dz) << 4) + random.nextInt(16);
                    BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, new BlockPos(x, 0, z));
                    tryConvert(level, top.below(), random);
                }
            }
        }
    }

    /** La lluvia cae sobre {@code pos}: si es arena, 10% de que se vuelva movediza. */
    public static boolean tryConvert(ServerLevel level, BlockPos pos, RandomSource random) {
        if (!level.isRaining() || !level.getBlockState(pos).is(Blocks.SAND)) return false;
        // La lluvia solo cae sobre el bloque mas alto de la columna
        if (level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, pos).getY() != pos.getY() + 1) return false;
        if (level.getBiome(pos).value().getPrecipitationAt(pos) != net.minecraft.world.level.biome.Biome.Precipitation.RAIN) return false;
        if (random.nextFloat() >= CHANCE) return false;
        level.setBlock(pos, ModBlocks.QUICKSAND.get().defaultBlockState(), 3);
        return true;
    }

    private QuicksandWeather() {}
}
