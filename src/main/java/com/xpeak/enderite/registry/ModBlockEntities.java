package com.xpeak.enderite.registry;

import com.xpeak.enderite.EnderiteMod;
import com.xpeak.enderite.magnet.MagnetBlockEntity;
import com.xpeak.enderite.robot.EngineeringTableBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, EnderiteMod.MODID);

    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<MagnetBlockEntity>> MAGNET = BLOCK_ENTITIES.register("magnet",
            () -> BlockEntityType.Builder.of(MagnetBlockEntity::new, ModBlocks.MAGNET.get()).build(null));

    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<EngineeringTableBlockEntity>> ENGINEERING_TABLE = BLOCK_ENTITIES.register("engineering_table",
            () -> BlockEntityType.Builder.of(EngineeringTableBlockEntity::new, ModBlocks.ENGINEERING_TABLE.get()).build(null));

    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<com.xpeak.enderite.robot.IllusionBlockEntity>> ILLUSION = BLOCK_ENTITIES.register("illusion_block",
            () -> BlockEntityType.Builder.of(com.xpeak.enderite.robot.IllusionBlockEntity::new, ModBlocks.ILLUSION_BLOCK.get()).build(null));

    private ModBlockEntities() {}
}
