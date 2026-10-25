package com.rudues.mod.entity;

import com.rudues.mod.registry.ModEntities;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
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

/** Wind Burst: one of the five green gusts. Flies straight, bursts on the first thing it meets. */
public class WindBoltEntity extends Projectile {
    private static final int MAX_AGE = 45;
    /** how big it is drawn and hits (1 = Benedikta's gust; Garuda's are bigger) */
    private static final net.minecraft.network.syncher.EntityDataAccessor<Float> SIZE =
            net.minecraft.network.syncher.SynchedEntityData.defineId(WindBoltEntity.class, net.minecraft.network.syncher.EntityDataSerializers.FLOAT);
    private float damage = FeatheredKnightEntity.BOLT_DAMAGE;

    public WindBoltEntity(EntityType<? extends WindBoltEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public WindBoltEntity(Level level, LivingEntity owner, Vec3 pos, Vec3 velocity) {
        this(ModEntities.WIND_BOLT.get(), level);
        setOwner(owner);
        setPos(pos.x, pos.y - 0.25, pos.z);
        setDeltaMovement(velocity);
        setYRot((float) Math.toDegrees(Math.atan2(-velocity.x, velocity.z)));
        setXRot((float) -Math.toDegrees(Math.atan2(velocity.y, velocity.horizontalDistance())));
    }

    /** a bigger, harder gust (Garuda's) */
    public WindBoltEntity sized(float size, float damage) {
        this.entityData.set(SIZE, size);
        this.damage = damage;
        return this;
    }

    public float size() {
        return this.entityData.get(SIZE);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(SIZE, 1.0F);
    }

    @Override
    public void tick() {
        super.tick();
        Vec3 v = getDeltaMovement();
        Vec3 start = position().add(0, 0.25, 0);
        Vec3 end = start.add(v);
        if (!level().isClientSide) {
            BlockHitResult hb = level().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
            if (hb.getType() != HitResult.Type.MISS) {
                burst(hb.getLocation());
                return;
            }
            Entity owner = getOwner();
            AABB box = getBoundingBox().expandTowards(v).inflate(0.45 * size());
            for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, box, e -> e != owner && e.isAlive() && !(e instanceof FeatheredKnightEntity)
                    && !(e instanceof GarudaEntity))) {
                if (e.hurt(damageSources().indirectMagic(this, owner), damage)) {
                    Vec3 push = v.normalize().scale(0.6);
                    e.setDeltaMovement(e.getDeltaMovement().add(push.x, 0.18, push.z));
                    e.hurtMarked = true;
                }
                burst(e.position().add(0, e.getBbHeight() * 0.5, 0));
                return;
            }
            if (tickCount > MAX_AGE) {
                burst(start);
                return;
            }
        } else if (tickCount > 1) {
            level().addParticle(FeatheredKnightEntity.gust(), start.x - v.x * 0.5, start.y - v.y * 0.5, start.z - v.z * 0.5, 0, 0, 0);
        }
        setPos(end.x, end.y - 0.25, end.z);
    }

    private void burst(Vec3 p) {
        if (level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.CLOUD, p.x, p.y, p.z, 8, 0.2, 0.2, 0.2, 0.06);
            sl.sendParticles(FeatheredKnightEntity.gust(), p.x, p.y, p.z, 10, 0.3, 0.3, 0.3, 0.0);
            FeatheredKnightEntity.windSound(level(), p, SoundEvents.PHANTOM_FLAP, 0.7F, 1.8F);
        }
        discard();
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double d) {
        return d < 96 * 96;
    }
}
