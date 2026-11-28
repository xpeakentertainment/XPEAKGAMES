package com.xpeak.enderite.robot;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/** Los brazos van guardados dentro del torso (NBT), asi el robot montado viaja junto. */
public final class RobotHelper {
    public static final String ARMS = "RobotArms";
    public static final int MAX_AMMO = 96;

    public static ItemStack getTorso(LivingEntity entity) {
        ItemStack chest = entity.getItemBySlot(EquipmentSlot.CHEST);
        return chest.getItem() instanceof RobotTorsoItem ? chest : ItemStack.EMPTY;
    }

    public static boolean wearsTorso(LivingEntity entity) {
        return !getTorso(entity).isEmpty();
    }

    public static ItemStack getArm(ItemStack torso, HumanoidArm side) {
        if (torso.isEmpty() || !torso.hasTag()) return ItemStack.EMPTY;
        CompoundTag arms = torso.getTag().getCompound(ARMS);
        String key = side == HumanoidArm.LEFT ? "Left" : "Right";
        return arms.contains(key) ? ItemStack.of(arms.getCompound(key)) : ItemStack.EMPTY;
    }

    public static void setArm(ItemStack torso, HumanoidArm side, ItemStack arm) {
        if (torso.isEmpty()) return;
        CompoundTag arms = torso.getOrCreateTag().getCompound(ARMS);
        String key = side == HumanoidArm.LEFT ? "Left" : "Right";
        if (arm.isEmpty()) arms.remove(key);
        else arms.put(key, arm.save(new CompoundTag()));
        torso.getOrCreateTag().put(ARMS, arms);
    }

    public static boolean hasArms(LivingEntity entity) {
        ItemStack torso = getTorso(entity);
        return !getArm(torso, HumanoidArm.LEFT).isEmpty() || !getArm(torso, HumanoidArm.RIGHT).isEmpty();
    }

    public static RobotArmType armType(ItemStack arm) {
        return arm.getItem() instanceof RobotArmItem a ? a.type : null;
    }

    public static boolean isBroken(ItemStack stack) {
        return stack.isDamageableItem() && stack.getDamageValue() >= stack.getMaxDamage() - 1;
    }

    /** Desgasta sin llegar a romper nunca: a 0 se queda "averiado" hasta que lo repares en la mesa. */
    public static void wear(ItemStack stack, int amount) {
        if (stack.isEmpty() || !stack.isDamageableItem() || amount <= 0) return;
        stack.setDamageValue(Math.min(stack.getMaxDamage() - 1, stack.getDamageValue() + amount));
    }

    public static void repair(ItemStack stack, int amount) {
        if (stack.isEmpty() || !stack.isDamageableItem()) return;
        stack.setDamageValue(Math.max(0, stack.getDamageValue() - amount));
    }

    public static int getAmmo(ItemStack arm) {
        return arm.hasTag() ? arm.getTag().getInt("Ammo") : 0;
    }

    public static void setAmmo(ItemStack arm, int ammo) {
        arm.getOrCreateTag().putInt("Ammo", Math.max(0, Math.min(MAX_AMMO, ammo)));
    }

    /** Lado del brazo que se controla con click izquierdo (el izquierdo) y click derecho (el derecho). */
    public static HumanoidArm sideFor(boolean leftClick) {
        return leftClick ? HumanoidArm.LEFT : HumanoidArm.RIGHT;
    }

    private RobotHelper() {}
}
