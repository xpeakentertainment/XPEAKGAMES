package com.xpeak.enderite.client;

import com.xpeak.enderite.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;

/** Zumbido grave y continuo mientras estas en el Limbo. */
public class LimboAmbientSound extends AbstractTickableSoundInstance {
    public LimboAmbientSound() {
        super(ModSounds.LIMBO_AMBIENT.get(), SoundSource.AMBIENT, SoundInstance.createUnseededRandom());
        this.looping = true;
        this.delay = 0;
        this.volume = 0.05F;
        this.relative = true;
        this.attenuation = Attenuation.NONE;
    }

    @Override
    public void tick() {
        if (Minecraft.getInstance().player == null || !ClientLimboState.inLimbo()) {
            this.volume -= 0.05F;
            if (this.volume <= 0.0F) this.stop();
        } else {
            this.volume = Math.min(0.85F, this.volume + 0.02F);
        }
    }

    public boolean isDone() {
        return this.isStopped();
    }
}
