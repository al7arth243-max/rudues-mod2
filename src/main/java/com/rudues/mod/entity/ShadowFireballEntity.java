package com.rudues.mod.entity;

import com.rudues.mod.registry.ModEntities;
import com.rudues.mod.registry.ModParticles;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The Hellfire Volley's fireball: flies straight (dodgeable), sets its target alight with purple fire and bursts on
 * impact, never sets blocks alight
 * and can't be punched back.
 */
public class ShadowFireballEntity extends SmallFireball {
    public static final float DAMAGE = 7.0F;
    public static final float SPLASH_DAMAGE = 3.0F;
    /** blocks per tick it settles at (it starts a little slower and accelerates) */
    public static final double SPEED = 1.05;
    private static final int LIFE = 120;

    public ShadowFireballEntity(EntityType<? extends ShadowFireballEntity> type, Level level) {
        super(type, level);
    }

    public ShadowFireballEntity(Level level, LivingEntity owner, Vec3 pos, Vec3 direction) {
        this(ModEntities.SHADOW_FIREBALL.get(), level);
        setOwner(owner);
        moveTo(pos.x, pos.y, pos.z, owner.getYRot(), 0.0F);
        Vec3 d = direction.normalize();
        // terminal speed of a hurting projectile = power / (1 - inertia 0.95)
        this.xPower = d.x * SPEED * 0.05;
        this.yPower = d.y * SPEED * 0.05;
        this.zPower = d.z * SPEED * 0.05;
        setDeltaMovement(d.scale(SPEED * 0.85));
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            level().addParticle(ModParticles.PURPLE_FLAME.get(), getX(), getY() + 0.15, getZ(), 0, 0, 0);
            if (random.nextInt(2) == 0) {
                level().addParticle(ModParticles.PURPLE_FLAME.get(), getX() + (random.nextDouble() - 0.5) * 0.3,
                        getY() + 0.15 + (random.nextDouble() - 0.5) * 0.3, getZ() + (random.nextDouble() - 0.5) * 0.3, 0, 0, 0);
            }
            if (random.nextInt(3) == 0) {
                level().addParticle(ParticleTypes.SMOKE, getX(), getY() + 0.15, getZ(), 0, 0.01, 0);
            }
        } else if (this.tickCount > LIFE) {
            discard();
        }
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        return entity != getOwner() && !(entity instanceof InfernalShadowEntity) && !(entity instanceof ShadowFireballEntity)
                && super.canHitEntity(entity);
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        if (level().isClientSide) {
            return;
        }
        Entity e = result.getEntity();
        Entity owner = getOwner();
        DamageSource src = hitSource(owner);
        if (e.hurt(src, DAMAGE * InfernalShadowEntity.powerOf(owner))) {
            ShadowFire.ignite(e, 5);
        }
        burst(e);
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        if (!level().isClientSide) {
            burst(null);     // no fire left behind on the blocks
        }
    }

    private void burst(Entity directHit) {
        ServerLevel sl = (ServerLevel) level();
        sl.sendParticles(ModParticles.PURPLE_FLAME.get(), getX(), getY(), getZ(), 24, 0.25, 0.25, 0.25, 0.08);
        sl.sendParticles(ModParticles.PURPLE_FLAME_BIG.get(), getX(), getY(), getZ(), 6, 0.2, 0.2, 0.2, 0.02);
        sl.sendParticles(ModParticles.PURPLE_SPARK.get(), getX(), getY(), getZ(), 10, 0.1, 0.1, 0.1, 0.3);
        sl.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 0.7F, 1.3F);
        Entity owner = getOwner();
        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(2.0),
                e -> e != directHit && e != owner && e.isAlive() && !(e instanceof InfernalShadowEntity))) {
            if (e.distanceToSqr(this) < 4.0 && e.hurt(hitSource(owner), SPLASH_DAMAGE * InfernalShadowEntity.powerOf(owner))) {
                ShadowFire.ignite(e, 3);
            }
        }
    }

    /** a projectile hit, not a fire one: the power's fire immunity must not shrug it off (the burn after is purple fire) */
    private DamageSource hitSource(Entity owner) {
        return damageSources().mobProjectile(this, owner instanceof LivingEntity l ? l : null);
    }

    /** no vanilla flames on the ball itself (it is drawn as purple fire) */
    @Override
    protected boolean shouldBurn() {
        return false;
    }

    @Override
    protected net.minecraft.core.particles.ParticleOptions getTrailParticle() {
        return ModParticles.PURPLE_FLAME.get();
    }

    /** can't be knocked back at the Shadow */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }
}
