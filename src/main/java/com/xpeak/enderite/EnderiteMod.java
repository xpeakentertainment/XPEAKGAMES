package com.xpeak.enderite;

import com.mojang.logging.LogUtils;
import com.xpeak.enderite.debug.SelfTest;
import com.xpeak.enderite.network.ModNetwork;
import com.xpeak.enderite.registry.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(EnderiteMod.MODID)
public class EnderiteMod {
    public static final String MODID = "enderite";
    public static final Logger LOGGER = LogUtils.getLogger();

    public EnderiteMod() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        ModBlocks.BLOCKS.register(bus);
        ModItems.ITEMS.register(bus);
        ModEntities.ENTITIES.register(bus);
        ModBlockEntities.BLOCK_ENTITIES.register(bus);
        ModMenus.MENUS.register(bus);
        ModSounds.SOUNDS.register(bus);
        ModCreativeTabs.TABS.register(bus);
        ModLootModifiers.SERIALIZERS.register(bus);
        bus.addListener(this::commonSetup);
        bus.addListener(ModEntities::registerAttributes);
        bus.addListener(ModCreativeTabs::addToVanillaTabs);

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, EnderiteConfig.SPEC);
        ModNetwork.register();

        if (SelfTest.ENABLED) {
            MinecraftForge.EVENT_BUS.register(SelfTest.class);
        }
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("Enderite: El Limbo cargado");
    }
}
