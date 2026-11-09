package com.nazarick.entity;

import javax.annotation.Nullable;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.level.Level;
import com.nazarick.world.NazarickRules;

/**
 * Elder Lich: a floating, hooded undead caster.
 * Hurls volleys of three fire bolts, and blinks away when enemies get close.
 */
public class ElderLichEntity extends Monster implements RangedAttackMob, NazarickAligned {
    public static final byte EVENT_CAST = 104;
    private static final int CAST_TICKS = 24;

    public final AnimationState idleAnimationState = new AnimationState();
    public final AnimationState castAnimationState = new AnimationState();
    private int castClientTicks;
    private int blinkCooldown;

    public ElderLichEntity(EntityType<? extends ElderLichEntity> type, Level level) {
        super(type, level);
        this.xpReward = 20;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 60.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.FOLLOW_RANGE, 32.0D)
                .add(Attributes.ARMOR, 4.0D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new RangedAttackGoal(this, 1.0D, 40, 60, 16.0F));
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 12.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, AbstractVillager.class, false));
    }

    @Override
    public void performRangedAttack(LivingEntity target, float velocity) {
        this.level().broadcastEntityEvent(this, EVENT_CAST);
        this.playSound(SoundEvents.EVOKER_CAST_SPELL, 1.0F, 0.7F);
        double dx = target.getX() - this.getX();
        double dy = target.getY(0.5D) - this.getY(0.6D);
        double dz = target.getZ() - this.getZ();
        for (int i = 0; i < 3; i++) {
            SmallFireball bolt = new SmallFireball(this.level(), this,
                    dx + this.random.nextGaussian() * 0.6D, dy, dz + this.random.nextGaussian() * 0.6D);
            bolt.setPos(this.getX(), this.getY(0.6D), this.getZ());
            this.level().addFreshEntity(bolt);
        }
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (this.getTarget() instanceof Player player && NazarickRules.isProtected(player)) {
            this.setTarget(null);
        }
        if (this.blinkCooldown > 0) {
            this.blinkCooldown--;
        }
        LivingEntity target = this.getTarget();
        if (target != null && this.blinkCooldown == 0 && this.distanceToSqr(target) < 16.0D) {
            double fromX = this.getX(), fromY = this.getY(), fromZ = this.getZ();
            for (int attempt = 0; attempt < 16; attempt++) {
                double x = this.getX() + (this.random.nextDouble() - 0.5D) * 24.0D;
                double y = this.getY() + (this.random.nextInt(9) - 4);
                double z = this.getZ() + (this.random.nextDouble() - 0.5D) * 24.0D;
                if (Math.abs(x - target.getX()) + Math.abs(z - target.getZ()) < 8.0D) {
                    continue;
                }
                if (this.randomTeleport(x, y, z, true)) {
                    this.level().playSound(null, fromX, fromY, fromZ, SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.0F, 0.6F);
                    this.playSound(SoundEvents.ENDERMAN_TELEPORT, 1.0F, 0.6F);
                    this.blinkCooldown = 100;
                    break;
                }
            }
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            this.idleAnimationState.startIfStopped(this.tickCount);
            if (this.castClientTicks > 0 && --this.castClientTicks == 0) {
                this.castAnimationState.stop();
            }
            if (this.random.nextInt(3) == 0) {
                this.level().addParticle(ParticleTypes.SOUL, this.getRandomX(0.6D), this.getY() + this.random.nextDouble() * 0.6D,
                        this.getRandomZ(0.6D), 0.0D, 0.02D, 0.0D);
            }
        }
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == EVENT_CAST) {
            this.castAnimationState.start(this.tickCount);
            this.castClientTicks = CAST_TICKS;
        } else {
            super.handleEntityEvent(id);
        }
    }

    @Override
    public boolean causeFallDamage(float fallDistance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public MobType getMobType() {
        return MobType.UNDEAD;
    }

    @Nullable
    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.SKELETON_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.SKELETON_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.SKELETON_DEATH;
    }

    @Override
    public float getVoicePitch() {
        return 0.7F;
    }
}
