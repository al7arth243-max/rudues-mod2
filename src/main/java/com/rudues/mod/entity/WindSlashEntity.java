package com.rudues.mod.entity;

import com.rudues.mod.registry.ModEntities;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
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
import org.joml.Vector3f;

import java.util.HashSet;
import java.util.Set;

/** Violent Gale: a piercing crescent of wind that flies straight and stops at walls. */
public class WindSlashEntity extends Projectile {
    private static final DustParticleOptions WIND = new DustParticleOptions(new Vector3f(0.88F, 0.82F, 1.0F), 1.6F);
    private static final int MAX_AGE = 22;
    private final Set<Integer> alreadyHit = new HashSet<>();

    public WindSlashEntity(EntityType<? extends WindSlashEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public WindSlashEntity(Level level, LivingEntity owner) {
        this(ModEntities.WIND_SLASH.get(), level);
        setOwner(owner);
        Vec3 look = owner.getLookAngle();
        setPos(owner.getX() + look.x, owner.getEyeY() - 0.2 + look.y, owner.getZ() + look.z);
        setDeltaMovement(look.scale(1.7));
        setYRot(owner.getYRot());
        setXRot(owner.getXRot());
    }

    @Override
    protected void defineSynchedData() {
    }

    @Override
    public void tick() {
        super.tick();
        Vec3 motion = getDeltaMovement();
        Vec3 start = position();
        Vec3 end = start.add(motion);

        if (!level().isClientSide) {
            BlockHitResult hit = level().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, this));
            if (hit.getType() != HitResult.Type.MISS) {
                if (level() instanceof ServerLevel sl) {
                    Vec3 p = hit.getLocation();
                    sl.sendParticles(ParticleTypes.CLOUD, p.x, p.y, p.z, 25, 0.5, 0.5, 0.5, 0.15);
                }
                discard();
                return;
            }

            Entity owner = getOwner();
            AABB box = getBoundingBox().expandTowards(motion).inflate(1.0, 0.5, 1.0);
            for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, box,
                    e -> e != owner && e.isAlive() && !alreadyHit.contains(e.getId())
                            && !(e instanceof FiendEntity f && owner instanceof LivingEntity o && f.isOwner(o)))) {
                alreadyHit.add(e.getId());
                e.hurt(damageSources().indirectMagic(this, owner), 14.0F);
                Vec3 push = motion.normalize().scale(1.2);
                e.setDeltaMovement(e.getDeltaMovement().add(push.x, 0.35, push.z));
                e.hurtMarked = true;
            }

            if (level() instanceof ServerLevel sl) {
                Vec3 dir = motion.normalize();
                Vec3 right = dir.cross(new Vec3(0, 1, 0));
                right = right.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : right.normalize();
                for (int i = -6; i <= 6; i++) {
                    double t = i / 6.0;
                    Vec3 p = start.add(right.scale(t * 2.2)).add(dir.scale(-0.9 * t * t));
                    sl.sendParticles(WIND, p.x, p.y, p.z, 1, 0, 0, 0, 0);
                    if (i % 2 == 0) {
                        sl.sendParticles(ParticleTypes.CLOUD, p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0.01);
                    }
                }
                if (tickCount % 3 == 0) {
                    sl.sendParticles(ParticleTypes.SWEEP_ATTACK, start.x, start.y, start.z, 1, 0, 0, 0, 0);
                }
            }
            if (tickCount > MAX_AGE) {
                discard();
                return;
            }
        }
        setPos(end);
    }

    @Override
    public boolean isPickable() {
        return false;
    }
}
