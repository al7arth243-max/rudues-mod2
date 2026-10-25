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
    /** scale (1 = Aeroga's; Garuda's great twisters are bigger), and how long it lasts */
    private static final net.minecraft.network.syncher.EntityDataAccessor<Float> SIZE =
            net.minecraft.network.syncher.SynchedEntityData.defineId(TornadoEntity.class, net.minecraft.network.syncher.EntityDataSerializers.FLOAT);
    private static final net.minecraft.network.syncher.EntityDataAccessor<Integer> LIFE_T =
            net.minecraft.network.syncher.SynchedEntityData.defineId(TornadoEntity.class, net.minecraft.network.syncher.EntityDataSerializers.INT);
    /** a set course (Garuda's): it rolls along this each tick instead of creeping after you */
    private Vec3 course = Vec3.ZERO;
    private float damage = DAMAGE;
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

    public TornadoEntity big(float size, int life, Vec3 course, float damage) {
        this.entityData.set(SIZE, size);
        this.entityData.set(LIFE_T, life);
        this.course = course;
        this.damage = damage;
        return this;
    }

    public float size() {
        return this.entityData.get(SIZE);
    }

    public int life() {
        return this.entityData.get(LIFE_T);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(SIZE, 1.0F);
        this.entityData.define(LIFE_T, LIFE);
    }

    /** 0..1: how much of it there is (spinning up, dying away) */
    public float strength(float partialTick) {
        float t = tickCount + partialTick;
        return Mth.clamp(Math.min(t / 10.0F, (life() - t) / 15.0F), 0.0F, 1.0F);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            clientFx();
            return;
        }
        if (tickCount >= life()) {
            discard();
            return;
        }
        Entity owner = getOwner();
        // creep after the nearest player
        Player p = level().getNearestPlayer(this, 24.0);
        Vec3 drift = course;
        if (course == Vec3.ZERO && p != null && !p.isSpectator()) {
            Vec3 d = new Vec3(p.getX() - getX(), 0, p.getZ() - getZ());
            if (d.lengthSqr() > 0.25) {
                drift = d.normalize().scale(CREEP);
            }
        }
        double ny = getY();
        if (course != Vec3.ZERO) {
            // a great twister rolls over the ground, up and down the crater's slopes
            ny = level().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    (int) Math.floor(getX() + drift.x), (int) Math.floor(getZ() + drift.z));
        }
        setPos(getX() + drift.x, ny, getZ() + drift.z);
        if (tickCount % 20 == 5) {
            playSound(SoundEvents.ELYTRA_FLYING, 0.5F, 1.4F);
        }
        float s = strength(0);
        if (s < 0.3F) {
            return;
        }
        float sz = size(), rad = RADIUS * sz, hgt = HEIGHT * sz;
        AABB box = new AABB(getX() - rad, getY() - 0.5, getZ() - rad, getX() + rad, getY() + hgt, getZ() + rad).inflate(0.5);
        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, box, e -> e != owner && e.isAlive() && !(e instanceof FeatheredKnightEntity) && !(e instanceof GarudaEntity))) {
            double dx = e.getX() - getX(), dz = e.getZ() - getZ();
            double r = Math.sqrt(dx * dx + dz * dz);
            if (r > rad + e.getBbWidth() * 0.5) {
                continue;
            }
            if (e instanceof Player pl && (pl.isCreative() || pl.isSpectator())) {
                continue;
            }
            e.hurt(damageSources().indirectMagic(this, owner), damage);
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
            double h = random.nextDouble() * HEIGHT * s * size();
            double r = (0.4 + 0.25 * h / size()) * size() * (0.8 + 0.3 * random.nextDouble());
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
