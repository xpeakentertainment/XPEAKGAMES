package com.xpeak.enderite.entity;

import com.xpeak.enderite.EnderiteMod;
import com.xpeak.enderite.limbo.Limbo;
import com.xpeak.enderite.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.navigation.WallClimberNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.ForgeEventFactory;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.UUID;

/**
 * El Crawler: parecido a un enderman, pero sin ojos y con una boca enorme y ensangrentada.
 * Escala paredes, rompe puertas y rompe bloques (tarda mas cuanto mas duro es el bloque).
 * Es lento si lo estas mirando y rapido si le das la espalda.
 */
public class CrawlerEntity extends Monster {
    public static final ResourceKey<DamageType> CRAWLER_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE,
            new ResourceLocation(EnderiteMod.MODID, "crawler"));

    private static final EntityDataAccessor<Byte> DATA_FLAGS = SynchedEntityData.defineId(CrawlerEntity.class, EntityDataSerializers.BYTE);
    private static final int FLAG_CLIMBING = 1;
    private static final int FLAG_DIGGING = 2;
    private static final int FLAG_WATCHED = 4;

    private static final UUID SPEED_MOD_ID = UUID.fromString("0b7a8f2e-6d62-4d6e-8a8c-2c2f7e3e9a11");

    @Nullable
    private UUID ownerId;
    @Nullable
    private BlockPos digPos;
    private int digProgress;
    private int digTotal;
    private int stuckTicks;
    private int noProgressTicks;
    private Vec3 lastCheckPos = Vec3.ZERO;
    private double bestDistance = Double.MAX_VALUE;
    private double digBestDistance = Double.MAX_VALUE;
    private int digNoProgressTicks;
    private int ownerMissingTicks;
    private boolean sawTargetLastTick;
    private int seeCooldown;

    public CrawlerEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 0;
        this.setMaxUpStep(1.1F);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 300.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.ATTACK_DAMAGE, 8.0D)
                .add(Attributes.FOLLOW_RANGE, 256.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
                .add(Attributes.ARMOR, 10.0D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_FLAGS, (byte) 0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new CrawlerBreakDoorGoal(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0D, true));
        this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 64.0F));
        this.targetSelector.addGoal(1, new OwnerTargetGoal(this));
        this.targetSelector.addGoal(2, new net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal<>(this, Player.class, false));
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        WallClimberNavigation nav = new WallClimberNavigation(this, level);
        nav.setCanOpenDoors(true);
        nav.setCanPassDoors(true);
        return nav;
    }

    // ------------------------------------------------------------------ Dueno (el jugador al que persigue)

    public void setOwner(UUID id) {
        this.ownerId = id;
    }

    @Nullable
    public UUID getOwnerId() {
        return ownerId;
    }

    @Nullable
    public Player getOwnerPlayer() {
        return ownerId == null ? null : this.level().getPlayerByUUID(ownerId);
    }

    // ------------------------------------------------------------------ Flags sincronizados

    private boolean getFlag(int f) {
        return (this.entityData.get(DATA_FLAGS) & f) != 0;
    }

    private void setFlag(int f, boolean v) {
        byte b = this.entityData.get(DATA_FLAGS);
        this.entityData.set(DATA_FLAGS, (byte) (v ? (b | f) : (b & ~f)));
    }

    public boolean isClimbing() {
        return getFlag(FLAG_CLIMBING);
    }

    public boolean isDigging() {
        return getFlag(FLAG_DIGGING);
    }

    public boolean isWatched() {
        return getFlag(FLAG_WATCHED);
    }

    @Override
    public boolean onClimbable() {
        return this.isClimbing();
    }

    // ------------------------------------------------------------------ Tick

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide) {
            this.setFlag(FLAG_CLIMBING, this.horizontalCollision);
            // Escalar suena igual que los pasos
            if (this.isClimbing() && this.getDeltaMovement().y > 0.01D && this.tickCount % 7 == 0) {
                this.playSound(ModSounds.CRAWLER_STEP.get(), 0.9F, 0.85F + this.random.nextFloat() * 0.3F);
            }
        }
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        ServerLevel level = (ServerLevel) this.level();
        boolean inLimbo = Limbo.isLimbo(level);

        if (inLimbo) {
            // Sin su presa en el Limbo, desaparece
            Player owner = getOwnerPlayer();
            if (ownerId == null || owner == null || !owner.isAlive()) {
                if (++ownerMissingTicks > 40) {
                    this.discard();
                    return;
                }
            } else {
                ownerMissingTicks = 0;
            }
        }

        LivingEntity target = this.getTarget();
        if (inLimbo) tickSeeSound(level, target);
        updateSpeed(target);
        tickDigging(level, target, inLimbo);
        if (inLimbo && target instanceof ServerPlayer player) tickRelocation(level, player);
    }

    /** "I see you": suena cuando te ve en el Limbo (y otra vez si te pierde de vista y te vuelve a encontrar). */
    private void tickSeeSound(ServerLevel level, @Nullable LivingEntity target) {
        if (seeCooldown > 0) seeCooldown--;
        boolean sees = target instanceof Player && target.isAlive() && this.distanceToSqr(target) < 56.0D * 56.0D
                && this.hasLineOfSight(target);
        if (sees && !sawTargetLastTick && seeCooldown <= 0) {
            level.playSound(null, this.getX(), this.getEyeY(), this.getZ(), ModSounds.CRAWLER_SEE.get(), SoundSource.HOSTILE, 4.0F, 1.0F);
            seeCooldown = 20 * 25;
        }
        sawTargetLastTick = sees;
    }

    /** Lento si lo miras, rapido si no. */
    private void updateSpeed(@Nullable LivingEntity target) {
        boolean watched = target instanceof Player p && isLookedAtBy(p);
        setFlag(FLAG_WATCHED, watched);
        double mult;
        if (target == null) mult = 0.0D;
        else if (watched) mult = -0.20D;
        else mult = 0.20D;
        if (target != null && this.distanceToSqr(target) > 40.0D * 40.0D) mult += 0.35D;
        AttributeInstance speed = this.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) return;
        AttributeModifier current = speed.getModifier(SPEED_MOD_ID);
        if (current != null && Math.abs(current.getAmount() - mult) < 1.0E-4) return;
        if (current != null) speed.removeModifier(SPEED_MOD_ID);
        if (mult != 0.0D) {
            speed.addTransientModifier(new AttributeModifier(SPEED_MOD_ID, "crawler_mirada", mult, AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
    }

    public boolean isLookedAtBy(Player player) {
        Vec3 view = player.getViewVector(1.0F).normalize();
        Vec3 to = new Vec3(this.getX() - player.getX(), this.getY(0.6D) - player.getEyeY(), this.getZ() - player.getZ());
        double len = to.length();
        if (len < 1.0E-3) return true;
        to = to.scale(1.0D / len);
        return view.dot(to) > 0.80D && player.hasLineOfSight(this);
    }

    // ------------------------------------------------------------------ Romper bloques

    private void tickDigging(ServerLevel level, @Nullable LivingEntity target, boolean inLimbo) {
        if (target == null || !target.isAlive()) {
            resetDig(level);
            return;
        }
        boolean griefing = inLimbo || ForgeEventFactory.getMobGriefingEvent(level, this);
        if (!griefing) {
            resetDig(level);
            return;
        }

        if (this.tickCount % 10 == 0) {
            if (this.position().distanceToSqr(lastCheckPos) < 0.05D) stuckTicks += 10;
            else stuckTicks = Math.max(0, stuckTicks - 10);
            lastCheckPos = this.position();
        }

        if (digPos != null) {
            BlockState state = level.getBlockState(digPos);
            if (state.isAir() || !canBreak(level, digPos, state) || digPos.distToCenterSqr(this.position()) > 25.0D) {
                resetDig(level);
                return;
            }
            digProgress++;
            this.getLookControl().setLookAt(digPos.getX() + 0.5D, digPos.getY() + 0.5D, digPos.getZ() + 0.5D);
            this.getNavigation().stop();
            int stage = Math.min(9, (int) (digProgress * 10.0F / digTotal));
            level.destroyBlockProgress(this.getId(), digPos, stage);
            if (digProgress % 5 == 0) {
                this.swing(InteractionHand.MAIN_HAND);
                level.playSound(null, digPos, state.getSoundType(level, digPos, this).getHitSound(), SoundSource.HOSTILE, 1.0F, 0.6F);
            }
            if (digProgress >= digTotal) {
                level.destroyBlockProgress(this.getId(), digPos, -1);
                level.levelEvent(2001, digPos, Block.getId(state));
                level.destroyBlock(digPos, !inLimbo, this);
                digPos = null;
                setFlag(FLAG_DIGGING, false);
                stuckTicks = 0;
                noProgressTicks = 0;
                digNoProgressTicks = 0;
                digBestDistance = this.distanceTo(target);
            }
            return;
        }

        double dist = this.distanceTo(target);
        if (dist < 1.6D) return;
        // Si en 3 segundos no se ha acercado ni un bloque (da vueltas, sube y baja una pared...) y no te ve: abre camino
        if (dist < digBestDistance - 1.0D) {
            digBestDistance = dist;
            digNoProgressTicks = 0;
        } else {
            digNoProgressTicks++;
        }
        Path path = this.getNavigation().getPath();
        boolean cantReach = path == null || !path.canReach() || this.getNavigation().isDone();
        boolean noLos = !this.hasLineOfSight(target);
        boolean wantDig = stuckTicks >= 20 || (cantReach && noLos && stuckTicks >= 10)
                || (digNoProgressTicks >= 60 && noLos);
        if (wantDig && this.tickCount % 4 == 0) {
            BlockPos p = findBlockToBreak(level, target);
            if (p != null) {
                digPos = p;
                digProgress = 0;
                BlockState st = level.getBlockState(p);
                digTotal = breakTicks(level, p, st);
                setFlag(FLAG_DIGGING, true);
            }
        }
    }

    /** Ticks que tarda en romper un bloque: depende de su dureza (tierra ~0.8 s, piedra ~1.5 s, obsidiana ~35 s). */
    public static int breakTicks(Level level, BlockPos pos, BlockState state) {
        float hardness = state.getDestroySpeed(level, pos);
        return (int) Mth.clamp(8.0F + hardness * 14.0F, 8.0F, 900.0F);
    }

    public static boolean canBreak(Level level, BlockPos pos, BlockState state) {
        if (state.isAir() || !state.getFluidState().isEmpty() && state.getCollisionShape(level, pos).isEmpty()) return false;
        float hardness = state.getDestroySpeed(level, pos);
        if (hardness < 0.0F) return false;
        if (state.is(BlockTags.WITHER_IMMUNE) || state.is(BlockTags.DRAGON_IMMUNE)) return false;
        return !state.getCollisionShape(level, pos).isEmpty();
    }

    @Nullable
    private BlockPos findBlockToBreak(Level level, LivingEntity target) {
        double[] heights = {0.4D, 1.4D, 2.4D};
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (double h : heights) {
            Vec3 from = new Vec3(this.getX(), this.getY() + h, this.getZ());
            Vec3 to = new Vec3(target.getX(), target.getY() + Math.min(h, target.getBbHeight() * 0.8D), target.getZ());
            BlockHitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
            if (hit.getType() == HitResult.Type.BLOCK) {
                BlockPos p = hit.getBlockPos();
                double d = p.distToCenterSqr(this.position());
                if (d <= 9.0D && canBreak(level, p, level.getBlockState(p)) && d < bestD) {
                    best = p;
                    bestD = d;
                }
            }
        }
        if (best != null) return best;
        // Techo (si la presa esta arriba) o suelo (si esta abajo)
        BlockPos base = this.blockPosition();
        if (target.getY() > this.getY() + 1.5D) {
            BlockPos up = base.above(3);
            if (canBreak(level, up, level.getBlockState(up))) return up;
        } else if (target.getY() < this.getY() - 1.5D && this.onGround()) {
            BlockPos down = base.below();
            if (canBreak(level, down, level.getBlockState(down))) return down;
        }
        // Delante, en la direccion de la presa
        Vec3 dir = target.position().subtract(this.position()).multiply(1, 0, 1);
        if (dir.lengthSqr() > 1.0E-4) {
            dir = dir.normalize();
            for (int dy = 0; dy < 3; dy++) {
                BlockPos front = BlockPos.containing(this.getX() + dir.x * 0.9D, this.getY() + dy + 0.2D, this.getZ() + dir.z * 0.9D);
                if (canBreak(level, front, level.getBlockState(front))) return front;
            }
        }
        return null;
    }

    private void resetDig(Level level) {
        if (digPos != null) level.destroyBlockProgress(this.getId(), digPos, -1);
        digPos = null;
        digProgress = 0;
        setFlag(FLAG_DIGGING, false);
    }

    /** Si se queda muy lejos o atascado mucho tiempo, reaparece a tu espalda (sin que lo veas). */
    private void tickRelocation(ServerLevel level, ServerPlayer player) {
        double d = this.distanceToSqr(player);
        if (d < bestDistance - 4.0D) {
            bestDistance = d;
            noProgressTicks = 0;
        } else if (digPos == null) {
            noProgressTicks++;
        }
        boolean tooFar = d > 72.0D * 72.0D;
        if (tooFar || noProgressTicks > 20 * 30) {
            Vec3 p = findSpawnPosition(level, player, 14.0D, 22.0D);
            this.teleportTo(p.x, p.y, p.z);
            this.getNavigation().stop();
            noProgressTicks = 0;
            digNoProgressTicks = 0;
            digBestDistance = Double.MAX_VALUE;
            bestDistance = this.distanceToSqr(player);
            level.playSound(null, this.getX(), this.getY(), this.getZ(), ModSounds.CRAWLER_AMBIENT.get(), SoundSource.HOSTILE, 3.0F, 0.6F);
        }
    }

    /** Busca un sitio entre minDist y maxDist del jugador, a poder ser donde no lo este mirando. */
    public static Vec3 findSpawnPosition(ServerLevel level, Player player, double minDist, double maxDist) {
        var random = level.random;
        Vec3 look = player.getViewVector(1.0F);
        boolean underground = player.getY() < level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                player.getBlockX(), player.getBlockZ()) - 6;
        Vec3 fallback = null;
        for (int attempt = 0; attempt < 40; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double dist = minDist + random.nextDouble() * (maxDist - minDist);
            double x = player.getX() + Math.cos(angle) * dist;
            double z = player.getZ() + Math.sin(angle) * dist;
            Vec3 dir = new Vec3(x - player.getX(), 0, z - player.getZ()).normalize();
            boolean behind = dir.dot(new Vec3(look.x, 0, look.z).normalize()) < 0.2D;
            BlockPos column = BlockPos.containing(x, player.getY(), z);
            BlockPos found = null;
            if (underground) {
                for (int dy = -6; dy <= 6 && found == null; dy++) {
                    BlockPos p = column.above(dy);
                    if (isStandable(level, p)) found = p;
                }
            } else {
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ());
                BlockPos p = new BlockPos(column.getX(), y, column.getZ());
                if (isStandable(level, p)) found = p;
            }
            if (found != null) {
                Vec3 v = Vec3.atBottomCenterOf(found);
                if (behind) return v;
                if (fallback == null) fallback = v;
            }
        }
        if (fallback != null) return fallback;
        // Si no hay sitio, aparece enterrado detras de ti y tendra que excavar
        Vec3 back = new Vec3(-look.x, 0, -look.z).normalize().scale(minDist);
        return player.position().add(back);
    }

    private static boolean isStandable(Level level, BlockPos feet) {
        if (!level.hasChunkAt(feet)) return false;
        BlockState below = level.getBlockState(feet.below());
        if (!below.isFaceSturdy(level, feet.below(), net.minecraft.core.Direction.UP)) return false;
        for (int i = 0; i < 3; i++) {
            BlockPos p = feet.above(i);
            if (!level.getBlockState(p).getCollisionShape(level, p).isEmpty() || !level.getFluidState(p).isEmpty()) return false;
        }
        return true;
    }

    // ------------------------------------------------------------------ Combate

    @Override
    public boolean doHurtTarget(Entity target) {
        float damage = (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE);
        DamageSource source = new DamageSource(this.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(CRAWLER_DAMAGE), this);
        boolean hit = target.hurt(source, damage);
        if (hit) {
            this.swing(InteractionHand.MAIN_HAND);
            this.playSound(ModSounds.CRAWLER_ATTACK.get(), 2.0F, 0.8F + this.random.nextFloat() * 0.2F);
            if (target instanceof LivingEntity living) {
                living.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0), this);
                living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 1), this);
                living.knockback(0.6D, Mth.sin(this.getYRot() * ((float) Math.PI / 180F)), -Mth.cos(this.getYRot() * ((float) Math.PI / 180F)));
            }
            this.doEnchantDamageEffects(this, target);
        }
        return hit;
    }

    /** En el Limbo es invencible. Fuera (si lo sacas con un huevo) se le puede matar, pero aguanta mucho. */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (Limbo.isLimbo(this.level()) && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return false;
        if (source.is(DamageTypeTags.IS_DROWNING) || source.is(DamageTypeTags.IS_FALL)) return false;
        return super.hurt(source, amount);
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public boolean canBreatheUnderwater() {
        return true;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    protected boolean shouldDespawnInPeaceful() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    // ------------------------------------------------------------------ Sonidos

    @Override
    protected SoundEvent getAmbientSound() {
        return ModSounds.CRAWLER_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.CRAWLER_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.CRAWLER_DEATH.get();
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        this.playSound(ModSounds.CRAWLER_STEP.get(), 0.9F, 0.7F + this.random.nextFloat() * 0.2F);
    }

    @Override
    public int getAmbientSoundInterval() {
        return 140;
    }

    @Override
    protected float getSoundVolume() {
        return 2.0F;
    }

    // ------------------------------------------------------------------ Guardado

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (ownerId != null) tag.putUUID("Owner", ownerId);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("Owner")) ownerId = tag.getUUID("Owner");
    }

    // ------------------------------------------------------------------ Objetivos de IA

    /** Siempre persigue a su presa (el jugador que entro al Limbo). */
    static class OwnerTargetGoal extends TargetGoal {
        private final CrawlerEntity crawler;

        OwnerTargetGoal(CrawlerEntity crawler) {
            super(crawler, false, false);
            this.crawler = crawler;
            this.setFlags(EnumSet.of(Goal.Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            Player owner = crawler.getOwnerPlayer();
            return owner != null && owner.isAlive() && !owner.isSpectator() && !owner.isCreative()
                    && owner.level() == crawler.level();
        }

        @Override
        public void start() {
            crawler.setTarget(crawler.getOwnerPlayer());
            super.start();
        }

        @Override
        public boolean canContinueToUse() {
            return canUse() && crawler.getTarget() == crawler.getOwnerPlayer();
        }

        @Override
        protected double getFollowDistance() {
            return 512.0D;
        }
    }

    /** Rompe puertas (de madera rapido, de hierro tambien pero tarda mas). */
    static class CrawlerBreakDoorGoal extends BreakDoorGoal {
        CrawlerBreakDoorGoal(CrawlerEntity crawler) {
            super(crawler, 40, d -> true);
        }

        @Override
        public boolean canUse() {
            return this.mob.getTarget() != null && super.canUse();
        }
    }
}
