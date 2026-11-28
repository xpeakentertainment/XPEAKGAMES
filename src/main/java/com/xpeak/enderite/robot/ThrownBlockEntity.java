package com.xpeak.enderite.robot;

import com.xpeak.enderite.registry.ModEntities;
import com.xpeak.enderite.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Bloque lanzado por el brazo catapulta (como un saque de banda). Va a toda velocidad; si choca con un bloque se convierte
 * en su objeto (drop), y si choca con una criatura le hace mucho dano y tambien se convierte en drop.
 */
public class ThrownBlockEntity extends Projectile {
    public static final double SPEED = 2.2D;
    private static final EntityDataAccessor<BlockState> BLOCK = SynchedEntityData.defineId(ThrownBlockEntity.class, EntityDataSerializers.BLOCK_STATE);
    private float damageMult = 1.0F;

    public ThrownBlockEntity(EntityType<? extends ThrownBlockEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(BLOCK, Blocks.STONE.defaultBlockState());
    }

    public BlockState blockState() {
        return this.entityData.get(BLOCK);
    }

    public static ThrownBlockEntity launch(ServerPlayer player, BlockState state, float mult) {
        ThrownBlockEntity t = new ThrownBlockEntity(ModEntities.THROWN_BLOCK.get(), player.level());
        t.setOwner(player);
        t.entityData.set(BLOCK, state);
        t.damageMult = mult;
        Vec3 look = player.getLookAngle();
        // sale de encima de la cabeza, como en un saque de banda
        Vec3 start = player.getEyePosition().add(0.0D, 0.55D, 0.0D).add(look.scale(0.9D));
        t.setPos(start.x, start.y - 0.35D, start.z);
        t.setDeltaMovement(look.scale(SPEED));
        player.level().addFreshEntity(t);
        player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(), ModSounds.CATAPULT_THROW.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        return t;
    }

    /** Dano al chocar con una criatura: mucho, y mas cuanto mas duro es el bloque. */
    public float impactDamage() {
        float hardness = Mth.clamp(blockState().getBlock().defaultDestroyTime(), 0.0F, 10.0F);
        return (12.0F + hardness * 1.2F) * damageMult;
    }

    @Override
    protected boolean canHitEntity(Entity e) {
        return e != this.getOwner() && !(e instanceof RobotPortalEntity) && super.canHitEntity(e);
    }

    @Override
    public void tick() {
        super.tick();
        Vec3 v = this.getDeltaMovement();
        if (!this.level().isClientSide) {
            HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
            if (hit.getType() == HitResult.Type.ENTITY) {
                hitEntity((EntityHitResult) hit);
                return;
            }
            if (hit.getType() == HitResult.Type.BLOCK) {
                hitBlock((BlockHitResult) hit);
                return;
            }
        }
        this.setPos(this.getX() + v.x, this.getY() + v.y, this.getZ() + v.z);
        this.setDeltaMovement(v.scale(0.99D).add(0.0D, -0.03D, 0.0D));
        if (!this.level().isClientSide && this.tickCount > 200) {
            dropAt(this.position());
            this.discard();
        }
    }

    private void hitEntity(EntityHitResult hit) {
        Entity e = hit.getEntity();
        Vec3 v = this.getDeltaMovement();
        e.hurt(this.damageSources().thrown(this, this.getOwner()), impactDamage());
        if (e instanceof LivingEntity le) le.knockback(1.4D, -v.x, -v.z);
        impact(e.position().add(0.0D, e.getBbHeight() * 0.5D, 0.0D));
        dropAt(e.position().add(0.0D, e.getBbHeight() * 0.5D, 0.0D));
        this.discard();
    }

    private void hitBlock(BlockHitResult hit) {
        Vec3 back = hit.getLocation().subtract(this.getDeltaMovement().normalize().scale(0.35D));
        impact(back);
        dropAt(back);
        this.discard();
    }

    private void impact(Vec3 at) {
        ServerLevel sl = (ServerLevel) this.level();
        sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, blockState()), at.x, at.y, at.z, 30, 0.3D, 0.3D, 0.3D, 0.15D);
        sl.playSound(null, at.x, at.y, at.z, blockState().getSoundType().getBreakSound(), SoundSource.BLOCKS, 1.0F, 0.8F);
        sl.playSound(null, at.x, at.y, at.z, ModSounds.CATAPULT_IMPACT.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    private void dropAt(Vec3 at) {
        ItemStack stack = new ItemStack(blockState().getBlock().asItem());
        if (stack.isEmpty()) return;
        ItemEntity item = new ItemEntity(this.level(), at.x, at.y, at.z, stack);
        item.setDeltaMovement(this.random.nextGaussian() * 0.05D, 0.2D, this.random.nextGaussian() * 0.05D);
        item.setDefaultPickUpDelay();
        this.level().addFreshEntity(item);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.put("Block", NbtUtils.writeBlockState(blockState()));
        tag.putFloat("Mult", damageMult);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.entityData.set(BLOCK, NbtUtils.readBlockState(net.minecraft.core.registries.BuiltInRegistries.BLOCK.asLookup(), tag.getCompound("Block")));
        damageMult = tag.contains("Mult") ? tag.getFloat("Mult") : 1.0F;
    }

    /** Se puede agarrar con la catapulta: ni aire, ni liquidos, ni bloques con contenido, ni irrompibles, ni demasiado duros. */
    public static boolean canGrab(Level level, BlockPos pos, BlockState state) {
        if (state.isAir() || !state.getFluidState().isEmpty() || state.hasBlockEntity()) return false;
        float hardness = state.getDestroySpeed(level, pos);
        if (hardness < 0.0F || hardness > 20.0F) return false;
        Block b = state.getBlock();
        return b.asItem() != net.minecraft.world.item.Items.AIR && !state.getCollisionShape(level, pos).isEmpty();
    }
}
