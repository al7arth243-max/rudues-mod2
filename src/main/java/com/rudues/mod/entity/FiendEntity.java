package com.rudues.mod.entity;

import com.rudues.mod.registry.ModParticles;
import com.rudues.mod.ability.FxUtil;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.EnumSet;
import java.util.UUID;

/** Small purple-flame creature. Flies through blocks like a vex, dives at enemies and explodes. */
public class FiendEntity extends PathfinderMob implements GeoEntity {

    private static final EntityDataAccessor<Boolean> CHARGING =
            SynchedEntityData.defineId(FiendEntity.class, EntityDataSerializers.BOOLEAN);
    private static final RawAnimation FLOAT = RawAnimation.begin().thenLoop("animation.flame_spirit.float");
    private static final RawAnimation DIVE = RawAnimation.begin().thenLoop("animation.flame_spirit.dive");
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    @Nullable
    private UUID ownerUUID;
    private int lifeTicks = 20 * 30;

    public FiendEntity(EntityType<? extends FiendEntity> type, Level level) {
        super(type, level);
        this.moveControl = new FiendMoveControl(this);
        this.xpReward = 0;
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 6.0D)
                .add(Attributes.ATTACK_DAMAGE, 4.0D)
                .add(Attributes.FOLLOW_RANGE, 24.0D);
    }

    // ------------------------------------------------------------ owner
    public void setOwner(Player owner) {
        this.ownerUUID = owner.getUUID();
    }

    @Nullable
    public Player getOwner() {
        return ownerUUID == null ? null : level().getPlayerByUUID(ownerUUID);
    }

    public boolean isOwner(LivingEntity e) {
        return ownerUUID != null && e.getUUID().equals(ownerUUID);
    }

    private boolean isValidEnemy(LivingEntity e) {
        return e instanceof Enemy && !(e instanceof FiendEntity) && !isOwner(e);
    }

    // ------------------------------------------------------------ AI
    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new ChargeGoal());
        this.goalSelector.addGoal(2, new HoverGoal());
        this.targetSelector.addGoal(1, new OwnerTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, LivingEntity.class, 5, true, false, this::isValidEnemy));
    }

    @Override
    public void tick() {
        this.noPhysics = true;
        super.tick();
        this.noPhysics = false;
        this.setNoGravity(true);

        if (level().isClientSide) {
            level().addParticle(ModParticles.PURPLE_FLAME.get(), getX() + (random.nextDouble() - 0.5) * 0.4, getY() + 0.2 + random.nextDouble() * 0.6,
                    getZ() + (random.nextDouble() - 0.5) * 0.4, 0, 0, 0);
            if (random.nextInt(3) == 0) {
                level().addParticle(ModParticles.PURPLE_EMBER.get(), getX(), getY() + 0.5, getZ(), 0, 0.02, 0);
            }
        } else {
            boolean charging = getTarget() != null && getTarget().isAlive();
            if (this.entityData.get(CHARGING) != charging) {
                this.entityData.set(CHARGING, charging);
            }
            if (--lifeTicks <= 0) {
                fizzle();
            }
        }
    }

    private void fizzle() {
        if (level() instanceof ServerLevel sl) {
            FxUtil.purpleBurst(sl, position().add(0, 0.4, 0), 15, 0.3);
        }
        discard();
    }

    private void explode() {
        if (level().isClientSide || isRemoved()) {
            return;
        }
        level().explode(this, getX(), getY(), getZ(), 1.8F, true, Level.ExplosionInteraction.NONE);
        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(3.0),
                e -> e != this && !(e instanceof FiendEntity) && !isOwner(e))) {
            e.setSecondsOnFire(8);
        }
        LivingEntity t = getTarget();
        if (t != null && t.isAlive() && distanceToSqr(t) < 16.0) {
            t.hurt(damageSources().mobAttack(this), 8.0F);
        }
        if (level() instanceof ServerLevel sl) {
            FxUtil.purpleBurst(sl, position().add(0, 0.4, 0), 50, 0.8);
        }
        discard();
    }

    // ------------------------------------------------------------ GeckoLib
    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(CHARGING, false);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 2,
                state -> state.setAndContinue(this.entityData.get(CHARGING) ? DIVE : FLOAT)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    // ------------------------------------------------------------ misc
    @Override
    public boolean fireImmune() {
        return true;
    }

    @Override
    public boolean isOnFire() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void doPush(net.minecraft.world.entity.Entity e) {
    }

    @Override
    protected void pushEntities() {
    }

    @Override
    public boolean causeFallDamage(float f, float m, DamageSource s) {
        return false;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.BLAZE_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.BLAZE_DEATH;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (ownerUUID != null) {
            tag.putUUID("Owner", ownerUUID);
        }
        tag.putInt("LifeTicks", lifeTicks);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("Owner")) {
            ownerUUID = tag.getUUID("Owner");
        }
        if (tag.contains("LifeTicks")) {
            lifeTicks = tag.getInt("LifeTicks");
        }
    }

    // ------------------------------------------------------------ inner AI classes
    private class FiendMoveControl extends MoveControl {
        FiendMoveControl(FiendEntity fiend) {
            super(fiend);
        }

        @Override
        public void tick() {
            if (this.operation == Operation.MOVE_TO) {
                Vec3 delta = new Vec3(this.wantedX - mob.getX(), this.wantedY - mob.getY(), this.wantedZ - mob.getZ());
                double dist = delta.length();
                if (dist < mob.getBoundingBox().getSize()) {
                    this.operation = Operation.WAIT;
                    mob.setDeltaMovement(mob.getDeltaMovement().scale(0.5D));
                } else {
                    mob.setDeltaMovement(mob.getDeltaMovement().add(delta.scale(this.speedModifier * 0.05D / dist)));
                    Vec3 motion = mob.getDeltaMovement();
                    mob.setYRot(-((float) Mth.atan2(motion.x, motion.z)) * (180F / (float) Math.PI));
                    mob.yBodyRot = mob.getYRot();
                }
            }
        }
    }

    private class ChargeGoal extends Goal {
        ChargeGoal() {
            setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            LivingEntity t = getTarget();
            return t != null && t.isAlive();
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void tick() {
            LivingEntity t = getTarget();
            if (t == null) {
                return;
            }
            if (getBoundingBox().inflate(0.35).intersects(t.getBoundingBox())) {
                explode();
                return;
            }
            Vec3 p = t.getEyePosition();
            getMoveControl().setWantedPosition(p.x, p.y, p.z, 1.6D);
        }
    }

    private class HoverGoal extends Goal {
        HoverGoal() {
            setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return getTarget() == null && !getMoveControl().hasWanted() && random.nextInt(5) == 0;
        }

        @Override
        public boolean canContinueToUse() {
            return false;
        }

        @Override
        public void start() {
            Player owner = getOwner();
            Vec3 c = owner != null ? owner.position().add(0, 2.0, 0) : position();
            getMoveControl().setWantedPosition(
                    c.x + random.nextInt(9) - 4, c.y + random.nextInt(3) - 1, c.z + random.nextInt(9) - 4, 1.0D);
        }
    }

    /** Targets whatever the owner just hit or was hit by. */
    private static class OwnerTargetGoal extends TargetGoal {
        private final FiendEntity fiend;
        private LivingEntity candidate;

        OwnerTargetGoal(FiendEntity fiend) {
            super(fiend, false);
            this.fiend = fiend;
            setFlags(EnumSet.of(Goal.Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            Player owner = fiend.getOwner();
            if (owner == null) {
                return false;
            }
            LivingEntity t = null;
            if (owner.getLastHurtByMob() != null && owner.tickCount - owner.getLastHurtByMobTimestamp() < 200) {
                t = owner.getLastHurtByMob();
            } else if (owner.getLastHurtMob() != null && owner.tickCount - owner.getLastHurtMobTimestamp() < 200) {
                t = owner.getLastHurtMob();
            }
            if (t == null || t == owner || t instanceof FiendEntity || !t.isAlive()) {
                return false;
            }
            candidate = t;
            return canAttack(t, TargetingConditions.DEFAULT);
        }

        @Override
        public void start() {
            mob.setTarget(candidate);
            super.start();
        }
    }
}
