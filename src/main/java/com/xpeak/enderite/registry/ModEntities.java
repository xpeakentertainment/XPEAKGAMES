package com.xpeak.enderite.registry;

import com.xpeak.enderite.EnderiteMod;
import com.xpeak.enderite.entity.CrawlerEntity;
import com.xpeak.enderite.entity.StuckToolEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, EnderiteMod.MODID);

    public static final RegistryObject<EntityType<CrawlerEntity>> CRAWLER = ENTITIES.register("crawler",
            () -> EntityType.Builder.of(CrawlerEntity::new, MobCategory.MONSTER)
                    .sized(0.7F, 3.0F).fireImmune().clientTrackingRange(16).updateInterval(2)
                    .build(EnderiteMod.MODID + ":crawler"));

    public static final RegistryObject<EntityType<StuckToolEntity>> STUCK_TOOL = ENTITIES.register("stuck_tool",
            () -> EntityType.Builder.<StuckToolEntity>of(StuckToolEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F).fireImmune().clientTrackingRange(10).updateInterval(20)
                    .build(EnderiteMod.MODID + ":stuck_tool"));

    public static final RegistryObject<EntityType<com.xpeak.enderite.robot.RobotRocketEntity>> ROBOT_ROCKET = ENTITIES.register("robot_rocket",
            () -> EntityType.Builder.<com.xpeak.enderite.robot.RobotRocketEntity>of(com.xpeak.enderite.robot.RobotRocketEntity::new, MobCategory.MISC)
                    .sized(0.3F, 0.3F).clientTrackingRange(8).updateInterval(1)
                    .build(EnderiteMod.MODID + ":robot_rocket"));

    public static final RegistryObject<EntityType<com.xpeak.enderite.robot.HarpoonEntity>> HARPOON = ENTITIES.register("harpoon",
            () -> EntityType.Builder.<com.xpeak.enderite.robot.HarpoonEntity>of(com.xpeak.enderite.robot.HarpoonEntity::new, MobCategory.MISC)
                    .sized(0.3F, 0.3F).clientTrackingRange(10).updateInterval(1)
                    .build(EnderiteMod.MODID + ":harpoon"));

    public static final RegistryObject<EntityType<com.xpeak.enderite.robot.RobotPortalEntity>> ROBOT_PORTAL = ENTITIES.register("robot_portal",
            () -> EntityType.Builder.<com.xpeak.enderite.robot.RobotPortalEntity>of(com.xpeak.enderite.robot.RobotPortalEntity::new, MobCategory.MISC)
                    .sized(1.0F, 1.0F).fireImmune().clientTrackingRange(10).updateInterval(20)
                    .build(EnderiteMod.MODID + ":robot_portal"));

    public static final RegistryObject<EntityType<com.xpeak.enderite.robot.ThrownBlockEntity>> THROWN_BLOCK = ENTITIES.register("thrown_block",
            () -> EntityType.Builder.<com.xpeak.enderite.robot.ThrownBlockEntity>of(com.xpeak.enderite.robot.ThrownBlockEntity::new, MobCategory.MISC)
                    .sized(0.7F, 0.7F).clientTrackingRange(10).updateInterval(1)
                    .build(EnderiteMod.MODID + ":thrown_block"));

    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(CRAWLER.get(), CrawlerEntity.createAttributes().build());
    }

    private ModEntities() {}
}
