package com.xpeak.enderite.registry;

import com.xpeak.enderite.EnderiteMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, EnderiteMod.MODID);

    public static final RegistryObject<SoundEvent> CRAWLER_AMBIENT = sound("entity.crawler.ambient");
    public static final RegistryObject<SoundEvent> CRAWLER_SCREAM = sound("entity.crawler.scream");
    public static final RegistryObject<SoundEvent> CRAWLER_ATTACK = sound("entity.crawler.attack");
    public static final RegistryObject<SoundEvent> CRAWLER_STEP = sound("entity.crawler.step");
    public static final RegistryObject<SoundEvent> CRAWLER_HURT = sound("entity.crawler.hurt");
    public static final RegistryObject<SoundEvent> CRAWLER_DEATH = sound("entity.crawler.death");
    public static final RegistryObject<SoundEvent> LIMBO_AMBIENT = sound("ambient.limbo.loop");
    public static final RegistryObject<SoundEvent> LIMBO_ENTER = sound("ambient.limbo.enter");
    public static final RegistryObject<SoundEvent> LIMBO_HEARTBEAT = sound("ambient.limbo.heartbeat");
    public static final RegistryObject<SoundEvent> LIMBO_ESCAPE = sound("ambient.limbo.escape");
    public static final RegistryObject<SoundEvent> TOOL_STICK = sound("item.tool.stick");
    public static final RegistryObject<SoundEvent> TOOL_PULL = sound("item.tool.pull");

    private static RegistryObject<SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(EnderiteMod.MODID, name)));
    }

    private ModSounds() {}
}
