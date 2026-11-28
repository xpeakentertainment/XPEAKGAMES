package com.xpeak.enderite.client;

import com.xpeak.enderite.EnderiteMod;
import com.xpeak.enderite.debug.ClientTest;
import com.xpeak.enderite.limbo.Limbo;
import com.xpeak.enderite.registry.ModEntities;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterDimensionSpecialEffectsEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = EnderiteMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        if (ClientTest.ENABLED) MinecraftForge.EVENT_BUS.register(ClientTest.class);
        event.enqueueWork(() -> {
            net.minecraft.client.gui.screens.MenuScreens.register(com.xpeak.enderite.registry.ModMenus.ENGINEERING_TABLE.get(), EngineeringTableScreen::new);
            net.minecraft.client.renderer.item.ItemProperties.register(com.xpeak.enderite.registry.ModItems.PORTABLE_MAGNET.get(),
                    new net.minecraft.resources.ResourceLocation(com.xpeak.enderite.EnderiteMod.MODID, "active"),
                    (stack, level, entity, seed) -> com.xpeak.enderite.magnet.PortableMagnetItem.isActive(stack) ? 1.0F : 0.0F);
        });
    }

    /** Arenas movedizas: la textura de la arena normal, un pelin mas oscura. */
    public static final int QUICKSAND_TINT = 0xD9D2C4;

    @SubscribeEvent
    public static void blockColors(net.minecraftforge.client.event.RegisterColorHandlersEvent.Block event) {
        event.register((state, level, pos, tint) -> QUICKSAND_TINT, com.xpeak.enderite.registry.ModBlocks.QUICKSAND.get());
    }

    @SubscribeEvent
    public static void itemColors(net.minecraftforge.client.event.RegisterColorHandlersEvent.Item event) {
        event.register((stack, tint) -> QUICKSAND_TINT, com.xpeak.enderite.registry.ModItems.QUICKSAND.get());
    }

    @SubscribeEvent
    public static void addLayers(EntityRenderersEvent.AddLayers event) {
        RobotClient.rightModel = new RobotArmModel(event.getEntityModels().bakeLayer(RobotArmModel.RIGHT));
        RobotClient.leftModel = new RobotArmModel(event.getEntityModels().bakeLayer(RobotArmModel.LEFT));
        for (String skin : event.getSkins()) {
            if (event.getSkin(skin) instanceof net.minecraft.client.renderer.entity.player.PlayerRenderer pr) {
                pr.addLayer(new RobotClient.ArmsLayer(pr));
            }
        }
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.CRAWLER.get(), CrawlerRenderer::new);
        event.registerEntityRenderer(ModEntities.STUCK_TOOL.get(), StuckToolRenderer::new);
        event.registerEntityRenderer(ModEntities.ROBOT_ROCKET.get(), RobotRocketRenderer::new);
    }

    @SubscribeEvent
    public static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(CrawlerModel.LAYER, CrawlerModel::createLayer);
        event.registerLayerDefinition(RobotArmModel.RIGHT, () -> RobotArmModel.create(true));
        event.registerLayerDefinition(RobotArmModel.LEFT, () -> RobotArmModel.create(false));
    }

    @SubscribeEvent
    public static void registerDimensionEffects(RegisterDimensionSpecialEffectsEvent event) {
        event.register(Limbo.ID, new LimboEffects());
    }

    @SubscribeEvent
    public static void registerOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAbove(VanillaGuiOverlay.VIGNETTE.id(), "limbo", LimboOverlay::render);
        event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "robot_hud", RobotClient::renderHud);
    }

    private ClientSetup() {}
}
