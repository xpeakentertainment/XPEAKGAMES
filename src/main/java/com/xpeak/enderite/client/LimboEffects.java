package com.xpeak.enderite.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** Cielo y luz del Limbo: sin nubes, niebla rojiza y luz enfermiza. */
public class LimboEffects extends DimensionSpecialEffects {
    public LimboEffects() {
        super(Float.NaN, true, SkyType.NORMAL, false, false);
    }

    @Override
    public Vec3 getBrightnessDependentFogColor(Vec3 color, float brightness) {
        return new Vec3(0.20D * brightness + 0.05D, 0.03D * brightness + 0.01D, 0.04D * brightness + 0.015D);
    }

    @Override
    public boolean isFoggyAt(int x, int z) {
        return false;
    }

    @Override
    public void adjustLightmapColors(ClientLevel level, float partialTicks, float skyDarken, float blockLightRedFlicker,
                                     float skyLight, int pixelX, int pixelY, Vector3f colors) {
        // Tono rojo enfermizo, y un minimo de luz para que se distingan las formas en la oscuridad
        float r = Math.max(colors.x() * 1.05F, 0.10F);
        float g = Math.max(colors.y() * 0.72F, 0.035F);
        float b = Math.max(colors.z() * 0.70F, 0.04F);
        colors.set(Math.min(r, 1.0F), Math.min(g, 1.0F), Math.min(b, 1.0F));
    }
}
