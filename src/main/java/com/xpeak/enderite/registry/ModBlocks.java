package com.xpeak.enderite.registry;

import com.xpeak.enderite.EnderiteMod;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, EnderiteMod.MODID);

    /** Mena del End: solo se puede picar con un pico de netherite (o de enderite). */
    public static final RegistryObject<Block> ENDERITE_ORE = BLOCKS.register("enderite_ore", () -> new Block(
            BlockBehaviour.Properties.of().mapColor(MapColor.SAND).instrument(NoteBlockInstrument.BASEDRUM)
                    .requiresCorrectToolForDrops().strength(40.0F, 1200.0F)
                    .sound(SoundType.ANCIENT_DEBRIS).lightLevel(s -> 4)));

    public static final RegistryObject<Block> ENDERITE_BLOCK = BLOCKS.register("enderite_block", () -> new Block(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).requiresCorrectToolForDrops()
                    .strength(60.0F, 2400.0F).sound(SoundType.NETHERITE_BLOCK).lightLevel(s -> 6)));

    public static final RegistryObject<Block> ENDERITE_BRICKS = BLOCKS.register("enderite_bricks", () -> new Block(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).instrument(NoteBlockInstrument.BASEDRUM)
                    .requiresCorrectToolForDrops().strength(4.0F, 12.0F).sound(SoundType.DEEPSLATE_BRICKS)));

    public static final RegistryObject<Block> CHISELED_ENDERITE_BRICKS = BLOCKS.register("chiseled_enderite_bricks", () -> new Block(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).instrument(NoteBlockInstrument.BASEDRUM)
                    .requiresCorrectToolForDrops().strength(4.0F, 12.0F).sound(SoundType.DEEPSLATE_BRICKS).lightLevel(s -> 3)));

    public static final RegistryObject<Block> ENDERITE_BRICK_STAIRS = BLOCKS.register("enderite_brick_stairs", () -> new StairBlock(
            () -> ENDERITE_BRICKS.get().defaultBlockState(),
            BlockBehaviour.Properties.copy(ENDERITE_BRICKS.get())));

    public static final RegistryObject<Block> ENDERITE_BRICK_SLAB = BLOCKS.register("enderite_brick_slab", () -> new SlabBlock(
            BlockBehaviour.Properties.copy(ENDERITE_BRICKS.get())));

    private ModBlocks() {}
}
