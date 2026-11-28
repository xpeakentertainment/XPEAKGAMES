package com.xpeak.enderite.robot;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
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
        RobotArmType type = armType(arm);
        int max = type != null && type.usesAmmo() ? type.maxAmmo : MAX_AMMO;
        arm.getOrCreateTag().putInt("Ammo", Math.max(0, Math.min(max, ammo)));
    }

    public static int maxAmmo(ItemStack arm) {
        RobotArmType type = armType(arm);
        return type != null && type.usesAmmo() ? type.maxAmmo : 0;
    }

    // ------------------------------------------------------------------ distorsionador de ilusiones

    /** Bloque elegido en el menu "Generar" del brazo de ilusiones (o null si aun no has elegido). */
    @Nullable
    public static Block getIllusion(ItemStack arm) {
        if (!arm.hasTag() || !arm.getTag().contains("Illusion")) return null;
        ResourceLocation id = ResourceLocation.tryParse(arm.getTag().getString("Illusion"));
        if (id == null) return null;
        Block b = ForgeRegistries.BLOCKS.getValue(id);
        return b == null || b == Blocks.AIR ? null : b;
    }

    public static void setIllusion(ItemStack arm, @Nullable Block block) {
        if (block == null) {
            if (arm.hasTag()) arm.getTag().remove("Illusion");
            return;
        }
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(block);
        if (id != null) arm.getOrCreateTag().putString("Illusion", id.toString());
    }

    // ------------------------------------------------------------------ catapulta

    /** Bloque que la catapulta tiene agarrado en alto (o null). */
    @Nullable
    public static BlockState getHeld(ItemStack arm) {
        if (!arm.hasTag() || !arm.getTag().contains("Held")) return null;
        BlockState s = NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), arm.getTag().getCompound("Held"));
        return s.isAir() ? null : s;
    }

    public static void setHeld(ItemStack arm, @Nullable BlockState state) {
        if (state == null) {
            if (arm.hasTag()) arm.getTag().remove("Held");
            return;
        }
        arm.getOrCreateTag().put("Held", NbtUtils.writeBlockState(state));
    }

    /** Algun brazo del robot tiene un bloque agarrado (para levantar los brazos al dibujar). */
    @Nullable
    public static BlockState heldBlock(LivingEntity entity) {
        ItemStack torso = getTorso(entity);
        if (torso.isEmpty()) return null;
        for (HumanoidArm side : HumanoidArm.values()) {
            ItemStack arm = getArm(torso, side);
            if (armType(arm) == RobotArmType.CATAPULT) {
                BlockState s = getHeld(arm);
                if (s != null) return s;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ portales

    /** Color del proximo portal que saldra del brazo (0 = cian, 1 = violeta). */
    public static int nextPortalColor(ItemStack arm) {
        return arm.hasTag() ? arm.getTag().getInt("PortalColor") : 0;
    }

    public static void setNextPortalColor(ItemStack arm, int color) {
        arm.getOrCreateTag().putInt("PortalColor", color & 1);
    }

    /** Lado del brazo que se controla con click izquierdo (el izquierdo) y click derecho (el derecho). */
    public static HumanoidArm sideFor(boolean leftClick) {
        return leftClick ? HumanoidArm.LEFT : HumanoidArm.RIGHT;
    }

    private RobotHelper() {}
}
