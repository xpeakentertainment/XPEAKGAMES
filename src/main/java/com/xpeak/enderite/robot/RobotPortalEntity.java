package com.xpeak.enderite.robot;

import com.xpeak.enderite.entity.StuckToolEntity;
import com.xpeak.enderite.registry.ModBlocks;
import com.xpeak.enderite.registry.ModEntities;
import com.xpeak.enderite.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * Portal del brazo de portales: ocupa dos caras de bloque (1 de ancho x 2 de alto en paredes; en suelos y techos va
 * orientado hacia donde mirabas). Lo que entra por uno sale por su pareja conservando la velocidad.
 */
public class RobotPortalEntity extends Entity {
    public static final double RANGE = 64.0D;
    public static final int CYAN = 0x33E6FF, VIOLET = 0xBF4DFF;
    private static final String IGNORE = "enderite_portal_ignore";
    private static final String COOLDOWN = "enderite_portal_cd";

    private static final EntityDataAccessor<Byte> COLOR = SynchedEntityData.defineId(RobotPortalEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Direction> FACING = SynchedEntityData.defineId(RobotPortalEntity.class, EntityDataSerializers.DIRECTION);
    private static final EntityDataAccessor<Direction> UP = SynchedEntityData.defineId(RobotPortalEntity.class, EntityDataSerializers.DIRECTION);

    @Nullable
    private UUID owner;

    public RobotPortalEntity(EntityType<? extends RobotPortalEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(COLOR, (byte) 0);
        this.entityData.define(FACING, Direction.NORTH);
        this.entityData.define(UP, Direction.UP);
    }

    public Direction facing() {
        return this.entityData.get(FACING);
    }

    public Direction up() {
        return this.entityData.get(UP);
    }

    public int color() {
        return this.entityData.get(COLOR);
    }

    public static int rgb(int color) {
        return color == 0 ? CYAN : VIOLET;
    }

    @Nullable
    public UUID owner() {
        return owner;
    }

    public static Vec3 vec(Direction d) {
        return new Vec3(d.getStepX(), d.getStepY(), d.getStepZ());
    }

    private static Vec3 abs(Vec3 v) {
        return new Vec3(Math.abs(v.x), Math.abs(v.y), Math.abs(v.z));
    }

    @Override
    protected AABB makeBoundingBox() {
        Vec3 c = this.position();
        Vec3 n = vec(facing()), u = vec(up()), w = n.cross(u);
        Vec3 ext = abs(u).scale(1.0D).add(abs(w).scale(0.5D)).add(abs(n).scale(0.05D));
        return new AABB(c.subtract(ext), c.add(ext));
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (FACING.equals(key) || UP.equals(key)) this.setBoundingBox(this.makeBoundingBox());
    }

    /** Zona que, si la tocas, te mete en el portal. */
    public AABB triggerBox() {
        Vec3 c = this.position();
        Vec3 n = vec(facing()), u = vec(up()), w = n.cross(u);
        Vec3 a = c.subtract(n.scale(0.05D)).subtract(w.scale(0.45D)).subtract(u.scale(0.95D));
        Vec3 b = c.add(n.scale(0.45D)).add(w.scale(0.45D)).add(u.scale(0.95D));
        return new AABB(a, b);
    }

    // ------------------------------------------------------------------ colocar

    /** Dispara un portal hacia donde miras. Devuelve false si no hay una superficie valida (no gasta municion). */
    public static boolean shoot(ServerPlayer player, Vec3 from, int color) {
        ServerLevel level = player.serverLevel();
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(RANGE));
        BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, player));
        trail(level, from, hit.getType() == HitResult.Type.MISS ? end : hit.getLocation(), color);
        level.playSound(null, from.x, from.y, from.z, ModSounds.PORTAL_SHOOT.get(), SoundSource.PLAYERS, 1.0F, color == 0 ? 1.0F : 0.8F);
        if (hit.getType() != HitResult.Type.BLOCK || !level.getFluidState(hit.getBlockPos()).isEmpty()) {
            fizzle(level, hit.getLocation());
            return false;
        }
        Direction n = hit.getDirection();
        Direction u = n.getAxis().isHorizontal() ? Direction.UP : player.getDirection();
        BlockPos b0 = hit.getBlockPos();
        double along = hit.getLocation().subtract(Vec3.atCenterOf(b0)).dot(vec(u));
        BlockPos[][] pairs = along >= 0.0D
                ? new BlockPos[][]{{b0, b0.relative(u)}, {b0.relative(u.getOpposite()), b0}}
                : new BlockPos[][]{{b0.relative(u.getOpposite()), b0}, {b0, b0.relative(u)}};
        for (BlockPos[] pair : pairs) {
            if (!validSupport(level, pair[0], n) || !validSupport(level, pair[1], n)) continue;
            Vec3 center = Vec3.atCenterOf(pair[0]).add(Vec3.atCenterOf(pair[1])).scale(0.5D).add(vec(n).scale(0.51D));
            if (place(player, level, center, n, u, color)) return true;
            break;
        }
        fizzle(level, hit.getLocation());
        return false;
    }

    private static boolean validSupport(Level level, BlockPos support, Direction n) {
        BlockState s = level.getBlockState(support);
        if (s.is(ModBlocks.ILLUSION_BLOCK.get()) || !s.isFaceSturdy(level, support, n)) return false;
        BlockPos front = support.relative(n);
        return level.getBlockState(front).getCollisionShape(level, front).isEmpty();
    }

    private static boolean place(ServerPlayer player, ServerLevel level, Vec3 center, Direction n, Direction u, int color) {
        RobotPortalEntity p = new RobotPortalEntity(ModEntities.ROBOT_PORTAL.get(), level);
        p.owner = player.getUUID();
        p.entityData.set(FACING, n);
        p.entityData.set(UP, u);
        p.entityData.set(COLOR, (byte) color);
        p.setPos(center.x, center.y, center.z);
        // si pisa otro portal: si es tuyo se sustituye, si es de otro no se puede
        for (RobotPortalEntity other : level.getEntitiesOfClass(RobotPortalEntity.class, p.getBoundingBox().inflate(0.2D))) {
            if (other.facing() != n) continue;
            if (!player.getUUID().equals(other.owner)) return false;
            other.close(false);
        }
        level.addFreshEntity(p);
        PortalRegistry reg = PortalRegistry.get(level.getServer());
        List<PortalRegistry.Entry> removed = reg.add(player.getUUID(), new PortalRegistry.Entry(p.getUUID(), level.dimension(), p.position(), n, u, color));
        for (PortalRegistry.Entry e : removed) {
            ServerLevel l2 = level.getServer().getLevel(e.dim());
            if (l2 != null) {
                Entity old = l2.getEntity(e.id());
                if (old instanceof RobotPortalEntity rp) rp.closeEffects();
                if (old != null) old.discard();
            }
        }
        level.playSound(null, center.x, center.y, center.z, ModSounds.PORTAL_OPEN.get(), SoundSource.BLOCKS, 1.0F, color == 0 ? 1.0F : 0.85F);
        level.sendParticles(dust(color), center.x, center.y, center.z, 30, 0.3D, 0.6D, 0.3D, 0.0D);
        return true;
    }

    private static DustParticleOptions dust(int color) {
        int c = rgb(color);
        return new DustParticleOptions(new Vector3f(((c >> 16) & 255) / 255.0F, ((c >> 8) & 255) / 255.0F, (c & 255) / 255.0F), 1.0F);
    }

    private static void trail(ServerLevel level, Vec3 from, Vec3 to, int color) {
        Vec3 d = to.subtract(from);
        int steps = (int) Math.min(64, d.length());
        for (int i = 1; i <= steps; i++) {
            Vec3 p = from.add(d.scale(i / (double) Math.max(1, steps)));
            level.sendParticles(dust(color), p.x, p.y, p.z, 1, 0.02D, 0.02D, 0.02D, 0.0D);
        }
    }

    public static void fizzle(ServerLevel level, Vec3 at) {
        level.sendParticles(ParticleTypes.SMOKE, at.x, at.y, at.z, 8, 0.15D, 0.15D, 0.15D, 0.02D);
        level.playSound(null, at.x, at.y, at.z, ModSounds.PORTAL_FIZZLE.get(), SoundSource.PLAYERS, 0.8F, 1.0F);
    }

    // ------------------------------------------------------------------ tick y viaje

    private boolean supported() {
        Vec3 n = vec(facing()), u = vec(up());
        Vec3 back = this.position().subtract(n.scale(0.5D));
        BlockPos s1 = BlockPos.containing(back.add(u.scale(0.5D)));
        BlockPos s2 = BlockPos.containing(back.subtract(u.scale(0.5D)));
        return validSupport(this.level(), s1, facing()) && validSupport(this.level(), s2, facing());
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            if (this.random.nextInt(3) == 0) {
                Vec3 n = vec(facing()), u = vec(up()), w = n.cross(u);
                double a = this.random.nextDouble() * Math.PI * 2.0D;
                Vec3 p = this.position().add(w.scale(Math.cos(a) * 0.48D)).add(u.scale(Math.sin(a) * 0.98D)).add(n.scale(0.05D));
                this.level().addParticle(dust(color()), p.x, p.y, p.z, 0.0D, 0.0D, 0.0D);
            }
            return;
        }
        ServerLevel sl = (ServerLevel) this.level();
        if (owner == null) {
            this.discard();
            return;
        }
        PortalRegistry reg = PortalRegistry.get(sl.getServer());
        if (this.tickCount % 20 == 0 && !reg.isActive(owner, this.getUUID())) {
            this.discard();
            return;
        }
        if (this.tickCount % 10 == 0 && !supported()) {
            close(true);
            return;
        }
        PortalRegistry.Entry partner = reg.partner(owner, this.getUUID());
        AABB trigger = triggerBox();
        long now = sl.getGameTime();
        for (Entity e : sl.getEntities(this, trigger.inflate(1.0D), RobotPortalEntity::canTeleport)) {
            CompoundTag pd = e.getPersistentData();
            boolean inside = e.getBoundingBox().intersects(trigger);
            if (pd.hasUUID(IGNORE) && pd.getUUID(IGNORE).equals(this.getUUID())) {
                if (!inside) pd.remove(IGNORE);
                continue;
            }
            if (!inside || partner == null || pd.getLong(COOLDOWN) > now) continue;
            teleport(sl, e, partner);
        }
    }

    private static boolean canTeleport(Entity e) {
        return e.isAlive() && !(e instanceof RobotPortalEntity) && !(e instanceof HarpoonEntity) && !(e instanceof StuckToolEntity)
                && !e.isPassenger() && !e.isVehicle() && !e.isSpectator() && e.canChangeDimensions();
    }

    /** Lleva una entidad a la salida del portal pareja, girando su velocidad (lo que entra rapido, sale rapido). */
    private void teleport(ServerLevel from, Entity e, PortalRegistry.Entry partner) {
        ServerLevel dest = from.getServer().getLevel(partner.dim());
        if (dest == null) return;
        boolean same = dest == from;
        if (!same && !(e instanceof ServerPlayer)) return;
        Vec3 n1 = vec(facing()), u1 = vec(up()), w1 = n1.cross(u1);
        Vec3 n2 = vec(partner.facing()), u2 = vec(partner.up()), w2 = n2.cross(u2);
        Vec3 vel = e instanceof ServerPlayer sp ? RobotServer.playerVelocity(sp) : e.getDeltaMovement();
        Vec3 out = map(vel, n1, u1, w1, n2, u2, w2);
        double outN = out.dot(n2);
        if (outN < 0.25D) out = out.add(n2.scale(0.25D - outN));
        double hw = e.getBbWidth() * 0.5D, hh = e.getBbHeight() * 0.5D;
        double ext = Math.abs(n2.x) * hw + Math.abs(n2.y) * hh + Math.abs(n2.z) * hw;
        Vec3 center = partner.center().add(n2.scale(ext + 0.08D));
        Vec3 feet = center.subtract(0.0D, hh, 0.0D);
        Vec3 look = map(e.getLookAngle(), n1, u1, w1, n2, u2, w2);
        float yaw = look.horizontalDistanceSqr() > 1.0E-4 ? (float) (Mth.atan2(-look.x, look.z) * (180.0D / Math.PI)) : e.getYRot();

        CompoundTag pd = e.getPersistentData();
        pd.putLong(COOLDOWN, from.getGameTime() + 5L);
        pd.putUUID(IGNORE, partner.id());
        from.playSound(null, e.getX(), e.getY(), e.getZ(), ModSounds.PORTAL_ENTER.get(), SoundSource.PLAYERS, 0.8F, 1.0F);
        if (e instanceof ServerPlayer sp) {
            if (same) sp.connection.teleport(feet.x, feet.y, feet.z, yaw, sp.getXRot());
            else sp.teleportTo(dest, feet.x, feet.y, feet.z, yaw, sp.getXRot());
            sp.setDeltaMovement(out);
            sp.hurtMarked = true;
            sp.resetFallDistance();
        } else {
            e.teleportTo(feet.x, feet.y, feet.z);
            e.setYRot(yaw);
            if (e instanceof LivingEntity le) le.setYHeadRot(yaw);
            e.setDeltaMovement(out);
            e.hasImpulse = true;
            e.hurtMarked = true;
            e.resetFallDistance();
        }
        dest.playSound(null, feet.x, feet.y, feet.z, ModSounds.PORTAL_ENTER.get(), SoundSource.PLAYERS, 0.8F, 1.2F);
        dest.sendParticles(dust(partner.color()), center.x, center.y, center.z, 12, 0.2D, 0.4D, 0.2D, 0.0D);
    }

    /** Pasa un vector del espacio del portal de entrada al de salida (entrar = salir por la cara de delante). */
    static Vec3 map(Vec3 v, Vec3 n1, Vec3 u1, Vec3 w1, Vec3 n2, Vec3 u2, Vec3 w2) {
        double a = v.dot(w1), b = v.dot(u1), c = v.dot(n1);
        return w2.scale(-a).add(u2.scale(b)).add(n2.scale(-c));
    }

    private void closeEffects() {
        if (this.level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.SMOKE, getX(), getY(), getZ(), 10, 0.2D, 0.5D, 0.2D, 0.01D);
            sl.playSound(null, getX(), getY(), getZ(), ModSounds.PORTAL_FIZZLE.get(), SoundSource.BLOCKS, 0.6F, 0.8F);
        }
    }

    /** Cierra el portal (se rompio la pared, o lo has sustituido). */
    public void close(boolean effects) {
        if (effects) closeEffects();
        if (owner != null && this.level() instanceof ServerLevel sl) PortalRegistry.get(sl.getServer()).remove(owner, this.getUUID());
        this.discard();
    }

    @Override
    public void remove(RemovalReason reason) {
        if (reason.shouldDestroy() && owner != null && this.level() instanceof ServerLevel sl) {
            PortalRegistry.get(sl.getServer()).remove(owner, this.getUUID());
        }
        super.remove(reason);
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double d) {
        return d < 96.0D * 96.0D;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("Owner")) owner = tag.getUUID("Owner");
        this.entityData.set(COLOR, tag.getByte("Color"));
        this.entityData.set(FACING, Direction.from3DDataValue(tag.getInt("Facing")));
        this.entityData.set(UP, Direction.from3DDataValue(tag.getInt("Up")));
        this.setBoundingBox(this.makeBoundingBox());
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (owner != null) tag.putUUID("Owner", owner);
        tag.putByte("Color", (byte) color());
        tag.putInt("Facing", facing().get3DDataValue());
        tag.putInt("Up", up().get3DDataValue());
    }
}
