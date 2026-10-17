package com.rudues.mod.entity;

import com.rudues.mod.registry.ModParticles;
import com.rudues.mod.ability.FxUtil;
import com.rudues.mod.registry.ModEntities;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import org.joml.Vector3f;

/** Rage Blast fireball. The "giant" variant is the demon's Hellfire. */
public class RageBlastEntity extends AbstractHurtingProjectile {
    private static final EntityDataAccessor<Boolean> GIANT =
            SynchedEntityData.defineId(RageBlastEntity.class, EntityDataSerializers.BOOLEAN);

    public RageBlastEntity(EntityType<? extends RageBlastEntity> type, Level level) {
        super(type, level);
    }

    public RageBlastEntity(Level level, LivingEntity owner, double dx, double dy, double dz) {
        super(ModEntities.RAGE_BLAST.get(), owner, dx, dy, dz, level);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(GIANT, false);
    }

    public void setGiant(boolean giant) {
        this.entityData.set(GIANT, giant);
    }

    public boolean isGiant() {
        return this.entityData.get(GIANT);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            if (isGiant()) {
                for (int i = 0; i < 14; i++) {
                    level().addParticle(ModParticles.PURPLE_FLAME_BIG.get(),
                            getX() + (random.nextDouble() - 0.5) * 2.4,
                            getY() + 0.3 + (random.nextDouble() - 0.5) * 2.4,
                            getZ() + (random.nextDouble() - 0.5) * 2.4, 0, 0, 0);
                }
                level().addParticle(ModParticles.PURPLE_SPARK.get(), getX() + (random.nextDouble() - 0.5) * 2,
                        getY() + (random.nextDouble() - 0.5) * 2, getZ() + (random.nextDouble() - 0.5) * 2, 0, 0, 0);
            } else {
                for (int i = 0; i < 4; i++) {
                    level().addParticle(ModParticles.PURPLE_FLAME.get(),
                            getX() + (random.nextDouble() - 0.5) * 0.5,
                            getY() + 0.3 + (random.nextDouble() - 0.5) * 0.5,
                            getZ() + (random.nextDouble() - 0.5) * 0.5, 0, 0, 0);
                }
                level().addParticle(ModParticles.PURPLE_EMBER.get(), getX(), getY() + 0.3, getZ(), 0, 0, 0);
            }
        } else if (tickCount > 160) {
            discard();
        }
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (level().isClientSide) {
            return;
        }
        Entity owner = getOwner();
        boolean giant = isGiant();
        level().explode(this, getX(), getY(), getZ(), giant ? 7.0F : 3.2F, true, Level.ExplosionInteraction.BLOCK);

        AABB area = getBoundingBox().inflate(giant ? 9.0 : 4.5);
        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, area,
                e -> e != owner && !(e instanceof FiendEntity f && owner instanceof LivingEntity o && f.isOwner(o)))) {
            e.setSecondsOnFire(giant ? 15 : 10);
        }
        if (level() instanceof ServerLevel sl) {
            FxUtil.purpleBurst(sl, position(), giant ? 260 : 80, giant ? 3.5 : 1.5);
            if (giant) {
                sl.sendParticles(ModParticles.PURPLE_SPARK.get(), getX(), getY(), getZ(), 120, 3.0, 3.0, 3.0, 0.6);
                sl.sendParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), getY(), getZ(), 1, 0, 0, 0, 0);
            }
        }
        discard();
    }

    @Override
    protected ParticleOptions getTrailParticle() {
        return ModParticles.PURPLE_EMBER.get();
    }

    @Override
    protected boolean shouldBurn() {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean hurt(net.minecraft.world.damagesource.DamageSource source, float amount) {
        return false;
    }
}
