package com.xpeak.enderite.content;

import com.xpeak.enderite.EnderiteMod;
import com.xpeak.enderite.registry.ModItems;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.ForgeTier;
import net.minecraftforge.common.TierSortingRegistry;

import java.util.List;

/**
 * Valores de la enderite: el doble que la netherite.
 * Netherite: 2031 usos, velocidad 9, +4 de dano, encantabilidad 15, armadura 3/8/6/3, dureza 3, retroceso 0.1.
 */
public final class EnderiteMaterials {
    public static final TagKey<Block> NEEDS_ENDERITE_TOOL = BlockTags.create(new ResourceLocation(EnderiteMod.MODID, "needs_enderite_tool"));

    public static final Tier ENDERITE_TIER = TierSortingRegistry.registerTier(
            new ForgeTier(5, 4062, 18.0F, 8.0F, 30, NEEDS_ENDERITE_TOOL,
                    () -> Ingredient.of(ModItems.ENDERITE_INGOT.get())),
            new ResourceLocation(EnderiteMod.MODID, "enderite"),
            List.of(Tiers.NETHERITE), List.of());

    public static final ArmorMaterial ENDERITE_ARMOR = new ArmorMaterial() {
        @Override
        public int getDurabilityForType(ArmorItem.Type type) {
            int base = switch (type) {
                case HELMET -> 11;
                case CHESTPLATE -> 16;
                case LEGGINGS -> 15;
                case BOOTS -> 13;
            };
            return base * 74; // netherite usa 37
        }

        @Override
        public int getDefenseForType(ArmorItem.Type type) {
            return switch (type) {
                case HELMET -> 6;
                case CHESTPLATE -> 16;
                case LEGGINGS -> 12;
                case BOOTS -> 6;
            };
        }

        @Override
        public int getEnchantmentValue() {
            return 30;
        }

        @Override
        public SoundEvent getEquipSound() {
            return SoundEvents.ARMOR_EQUIP_NETHERITE;
        }

        @Override
        public Ingredient getRepairIngredient() {
            return Ingredient.of(ModItems.ENDERITE_INGOT.get());
        }

        @Override
        public String getName() {
            return EnderiteMod.MODID + ":enderite";
        }

        @Override
        public float getToughness() {
            return 6.0F;
        }

        @Override
        public float getKnockbackResistance() {
            return 0.2F;
        }
    };

    private EnderiteMaterials() {}
}
