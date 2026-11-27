package com.xpeak.enderite.nightmare;

import com.xpeak.enderite.EnderiteMod;
import com.xpeak.enderite.limbo.Limbo;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.BreakDoorGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.food.FoodData;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.*;

/**
 * Dificultad de Pesadilla. Cada nivel (1-5) hace el juego mas dificil:
 * <ul>
 *   <li>Siempre: monstruos con +20% vida, +15% dano, +4% velocidad, +25% rango de vision y +1 armadura por nivel; +20% hambre por nivel.</li>
 *   <li>Nivel 1: los zombis rompen puertas en cualquier dificultad.</li>
 *   <li>Nivel 2: las aranas envenenan y algunos creepers aparecen cargados.</li>
 *   <li>Nivel 3: los monstruos te rastrean a traves de las paredes y los esqueletos disparan flechas de fuego.</li>
 *   <li>Nivel 4: aparecen mas monstruos.</li>
 *   <li>Nivel 5: los monstruos ya no se queman al sol y se regeneran.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = EnderiteMod.MODID)
public final class NightmareEvents {
    private static final UUID HEALTH_ID = UUID.fromString("5f1c1a4e-8d4b-4f63-9a30-1b6a4e7d0001");
    private static final UUID DAMAGE_ID = UUID.fromString("5f1c1a4e-8d4b-4f63-9a30-1b6a4e7d0002");
    private static final UUID SPEED_ID = UUID.fromString("5f1c1a4e-8d4b-4f63-9a30-1b6a4e7d0003");
    private static final UUID FOLLOW_ID = UUID.fromString("5f1c1a4e-8d4b-4f63-9a30-1b6a4e7d0004");
    private static final UUID ARMOR_ID = UUID.fromString("5f1c1a4e-8d4b-4f63-9a30-1b6a4e7d0005");
    private static final UUID KNOCKBACK_ID = UUID.fromString("5f1c1a4e-8d4b-4f63-9a30-1b6a4e7d0006");

    private static final Map<UUID, Float> LAST_EXHAUSTION = new HashMap<>();
    private static final List<Runnable> EXTRA_SPAWNS = new ArrayList<>();

    private static int level() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        return server == null ? 0 : NightmareData.level(server);
    }

    private static boolean affects(Entity e) {
        return e instanceof Mob && e instanceof Enemy && !(e instanceof com.xpeak.enderite.entity.CrawlerEntity)
                && !(e instanceof EnderMan) && !(e instanceof ZombifiedPiglin)
                && e.getType().getCategory() == MobCategory.MONSTER;
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || Limbo.isLimbo(event.getLevel())) return;
        Entity entity = event.getEntity();
        int lvl = level();

        if (entity instanceof AbstractArrow arrow && lvl >= 3 && arrow.getOwner() instanceof AbstractSkeleton) {
            arrow.setSecondsOnFire(100);
            return;
        }
        if (!affects(entity)) return;
        Mob mob = (Mob) entity;

        applyModifier(mob, Attributes.MAX_HEALTH, HEALTH_ID, "pesadilla_vida", 0.20D * lvl, AttributeModifier.Operation.MULTIPLY_BASE);
        applyModifier(mob, Attributes.ATTACK_DAMAGE, DAMAGE_ID, "pesadilla_dano", 0.15D * lvl, AttributeModifier.Operation.MULTIPLY_BASE);
        applyModifier(mob, Attributes.MOVEMENT_SPEED, SPEED_ID, "pesadilla_velocidad", 0.04D * lvl, AttributeModifier.Operation.MULTIPLY_BASE);
        applyModifier(mob, Attributes.FOLLOW_RANGE, FOLLOW_ID, "pesadilla_vision", 0.25D * lvl, AttributeModifier.Operation.MULTIPLY_BASE);
        applyModifier(mob, Attributes.ARMOR, ARMOR_ID, "pesadilla_armadura", 1.0D * lvl, AttributeModifier.Operation.ADDITION);
        applyModifier(mob, Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_ID, "pesadilla_retroceso", 0.05D * lvl, AttributeModifier.Operation.ADDITION);
        if (lvl > 0 && !event.loadedFromDisk()) mob.setHealth(mob.getMaxHealth());
        if (lvl <= 0) return;

        // Nivel 1: los zombis rompen puertas siempre
        if (mob instanceof Zombie zombie && !(mob instanceof Drowned)
                && net.minecraft.world.entity.ai.util.GoalUtils.hasGroundPathNavigation(zombie)) {
            zombie.setCanBreakDoors(true);
            zombie.goalSelector.addGoal(1, new BreakDoorGoal(zombie, d -> true));
        }
        // Nivel 3: te rastrean a traves de las paredes
        if (lvl >= 3 && mob instanceof PathfinderMob pm && (mob instanceof Zombie || mob instanceof AbstractSkeleton
                || mob instanceof Spider || mob instanceof Creeper)) {
            pm.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(pm, Player.class, 10, false, false, null));
        }
        // Nivel 5: no se queman al sol y se regeneran
        if (lvl >= 5) {
            mob.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, MobEffectInstance.INFINITE_DURATION, 0, true, false));
            mob.addEffect(new MobEffectInstance(MobEffects.REGENERATION, MobEffectInstance.INFINITE_DURATION, 0, true, false));
        }
    }

    private static void applyModifier(Mob mob, Attribute attribute, UUID id, String name, double amount, AttributeModifier.Operation op) {
        AttributeInstance inst = mob.getAttribute(attribute);
        if (inst == null) return;
        AttributeModifier current = inst.getModifier(id);
        if (current != null) {
            if (Math.abs(current.getAmount() - amount) < 1.0E-6) return;
            inst.removeModifier(id);
        }
        if (amount != 0.0D) inst.addPermanentModifier(new AttributeModifier(id, name, amount, op));
    }

    @SubscribeEvent
    public static void onFinalizeSpawn(MobSpawnEvent.FinalizeSpawn event) {
        int lvl = level();
        if (lvl <= 0 || !(event.getLevel() instanceof ServerLevel level) || Limbo.isLimbo(level)) return;
        Mob mob = event.getEntity();
        if (!affects(mob)) return;

        // Nivel 2: creepers cargados
        if (lvl >= 2 && mob instanceof Creeper creeper && mob.getRandom().nextFloat() < 0.10F * (lvl - 1)) {
            CompoundTag tag = new CompoundTag();
            creeper.addAdditionalSaveData(tag);
            tag.putBoolean("powered", true);
            creeper.readAdditionalSaveData(tag);
        }
        // Nivel 4: aparecen mas monstruos
        MobSpawnType type = event.getSpawnType();
        if (lvl >= 4 && (type == MobSpawnType.NATURAL || type == MobSpawnType.CHUNK_GENERATION)
                && mob.getRandom().nextFloat() < 0.25F * (lvl - 3)) {
            EntityType<?> entityType = mob.getType();
            double x = event.getX(), y = event.getY(), z = event.getZ();
            EXTRA_SPAWNS.add(() -> {
                Entity extra = entityType.create(level);
                if (extra instanceof Mob m) {
                    m.moveTo(x + level.random.nextInt(3) - 1, y, z + level.random.nextInt(3) - 1, level.random.nextFloat() * 360.0F, 0.0F);
                    if (level.noCollision(m)) {
                        m.finalizeSpawn(level, level.getCurrentDifficultyAt(m.blockPosition()), MobSpawnType.REINFORCEMENT, null, null);
                        level.addFreshEntity(m);
                    }
                }
            });
        }
    }

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        int lvl = level();
        if (lvl < 2) return;
        if (event.getSource().getEntity() instanceof Spider && !(event.getSource().getEntity() instanceof CaveSpider)) {
            event.getEntity().addEffect(new MobEffectInstance(MobEffects.POISON, 100 + 20 * lvl, 0));
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || EXTRA_SPAWNS.isEmpty()) return;
        List<Runnable> copy = new ArrayList<>(EXTRA_SPAWNS);
        EXTRA_SPAWNS.clear();
        copy.forEach(Runnable::run);
    }

    /** Mas hambre: el cansancio que acumulas se multiplica por (1 + 0.2 * nivel). */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        int lvl = level();
        UUID id = player.getUUID();
        FoodData food = player.getFoodData();
        float current = food.getExhaustionLevel();
        Float last = LAST_EXHAUSTION.get(id);
        if (lvl > 0 && last != null && !player.getAbilities().invulnerable) {
            float delta = current >= last ? current - last : current + 4.0F - last;
            if (delta > 0.0F && delta < 4.0F) {
                food.addExhaustion(delta * 0.20F * lvl);
            }
        }
        LAST_EXHAUSTION.put(id, food.getExhaustionLevel());
    }

    private NightmareEvents() {}
}
