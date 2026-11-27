package com.xpeak.enderite.limbo;

import com.xpeak.enderite.EnderiteConfig;
import com.xpeak.enderite.EnderiteMod;
import com.xpeak.enderite.entity.CrawlerEntity;
import com.xpeak.enderite.network.ModNetwork;
import com.xpeak.enderite.nightmare.NightmareData;
import com.xpeak.enderite.registry.ModEntities;
import com.xpeak.enderite.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;

/** Toda la logica del Limbo en el servidor: entrar al dormir, sobrevivir hasta el amanecer, morir, volver. */
@Mod.EventBusSubscriber(modid = EnderiteMod.MODID)
public final class LimboManager {
    private static final String TAG = "enderite_limbo";

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static final Set<UUID> ROLLED_THIS_SLEEP = new HashSet<>();
    private static final Set<UUID> FORCE_NEXT_SLEEP = new HashSet<>();
    private static final Set<UUID> DIED_IN_LIMBO = new HashSet<>();
    private static final List<Runnable> QUEUE = new ArrayList<>();

    public static final class Session {
        BlockPos bed;
        boolean hasBed;
        long entryGameTime;
        boolean night;
        int phase = Limbo.PHASE_LOADING;
        int ticks;
        WorldCopier copier;
        int crawlerTimer = -1;
        UUID crawlerId;
        Vec3 standPos;

        public int phase() {
            return phase;
        }

        public WorldCopier copier() {
            return copier;
        }

        public UUID crawlerId() {
            return crawlerId;
        }
    }

    // ------------------------------------------------------------------ API publica

    public static Session getSession(ServerPlayer player) {
        return SESSIONS.get(player.getUUID());
    }

    public static boolean isInLimbo(ServerPlayer player) {
        return Limbo.isLimbo(player.level());
    }

    /** La proxima vez que este jugador duerma entrara al Limbo seguro (comando /limbo forzar). */
    public static void forceNextSleep(ServerPlayer player) {
        FORCE_NEXT_SLEEP.add(player.getUUID());
    }

    /** Hace aparecer al Crawler ya (para pruebas). */
    public static void spawnCrawlerNow(ServerPlayer player, double minDist, double maxDist) {
        Session s = SESSIONS.get(player.getUUID());
        if (s != null && isInLimbo(player)) {
            spawnCrawler(player, s, minDist, maxDist);
        }
    }

    /**
     * Mete al jugador en el Limbo. Se copia el mundo alrededor de {@code bed} y aparece de pie sobre la cama.
     */
    public static boolean enterLimbo(ServerPlayer player, BlockPos bed, boolean onBed) {
        MinecraftServer server = player.server;
        ServerLevel limbo = server.getLevel(Limbo.LIMBO);
        if (limbo == null) {
            EnderiteMod.LOGGER.error("No existe la dimension del Limbo (enderite:limbo)");
            return false;
        }
        if (isInLimbo(player) || SESSIONS.containsKey(player.getUUID())) return false;

        ServerLevel overworld = server.overworld();
        Session s = new Session();
        s.bed = bed.immutable();
        s.hasBed = onBed;
        s.entryGameTime = overworld.getGameTime();
        s.night = (overworld.getDayTime() % 24000L) >= 12000L;
        s.standPos = onBed ? Vec3.atBottomCenterOf(bed).add(0.0D, 0.5625D, 0.0D) : Vec3.atBottomCenterOf(bed);

        if (player.isSleeping()) player.stopSleepInBed(true, true);
        if (player.isPassenger()) player.stopRiding();

        int radius = EnderiteConfig.COPY_RADIUS.get();
        s.copier = new WorldCopier(overworld, limbo, new ChunkPos(bed), radius);

        SESSIONS.put(player.getUUID(), s);
        saveSession(player, s);

        player.teleportTo(limbo, s.standPos.x, s.standPos.y, s.standPos.z, player.getYRot(), player.getXRot());
        player.setDeltaMovement(Vec3.ZERO);
        player.resetFallDistance();
        ModNetwork.sendLimboState(player, Limbo.PHASE_LOADING, 0);
        EnderiteMod.LOGGER.info("{} ha entrado al Limbo en {}", player.getName().getString(), bed);
        return true;
    }

