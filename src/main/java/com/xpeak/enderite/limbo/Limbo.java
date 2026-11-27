package com.xpeak.enderite.limbo;

import com.xpeak.enderite.EnderiteMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

public final class Limbo {
    public static final ResourceLocation ID = new ResourceLocation(EnderiteMod.MODID, "limbo");
    public static final ResourceKey<Level> LIMBO = ResourceKey.create(Registries.DIMENSION, ID);

    /** Fases que se mandan al cliente. */
    public static final int PHASE_NONE = 0;
    public static final int PHASE_LOADING = 1;
    public static final int PHASE_ACTIVE = 2;

    public static boolean isLimbo(Level level) {
        return level != null && level.dimension() == LIMBO;
    }

    private Limbo() {}
}
