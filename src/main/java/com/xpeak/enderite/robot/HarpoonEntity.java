package com.xpeak.enderite.robot;

import com.xpeak.enderite.registry.ModEntities;
import com.xpeak.enderite.registry.ModSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Punta del brazo arpon. Vuela hasta 30 bloques; si se clava en un bloque o en una criatura (a la que hace un poco de dano),
 * tira de ti hacia alli. El tiron lo hace el cliente del duenyo (el movimiento del jugador es suyo); el servidor decide cuando acaba.
 */
public class HarpoonEntity extends Projectile {
    public static final double RANGE = 30.0D;
    public static final double SPEED = 3.0D;
    public static final byte FLYING = 0, STUCK = 1, ATTACHED = 2;

    private static final EntityDataAccessor<Byte> STATE = SynchedEntityData.defineId(HarpoonEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> TARGET = SynchedEntityData.defineId(HarpoonEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> LEFT = SynchedEntityData.defineId(HarpoonEntity.class, EntityDataSerializers.BOOLEAN);

    @Nullable
    private Vec3 start;
    private float damage = 4.0F;
    private int pullTicks;
    private boolean fromSave;

    public HarpoonEntity(EntityType<? extends HarpoonEntity> type, Level level) {
        super(type, level);
        this.noCulling = true;
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(STATE, FLYING);
        this.entityData.define(TARGET, -1);
        this.entityData.define(LEFT, false);
    }

    /** Dispara el arpon desde el brazo hacia donde miras. */
    public static HarpoonEntity fire(ServerPlayer player, HumanoidArm side, float mult) {
        HarpoonEntity h = new HarpoonEntity(ModEntities.HARPOON.get(), player.level());
        h.setOwner(player);
        h.entityData.set(LEFT, side == HumanoidArm.LEFT);
        Vec3 origin = RobotServer.armOrigin(player, side);
        Vec3 eye = player.getEyePosition();
        Vec3 aim = eye.add(player.getLookAngle().scale(RANGE));
        BlockHitResult eyeHit = player.level().clip(new ClipContext(eye, aim, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 target = eyeHit.getType() == HitResult.Type.MISS ? aim : eyeHit.getLocation();
        Vec3 dir = target.subtract(origin);
        if (dir.lengthSqr() < 1.0E-4) dir = player.getLookAngle();
        dir = dir.normalize();
        h.setPos(origin.x, origin.y, origin.z);
        h.start = origin;
        h.damage = 4.0F * mult;
        h.setDeltaMovement(dir.scale(SPEED));
        h.faceMotion(dir);
        h.yRotO = h.getYRot();
        h.xRotO = h.getXRot();
        player.level().addFreshEntity(h);
        player.level().playSound(null, origin.x, origin.y, origin.z, ModSounds.HARPOON_FIRE.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        return h;
    }

    @Nullable
    public static HarpoonEntity find(Player owner, HumanoidArm side) {
        List<HarpoonEntity> list = owner.level().getEntitiesOfClass(HarpoonEntity.class, owner.getBoundingBox().inflate(RANGE + 16.0D),
                h -> h.getOwner() == owner && h.isLeft() == (side == HumanoidArm.LEFT) && !h.isRemoved());
        return list.isEmpty() ? null : list.get(0);
    }

    public boolean isLeft() {
        return this.entityData.get(LEFT);
    }

    public byte state() {
        return this.entityData.get(STATE);
    }

    public boolean isPulling() {
        return state() != FLYING;
    }

    /** Punto hacia el que tira el arpon. */
    public Vec3 anchor() {
        return this.position();
    }

    /** Velocidad con la que el arpon arrastra a su duenyo hacia el punto clavado. */
    public static Vec3 pullVelocity(Entity p, Vec3 anchor) {
        Vec3 center = p.position().add(0.0D, p.getBbHeight() * 0.5D, 0.0D);
        Vec3 to = anchor.subtract(center);
        double d = to.length();
        if (d < 1.0E-3) return Vec3.ZERO;
        double speed = Math.min(1.45D, 0.55D + d * 0.08D);
        return to.scale(speed / d);
    }

    private void faceMotion(Vec3 v) {
        double h = v.horizontalDistance();
        this.setYRot((float) (Mth.atan2(v.x, v.z) * (180.0D / Math.PI)));
        this.setXRot((float) (Mth.atan2(v.y, h) * (180.0D / Math.PI)));
    }

    private static boolean hasHarpoonArm(Player p, boolean left) {
        ItemStack arm = RobotHelper.getArm(RobotHelper.getTorso(p), left ? HumanoidArm.LEFT : HumanoidArm.RIGHT);
        return RobotHelper.armType(arm) == RobotArmType.HARPOON;
    }

    @Override
    protected boolean canHitEntity(Entity e) {
        return e != this.getOwner() && !(e instanceof RobotPortalEntity) && super.canHitEntity(e);
    }

    @Override
    public void tick() {
        super.tick();
        Entity owner = this.getOwner();
        if (!this.level().isClientSide) {
            if (fromSave || !(owner instanceof Player p) || !p.isAlive() || p.level() != this.level() || !hasHarpoonArm(p, isLeft())) {
                this.discard();
                return;
            }
        }
        byte st = state();
        if (st == FLYING) {
            Vec3 v = this.getDeltaMovement();
            if (!this.level().isClientSide) {
                HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
                if (hit.getType() != HitResult.Type.MISS) {
                    onHarpoonHit(hit);
                    return;
                }
            }
            this.setPos(this.getX() + v.x, this.getY() + v.y, this.getZ() + v.z);
            this.faceMotion(v);
            if (!this.level().isClientSide) {
                if (start == null) start = this.position();
                if (this.position().distanceTo(start) > RANGE) retract();
                else if (this.tickCount % 2 == 0) ((ServerLevel) this.level()).sendParticles(ParticleTypes.CRIT, getX(), getY(), getZ(), 1, 0, 0, 0, 0);
            }
            return;
        }

        this.setDeltaMovement(Vec3.ZERO);
        if (st == ATTACHED) {
            Entity t = this.level().getEntity(this.entityData.get(TARGET));
            if (t == null || !t.isAlive()) {
                if (!this.level().isClientSide) this.discard();
                return;
            }
            this.setPos(t.getX(), t.getY() + t.getBbHeight() * 0.6D, t.getZ());
        }
        if (!this.level().isClientSide && owner instanceof Player p) {
            pullTicks++;
            p.resetFallDistance();
            Vec3 anchor = anchor();
            if (p instanceof FakePlayer) {
                // jugadores falsos (pruebas): el tiron se hace aqui mismo
                Vec3 v = pullVelocity(p, anchor);
                p.setDeltaMovement(v);
                p.move(MoverType.SELF, v);
            }
            double d = p.position().add(0.0D, p.getBbHeight() * 0.5D, 0.0D).distanceTo(anchor);
            if (d < 2.2D || pullTicks > 60 || p.isShiftKeyDown()) this.discard();
        }
    }

    private void onHarpoonHit(HitResult hit) {
        if (hit instanceof EntityHitResult eh) {
            Entity t = eh.getEntity();
            int invul = t.invulnerableTime;
            t.invulnerableTime = 0;
            if (!t.hurt(this.damageSources().thrown(this, this.getOwner()), damage)) t.invulnerableTime = invul;
            this.entityData.set(TARGET, t.getId());
            this.entityData.set(STATE, ATTACHED);
            this.setPos(t.getX(), t.getY() + t.getBbHeight() * 0.6D, t.getZ());
        } else if (hit instanceof BlockHitResult bh) {
            Vec3 loc = bh.getLocation();
            this.setPos(loc.x, loc.y, loc.z);
            this.entityData.set(STATE, STUCK);
        }
        this.setDeltaMovement(Vec3.ZERO);
        this.level().playSound(null, getX(), getY(), getZ(), ModSounds.HARPOON_HIT.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        ((ServerLevel) this.level()).sendParticles(ParticleTypes.CRIT, getX(), getY(), getZ(), 8, 0.15D, 0.15D, 0.15D, 0.2D);
    }

    /** Recoge el arpon (se acabo el alcance, o has vuelto a hacer clic). */
    public void retract() {
        this.level().playSound(null, getX(), getY(), getZ(), ModSounds.HARPOON_FIRE.get(), SoundSource.PLAYERS, 0.5F, 1.6F);
        this.discard();
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double d) {
        return d < 128.0D * 128.0D;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        fromSave = true; // un arpon guardado en el mundo no sirve: se recoge al cargar
    }
}
