package com.xpeak.enderite.registry;

import com.xpeak.enderite.EnderiteMod;
import com.xpeak.enderite.content.EnderiteMaterials;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.List;
import java.util.function.Supplier;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, EnderiteMod.MODID);

    private static Item.Properties fire() {
        return new Item.Properties().fireResistant();
    }

    // Materiales
    public static final RegistryObject<Item> ENDERITE_SCRAP = ITEMS.register("enderite_scrap", () -> new Item(fire()));
    public static final RegistryObject<Item> ENDERITE_INGOT = ITEMS.register("enderite_ingot", () -> new Item(fire()));
    public static final RegistryObject<Item> ENDERITE_UPGRADE_SMITHING_TEMPLATE = ITEMS.register("enderite_upgrade_smithing_template",
            ModItems::createUpgradeTemplate);

    // Herramientas (dano total x2 respecto a netherite: espada 16, hacha 20, pico 12, pala 13, azada 2)
    public static final RegistryObject<Item> ENDERITE_SWORD = ITEMS.register("enderite_sword",
            () -> new SwordItem(EnderiteMaterials.ENDERITE_TIER, 7, -2.4F, fire()));
    public static final RegistryObject<Item> ENDERITE_PICKAXE = ITEMS.register("enderite_pickaxe",
            () -> new PickaxeItem(EnderiteMaterials.ENDERITE_TIER, 3, -2.8F, fire()));
    public static final RegistryObject<Item> ENDERITE_AXE = ITEMS.register("enderite_axe",
            () -> new AxeItem(EnderiteMaterials.ENDERITE_TIER, 11.0F, -3.0F, fire()));
    public static final RegistryObject<Item> ENDERITE_SHOVEL = ITEMS.register("enderite_shovel",
            () -> new ShovelItem(EnderiteMaterials.ENDERITE_TIER, 4.0F, -3.0F, fire()));
    public static final RegistryObject<Item> ENDERITE_HOE = ITEMS.register("enderite_hoe",
            () -> new HoeItem(EnderiteMaterials.ENDERITE_TIER, -7, 0.0F, fire()));

    // Armadura
    public static final RegistryObject<Item> ENDERITE_HELMET = ITEMS.register("enderite_helmet",
            () -> new ArmorItem(EnderiteMaterials.ENDERITE_ARMOR, ArmorItem.Type.HELMET, fire()));
    public static final RegistryObject<Item> ENDERITE_CHESTPLATE = ITEMS.register("enderite_chestplate",
            () -> new ArmorItem(EnderiteMaterials.ENDERITE_ARMOR, ArmorItem.Type.CHESTPLATE, fire()));
    public static final RegistryObject<Item> ENDERITE_LEGGINGS = ITEMS.register("enderite_leggings",
            () -> new ArmorItem(EnderiteMaterials.ENDERITE_ARMOR, ArmorItem.Type.LEGGINGS, fire()));
    public static final RegistryObject<Item> ENDERITE_BOOTS = ITEMS.register("enderite_boots",
            () -> new ArmorItem(EnderiteMaterials.ENDERITE_ARMOR, ArmorItem.Type.BOOTS, fire()));

    // Bloques
    public static final RegistryObject<Item> ENDERITE_ORE = block(ModBlocks.ENDERITE_ORE, true);
    public static final RegistryObject<Item> ENDERITE_BLOCK = block(ModBlocks.ENDERITE_BLOCK, true);
    public static final RegistryObject<Item> ENDERITE_BRICKS = block(ModBlocks.ENDERITE_BRICKS, false);
    public static final RegistryObject<Item> CHISELED_ENDERITE_BRICKS = block(ModBlocks.CHISELED_ENDERITE_BRICKS, false);
    public static final RegistryObject<Item> ENDERITE_BRICK_STAIRS = block(ModBlocks.ENDERITE_BRICK_STAIRS, false);
    public static final RegistryObject<Item> ENDERITE_BRICK_SLAB = block(ModBlocks.ENDERITE_BRICK_SLAB, false);

    // Huevo del Crawler
    public static final RegistryObject<Item> CRAWLER_SPAWN_EGG = ITEMS.register("crawler_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.CRAWLER, 0x0B0B0D, 0x8A0B0B, new Item.Properties()));

    private static RegistryObject<Item> block(RegistryObject<? extends Block> block, boolean fireResistant) {
        Supplier<Item> s = () -> new BlockItem(block.get(), fireResistant ? fire() : new Item.Properties());
        return ITEMS.register(block.getId().getPath(), s);
    }

    private static SmithingTemplateItem createUpgradeTemplate() {
        ChatFormatting blue = ChatFormatting.BLUE;
        ChatFormatting gray = ChatFormatting.GRAY;
        return new SmithingTemplateItem(
                Component.translatable("item.enderite.smithing_template.enderite_upgrade.applies_to").withStyle(blue),
                Component.translatable("item.enderite.smithing_template.enderite_upgrade.ingredients").withStyle(blue),
                Component.translatable("upgrade.enderite.enderite_upgrade").withStyle(gray),
                Component.translatable("item.enderite.smithing_template.enderite_upgrade.base_slot_description"),
                Component.translatable("item.enderite.smithing_template.enderite_upgrade.additions_slot_description"),
                List.of(new ResourceLocation("item/empty_armor_slot_helmet"), new ResourceLocation("item/empty_slot_sword"),
                        new ResourceLocation("item/empty_armor_slot_chestplate"), new ResourceLocation("item/empty_slot_pickaxe"),
                        new ResourceLocation("item/empty_armor_slot_leggings"), new ResourceLocation("item/empty_slot_axe"),
                        new ResourceLocation("item/empty_armor_slot_boots"), new ResourceLocation("item/empty_slot_hoe"),
                        new ResourceLocation("item/empty_slot_shovel")),
                List.of(new ResourceLocation("item/empty_slot_ingot")));
    }

    private ModItems() {}
}