    /** Saca al jugador del Limbo (ha sobrevivido hasta el amanecer). */
    public static void leaveLimbo(ServerPlayer player, boolean survived) {
        Session s = SESSIONS.remove(player.getUUID());
        if (s == null) s = loadSession(player);
        clearSession(player);
        if (s != null && s.copier != null) s.copier.release();
        removeCrawlersOf(player.server, player.getUUID());

        ServerLevel overworld = player.server.overworld();
        if (isInLimbo(player)) {
            BlockPos bed = s != null ? s.bed : null;
            BlockState state = bed != null ? overworld.getBlockState(bed) : null;
            if (bed != null && s.hasBed && state.getBlock() instanceof BedBlock) {
                Vec3 p = Vec3.atBottomCenterOf(bed).add(0.0D, 0.5625D, 0.0D);
                player.teleportTo(overworld, p.x, p.y, p.z, player.getYRot(), player.getXRot());
                if (overworld.isDay()) {
                    // Te despiertas en tu cama como si fuera una pesadilla
                    player.startSleeping(bed);
                }
            } else {
                BlockPos target = bed != null ? bed : overworld.getSharedSpawnPos();
                Vec3 p = Vec3.atBottomCenterOf(target);
                player.teleportTo(overworld, p.x, p.y, p.z, player.getYRot(), player.getXRot());
            }
        }
        player.removeEffect(MobEffects.DARKNESS);
        player.removeEffect(MobEffects.BLINDNESS);
        ModNetwork.sendLimboState(player, Limbo.PHASE_NONE, 0);
        if (survived) {
            title(player, Component.translatable("enderite.limbo.survived").withStyle(ChatFormatting.GOLD),
                    Component.translatable("enderite.limbo.survived.sub").withStyle(ChatFormatting.GRAY));
            overworld.playSound(null, player.blockPosition(), ModSounds.LIMBO_ESCAPE.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        }
        NightmareData.onLimboVisitEnded(player.server);
    }

    // ------------------------------------------------------------------ Eventos

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        UUID id = player.getUUID();
        if (!player.isAlive() || DIED_IN_LIMBO.contains(id)) return;

        if (!isInLimbo(player)) {
            Session stale = SESSIONS.remove(id);
            if (stale != null && stale.copier != null) stale.copier.release();
        }

        if (player.level().dimension() == Level.OVERWORLD) {
            if (player.isSleeping()) {
                if (player.getSleepTimer() >= 100 && ROLLED_THIS_SLEEP.add(id)) {
                    boolean forced = FORCE_NEXT_SLEEP.remove(id);
                    double chance = EnderiteConfig.LIMBO_CHANCE.get();
                    if (forced || player.getRandom().nextDouble() * 100.0D < chance) {
                        BlockPos bed = player.getSleepingPos().orElse(player.blockPosition());
                        QUEUE.add(() -> {
                            if (player.isAlive() && !player.hasDisconnected()) enterLimbo(player, bed, true);
                        });
                    }
                }
            } else {
                ROLLED_THIS_SLEEP.remove(id);
            }
            return;
        }

        if (!isInLimbo(player)) return;
        Session s = SESSIONS.get(id);
        if (s == null) {
            s = loadSession(player);
            if (s == null) s = newSessionHere(player);
            s.phase = Limbo.PHASE_ACTIVE;
            s.crawlerTimer = 100;
            SESSIONS.put(id, s);
            ModNetwork.sendLimboState(player, Limbo.PHASE_ACTIVE, remainingTicks(player.server, s));
        }
        tickSession(player, s);
    }

    private static void tickSession(ServerPlayer player, Session s) {
        s.ticks++;
        MinecraftServer server = player.server;

        if (s.phase == Limbo.PHASE_LOADING) {
            boolean done = s.copier == null || s.copier.tick(10_000_000L);
            boolean inner = done || s.copier.isInnerDone(2);
            // Quieto sobre la cama mientras se copia el mundo
            if (player.position().distanceToSqr(s.standPos) > 0.04D) {
                player.connection.teleport(s.standPos.x, s.standPos.y, s.standPos.z, player.getYRot(), player.getXRot());
            }
            player.setDeltaMovement(Vec3.ZERO);
            player.resetFallDistance();
            if ((inner && s.ticks >= 60) || s.ticks >= 600) activate(player, s);
            return;
        }

        if (s.copier != null && s.copier.tick(4_000_000L)) {
            EnderiteMod.LOGGER.info("Copia del Limbo terminada: {} chunks, {} bloques cambiados", s.copier.copiedChunks, s.copier.changedBlocks);
            s.copier.release();
            s.copier = null;
        }

        // Reglas: no correr, no planear con elitros, no montar nada
        if (player.isSprinting()) player.setSprinting(false);
        if (player.isFallFlying()) player.stopFallFlying();
        if (player.isPassenger()) player.stopRiding();

        // Crawler
        if (s.crawlerTimer > 0) {
            if (--s.crawlerTimer == 0) spawnCrawler(player, s, 26.0D, 40.0D);
        } else if (s.crawlerId != null && s.ticks % 20 == 0) {
            Entity c = player.serverLevel().getEntity(s.crawlerId);
            if (c == null || !c.isAlive()) {
                s.crawlerId = null;
                s.crawlerTimer = 200;
            }
        } else if (s.crawlerId == null && s.crawlerTimer <= 0) {
            s.crawlerTimer = 200;
        }

        if (s.ticks % 20 == 0) {
            ModNetwork.sendLimboState(player, Limbo.PHASE_ACTIVE, remainingTicks(server, s));
        }

        if (isFree(server, s)) {
            leaveLimbo(player, true);
        }
    }

