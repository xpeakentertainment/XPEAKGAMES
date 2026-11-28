package com.xpeak.enderite.robot;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/** Brazo robot intercambiable. Se coloca en los huecos laterales que aparecen al llevar un torso robot. */
public class RobotArmItem extends Item {
    public final RobotArmType type;

    public RobotArmItem(RobotArmType type, Properties props) {
        super(props.stacksTo(1).durability(type.durability));
        this.type = type;
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
        tooltip.add(Component.translatable("item.enderite.robot_arm." + type.id + ".desc").withStyle(ChatFormatting.GRAY));
        if (type.usesAmmo()) {
            tooltip.add(Component.translatable("item.enderite.robot.ammo", RobotHelper.getAmmo(stack), type.maxAmmo)
                    .withStyle(ChatFormatting.GOLD));
            tooltip.add(Component.translatable("item.enderite.robot.ammo_type", type.ammoItem().getDescription())
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        if (type == RobotArmType.ILLUSION) {
            net.minecraft.world.level.block.Block b = RobotHelper.getIllusion(stack);
            if (b != null) tooltip.add(Component.translatable("item.enderite.robot.illusion_selected", b.getName()).withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        int pct = 100 - (int) (100.0F * stack.getDamageValue() / Math.max(1, stack.getMaxDamage() - 1));
        tooltip.add(Component.translatable("item.enderite.robot.integrity", pct).withStyle(RobotHelper.isBroken(stack) ? ChatFormatting.RED : ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("item.enderite.robot.arm_hint").withStyle(ChatFormatting.DARK_AQUA));
    }
}
