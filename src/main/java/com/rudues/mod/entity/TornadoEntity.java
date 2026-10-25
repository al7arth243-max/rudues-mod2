package com.rudues.mod.entity;

import com.rudues.mod.registry.ModEntities;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Aeroga's tornado: a green twister that stands where the orb burst for five seconds, creeping after the nearest
 * player. Anything caught in it is whirled round, lifted and cut.
 */
public class TornadoEntity extends Projectile {
    public static final int LIFE = 100;
    public static final float RADIUS = 1.7F, HEIGHT = 5.5F;
    private static final float DAMAGE = 3.0F;
    private static final double CREEP = 0.035;

    public TornadoEntity(EntityType<? extends TornadoEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public TornadoEntity(Level level, @Nullable LivingEntity owner, Vec3 at) {
        this(ModEntities.TORNADO.get(), level);
        if (owner != null) {
            setOwner(owner);
        }
        setPos(at.x, at.y, at.z);
    }

    @Override
    protected void defineSynchedData() {
    }

    /** 0..1: how much of it there is (spinning up, dying away) */
    public float strength(float partialTick) {
        float t = tickCount + partialTick;
        return Mth.clamp(Math.min(t / 10.0F, (LIFE - t) / 15.0F), 0.0F, 1.0F);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            clientFx();
            return;
        }
        if (tickCount >= LIFE) {
            discard();
            return;
        }
        Entity owner = getOwner();
        // creep after the nearest player
        Player p = level().getNearestPlayer(this, 24.0);
        Vec3 drift = Vec3.ZERO;
        if (p != null && !p.isSpectator()) {
            Vec3 d = new Vec3(p.getX() - getX(), 0, p.getZ() - getZ());
            if (d.lengthSqr() > 0.25) {
                drift = d.normalize().scale(CREEP);
            }
        }
        setPos(getX() + drift.x, getY(), getZ() + drift.z);
        if (tickCount % 20 == 5) {
            playSound(SoundEvents.ELYTRA_FLYING, 0.5F, 1.4F);
        }
        float s = strength(0);
        if (s < 0.3F) {
            return;
        }
        AABB box = new AABB(getX() - RADIUS, getY() - 0.5, getZ() - RADIUS, getX() + RADIUS, getY() + HEIGHT, getZ() + RADIUS).inflate(0.5);
        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, box, e -> e != owner && e.isAlive() && !(e instanceof FeatheredKnightEntity))) {
            double dx = e.getX() - getX(), dz = e.getZ() - getZ();
            double r = Math.sqrt(dx * dx + dz * dz);
            if (r > RADIUS + e.getBbWidth() * 0.5) {
                continue;
            }
            if (e instanceof Player pl && (pl.isCreative() || pl.isSpectator())) {
                continue;
            }
            e.hurt(damageSources().indirectMagic(this, owner), DAMAGE);
            // round and up, pulled in a little
            double tx = -dz, tz = dx;
            double n = Math.max(0.3, r);
            Vec3 m = e.getDeltaMovement();
            e.setDeltaMovement(m.x * 0.6 + tx / n * 0.22 - dx / n * 0.05, Math.max(m.y, 0.0) * 0.6 + 0.16, m.z * 0.6 + tz / n * 0.22 - dz / n * 0.05);
            e.hurtMarked = true;
            e.fallDistance = 0;
        }
    }

    private void clientFx() {
        float s = strength(0);
        for (int i = 0; i < 3; i++) {
            double h = random.nextDouble() * HEIGHT * s;
            double r = (0.4 + 0.25 * h) * (0.8 + 0.3 * random.nextDouble());
            double a = (tickCount * 0.6 + random.nextDouble() * Math.PI * 2);
            level().addParticle(FeatheredKnightEntity.gust(), getX() + Math.cos(a) * r, getY() + h, getZ() + Math.sin(a) * r, 0, 0.05, 0);
        }
        if (random.nextInt(2) == 0) {
            double a = random.nextDouble() * Math.PI * 2;
            level().addParticle(ParticleTypes.CLOUD, getX() + Math.cos(a) * 0.9, getY() + 0.1, getZ() + Math.sin(a) * 0.9, -Math.sin(a) * 0.15, 0.03, Math.cos(a) * 0.15);
        }
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double d) {
        return d < 128 * 128;
    }
}
