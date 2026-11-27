package com.xpeak.enderite.entity;

import com.xpeak.enderite.registry.ModEntities;
import com.xpeak.enderite.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Una herramienta clavada en la pared. El jugador se queda colgando de ella (va "montado").
 * Click derecho sobre ella para recuperarla.
 */
public class StuckToolEntity extends Entity {
    private static final EntityDataAccessor<ItemStack> DATA_ITEM = SynchedEntityData.defineId(StuckToolEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<Direction> DATA_FACE = SynchedEntityData.defineId(StuckToolEntity.class, EntityDataSerializers.DIRECTION);

    /** Distancia entre el centro de la herramienta y los pies del jugador que cuelga. */
    public static final double HANG_OFFSET = 1.95D;

    @Nullable
    private BlockPos attachedPos;
    @Nullable
    private UUID ownerId;

    public StuckToolEntity(EntityType<? extends StuckToolEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public static StuckToolEntity create(Level level, ItemStack stack, Direction face, BlockPos attached, Vec3 center, UUID owner) {
        StuckToolEntity e = new StuckToolEntity(ModEntities.STUCK_TOOL.get(), level);
        e.setItem(stack);
        e.setFace(face);
        e.attachedPos = attached.immutable();
        e.ownerId = owner;
        e.setPos(center.x, center.y - 0.25D, center.z);
        e.setYRot(face.toYRot());
        return e;
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_ITEM, ItemStack.EMPTY);
        this.entityData.define(DATA_FACE, Direction.NORTH);
    }

    public ItemStack getItem() {
        return this.entityData.get(DATA_ITEM);
    }

    public void setItem(ItemStack stack) {
        this.entityData.set(DATA_ITEM, stack.copy());
    }

    /** Cara de la pared (apunta hacia fuera, hacia el jugador). */
    public Direction getFace() {
        return this.entityData.get(DATA_FACE);
    }

    public void setFace(Direction face) {
        this.entityData.set(DATA_FACE, face);
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide && this.tickCount % 10 == 0) {
            if (attachedPos == null) attachedPos = BlockPos.containing(this.getX() - getFace().getStepX() * 0.5D,
                    this.getY() + 0.25D, this.getZ() - getFace().getStepZ() * 0.5D);
            BlockState state = this.level().getBlockState(attachedPos);
            if (state.getCollisionShape(this.level(), attachedPos).isEmpty()) {
                // Han roto el bloque: la herramienta cae
                this.ejectPassengers();
                this.spawnAtLocation(getItem(), 0.0F);
                this.discard();
            }
        }
    }

    // ------------------------------------------------------------------ Recuperar la herramienta

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (this.level().isClientSide) return InteractionResult.SUCCESS;
        if (!this.isAlive()) return InteractionResult.PASS;
        ItemStack stack = getItem().copy();
        this.ejectPassengers();
        this.discard();
        if (!stack.isEmpty()) {
            if (player.getItemInHand(hand).isEmpty()) {
                player.setItemInHand(hand, stack);
            } else if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        }
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(), ModSounds.TOOL_PULL.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        return InteractionResult.CONSUME;
    }

    @Override
    public boolean isPickable() {
        return this.isAlive();
    }

    @Override
    public boolean canRiderInteract() {
        return true;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    // ------------------------------------------------------------------ Colgarse

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return this.getPassengers().isEmpty() && passenger instanceof Player;
    }

    @Override
    public boolean shouldRiderSit() {
        return false;
    }

    public Vec3 hangPosition() {
        Direction f = getFace();
        double cx = this.getX();
        double cy = this.getY() + 0.25D;
        double cz = this.getZ();
        return new Vec3(cx + f.getStepX() * 0.22D, cy - HANG_OFFSET, cz + f.getStepZ() * 0.22D);
    }

    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction move) {
        if (!this.hasPassenger(passenger)) return;
        Vec3 p = hangPosition();
        move.accept(passenger, p.x, p.y, p.z);
        passenger.resetFallDistance();
    }

    @Override
    public double getPassengersRidingOffset() {
        return -HANG_OFFSET;
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        return hangPosition();
    }

    // ------------------------------------------------------------------ Guardado y red

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        setItem(ItemStack.of(tag.getCompound("Item")));
        setFace(Direction.from3DDataValue(tag.getByte("Face")));
        if (tag.contains("Attached")) attachedPos = NbtUtils.readBlockPos(tag.getCompound("Attached"));
        if (tag.hasUUID("Owner")) ownerId = tag.getUUID("Owner");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.put("Item", getItem().save(new CompoundTag()));
        tag.putByte("Face", (byte) getFace().get3DDataValue());
        if (attachedPos != null) tag.put("Attached", NbtUtils.writeBlockPos(attachedPos));
        if (ownerId != null) tag.putUUID("Owner", ownerId);
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return new ClientboundAddEntityPacket(this);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 64.0D * 64.0D;
    }
}
