package com.rudues.mod.entity;

import com.rudues.mod.registry.ModEntities;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
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

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Wind Blade: a wide crescent of green wind. It forms in front of the Knight while she draws back (growing and
 * brightening), and when she sweeps it flies out flat, cutting through everything in its width until it meets a wall.
 */
public class WindBladeEntity extends Projectile {
    private static final EntityDataAccessor<Boolean> FIRED = SynchedEntityData.defineId(WindBladeEntity.class, EntityDataSerializers.BOOLEAN);
    public static final int FORM = KnightMoveData.WIND_BLADE_RELEASE - KnightMoveData.WIND_BLADE_FORM;
    /** half its width, and how far it can fly */
    public static final float HALF_WIDTH = 2.8F;
    private static final double SPEED = 1.35;
    private static final int FLIGHT = 30;
    private int flight;
    private final Set<UUID> hit = new HashSet<>();

    public WindBladeEntity(EntityType<? extends WindBladeEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public WindBladeEntity(Level level, FeatheredKnightEntity owner) {
        this(ModEntities.WIND_BLADE.get(), level);
        setOwner(owner);
        Vec3 a = owner.bladeAnchor(1.0F);
        setPos(a.x, a.y, a.z);
        setYRot(owner.yBodyRot);
    }

    /** a blade already loosed (Swarm's wind slash from her hand) */
    public WindBladeEntity(Level level, FeatheredKnightEntity owner, Vec3 at, Vec3 dir) {
        this(ModEntities.WIND_BLADE.get(), level);
        setOwner(owner);
        setPos(at.x, at.y, at.z);
        fire(dir);
        yRotO = getYRot();
        xRotO = getXRot();
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(FIRED, false);
    }

    public boolean fired() {
        return this.entityData.get(FIRED);
    }

    /** 0..1 while forming, then 1 */
    public float formed(float partialTick) {
        if (fired()) {
            return 1.0F;
        }
        return Mth.clamp((tickCount + partialTick) / FORM, 0.0F, 1.0F);
    }

    /** dies away at the end of its flight */
    public float fade(float partialTick) {
        if (!fired()) {
            return 1.0F;
        }
        return Mth.clamp((FLIGHT + 6 - (flight + partialTick)) / 8.0F, 0.0F, 1.0F);
    }

    public void fire(Vec3 dir) {
        setDeltaMovement(dir.scale(SPEED));
        setYRot((float) Math.toDegrees(Math.atan2(-dir.x, dir.z)));
        setXRot((float) -Math.toDegrees(Math.atan2(dir.y, dir.horizontalDistance())));
        this.entityData.set(FIRED, true);
    }

    @Override
    public void tick() {
        super.tick();
        if (!fired()) {
            if (getOwner() instanceof FeatheredKnightEntity k && k.isAlive()) {
                Vec3 a = k.bladeAnchor(1.0F);
                setPos(a.x, a.y, a.z);
                setYRot(k.yBodyRot);
                yRotO = k.yBodyRotO;
            } else if (!level().isClientSide) {
                discard();
                return;
            }
            setDeltaMovement(Vec3.ZERO);
            return;
        }
        flight++;
        Vec3 v = getDeltaMovement();
        Vec3 start = position(), end = start.add(v);
        if (!level().isClientSide) {
            BlockHitResult hb = level().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
            if (hb.getType() != HitResult.Type.MISS || flight > FLIGHT + 6) {
                if (level() instanceof ServerLevel sl) {
                    Vec3 p = hb.getType() != HitResult.Type.MISS ? hb.getLocation() : end;
                    sl.sendParticles(ParticleTypes.CLOUD, p.x, p.y, p.z, 20, 1.2, 0.4, 1.2, 0.08);
                    sl.sendParticles(FeatheredKnightEntity.gust(), p.x, p.y, p.z, 24, 1.6, 0.4, 1.6, 0.0);
                }
                discard();
                return;
            }
            if (flight <= FLIGHT) {
                cut(start, v);
            }
        }
        setPos(end.x, end.y, end.z);
    }

    private void cut(Vec3 at, Vec3 v) {
        Entity owner = getOwner();
        Vec3 f = new Vec3(v.x, 0, v.z).normalize();
        Vec3 side = new Vec3(-f.z, 0, f.x);
        AABB box = new AABB(at, at.add(v)).inflate(HALF_WIDTH + 0.5, 1.2, HALF_WIDTH + 0.5);
        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, box,
                e -> e != owner && e.isAlive() && !(e instanceof FeatheredKnightEntity) && !hit.contains(e.getUUID()))) {
            Vec3 d = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(at);
            double along = d.dot(f), across = d.dot(side), up = d.y;
            if (along < -1.2 || along > v.length() + 1.2 || Math.abs(across) > HALF_WIDTH + e.getBbWidth() * 0.5 || up < -1.6 || up > 1.4) {
                continue;
            }
            hit.add(e.getUUID());
            if (e.hurt(damageSources().indirectMagic(this, owner), FeatheredKnightEntity.BLADE_DAMAGE)) {
                e.setDeltaMovement(e.getDeltaMovement().add(f.x * 1.1, 0.35, f.z * 1.1));
                e.hurtMarked = true;
                if (level() instanceof ServerLevel sl) {
                    sl.sendParticles(ParticleTypes.SWEEP_ATTACK, e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), 1, 0, 0, 0, 0);
                }
            }
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

    @Override
    public AABB getBoundingBoxForCulling() {
        return super.getBoundingBoxForCulling().inflate(HALF_WIDTH + 0.5, 0.5, HALF_WIDTH + 0.5);
    }
}
