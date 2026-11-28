package com.xpeak.enderite.robot;

import com.xpeak.enderite.registry.ModBlocks;
import com.xpeak.enderite.registry.ModMenus;
import com.xpeak.enderite.registry.ModSounds;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Menu de la Mesa de ingenieria.
 * Huecos del menu: 0 torso, 1 brazo izquierdo, 2 brazo derecho, 3 municion, 4 reparacion, 5..40 inventario.
 */
public class EngineeringTableMenu extends AbstractContainerMenu {
    public static final int SLOT_TORSO = 0, SLOT_LEFT = 1, SLOT_RIGHT = 2, SLOT_AMMO = 3, SLOT_REPAIR = 4, INV_START = 5;
    private final Container table;
    private final ContainerLevelAccess access;

    public EngineeringTableMenu(int id, Inventory inv, FriendlyByteBuf buf) {
        this(id, inv, new SimpleContainer(3), ContainerLevelAccess.NULL);
    }

    public EngineeringTableMenu(int id, Inventory inv, Container table, ContainerLevelAccess access) {
        super(ModMenus.ENGINEERING_TABLE.get(), id);
        this.table = table;
        this.access = access;
        table.startOpen(inv.player);

        this.addSlot(new Slot(table, EngineeringTableBlockEntity.TORSO, 44, 38) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem() instanceof RobotTorsoItem;
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
        this.addSlot(new ArmSlot(table, HumanoidArm.LEFT, 18, 38));
        this.addSlot(new ArmSlot(table, HumanoidArm.RIGHT, 70, 38));
        this.addSlot(new Slot(table, EngineeringTableBlockEntity.AMMO, 18, 78) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return RobotArmType.forAmmo(stack.getItem()) != null;
            }
        });
        this.addSlot(new Slot(table, EngineeringTableBlockEntity.REPAIR, 70, 78) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(Items.IRON_INGOT);
            }
        });
        for (int r = 0; r < 3; r++) for (int c = 0; c < 9; c++) this.addSlot(new Slot(inv, c + r * 9 + 9, 8 + c * 18, 118 + r * 18));
        for (int c = 0; c < 9; c++) this.addSlot(new Slot(inv, c, 8 + c * 18, 176));
    }

    /** Hueco de brazo: lee y escribe directamente dentro del torso. */
    static class ArmSlot extends Slot {
        private final Container table;
        private final HumanoidArm side;

        ArmSlot(Container table, HumanoidArm side, int x, int y) {
            super(new SimpleContainer(1), 0, x, y);
            this.table = table;
            this.side = side;
        }

        private ItemStack torso() {
            return table.getItem(EngineeringTableBlockEntity.TORSO);
        }

        @Override
        public ItemStack getItem() {
            return RobotHelper.getArm(torso(), side);
        }

        @Override
        public boolean hasItem() {
            return !getItem().isEmpty();
        }

        @Override
        public void set(ItemStack stack) {
            ItemStack torso = torso();
            if (torso.getItem() instanceof RobotTorsoItem) {
                RobotHelper.setArm(torso, side, stack);
                table.setChanged();
            }
            this.setChanged();
        }

        @Override
        public void setByPlayer(ItemStack stack) {
            this.set(stack);
        }

        @Override
        public ItemStack remove(int amount) {
            ItemStack arm = getItem();
            if (arm.isEmpty() || amount <= 0) return ItemStack.EMPTY;
            set(ItemStack.EMPTY);
            return arm;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return stack.getItem() instanceof RobotArmItem && torso().getItem() instanceof RobotTorsoItem;
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public int getMaxStackSize(ItemStack stack) {
            return 1;
        }

        @Override
        public boolean isActive() {
            return true;
        }
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        boolean ok = EngineeringRecipes.build(player, id);
        if (ok) access.execute((level, pos) -> level.playSound(null, pos, ModSounds.ENGINEERING_BUILD.get(), SoundSource.BLOCKS, 1.0F, 1.0F));
        return ok;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index < INV_START) {
            if (index == SLOT_LEFT || index == SLOT_RIGHT) {
                if (!this.moveItemStackTo(copy, INV_START, this.slots.size(), true)) return ItemStack.EMPTY;
                slot.set(ItemStack.EMPTY);
                return ItemStack.EMPTY;
            }
            if (!this.moveItemStackTo(stack, INV_START, this.slots.size(), true)) return ItemStack.EMPTY;
        } else {
            if (stack.getItem() instanceof RobotTorsoItem) {
                if (!this.moveItemStackTo(stack, SLOT_TORSO, SLOT_TORSO + 1, false)) return ItemStack.EMPTY;
            } else if (stack.getItem() instanceof RobotArmItem) {
                Slot target = !this.slots.get(SLOT_LEFT).hasItem() && this.slots.get(SLOT_LEFT).mayPlace(stack) ? this.slots.get(SLOT_LEFT)
                        : !this.slots.get(SLOT_RIGHT).hasItem() && this.slots.get(SLOT_RIGHT).mayPlace(stack) ? this.slots.get(SLOT_RIGHT) : null;
                if (target == null) return ItemStack.EMPTY;
                target.set(stack.split(1));
            } else if (RobotArmType.forAmmo(stack.getItem()) != null) {
                if (!this.moveItemStackTo(stack, SLOT_AMMO, SLOT_AMMO + 1, false)) return ItemStack.EMPTY;
            } else if (stack.is(Items.IRON_INGOT)) {
                if (!this.moveItemStackTo(stack, SLOT_REPAIR, SLOT_REPAIR + 1, false)) return ItemStack.EMPTY;
            } else {
                return ItemStack.EMPTY;
            }
        }
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, ModBlocks.ENGINEERING_TABLE.get());
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        table.stopOpen(player);
    }

    public Container getTable() {
        return table;
    }
}
