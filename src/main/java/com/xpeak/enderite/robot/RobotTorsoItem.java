package com.xpeak.enderite.robot;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.xpeak.enderite.EnderiteMod;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/** Torso robot: azul = x1.5 velocidad, rojo = x1.5 dano, verde = x2 vida. Solo se repara en la Mesa de ingenieria. */
public class RobotTorsoItem extends ArmorItem {
    private static final UUID CORE_ID = UUID.fromString("9b3c0f1e-55aa-4a7e-b5d6-2c1f0e7a0b01");

    public static final ArmorMaterial MATERIAL = new ArmorMaterial() {
        @Override public int getDurabilityForType(ArmorItem.Type type) { return 700; }
        @Override public int getDefenseForType(ArmorItem.Type type) { return 8; }
        @Override public int getEnchantmentValue() { return 0; }
        @Override public SoundEvent getEquipSound() { return SoundEvents.ARMOR_EQUIP_IRON; }
        @Override public Ingredient getRepairIngredient() { return Ingredient.EMPTY; }
        @Override public String getName() { return EnderiteMod.MODID + ":robot"; }
        @Override public float getToughness() { return 2.0F; }
        @Override public float getKnockbackResistance() { return 0.1F; }
    };

    public final RobotCore core;

    public RobotTorsoItem(RobotCore core, Properties props) {
        super(MATERIAL, ArmorItem.Type.CHESTPLATE, props);
        this.core = core;
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(EquipmentSlot slot, ItemStack stack) {
        Multimap<Attribute, AttributeModifier> base = super.getAttributeModifiers(slot, stack);
        if (slot != EquipmentSlot.CHEST || RobotHelper.isBroken(stack)) return base;
        ImmutableMultimap.Builder<Attribute, AttributeModifier> b = ImmutableMultimap.builder();
        b.putAll(base);
        switch (core) {
            case BLUE -> b.put(Attributes.MOVEMENT_SPEED, new AttributeModifier(CORE_ID, "Nucleo azul", 0.5D, AttributeModifier.Operation.MULTIPLY_BASE));
            case RED -> b.put(Attributes.ATTACK_DAMAGE, new AttributeModifier(CORE_ID, "Nucleo rojo", 0.5D, AttributeModifier.Operation.MULTIPLY_TOTAL));
            case GREEN -> b.put(Attributes.MAX_HEALTH, new AttributeModifier(CORE_ID, "Nucleo verde", 1.0D, AttributeModifier.Operation.MULTIPLY_BASE));
        }
        return b.build();
    }

    @Override
    public String getArmorTexture(ItemStack stack, Entity entity, EquipmentSlot slot, String type) {
        return EnderiteMod.MODID + ":textures/models/armor/robot_torso_" + core.id + ".png";
    }

    /** Nunca se rompe del todo: se queda averiado. */
    @Override
    public <T extends LivingEntity> int damageItem(ItemStack stack, int amount, T entity, Consumer<T> onBroken) {
        return Math.max(0, Math.min(amount, stack.getMaxDamage() - 1 - stack.getDamageValue()));
    }

    @Override
    public boolean isRepairable(ItemStack stack) {
        return false;
    }

    @Override
    public boolean isValidRepairItem(ItemStack toRepair, ItemStack repair) {
        return false;
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return false;
    }

    @Override
    public boolean isBookEnchantable(ItemStack stack, ItemStack book) {
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.enderite.robot_torso.effect." + core.id).withStyle(core.format));
        ItemStack left = RobotHelper.getArm(stack, HumanoidArm.LEFT);
        ItemStack right = RobotHelper.getArm(stack, HumanoidArm.RIGHT);
        tooltip.add(Component.translatable("item.enderite.robot.left_arm",
                left.isEmpty() ? Component.translatable("item.enderite.robot.none") : left.getHoverName()).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.enderite.robot.right_arm",
                right.isEmpty() ? Component.translatable("item.enderite.robot.none") : right.getHoverName()).withStyle(ChatFormatting.GRAY));
        int pct = 100 - (int) (100.0F * stack.getDamageValue() / Math.max(1, stack.getMaxDamage() - 1));
        tooltip.add(Component.translatable("item.enderite.robot.integrity", pct).withStyle(RobotHelper.isBroken(stack) ? ChatFormatting.RED : ChatFormatting.DARK_GRAY));
        if (RobotHelper.isBroken(stack)) tooltip.add(Component.translatable("item.enderite.robot.broken").withStyle(ChatFormatting.RED));
        tooltip.add(Component.translatable("item.enderite.robot.water").withStyle(ChatFormatting.DARK_AQUA));
    }
}