    private static void activate(ServerPlayer player, Session s) {
        s.phase = Limbo.PHASE_ACTIVE;
        player.connection.teleport(s.standPos.x, s.standPos.y, s.standPos.z, player.getYRot(), player.getXRot());
        s.crawlerTimer = Math.max(1, EnderiteConfig.CRAWLER_DELAY_SECONDS.get() * 20);
        ModNetwork.sendLimboState(player, Limbo.PHASE_ACTIVE, remainingTicks(player.server, s));
        title(player, Component.translatable("enderite.limbo.title").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD),
                Component.translatable("enderite.limbo.subtitle").withStyle(ChatFormatting.RED));
        player.serverLevel().playSound(null, player.blockPosition(), ModSounds.LIMBO_ENTER.get(), SoundSource.AMBIENT, 1.0F, 1.0F);
        player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 80, 0, false, false));
    }

    /** Sobrevives cuando se hace de dia del todo (las 6:00). Si entraste de dia (tormenta), aguantas 5 minutos. */
    private static boolean isFree(MinecraftServer server, Session s) {
        ServerLevel ow = server.overworld();
        long elapsed = ow.getGameTime() - s.entryGameTime;
        if (elapsed >= 14000L) return true;
        if (s.night) {
            long dt = ow.getDayTime() % 24000L;
            return dt < 12000L && elapsed > 40L;
        }
        return elapsed >= 6000L;
    }

    public static int remainingTicks(MinecraftServer server, Session s) {
        ServerLevel ow = server.overworld();
        long elapsed = ow.getGameTime() - s.entryGameTime;
        long fallback = Math.max(0L, 14000L - elapsed);
        long r;
        if (s.night) {
            long dt = ow.getDayTime() % 24000L;
            r = dt >= 12000L ? 24000L - dt : 0L;
        } else {
            r = Math.max(0L, 6000L - elapsed);
        }
        return (int) Math.min(r, fallback);
    }

    private static void spawnCrawler(ServerPlayer player, Session s, double minDist, double maxDist) {
        ServerLevel level = player.serverLevel();
        removeCrawlersOf(player.server, player.getUUID());
        CrawlerEntity crawler = ModEntities.CRAWLER.get().create(level);
        if (crawler == null) return;
        Vec3 pos = CrawlerEntity.findSpawnPosition(level, player, minDist, maxDist);
        crawler.moveTo(pos.x, pos.y, pos.z, level.random.nextFloat() * 360.0F, 0.0F);
        crawler.setOwner(player.getUUID());
        crawler.setTarget(player);
        crawler.setPersistenceRequired();
        level.addFreshEntity(crawler);
        s.crawlerId = crawler.getUUID();
        s.crawlerTimer = 0;
        level.playSound(null, crawler.getX(), crawler.getY(), crawler.getZ(), ModSounds.CRAWLER_SCREAM.get(), SoundSource.HOSTILE, 10.0F, 0.8F);
        player.displayClientMessage(Component.translatable("enderite.limbo.crawler_coming").withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC), true);
    }

    public static void removeCrawlersOf(MinecraftServer server, UUID owner) {
        ServerLevel limbo = server.getLevel(Limbo.LIMBO);
        if (limbo == null) return;
        List<Entity> toRemove = new ArrayList<>();
        for (Entity e : limbo.getAllEntities()) {
            if (e instanceof CrawlerEntity c && owner.equals(c.getOwnerId())) toRemove.add(e);
        }
        toRemove.forEach(Entity::discard);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!QUEUE.isEmpty()) {
            List<Runnable> copy = new ArrayList<>(QUEUE);
            QUEUE.clear();
            copy.forEach(Runnable::run);
        }
    }

    /** Invulnerable mientras se copia el mundo (por si apareces dentro de un bloque). */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onAttack(LivingAttackEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && isInLimbo(player)) {
            Session s = SESSIONS.get(player.getUUID());
            if (s != null && s.phase == Limbo.PHASE_LOADING) event.setCanceled(true);
        }
    }

    /** Si mueres en el Limbo pierdes TODO para siempre. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player) || !isInLimbo(player)) return;
        player.getInventory().clearContent();
        player.experienceLevel = 0;
        player.totalExperience = 0;
        player.experienceProgress = 0.0F;
        DIED_IN_LIMBO.add(player.getUUID());
        Session s = SESSIONS.remove(player.getUUID());
        if (s != null && s.copier != null) s.copier.release();
        clearSession(player);
        removeCrawlersOf(player.server, player.getUUID());
    }

    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && isInLimbo(player)) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onXpDrop(LivingExperienceDropEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && isInLimbo(player)) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Session stale = SESSIONS.remove(player.getUUID());
        if (stale != null && stale.copier != null) stale.copier.release();
        if (DIED_IN_LIMBO.remove(player.getUUID())) {
            clearSession(player);
            ModNetwork.sendLimboState(player, Limbo.PHASE_NONE, 0);
            title(player, Component.translatable("enderite.limbo.lost").withStyle(ChatFormatting.DARK_RED),
                    Component.translatable("enderite.limbo.lost.sub").withStyle(ChatFormatting.GRAY));
            NightmareData.onLimboVisitEnded(player.server);
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (isInLimbo(player)) {
            Session s = loadSession(player);
            if (s == null) s = newSessionHere(player);
            s.phase = Limbo.PHASE_ACTIVE;
            s.crawlerTimer = 200;
            SESSIONS.put(player.getUUID(), s);
            ModNetwork.sendLimboState(player, Limbo.PHASE_ACTIVE, remainingTicks(player.server, s));
        } else if (player.getPersistentData().contains(TAG)) {
            clearSession(player);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Session s = SESSIONS.remove(player.getUUID());
        if (s != null && s.copier != null) s.copier.release();
        ROLLED_THIS_SLEEP.remove(player.getUUID());
        if (player.server != null) removeCrawlersOf(player.server, player.getUUID());
    }

    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (event.getFrom() == Limbo.LIMBO && event.getTo() != Limbo.LIMBO) {
            // Salio por otro medio (comando /tp...): limpiar sin castigo
            Session s = SESSIONS.remove(player.getUUID());
            if (s != null) {
                if (s.copier != null) s.copier.release();
                clearSession(player);
                removeCrawlersOf(player.server, player.getUUID());
                ModNetwork.sendLimboState(player, Limbo.PHASE_NONE, 0);
            }
        } else if (event.getTo() == Limbo.LIMBO && !SESSIONS.containsKey(player.getUUID())) {
            Session s = newSessionHere(player);
            s.phase = Limbo.PHASE_ACTIVE;
            s.crawlerTimer = 200;
            SESSIONS.put(player.getUUID(), s);
            saveSession(player, s);
            ModNetwork.sendLimboState(player, Limbo.PHASE_ACTIVE, remainingTicks(player.server, s));
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        for (Session s : SESSIONS.values()) {
            if (s.copier != null) s.copier.release();
        }
        SESSIONS.clear();
        ROLLED_THIS_SLEEP.clear();
        QUEUE.clear();
    }

    // ------------------------------------------------------------------ Persistencia

    private static Session newSessionHere(ServerPlayer player) {
        Session s = new Session();
        ServerLevel ow = player.server.overworld();
        s.bed = player.getRespawnPosition() != null && player.getRespawnDimension() == Level.OVERWORLD
                ? player.getRespawnPosition() : ow.getSharedSpawnPos();
        s.hasBed = ow.getBlockState(s.bed).getBlock() instanceof BedBlock;
        s.entryGameTime = ow.getGameTime();
        s.night = (ow.getDayTime() % 24000L) >= 12000L;
        s.standPos = player.position();
        return s;
    }

    private static void saveSession(ServerPlayer player, Session s) {
        CompoundTag t = new CompoundTag();
        t.putInt("x", s.bed.getX());
        t.putInt("y", s.bed.getY());
        t.putInt("z", s.bed.getZ());
        t.putBoolean("bed", s.hasBed);
        t.putLong("entry", s.entryGameTime);
        t.putBoolean("night", s.night);
        player.getPersistentData().put(TAG, t);
    }

    private static Session loadSession(ServerPlayer player) {
        if (!player.getPersistentData().contains(TAG)) return null;
        CompoundTag t = player.getPersistentData().getCompound(TAG);
        Session s = new Session();
        s.bed = new BlockPos(t.getInt("x"), t.getInt("y"), t.getInt("z"));
        s.hasBed = t.getBoolean("bed");
        s.entryGameTime = t.getLong("entry");
        s.night = t.getBoolean("night");
        s.standPos = player.position();
        return s;
    }

    private static void clearSession(ServerPlayer player) {
        player.getPersistentData().remove(TAG);
    }

    private static void title(ServerPlayer player, Component title, Component subtitle) {
        player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 30));
        player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
        player.connection.send(new ClientboundSetTitleTextPacket(title));
    }

    private LimboManager() {}
}
