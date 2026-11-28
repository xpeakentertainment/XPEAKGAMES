package com.xpeak.enderite.registry;

import com.xpeak.enderite.EnderiteMod;
import com.xpeak.enderite.robot.EngineeringTableMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, EnderiteMod.MODID);

    public static final RegistryObject<MenuType<EngineeringTableMenu>> ENGINEERING_TABLE = MENUS.register("engineering_table",
            () -> IForgeMenuType.create(EngineeringTableMenu::new));

    private ModMenus() {}
}
