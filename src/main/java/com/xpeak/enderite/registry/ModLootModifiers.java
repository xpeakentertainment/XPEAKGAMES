package com.xpeak.enderite.registry;

import com.google.common.base.Suppliers;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.xpeak.enderite.EnderiteMod;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

public final class ModLootModifiers {
    public static final DeferredRegister<Codec<? extends IGlobalLootModifier>> SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, EnderiteMod.MODID);

    public static final RegistryObject<Codec<AddItemModifier>> ADD_ITEM = SERIALIZERS.register("add_item", AddItemModifier.CODEC);

    /** Anade un objeto al botin con cierta probabilidad (se usa para la plantilla de enderite en las ciudades del End). */
    public static class AddItemModifier extends LootModifier {
        public static final Supplier<Codec<AddItemModifier>> CODEC = Suppliers.memoize(() ->
                RecordCodecBuilder.create(inst -> codecStart(inst)
                        .and(ForgeRegistries.ITEMS.getCodec().fieldOf("item").forGetter(m -> m.item))
                        .and(Codec.FLOAT.fieldOf("chance").forGetter(m -> m.chance))
                        .and(Codec.INT.optionalFieldOf("count", 1).forGetter(m -> m.count))
                        .apply(inst, AddItemModifier::new)));

        private final Item item;
        private final float chance;
        private final int count;

        public AddItemModifier(LootItemCondition[] conditions, Item item, float chance, int count) {
            super(conditions);
            this.item = item;
            this.chance = chance;
            this.count = count;
        }

        @Override
        protected @NotNull ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
            if (context.getRandom().nextFloat() < chance) {
                generatedLoot.add(new ItemStack(item, count));
            }
            return generatedLoot;
        }

        @Override
        public Codec<? extends IGlobalLootModifier> codec() {
            return CODEC.get();
        }
    }

    private ModLootModifiers() {}
}
