package com.rudues.mod.entity;

import com.rudues.mod.registry.ModEntities;
import com.rudues.mod.registry.ModParticles;
import net.minecraft.core.particles.ParticleOptions;
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
 * The Infernal Hero's fireball: like the Shadow's, but a great deal bigger, slower and heavier - it bursts in a wide
 * blast and sets everything it catches alight with purple fire. Flies straight (dodge it), can't be knocked back.
 */
public class HeroFireballEntity extends SmallFireball {
    public static final float DAMAGE = 14.0F;
    public static final float SPLASH_DAMAGE = 7.0F;
    public static final double SPLASH_RADIUS = 3.8;
    public static final double SPEED = 0.85;
    private static final int LIFE = 140;

    public HeroFireballEntity(EntityType<? extends HeroFireballEntity> type, Level level) {
        super(type, level);
    }

    public HeroFireballEntity(Level level, LivingEntity owner, Vec3 pos, Vec3 direction) {
        this(ModEntities.HERO_FIREBALL.get(), level);
        setOwner(owner);
        moveTo(pos.x, pos.y - 0.5, pos.z, owner.getYRot(), 0.0F);
        Vec3 d = direction.normalize();
        this.xPower = d.x * SPEED * 0.05;
        this.yPower = d.y * SPEED * 0.05;
        this.zPower = d.z * SPEED * 0.05;
        setDeltaMovement(d.scale(SPEED * 0.8));
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            for (int i = 0; i < 2; i++) {
                level().addParticle(i == 0 ? ModParticles.DARK_FLAME_BIG.get() : ModParticles.DARK_FLAME.get(),
                        getX() + (random.nextDouble() - 0.5) * 0.9, getY() + 0.5 + (random.nextDouble() - 0.5) * 0.9,
                        getZ() + (random.nextDouble() - 0.5) * 0.9, 0, 0, 0);
            }
            if (random.nextInt(2) == 0) {
                level().addParticle(ParticleTypes.LARGE_SMOKE, getX(), getY() + 0.5, getZ(), 0, 0.02, 0);
            }
        } else if (this.tickCount > LIFE) {
            discard();
        }
    }

    @Override
    protected boolean shouldBurn() {
        return false;
    }

    @Override
    protected ParticleOptions getTrailParticle() {
        return ModParticles.DARK_EMBER.get();
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        return entity != getOwner() && !(entity instanceof InfernalHeroEntity) && !(entity instanceof HeroFireballEntity)
                && super.canHitEntity(entity);
    }

    /** a projectile hit, not a fire one: the power's fire immunity doesn't shrug it off */
    private DamageSource hitSource(Entity owner) {
        return damageSources().mobProjectile(this, owner instanceof LivingEntity l ? l : null);
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        if (level().isClientSide) {
            return;
        }
        Entity e = result.getEntity();
        Entity owner = getOwner();
        if (e.hurt(hitSource(owner), DAMAGE)) {
            ShadowFire.ignite(e, 6);
        }
        burst(e);
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        if (!level().isClientSide) {
            burst(null);
        }
    }

    private void burst(Entity directHit) {
        ServerLevel sl = (ServerLevel) level();
        Vec3 c = position().add(0, 0.5, 0);
        sl.sendParticles(ModParticles.DARK_FLAME_BIG.get(), c.x, c.y, c.z, 26, 0.8, 0.6, 0.8, 0.06);
        sl.sendParticles(ModParticles.DARK_FLAME.get(), c.x, c.y, c.z, 40, 1.2, 0.5, 1.2, 0.12);
        sl.sendParticles(ModParticles.DARK_SPARK.get(), c.x, c.y, c.z, 16, 0.5, 0.5, 0.5, 0.4);
        sl.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y, c.z, 2, 0.4, 0.3, 0.4, 0.0);
        sl.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 1.4F, 0.8F);
        Entity owner = getOwner();
        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(SPLASH_RADIUS),
                e -> e != directHit && e != owner && e.isAlive() && !(e instanceof InfernalHeroEntity))) {
            if (e.distanceToSqr(c) < SPLASH_RADIUS * SPLASH_RADIUS && e.hurt(hitSource(owner), SPLASH_DAMAGE)) {
                ShadowFire.ignite(e, 4);
                Vec3 push = e.position().subtract(c).multiply(1, 0, 1).normalize().scale(0.6);
                e.setDeltaMovement(e.getDeltaMovement().add(push.x, 0.35, push.z));
                e.hurtMarked = true;
            }
        }
        discard();
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }
}
