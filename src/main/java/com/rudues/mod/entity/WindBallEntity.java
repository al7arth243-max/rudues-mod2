package com.rudues.mod.entity;

import com.rudues.mod.registry.ModEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Garuda's great ball of wind in the end fight. Garuda places it each tick; it only draws: a churning green sphere
 * that takes fire (the Hero of Hell's dark purple flame) as he pushes against it.
 */
public class WindBallEntity extends Entity {
    private static final EntityDataAccessor<Float> RADIUS = SynchedEntityData.defineId(WindBallEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> FIRE = SynchedEntityData.defineId(WindBallEntity.class, EntityDataSerializers.FLOAT);

    public WindBallEntity(EntityType<? extends WindBallEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public WindBallEntity(Level level, Vec3 centre) {
        this(ModEntities.WIND_BALL.get(), level);
        place(centre);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(RADIUS, 0.2F);
        this.entityData.define(FIRE, 0.0F);
    }

    public float radius() {
        return this.entityData.get(RADIUS);
    }

    public float fire() {
        return this.entityData.get(FIRE);
    }

    public void setRadius(float r) {
        this.entityData.set(RADIUS, r);
    }

    public void setFire(float f) {
        this.entityData.set(FIRE, Math.max(0.0F, Math.min(1.0F, f)));
    }

    /** the entity stands at the ball's centre */
    public void place(Vec3 c) {
        setPos(c.x, c.y, c.z);
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && tickCount > 20 * 30) {
            discard();
        }
        if (level().isClientSide) {
            float r = radius();
            for (int i = 0; i < 3; i++) {
                Vec3 d = new Vec3(random.nextDouble() - 0.5, random.nextDouble() - 0.5, random.nextDouble() - 0.5).normalize().scale(r * 1.4);
                Vec3 p = position().add(d);
                if (random.nextFloat() < fire()) {
                    level().addParticle(com.rudues.mod.registry.ModParticles.PURPLE_FLAME_BIG.get(), p.x, p.y, p.z, -d.x * 0.03, -d.y * 0.03, -d.z * 0.03);
                } else {
                    level().addParticle(FeatheredKnightEntity.gust(), p.x, p.y, p.z, 0, 0, 0);
                }
            }
        }
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

    @Override
    public boolean shouldRenderAtSqrDistance(double d) {
        return d < 256 * 256;
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return new ClientboundAddEntityPacket(this);
    }
}
