package com.apocscode.logiclink.entity;

import com.apocscode.logiclink.ModRegistry;
import com.apocscode.logiclink.block.ContraptionRemoteBlock;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;

/**
 * Invisible seat entity spawned when a player right-clicks a Contraption Remote block.
 * The player rides this entity while seated, enabling gamepad input routing.
 * <p>
 * Modeled after Create's SeatEntity: tiny, invisible, no physics.
 * Auto-removes when the passenger dismounts or the block is broken.
 */
public class RemoteSeatEntity extends Entity {

    public RemoteSeatEntity(EntityType<?> entityType, Level level) {
        super(entityType, level);
    }

    public RemoteSeatEntity(Level level) {
        this(ModRegistry.REMOTE_SEAT.get(), level);
        noPhysics = true;
    }

    public static EntityType.Builder<?> build(EntityType.Builder<?> builder) {
        @SuppressWarnings("unchecked")
        EntityType.Builder<RemoteSeatEntity> entityBuilder = (EntityType.Builder<RemoteSeatEntity>) builder;
        return entityBuilder.sized(0.25f, 0.35f);
    }

    @Override
    public void setPos(double x, double y, double z) {
        super.setPos(x, y, z);
        AABB bb = getBoundingBox();
        Vec3 diff = new Vec3(x, y, z).subtract(bb.getCenter());
        setBoundingBox(bb.move(diff));
    }

    @Override
    protected void positionRider(Entity pEntity, Entity.MoveFunction pCallback) {
        if (!this.hasPassenger(pEntity)) return;
        // Position the player sitting on top of the block (block is 14px = 0.875 blocks tall)
        double heightOffset = this.getPassengerRidingPosition(pEntity).y
                - pEntity.getVehicleAttachmentPoint(this).y;
        pCallback.accept(pEntity, this.getX(), heightOffset, this.getZ());
    }

    @Override
    public void onPassengerTurned(Entity entity) {
        // Allow the player to look around freely while seated
        entity.setYHeadRot(entity.getYRot());
    }

    @Override
    public void setDeltaMovement(Vec3 vec) {
        // Cannot be pushed
    }

    @Override
    public void tick() {
        if (level().isClientSide) return;
        // Stay alive while occupied AND the block still exists
        boolean blockPresent = level().getBlockState(blockPosition()).getBlock() instanceof ContraptionRemoteBlock;
        if (isVehicle() && blockPresent) return;
        this.discard();
    }

    @Override
    protected boolean canRide(Entity entity) {
        return !(entity instanceof FakePlayer);
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        return super.getDismountLocationForPassenger(passenger).add(0, 0.5f, 0);
    }

    // ==================== Required overrides (no data needed) ====================

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        // No synched data needed — entity is transient
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        // No persistent data
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        // No persistent data
    }
}
