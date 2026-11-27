package com.xpeak.enderite.limbo;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.lighting.ChunkSkyLightSources;

import java.util.*;

/**
 * Copia el mundo real al Limbo alrededor de un punto.
 * <p>
 * El Limbo usa el mismo generador y la misma semilla que el mundo normal, asi que el terreno natural ya es identico.
 * Aqui solo se copian las diferencias (casas, minas, cofres, carteles...) bloque a bloque, directamente en las
 * secciones del chunk: sin actualizar vecinos, sin soltar objetos y sin activar redstone/pistones/TNT.
 */
public class WorldCopier {
    public static final TicketType<ChunkPos> TICKET = TicketType.create("enderite_limbo_copy",
            Comparator.comparingLong(ChunkPos::toLong), 20 * 60 * 5);

    private final ServerLevel src;
    private final ServerLevel dst;
    private final ChunkPos center;
    private final int radius;
    private final List<ChunkPos> pending = new ArrayList<>();
    private boolean released;
    private int age;
    public int changedBlocks;
    public int copiedChunks;
    public int skippedChunks;

    public WorldCopier(ServerLevel src, ServerLevel dst, ChunkPos center, int radius) {
        this.src = src;
        this.dst = dst;
        this.center = center;
        this.radius = radius;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                pending.add(new ChunkPos(center.x + dx, center.z + dz));
            }
        }
        pending.sort(Comparator.comparingInt(p -> dist(p)));
        // Mantiene cargado el mundo real (aunque el jugador se vaya) y genera el Limbo de forma asincrona
        src.getChunkSource().addRegionTicket(TICKET, center, radius, center);
        dst.getChunkSource().addRegionTicket(TICKET, center, radius + 1, center);
    }

    private int dist(ChunkPos p) {
        int dx = p.x - center.x, dz = p.z - center.z;
        return dx * dx + dz * dz;
    }

    /**
     * Procesa chunks durante como mucho {@code budgetNanos}.
     *
     * @return true cuando ya no queda nada por copiar.
     */
    public boolean tick(long budgetNanos) {
        age++;
        if (age > 20 * 120) {
            // Algo no termino de cargar en 2 minutos: el resto se queda con el terreno natural (que ya es igual).
            skippedChunks += pending.size();
            pending.clear();
            return true;
        }
        long start = System.nanoTime();
        Iterator<ChunkPos> it = pending.iterator();
        while (it.hasNext()) {
            if (System.nanoTime() - start > budgetNanos) break;
            ChunkPos cp = it.next();
            LevelChunk s = src.getChunkSource().getChunkNow(cp.x, cp.z);
            if (s == null) continue; // el ticket lo esta cargando
            LevelChunk d = dst.getChunkSource().getChunkNow(cp.x, cp.z);
            if (d == null) continue; // todavia se esta generando
            changedBlocks += copyChunk(s, d);
            copiedChunks++;
            it.remove();
        }
        return pending.isEmpty();
    }

    /** Copia todo de golpe (para pruebas o areas pequenas). Fuerza la carga/generacion de los chunks. */
    public void copyAllNow() {
        for (ChunkPos cp : new ArrayList<>(pending)) {
            LevelChunk s = src.getChunk(cp.x, cp.z);
            LevelChunk d = dst.getChunk(cp.x, cp.z);
            changedBlocks += copyChunk(s, d);
            copiedChunks++;
        }
        pending.clear();
    }

    public boolean isInnerDone(int innerRadius) {
        for (ChunkPos p : pending) {
            if (Math.max(Math.abs(p.x - center.x), Math.abs(p.z - center.z)) <= innerRadius) return false;
        }
        return true;
    }

    public int remaining() {
        return pending.size();
    }

    public void release() {
        if (released) return;
        released = true;
        src.getChunkSource().removeRegionTicket(TICKET, center, radius, center);
        dst.getChunkSource().removeRegionTicket(TICKET, center, radius + 1, center);
    }

    private int copyChunk(LevelChunk s, LevelChunk d) {
        int changed = 0;
        ThreadedLevelLightEngine light = dst.getChunkSource().getLightEngine();
        LevelChunkSection[] ss = s.getSections();
        LevelChunkSection[] ds = d.getSections();
        Collection<Map.Entry<Heightmap.Types, Heightmap>> heightmaps = d.getHeightmaps();
        ChunkSkyLightSources sky = d.getSkyLightSources();
        int bx = s.getPos().getMinBlockX();
        int bz = s.getPos().getMinBlockZ();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();

        for (int i = 0; i < ss.length; i++) {
            int sectionY = s.getMinSection() + i;
            int j = sectionY - d.getMinSection();
            if (j < 0 || j >= ds.length) continue;
            LevelChunkSection a = ss[i];
            LevelChunkSection b = ds[j];
            if (a.hasOnlyAir() && b.hasOnlyAir()) continue;
            int by = SectionPos.sectionToBlockCoord(sectionY);
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        BlockState ns = a.getBlockState(x, y, z);
                        BlockState os = b.getBlockState(x, y, z);
                        if (ns == os) continue;
                        m.set(bx + x, by + y, bz + z);
                        boolean wasEmpty = b.hasOnlyAir();
                        if (os.hasBlockEntity()) d.removeBlockEntity(m.immutable());
                        b.setBlockState(x, y, z, ns);
                        boolean nowEmpty = b.hasOnlyAir();
                        if (wasEmpty != nowEmpty) light.updateSectionStatus(SectionPos.of(m), nowEmpty);
                        for (Map.Entry<Heightmap.Types, Heightmap> e : heightmaps) {
                            e.getValue().update(x, by + y, z, ns);
                        }
                        sky.update(d, x, by + y, z);
                        light.checkBlock(m.immutable());
                        dst.getChunkSource().blockChanged(m);
                        changed++;
                    }
                }
            }
        }

        // Datos de bloques (carteles, cofres, estandartes, cabezas, macetas...)
        for (Map.Entry<BlockPos, BlockEntity> e : new ArrayList<>(s.getBlockEntities().entrySet())) {
            BlockPos p = e.getKey();
            BlockState st = d.getBlockState(p);
            if (!st.hasBlockEntity()) continue;
            CompoundTag tag;
            try {
                tag = e.getValue().saveWithFullMetadata();
            } catch (Exception ex) {
                continue;
            }
            BlockEntity existing = d.getBlockEntity(p, LevelChunk.EntityCreationType.CHECK);
            if (existing != null) {
                try {
                    if (existing.saveWithFullMetadata().equals(tag)) continue;
                } catch (Exception ignored) {
                }
                d.removeBlockEntity(p);
            }
            BlockEntity created = BlockEntity.loadStatic(p, st, tag);
            if (created != null) {
                d.addAndRegisterBlockEntity(created);
                dst.sendBlockUpdated(p, st, st, 3);
                changed++;
            }
        }
        if (changed > 0) d.setUnsaved(true);
        return changed;
    }
}
