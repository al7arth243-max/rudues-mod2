package com.rudues.mod.entity;

import com.rudues.mod.registry.ModEntities;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Aeroga: a ball of wind that gathers above the Knight's raised hand, then is hurled. It bursts on whatever it meets
 * and leaves a tornado standing on the ground beneath for five seconds.
 */
public class AerogaOrbEntity extends Projectile {
    private static final EntityDataAccessor<Boolean> LAUNCHED = SynchedEntityData.defineId(AerogaOrbEntity.class, EntityDataSerializers.BOOLEAN);
    /** ticks it takes to gather to full size */
    public static final int GROW = KnightMoveData.AEROGA_LAUNCH - KnightMoveData.AEROGA_GATHER;
    public static final float SIZE = 1.7F;
    private static final double SPEED = 0.95, RADIUS = 2.8;
    private int flight;

    public AerogaOrbEntity(EntityType<? extends AerogaOrbEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public AerogaOrbEntity(Level level, FeatheredKnightEntity owner) {
        this(ModEntities.AEROGA_ORB.get(), level);
        setOwner(owner);
        Vec3 a = owner.orbAnchor(1.0F);
        setPos(a.x, a.y - 0.5, a.z);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(LAUNCHED, false);
    }

    public boolean launched() {
        return this.entityData.get(LAUNCHED);
    }

    /** the orb's size now (blocks across) */
    public float size(float partialTick) {
        if (launched()) {
            return SIZE;
        }
        float k = Mth.clamp((tickCount + partialTick) / GROW, 0.0F, 1.0F);
        return SIZE * (0.15F + 0.85F * (1.0F - (1.0F - k) * (1.0F - k)));
    }

    public void launch(Vec3 target) {
        Vec3 from = position().add(0, 0.5, 0);
        Vec3 d = target.add(0, 0.3, 0).subtract(from);
        setDeltaMovement(d.normalize().scale(SPEED));
        this.entityData.set(LAUNCHED, true);
    }

    @Override
    public void tick() {
        super.tick();
        if (!launched()) {
            // held up over her hand
            if (getOwner() instanceof FeatheredKnightEntity k && k.isAlive()) {
                Vec3 a = k.orbAnchor(1.0F);
                setPos(a.x, a.y - 0.5, a.z);
            } else if (!level().isClientSide) {
                discard();
                return;
            }
            setDeltaMovement(Vec3.ZERO);
            if (level().isClientSide) {
                gatherFx();
            }
            return;
        }
        flight++;
        Vec3 v = getDeltaMovement();
        Vec3 start = position().add(0, 0.5, 0);
        Vec3 end = start.add(v);
        if (!level().isClientSide) {
            BlockHitResult hb = level().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
            if (hb.getType() != HitResult.Type.MISS) {
                burst(hb.getLocation().subtract(v.normalize().scale(0.3)));
                return;
            }
            Entity owner = getOwner();
            AABB box = getBoundingBox().expandTowards(v).inflate(0.4);
            if (!level().getEntitiesOfClass(LivingEntity.class, box, e -> e != owner && e.isAlive() && !(e instanceof FeatheredKnightEntity)).isEmpty()) {
                burst(end);
                return;
            }
            if (flight > 60) {
                burst(end);
                return;
            }
        } else {
            for (int i = 0; i < 2; i++) {
                level().addParticle(FeatheredKnightEntity.gust(), start.x + (random.nextDouble() - 0.5), start.y + (random.nextDouble() - 0.5),
                        start.z + (random.nextDouble() - 0.5), 0, 0, 0);
            }
        }
        setPos(end.x, end.y - 0.5, end.z);
    }

    private void gatherFx() {
        // air drawn in to the ball
        Vec3 c = position().add(0, 0.5, 0);
        for (int i = 0; i < 2; i++) {
            Vec3 d = new Vec3(random.nextDouble() - 0.5, random.nextDouble() - 0.5, random.nextDouble() - 0.5).normalize().scale(2.2);
            Vec3 p = c.add(d);
            level().addParticle(FeatheredKnightEntity.gust(), p.x, p.y, p.z, 0, 0, 0);
            level().addParticle(ParticleTypes.CLOUD, p.x, p.y, p.z, -d.x * 0.08, -d.y * 0.08, -d.z * 0.08);
        }
    }

    private void burst(Vec3 p) {
        Entity owner = getOwner();
        if (level() instanceof ServerLevel sl) {
            for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, new AABB(p, p).inflate(RADIUS),
                    e -> e != owner && e.isAlive() && !(e instanceof FeatheredKnightEntity))) {
                Vec3 c = e.position().add(0, e.getBbHeight() * 0.5, 0);
                if (c.distanceTo(p) <= RADIUS + e.getBbWidth() * 0.5 && e.hurt(damageSources().indirectMagic(this, owner), FeatheredKnightEntity.ORB_DAMAGE)) {
                    Vec3 away = c.subtract(p);
                    away = new Vec3(away.x, 0, away.z);
                    away = away.lengthSqr() < 1e-4 ? Vec3.ZERO : away.normalize();
                    e.setDeltaMovement(e.getDeltaMovement().add(away.x * 0.9, 0.55, away.z * 0.9));
                    e.hurtMarked = true;
                }
            }
            sl.sendParticles(ParticleTypes.CLOUD, p.x, p.y, p.z, 30, 0.8, 0.6, 0.8, 0.15);
            sl.sendParticles(FeatheredKnightEntity.gust(), p.x, p.y, p.z, 40, 1.2, 0.8, 1.2, 0.0);
            sl.sendParticles(ParticleTypes.SWEEP_ATTACK, p.x, p.y, p.z, 3, 0.6, 0.3, 0.6, 0.0);
            FeatheredKnightEntity.windSound(sl, p, SoundEvents.GENERIC_EXPLODE, 0.7F, 1.5F);
            FeatheredKnightEntity.windSound(sl, p, SoundEvents.TRIDENT_RIPTIDE_1, 1.2F, 0.8F);
            // the tornado, on the ground below
            BlockHitResult down = sl.clip(new ClipContext(p.add(0, 0.5, 0), p.add(0, -8, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
            Vec3 g = down.getType() == HitResult.Type.MISS ? p : down.getLocation();
            sl.addFreshEntity(new TornadoEntity(sl, owner instanceof LivingEntity le ? le : null, g));
        }
        discard();
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
