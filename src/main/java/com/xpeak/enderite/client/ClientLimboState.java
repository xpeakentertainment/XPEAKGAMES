package com.xpeak.enderite.client;

import com.xpeak.enderite.limbo.Limbo;
import net.minecraft.client.Minecraft;

/** Estado del Limbo que conoce el cliente (lo manda el servidor). */
public final class ClientLimboState {
    private static int phase = Limbo.PHASE_NONE;
    private static int ticksToDawn;
    private static long receivedAt;
    private static long phaseChangedAt;

    public static void set(int newPhase, int dawn) {
        Minecraft mc = Minecraft.getInstance();
        long now = mc.level != null ? mc.level.getGameTime() : 0L;
        if (newPhase != phase) phaseChangedAt = now;
        phase = newPhase;
        ticksToDawn = dawn;
        receivedAt = now;
    }

    public static int phase() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return Limbo.PHASE_NONE;
        if (!Limbo.isLimbo(mc.level)) return Limbo.PHASE_NONE;
        // Si estamos en el Limbo y el servidor no ha dicho nada (p. ej. /tp), se trata como activo
        return phase == Limbo.PHASE_NONE ? Limbo.PHASE_ACTIVE : phase;
    }

    public static boolean inLimbo() {
        return phase() != Limbo.PHASE_NONE;
    }

    public static int ticksToDawn() {
        Minecraft mc = Minecraft.getInstance();
        long now = mc.level != null ? mc.level.getGameTime() : receivedAt;
        return (int) Math.max(0L, ticksToDawn - Math.max(0L, now - receivedAt));
    }

    /** Ticks desde el ultimo cambio de fase (para fundidos). */
    public static long ticksSincePhaseChange() {
        Minecraft mc = Minecraft.getInstance();
        long now = mc.level != null ? mc.level.getGameTime() : phaseChangedAt;
        return Math.max(0L, now - phaseChangedAt);
    }

    public static void reset() {
        phase = Limbo.PHASE_NONE;
        ticksToDawn = 0;
    }

    private ClientLimboState() {}
}
