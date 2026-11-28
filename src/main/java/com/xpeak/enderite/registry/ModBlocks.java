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

    /** Arenas movedizas: un poquito mas oscuras que la arena normal (se tinen en el cliente). */
    public static final RegistryObject<Block> QUICKSAND = BLOCKS.register("quicksand", () -> new com.xpeak.enderite.block.QuicksandBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.SAND).strength(0.6F).sound(SoundType.SAND)
                    .forceSolidOn().noOcclusion().isSuffocating((s, l, p) -> false).isViewBlocking((s, l, p) -> false)));

    public static final RegistryObject<Block> MAGNET = BLOCKS.register("magnet", () -> new com.xpeak.enderite.magnet.MagnetBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).requiresCorrectToolForDrops().strength(4.0F, 6.0F)
                    .sound(SoundType.METAL).lightLevel(s -> s.getValue(com.xpeak.enderite.magnet.MagnetBlock.POWERED) ? 7 : 0)));

    public static final RegistryObject<Block> ENGINEERING_TABLE = BLOCKS.register("engineering_table", () -> new com.xpeak.enderite.robot.EngineeringTableBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).requiresCorrectToolForDrops().strength(3.5F, 6.0F)
                    .sound(SoundType.METAL).noOcclusion()));

    /** Bloque ilusorio del Distorsionador de ilusiones: se ve como otro bloque pero se atraviesa (no-clip). */
    public static final RegistryObject<Block> ILLUSION_BLOCK = BLOCKS.register("illusion_block", () -> new com.xpeak.enderite.robot.IllusionBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.NONE).noCollission().instabreak().noOcclusion().noLootTable()
                    .sound(SoundType.AMETHYST).isSuffocating((s, l, p) -> false).isViewBlocking((s, l, p) -> false)
                    .isRedstoneConductor((s, l, p) -> false).pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY)));

    private ModBlocks() {}
}
