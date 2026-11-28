package com.xpeak.enderite.robot;

import com.xpeak.enderite.registry.ModItems;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import javax.annotation.Nullable;
import java.util.function.Supplier;

/**
 * Tipos de brazo robot.
 * Cada brazo (menos la motosierra y el arpon) gasta su propia municion, que se carga en la Mesa de ingenieria:
 * {@code unitsPerItem} unidades por cada objeto de municion, hasta {@code maxAmmo}.
 */
public enum RobotArmType {
    CHAINSAW("motosierra", 500, null, 0, 0),
    ROCKET("lanzacohetes", 300, () -> Items.FIREWORK_ROCKET, 1, 96),
    HARPOON("arpon", 400, null, 0, 0),
    FLAMETHROWER("lanzallamas", 400, () -> ModItems.FUEL_CANISTER.get(), 100, 400),
    PORTAL("portales", 300, () -> ModItems.PORTAL_CELL.get(), 1, 32),
    EXO("exobrazo", 350, () -> ModItems.EXO_BATTERY.get(), 100, 600),
    ILLUSION("ilusiones", 300, () -> ModItems.ILLUSION_CARTRIDGE.get(), 1, 64),
    CATAPULT("catapulta", 400, () -> ModItems.CATAPULT_SPRING.get(), 1, 32);

    public final String id;
    public final int durability;
    @Nullable
    private final Supplier<Item> ammoItem;
    public final int unitsPerItem;
    public final int maxAmmo;

    RobotArmType(String id, int durability, @Nullable Supplier<Item> ammoItem, int unitsPerItem, int maxAmmo) {
        this.id = id;
        this.durability = durability;
        this.ammoItem = ammoItem;
        this.unitsPerItem = unitsPerItem;
        this.maxAmmo = maxAmmo;
    }

    public boolean usesAmmo() {
        return ammoItem != null;
    }

    @Nullable
    public Item ammoItem() {
        return ammoItem == null ? null : ammoItem.get();
    }

    /** Brazos que funcionan mientras mantienes el clic (los demas, un uso por clic). */
    public boolean continuous() {
        return this == CHAINSAW || this == FLAMETHROWER || this == EXO;
    }

    @Nullable
    public static RobotArmType forAmmo(Item item) {
        for (RobotArmType t : values()) if (t.ammoItem != null && t.ammoItem.get() == item) return t;
        return null;
    }
}
