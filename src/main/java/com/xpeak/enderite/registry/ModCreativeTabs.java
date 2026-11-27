package com.xpeak.enderite.registry;

import com.xpeak.enderite.EnderiteMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, EnderiteMod.MODID);

    public static final RegistryObject<CreativeModeTab> MAIN = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.enderite"))
            .icon(() -> new ItemStack(ModItems.ENDERITE_INGOT.get()))
            .displayItems((params, out) -> {
                out.accept(ModItems.ENDERITE_ORE.get());
                out.accept(ModItems.ENDERITE_SCRAP.get());
                out.accept(ModItems.ENDERITE_INGOT.get());
                out.accept(ModItems.ENDERITE_UPGRADE_SMITHING_TEMPLATE.get());
                out.accept(ModItems.ENDERITE_BLOCK.get());
                out.accept(ModItems.ENDERITE_BRICKS.get());
                out.accept(ModItems.CHISELED_ENDERITE_BRICKS.get());
                out.accept(ModItems.ENDERITE_BRICK_STAIRS.get());
                out.accept(ModItems.ENDERITE_BRICK_SLAB.get());
                out.accept(ModItems.ENDERITE_SWORD.get());
                out.accept(ModItems.ENDERITE_PICKAXE.get());
                out.accept(ModItems.ENDERITE_AXE.get());
                out.accept(ModItems.ENDERITE_SHOVEL.get());
                out.accept(ModItems.ENDERITE_HOE.get());
                out.accept(ModItems.ENDERITE_HELMET.get());
                out.accept(ModItems.ENDERITE_CHESTPLATE.get());
                out.accept(ModItems.ENDERITE_LEGGINGS.get());
                out.accept(ModItems.ENDERITE_BOOTS.get());
                out.accept(ModItems.CRAWLER_SPAWN_EGG.get());
            })
            .build());

    public static void addToVanillaTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            event.accept(ModItems.CRAWLER_SPAWN_EGG);
        }
    }

    private ModCreativeTabs() {}
}
