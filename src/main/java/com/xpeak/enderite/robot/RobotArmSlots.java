package com.xpeak.enderite.robot;

import com.xpeak.enderite.registry.ModSounds;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/** Los dos huecos de brazo que aparecen en el inventario al llevar un torso robot. */
public final class RobotArmSlots {
    public static void handleClick(ServerPlayer player, boolean left, boolean creative, ItemStack creativeStack) {
        ItemStack torso = RobotHelper.getTorso(player);
        if (torso.isEmpty()) return;
        HumanoidArm side = left ? HumanoidArm.LEFT : HumanoidArm.RIGHT;
        ItemStack current = RobotHelper.getArm(torso, side);
        if (creative && player.getAbilities().instabuild) {
            if (!creativeStack.isEmpty() && !(creativeStack.getItem() instanceof RobotArmItem)) return;
            RobotHelper.setArm(torso, side, creativeStack.isEmpty() ? ItemStack.EMPTY : creativeStack.copyWithCount(1));
        } else {
            AbstractContainerMenu menu = player.containerMenu;
            ItemStack carried = menu.getCarried();
            if (!carried.isEmpty() && (!(carried.getItem() instanceof RobotArmItem) || carried.getCount() != 1)) return;
            if (carried.isEmpty() && current.isEmpty()) return;
            RobotHelper.setArm(torso, side, carried.copy());
            menu.setCarried(current);
            menu.broadcastChanges();
        }
        player.inventoryMenu.broadcastChanges();
        player.level().playSound(null, player.blockPosition(), ModSounds.ROBOT_EQUIP.get(), SoundSource.PLAYERS, 0.8F, 1.0F);
    }

    private RobotArmSlots() {}
}
