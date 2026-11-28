package com.xpeak.enderite.robot;

import com.xpeak.enderite.registry.ModItems;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.function.Supplier;

/** Piezas que se fabrican en la Mesa de ingenieria y lo que cuestan (se coge del inventario). */
public final class EngineeringRecipes {
    public record Cost(Item item, int count) {}

    public record Part(Supplier<Item> result, List<Cost> costs) {}

    public static final List<Part> PARTS = List.of(
            new Part(ModItems.ROBOT_TORSO_BLUE, List.of(new Cost(Items.IRON_INGOT, 10), new Cost(Items.REDSTONE, 6),
                    new Cost(Items.DIAMOND, 1), new Cost(Items.LAPIS_LAZULI, 8))),
            new Part(ModItems.ROBOT_TORSO_RED, List.of(new Cost(Items.IRON_INGOT, 10), new Cost(Items.REDSTONE, 6),
                    new Cost(Items.DIAMOND, 1), new Cost(Items.BLAZE_POWDER, 4))),
            new Part(ModItems.ROBOT_TORSO_GREEN, List.of(new Cost(Items.IRON_INGOT, 10), new Cost(Items.REDSTONE, 6),
                    new Cost(Items.DIAMOND, 1), new Cost(Items.EMERALD, 4))),
            new Part(ModItems.ROBOT_ARM_CHAINSAW, List.of(new Cost(Items.IRON_INGOT, 6), new Cost(Items.REDSTONE, 3),
                    new Cost(Items.IRON_AXE, 1))),
            new Part(ModItems.ROBOT_ARM_ROCKET, List.of(new Cost(Items.IRON_INGOT, 6), new Cost(Items.REDSTONE, 3),
                    new Cost(Items.DISPENSER, 1), new Cost(Items.GUNPOWDER, 4))));

    public static int count(Inventory inv, Item item) {
        int n = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(item)) n += s.getCount();
        }
        return n;
    }

    public static boolean canAfford(Player player, Part part) {
        if (player.getAbilities().instabuild) return true;
        for (Cost c : part.costs()) if (count(player.getInventory(), c.item()) < c.count()) return false;
        return true;
    }

    public static boolean build(Player player, int index) {
        if (index < 0 || index >= PARTS.size()) return false;
        Part part = PARTS.get(index);
        if (!canAfford(player, part)) return false;
        if (!player.getAbilities().instabuild) {
            Inventory inv = player.getInventory();
            for (Cost c : part.costs()) {
                int left = c.count();
                for (int i = 0; i < inv.getContainerSize() && left > 0; i++) {
                    ItemStack s = inv.getItem(i);
                    if (s.is(c.item())) {
                        int take = Math.min(left, s.getCount());
                        s.shrink(take);
                        left -= take;
                    }
                }
            }
        }
        ItemStack out = new ItemStack(part.result().get());
        if (!player.getInventory().add(out)) player.drop(out, false);
        return true;
    }

    private EngineeringRecipes() {}
}
