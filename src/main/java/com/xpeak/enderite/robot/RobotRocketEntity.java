package com.xpeak.enderite.robot;

import com.xpeak.enderite.registry.ModEntities;
import com.xpeak.enderite.registry.ModSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.ForgeEventFactory;

/** Cohete del brazo lanzacohetes: vuela recto y explota (mucho mas dano que un cohete de fuegos artificiales). */
public class RobotRocketEntity extends Projectile {
    public static final float DIRECT_DAMAGE = 14.0F;
    public static final float SPLASH_DAMAGE = 8.0F;
    private float damageMultiplier = 1.0F;

    public RobotRocketEntity(EntityType<? extends RobotRocketEntity> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
    }

    public static RobotRocketEntity shoot(Level level, LivingEntity owner, Vec3 from, Vec3 dir, float multiplier) {
        RobotRocketEntity r = new RobotRocketEntity(ModEntities.ROBOT_ROCKET.get(), level);
        r.setOwner(owner);
        r.setPos(from.x, from.y, from.z);
        r.damageMultiplier = multiplier;
        r.setDeltaMovement(dir.normalize().scale(1.6D));
        ProjectileUtil.rotateTowardsMovement(r, 1.0F);
        r.yRotO = r.getYRot();
        r.xRotO = r.getXRot();
        level.addFreshEntity(r);
        return r;
    }

    @Override
    protected void defineSynchedData() {
    }

    @Override
    public void tick() {
        super.tick();
        HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
        if (hit.getType() != HitResult.Type.MISS && !ForgeEventFactory.onProjectileImpact(this, hit)) {
            this.onHit(hit);
        }
        Vec3 v = this.getDeltaMovement();
        this.setPos(this.getX() + v.x, this.getY() + v.y, this.getZ() + v.z);
        ProjectileUtil.rotateTowardsMovement(this, 0.5F);
        if (this.level().isClientSide) {
            this.level().addParticle(ParticleTypes.FLAME, this.getX() - v.x * 0.5D, this.getY() - v.y * 0.5D, this.getZ() - v.z * 0.5D, 0, 0, 0);
            this.level().addParticle(ParticleTypes.LARGE_SMOKE, this.getX() - v.x, this.getY() - v.y, this.getZ() - v.z, 0, 0.01D, 0);
        } else if (this.tickCount > 80 || this.isInWater()) {
            explode();
        }
    }

    @Override
    protected boolean canHitEntity(Entity target) {
        return super.canHitEntity(target) && target != this.getOwner();
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        if (!this.level().isClientSide) {
            Entity target = result.getEntity();
            Entity owner = this.getOwner();
            target.hurt(this.damageSources().explosion(this, owner), DIRECT_DAMAGE * damageMultiplier);
        }
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (!this.level().isClientSide) explode();
    }

    private void explode() {
        if (!(this.level() instanceof ServerLevel level) || this.isRemoved()) return;
        Entity owner = this.getOwner();
        for (Entity e : level.getEntities(this, this.getBoundingBox().inflate(2.8D))) {
            if (e == owner || !(e instanceof LivingEntity living)) continue;
            double d = Math.sqrt(e.distanceToSqr(this));
            float dmg = (float) (SPLASH_DAMAGE * damageMultiplier * Math.max(0.2D, 1.0D - d / 3.5D));
            living.hurt(this.damageSources().explosion(this, owner), dmg);
            Vec3 push = e.position().subtract(this.position()).normalize().scale(0.6D);
            living.push(push.x, 0.25D, push.z);
        }
        level.sendParticles(ParticleTypes.EXPLOSION, this.getX(), this.getY(), this.getZ(), 2, 0.3D, 0.3D, 0.3D, 0.0D);
        level.sendParticles(ParticleTypes.FLAME, this.getX(), this.getY(), this.getZ(), 14, 0.4D, 0.4D, 0.4D, 0.08D);
        level.playSound(null, this.getX(), this.getY(), this.getZ(), ModSounds.ROBOT_ROCKET_EXPLODE.get(), SoundSource.PLAYERS, 1.6F, 1.0F);
        this.discard();
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 128.0D * 128.0D;
    }
}
