package com.rudues.mod.entity;

import com.rudues.mod.registry.ModEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.UUID;

/** The visible Concentrated Beam. Pure visual: HeroForm updates its origin/aim/length every tick. */
public class BeamEntity extends Entity {
    private static final EntityDataAccessor<Float> LENGTH =
            SynchedEntityData.defineId(BeamEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Optional<UUID>> OWNER =
            SynchedEntityData.defineId(BeamEntity.class, EntityDataSerializers.OPTIONAL_UUID);

    public BeamEntity(EntityType<? extends BeamEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public BeamEntity(Level level, Player owner) {
        this(ModEntities.BEAM.get(), level);
        this.entityData.set(OWNER, Optional.of(owner.getUUID()));
    }

    public void aim(Vec3 origin, Vec3 dir, float length) {
        setPos(origin.x, origin.y, origin.z);
        setYRot((float) (Mth.atan2(-dir.x, dir.z) * (180.0 / Math.PI)));
        setXRot((float) (-(Mth.atan2(dir.y, Math.sqrt(dir.x * dir.x + dir.z * dir.z)) * (180.0 / Math.PI))));
        this.entityData.set(LENGTH, length);
    }

    public float getLength() {
        return this.entityData.get(LENGTH);
    }

    public UUID getOwnerUUID() {
        return this.entityData.get(OWNER).orElse(null);
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && tickCount > 140) {
            discard(); // failsafe
        }
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(LENGTH, 0.0F);
        this.entityData.define(OWNER, Optional.empty());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }
}
