package com.xpeak.enderite.limbo;

import com.xpeak.enderite.EnderiteMod;
import com.xpeak.enderite.entity.CrawlerEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerSleepInBedEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * Reglas del Limbo (se aplican en cliente y servidor): no puedes interactuar con nada, ni atacar,
 * ni romper ni colocar bloques, ni dormir. Tampoco existe ninguna entidad aparte de los jugadores y el Crawler.
 */
@Mod.EventBusSubscriber(modid = EnderiteMod.MODID)
public final class LimboRules {

    private static boolean restricted(Player player) {
        return player != null && Limbo.isLimbo(player.level()) && !player.isSpectator();
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (restricted(event.getEntity())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (restricted(event.getEntity())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (restricted(event.getEntity())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (restricted(event.getEntity())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (restricted(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        if (restricted(event.getEntity())) event.setNewSpeed(0.0F);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAttackEntity(AttackEntityEvent event) {
        if (restricted(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (restricted(event.getPlayer())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof Player p && restricted(p)) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onTrample(BlockEvent.FarmlandTrampleEvent event) {
        if (event.getLevel() instanceof ServerLevel l && Limbo.isLimbo(l)) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onSleep(PlayerSleepInBedEvent event) {
        if (Limbo.isLimbo(event.getEntity().level())) {
            event.setResult(Player.BedSleepingProblem.NOT_POSSIBLE_HERE);
        }
    }

    /** Tirar objetos los haria desaparecer (no existen entidades): se devuelven al inventario. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onToss(ItemTossEvent event) {
        Player player = event.getPlayer();
        if (restricted(player)) {
            ItemStack stack = event.getEntity().getItem().copy();
            event.setCanceled(true);
            if (!player.getInventory().add(stack)) {
                player.getInventory().placeItemBackInInventory(stack);
            }
            player.displayClientMessage(Component.translatable("enderite.limbo.no_interact"), true);
        }
    }

    /** Nada de lo que hagas en el Limbo hace dano (por si acaso llega por otra via). */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingAttack(LivingAttackEvent event) {
        Entity src = event.getSource().getEntity();
        if (src instanceof Player p && restricted(p) && !(event.getEntity() instanceof Player)) {
            event.setCanceled(true);
        }
    }

    /** En el Limbo no existe ninguna otra entidad. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !Limbo.isLimbo(event.getLevel())) return;
        Entity e = event.getEntity();
        if (!(e instanceof Player) && !(e instanceof CrawlerEntity)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level) || !Limbo.isLimbo(level)) return;
        if (level.getGameTime() % 40L != 0L) return;
        List<Entity> remove = new ArrayList<>();
        for (Entity e : level.getAllEntities()) {
            if (!(e instanceof Player) && !(e instanceof CrawlerEntity)) remove.add(e);
        }
        remove.forEach(Entity::discard);
    }

    private LimboRules() {}
}
