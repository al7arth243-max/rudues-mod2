package com.rudues.mod.entity;

import com.rudues.mod.registry.ModEntities;
import com.rudues.mod.registry.ModParticles;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * A fire geyser from the Inferno Vortex: a glowing warning circle for 2 seconds, then a column of fire for 3 seconds.
 * Erupting launches and burns whatever stands on it; standing in it keeps burning.
 */
public class FireGeyserEntity extends Entity {
    public static final int WARN_TICKS = 40;
    public static final int ERUPT_TICKS = 60;
    public static final int LIFE = WARN_TICKS + ERUPT_TICKS;
    public static final float RADIUS = 1.4F;
    public static final float HEIGHT = 6.5F;
    public static final float ERUPT_DAMAGE = 7.0F;
    public static final float BURN_DAMAGE = 3.0F;

    @Nullable
    private InfernalShadowEntity owner;

    public FireGeyserEntity(EntityType<? extends FireGeyserEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public FireGeyserEntity(Level level, Vec3 pos, @Nullable InfernalShadowEntity owner) {
        this(ModEntities.FIRE_GEYSER.get(), level);
        this.owner = owner;
        setPos(pos.x, pos.y, pos.z);
    }

    @Override
    protected void defineSynchedData() {
    }

    @Override
    public void tick() {
        super.tick();
        int age = this.tickCount;
        if (level().isClientSide) {
            clientEffects(age);
            return;
        }
        if (age >= LIFE) {
            discard();
            return;
        }
        ServerLevel sl = (ServerLevel) level();
        if (age == 1) {
            sl.playSound(null, getX(), getY(), getZ(), SoundEvents.LAVA_AMBIENT, SoundSource.HOSTILE, 0.9F, 0.7F);
        }
        if (age == WARN_TICKS) {
            sl.playSound(null, getX(), getY(), getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 1.4F, 0.5F);
            sl.playSound(null, getX(), getY(), getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.HOSTILE, 1.2F, 0.6F);
        }
        if (age > WARN_TICKS && age % 20 == 0) {
            sl.playSound(null, getX(), getY(), getZ(), SoundEvents.FIRE_AMBIENT, SoundSource.HOSTILE, 1.0F, 0.7F);
        }
        if (age >= WARN_TICKS && (age == WARN_TICKS || (age - WARN_TICKS) % 10 == 0)) {
            burn(age == WARN_TICKS);
        }
    }

    private void burn(boolean eruption) {
        AABB box = new AABB(getX() - RADIUS - 0.5, getY() - 0.5, getZ() - RADIUS - 0.5, getX() + RADIUS + 0.5, getY() + HEIGHT, getZ() + RADIUS + 0.5);
        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && !(e instanceof InfernalShadowEntity))) {
            double dx = e.getX() - getX(), dz = e.getZ() - getZ();
            if (dx * dx + dz * dz > (RADIUS + e.getBbWidth() * 0.5) * (RADIUS + e.getBbWidth() * 0.5)) {
                continue;
            }
            DamageSource src = owner != null && owner.isAlive() ? damageSources().mobAttack(owner) : damageSources().inFire();
            if (e.hurt(src, (eruption ? ERUPT_DAMAGE : BURN_DAMAGE) * InfernalShadowEntity.powerOf(owner))) {
                ShadowFire.ignite(e, 4);
                if (eruption) {
                    e.setDeltaMovement(e.getDeltaMovement().x * 0.3, 0.95, e.getDeltaMovement().z * 0.3);
                    e.hurtMarked = true;
                }
            }
        }
    }

    private void clientEffects(int age) {
        if (age < WARN_TICKS) {
            // bubbling: the ground about to burst
            if (random.nextInt(3) == 0) {
                double a = random.nextDouble() * Math.PI * 2, r = random.nextDouble() * RADIUS;
                level().addParticle(ModParticles.PURPLE_EMBER.get(), getX() + Math.cos(a) * r, getY() + 0.1, getZ() + Math.sin(a) * r, 0, 0.07, 0);
            }
            if (age > WARN_TICKS - 12) {
                level().addParticle(ParticleTypes.SMOKE, getX() + (random.nextDouble() - 0.5) * RADIUS, getY() + 0.1,
                        getZ() + (random.nextDouble() - 0.5) * RADIUS, 0, 0.06, 0);
            }
        } else if (age < LIFE - 4) {
            for (int i = 0; i < 7; i++) {
                double a = random.nextDouble() * Math.PI * 2, r = random.nextDouble() * RADIUS * 0.8;
                level().addParticle(ModParticles.PURPLE_FLAME.get(), getX() + Math.cos(a) * r, getY() + random.nextDouble() * 1.5,
                        getZ() + Math.sin(a) * r, Math.cos(a) * 0.02, 0.35 + random.nextDouble() * 0.25, Math.sin(a) * 0.02);
            }
            if (random.nextInt(2) == 0) {
                level().addParticle(ParticleTypes.LARGE_SMOKE, getX(), getY() + HEIGHT * 0.9, getZ(), (random.nextDouble() - 0.5) * 0.1, 0.12, (random.nextDouble() - 0.5) * 0.1);
            }
            if (random.nextInt(3) == 0) {
                level().addParticle(ModParticles.PURPLE_EMBER.get(), getX(), getY() + random.nextDouble() * HEIGHT, getZ(), 0, 0.1, 0);
            }
            if (age == WARN_TICKS) {
                for (int i = 0; i < 12; i++) {
                    level().addParticle(ModParticles.PURPLE_EMBER.get(), getX(), getY() + 0.3, getZ(), 0, 0.07, 0);
                }
            }
        }
    }

    /** the warning (0..1 filled) and eruption (0 before, 1 while burning, fading at the end) for the renderer */
    public float warnProgress(float partialTick) {
        return Math.min(1.0F, (this.tickCount + partialTick) / WARN_TICKS);
    }

    public float eruption(float partialTick) {
        float t = this.tickCount + partialTick - WARN_TICKS;
        if (t < 0) {
            return 0.0F;
        }
        float rise = Math.min(1.0F, t / 4.0F);
        float fade = Math.min(1.0F, (LIFE - (this.tickCount + partialTick)) / 6.0F);
        return Math.max(0.0F, Math.min(rise, fade));
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }
}
