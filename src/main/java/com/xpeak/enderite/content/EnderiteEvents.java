package com.xpeak.enderite.content;

import com.xpeak.enderite.EnderiteMod;
import com.xpeak.enderite.registry.ModBlocks;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = EnderiteMod.MODID)
public final class EnderiteEvents {

    /** La mena de enderite no avanza NADA si no usas un pico de netherite (o mejor). */
    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        BlockState state = event.getState();
        if (state.is(ModBlocks.ENDERITE_ORE.get()) || state.is(ModBlocks.ENDERITE_BLOCK.get())) {
            Player player = event.getEntity();
            ItemStack tool = player.getMainHandItem();
            if (!tool.isCorrectToolForDrops(state)) {
                event.setNewSpeed(0.0F);
            }
        }
    }

    /**
     * El juego limita la armadura a 30 puntos, asi que la parte "x2" que no cabe se aplica
     * como reduccion extra: cada pieza de enderite quita un 10% mas del dano (40% con el set completo).
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onHurt(LivingHurtEvent event) {
        LivingEntity entity = event.getEntity();
        int pieces = 0;
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack stack = entity.getItemBySlot(slot);
            if (stack.getItem() instanceof ArmorItem armor && armor.getMaterial() == EnderiteMaterials.ENDERITE_ARMOR) {
                pieces++;
            }
        }
        if (pieces > 0 && !event.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_ARMOR)) {
            event.setAmount(event.getAmount() * (1.0F - 0.10F * pieces));
        }
    }

    private EnderiteEvents() {}
}
