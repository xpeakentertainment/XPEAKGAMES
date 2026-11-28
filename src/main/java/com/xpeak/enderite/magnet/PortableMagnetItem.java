package com.xpeak.enderite.magnet;

import com.xpeak.enderite.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;

/** Iman portatil: click derecho para encenderlo/apagarlo. Encendido atrae los objetos hacia ti (50 bloques). */
public class PortableMagnetItem extends Item {
    public PortableMagnetItem(Properties props) {
        super(props);
    }

    public static boolean isActive(ItemStack stack) {
        return stack.hasTag() && stack.getTag().getBoolean("Active");
    }

    public static void setActive(ItemStack stack, boolean active) {
        stack.getOrCreateTag().putBoolean("Active", active);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            boolean on = !isActive(stack);
            setActive(stack, on);
            level.playSound(null, player.blockPosition(), on ? ModSounds.MAGNET_ON.get() : ModSounds.MAGNET_OFF.get(),
                    SoundSource.PLAYERS, 0.8F, 1.0F);
            player.displayClientMessage(Component.translatable(on ? "item.enderite.portable_magnet.on" : "item.enderite.portable_magnet.off")
                    .withStyle(on ? ChatFormatting.AQUA : ChatFormatting.GRAY), true);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (level.isClientSide || !(entity instanceof Player player) || !isActive(stack) || player.isSpectator()) return;
        if (com.xpeak.enderite.limbo.Limbo.isLimbo(level)) return;
        ServerLevel sl = (ServerLevel) level;
        long t = level.getGameTime();
        // Si llevas varios imanes, solo trabaja el primero
        if (firstActive(player) != stack) return;
        Vec3 center = player.position().add(0.0D, 0.9D, 0.0D);
        MagnetLogic.pull(sl, center, player, null, t);
        MagnetLogic.ambientParticles(sl, player.position().add(0.0D, 0.1D, 0.0D), 0.7D, t);
    }

    private static ItemStack firstActive(Player player) {
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.getItem() instanceof PortableMagnetItem && isActive(s)) return s;
        }
        return ItemStack.EMPTY;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return isActive(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(isActive(stack) ? "item.enderite.portable_magnet.on" : "item.enderite.portable_magnet.off")
                .withStyle(isActive(stack) ? ChatFormatting.AQUA : ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.enderite.portable_magnet.desc").withStyle(ChatFormatting.DARK_GRAY));
    }
}
