package com.xpeak.enderite.robot;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/** Municion propia de un brazo robot. Se mete en el hueco de municion de la Mesa de ingenieria. */
public class AmmoItem extends Item {
    private final String armId;

    public AmmoItem(String armId, Properties props) {
        super(props);
        this.armId = armId;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        RobotArmType type = RobotArmType.forAmmo(this);
        tooltip.add(Component.translatable("item.enderite.ammo.for", Component.translatable("item.enderite.robot_arm_" + armKey()))
                .withStyle(ChatFormatting.GOLD));
        if (type != null) {
            tooltip.add(Component.translatable("item.enderite.ammo.units." + type.id, type.unitsPerItem).withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.translatable("item.enderite.ammo.hint").withStyle(ChatFormatting.DARK_AQUA));
    }

    private String armKey() {
        return armId;
    }
}
