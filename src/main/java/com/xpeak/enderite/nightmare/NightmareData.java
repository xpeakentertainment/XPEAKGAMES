package com.xpeak.enderite.nightmare;

import com.xpeak.enderite.EnderiteConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.saveddata.SavedData;

/** Nivel de Pesadilla del mundo: sube cada vez que alguien vuelve del Limbo y hace todo el juego mas dificil. */
public class NightmareData extends SavedData {
    private static final String NAME = "enderite_pesadilla";
    private int level;

    public static NightmareData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(NightmareData::load, NightmareData::new, NAME);
    }

    public static int level(MinecraftServer server) {
        return server == null ? 0 : get(server).level;
    }

    private static NightmareData load(CompoundTag tag) {
        NightmareData d = new NightmareData();
        d.level = tag.getInt("nivel");
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("nivel", level);
        return tag;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = Math.max(0, Math.min(level, EnderiteConfig.NIGHTMARE_MAX.get()));
        setDirty();
    }

    /** Se llama cuando un jugador vuelve del Limbo (vivo o muerto). */
    public static void onLimboVisitEnded(MinecraftServer server) {
        NightmareData data = get(server);
        int max = EnderiteConfig.NIGHTMARE_MAX.get();
        if (data.level >= max) return;
        data.setLevel(data.level + 1);
        int lvl = data.level;
        Component msg = Component.translatable("enderite.nightmare.up", lvl, max).withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD);
        Component desc = Component.translatable("enderite.nightmare.level." + Math.min(lvl, 5)).withStyle(ChatFormatting.RED);
        server.getPlayerList().getPlayers().forEach(p -> {
            p.sendSystemMessage(msg);
            p.sendSystemMessage(desc);
            p.playNotifySound(SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 0.6F, 0.5F);
        });
    }
}
