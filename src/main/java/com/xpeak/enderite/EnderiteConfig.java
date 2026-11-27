package com.xpeak.enderite;

import net.minecraftforge.common.ForgeConfigSpec;

public final class EnderiteConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.DoubleValue LIMBO_CHANCE;
    public static final ForgeConfigSpec.IntValue COPY_RADIUS;
    public static final ForgeConfigSpec.IntValue CRAWLER_DELAY_SECONDS;
    public static final ForgeConfigSpec.IntValue NIGHTMARE_MAX;
    public static final ForgeConfigSpec.IntValue CLING_DURABILITY_PERCENT;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("limbo");
        LIMBO_CHANCE = b.comment("Probabilidad (en %) de entrar al Limbo cada vez que duermes")
                .defineInRange("probabilidad", 1.0, 0.0, 100.0);
        COPY_RADIUS = b.comment("Radio (en chunks) del mundo que se copia al Limbo alrededor de tu cama")
                .defineInRange("radioCopiaChunks", 8, 1, 16);
        CRAWLER_DELAY_SECONDS = b.comment("Segundos que tarda el Crawler en aparecer tras llegar al Limbo")
                .defineInRange("retrasoCrawlerSegundos", 18, 0, 600);
        b.pop();
        b.push("pesadilla");
        NIGHTMARE_MAX = b.comment("Nivel maximo de Pesadilla (sube 1 cada vez que alguien vuelve del Limbo)")
                .defineInRange("nivelMaximo", 5, 0, 10);
        b.pop();
        b.push("agarre");
        CLING_DURABILITY_PERCENT = b.comment("Porcentaje de la durabilidad maxima que gasta clavar una herramienta en la pared")
                .defineInRange("durabilidadPorcentaje", 8, 0, 100);
        b.pop();
        SPEC = b.build();
    }

    private EnderiteConfig() {}
}
