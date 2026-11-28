package com.xpeak.enderite.robot;

import com.xpeak.enderite.registry.ModBlockEntities;
import com.xpeak.enderite.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/**
 * Huecos: 0 = torso, 1 = municion (cohetes de fuegos artificiales), 2 = material de reparacion (lingotes de hierro).
 * Los brazos no son huecos propios: se guardan dentro del torso.
 */
public class EngineeringTableBlockEntity extends BlockEntity implements MenuProvider {
    public static final int TORSO = 0, AMMO = 1, REPAIR = 2;
    private final SimpleContainer container = new SimpleContainer(3) {
        @Override
        public void setChanged() {
            super.setChanged();
            EngineeringTableBlockEntity.this.setChanged();
        }
    };

    public EngineeringTableBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ENGINEERING_TABLE.get(), pos, state);
    }

    public SimpleContainer getContainer() {
        return container;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, EngineeringTableBlockEntity be) {
        if (level.getGameTime() % 10 != 0) return;
        if (be.process()) level.playSound(null, pos, ModSounds.ENGINEERING_BUILD.get(), SoundSource.BLOCKS, 0.5F, 1.4F);
    }

    /** Carga municion y repara. Devuelve true si ha hecho algo. */
    public boolean process() {
        ItemStack torso = container.getItem(TORSO);
        if (!(torso.getItem() instanceof RobotTorsoItem)) return false;
        boolean did = false;

        // Municion: cada cohete de fuegos artificiales = 1 cohete del brazo
        ItemStack ammo = container.getItem(AMMO);
        if (ammo.is(Items.FIREWORK_ROCKET)) {
            for (HumanoidArm side : HumanoidArm.values()) {
                ItemStack arm = RobotHelper.getArm(torso, side);
                if (RobotHelper.armType(arm) != RobotArmType.ROCKET) continue;
                int have = RobotHelper.getAmmo(arm);
                int add = Math.min(RobotHelper.MAX_AMMO - have, ammo.getCount());
                if (add > 0) {
                    RobotHelper.setAmmo(arm, have + add);
                    RobotHelper.setArm(torso, side, arm);
                    ammo.shrink(add);
                    did = true;
                }
                if (ammo.isEmpty()) break;
            }
        }

        // Reparacion: cada lingote de hierro repara un 25% del torso y de los brazos (solo aqui se puede reparar)
        ItemStack mat = container.getItem(REPAIR);
        if (mat.is(Items.IRON_INGOT) && needsRepair(torso)) {
            RobotHelper.repair(torso, torso.getMaxDamage() / 4);
            for (HumanoidArm side : HumanoidArm.values()) {
                ItemStack arm = RobotHelper.getArm(torso, side);
                if (!arm.isEmpty()) {
                    RobotHelper.repair(arm, arm.getMaxDamage() / 4);
                    RobotHelper.setArm(torso, side, arm);
                }
            }
            mat.shrink(1);
            did = true;
        }
        if (did) container.setChanged();
        return did;
    }

    private static boolean needsRepair(ItemStack torso) {
        if (torso.getDamageValue() > 0) return true;
        for (HumanoidArm side : HumanoidArm.values()) {
            ItemStack arm = RobotHelper.getArm(torso, side);
            if (!arm.isEmpty() && arm.getDamageValue() > 0) return true;
        }
        return false;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.enderite.engineering_table");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new EngineeringTableMenu(id, inv, container, ContainerLevelAccess.create(level, worldPosition));
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        ContainerHelper.saveAllItems(tag, itemsList());
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        var list = net.minecraft.core.NonNullList.withSize(container.getContainerSize(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, list);
        for (int i = 0; i < list.size(); i++) container.setItem(i, list.get(i));
    }

    private net.minecraft.core.NonNullList<ItemStack> itemsList() {
        var list = net.minecraft.core.NonNullList.withSize(container.getContainerSize(), ItemStack.EMPTY);
        for (int i = 0; i < list.size(); i++) list.set(i, container.getItem(i));
        return list;
    }
}
