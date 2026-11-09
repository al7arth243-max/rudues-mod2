package com.nazarick.entity;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import com.nazarick.world.NazarickRules;

/**
 * Death Knight: a 3-block-tall undead warrior (Warden scale).
 * - Heavy overhead flamberge swings that launch targets.
 * - Blocks projectiles that hit it from the front with its tower shield.
 * - Roars when it first locks onto a target.
 * - Undying: the first blow that would kill it leaves it at 1 HP instead.
 */
public class DeathKnightEntity extends Monster implements NazarickAligned {
    public static final byte EVENT_ATTACK = 101;
    public static final byte EVENT_BLOCK = 102;
    public static final byte EVENT_ROAR = 103;

    public final AnimationState idleAnimationState = new AnimationState();
    public final AnimationState attackAnimationState = new AnimationState();
    public final AnimationState blockAnimationState = new AnimationState();
    public final AnimationState roarAnimationState = new AnimationState();
    private int attackClientTicks;
    private int blockClientTicks;
    private int roarClientTicks;
    private int roarServerTicks;
    private boolean undyingUsed;

    public DeathKnightEntity(EntityType<? extends DeathKnightEntity> type, Level level) {
        super(type, level);
        this.xpReward = 50;
        this.setMaxUpStep(1.0F);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 160.0D)
                .add(Attributes.ATTACK_DAMAGE, 14.0D)
                .add(Attributes.ATTACK_KNOCKBACK, 1.2D)
                .add(Attributes.MOVEMENT_SPEED, 0.24D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
                .add(Attributes.ARMOR, 14.0D)
                .add(Attributes.ARMOR_TOUGHNESS, 6.0D)
                .add(Attributes.FOLLOW_RANGE, 32.0D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0D, true));
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.7D));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 12.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, AbstractVillager.class, false));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, IronGolem.class, true));
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        this.level().broadcastEntityEvent(this, EVENT_ATTACK);
        boolean hit = super.doHurtTarget(target);
        if (hit) {
            target.setDeltaMovement(target.getDeltaMovement().add(0.0D, 0.35D, 0.0D));
        }
        return hit;
    }

    @Override
    public void setTarget(@Nullable LivingEntity target) {
        LivingEntity previous = this.getTarget();
        super.setTarget(target);   // Nazarick's rules may veto this (see NazarickEvents)
        if (this.getTarget() != null && previous == null && !this.level().isClientSide) {
            this.level().broadcastEntityEvent(this, EVENT_ROAR);
            this.playSound(SoundEvents.RAVAGER_ROAR, 1.5F, 0.5F);
            this.roarServerTicks = 30;
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!this.level().isClientSide && source.getDirectEntity() instanceof Projectile
                && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            Vec3 from = source.getSourcePosition();
            if (from != null) {
                Vec3 look = this.getViewVector(1.0F);
                Vec3 to = from.subtract(this.position()).normalize();
                if (to.x * look.x + to.z * look.z > 0.2D) {
                    this.level().broadcastEntityEvent(this, EVENT_BLOCK);
                    this.playSound(SoundEvents.SHIELD_BLOCK, 1.0F, 0.8F);
                    return false;
                }
            }
        }
        return super.hurt(source, amount);
    }

    /** Undying: the first lethal blow leaves the Death Knight standing at 1 HP. (/kill still works.) */
    @Override
    public void setHealth(float health) {
        if (health <= 0.0F && health > -1000.0F && !this.undyingUsed && !this.level().isClientSide && this.isAlive()) {
            this.undyingUsed = true;
            super.setHealth(1.0F);
            this.level().broadcastEntityEvent(this, EVENT_ROAR);
            this.playSound(SoundEvents.RAVAGER_ROAR, 2.0F, 0.4F);
            this.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 60, 4));
            return;
        }
        super.setHealth(health);
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (this.getTarget() instanceof Player player && NazarickRules.isProtected(player)) {
            this.setTarget(null);
        }
        if (this.roarServerTicks > 0) {
            this.roarServerTicks--;
            this.getNavigation().stop();
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            this.idleAnimationState.startIfStopped(this.tickCount);
            if (this.attackClientTicks > 0 && --this.attackClientTicks == 0) this.attackAnimationState.stop();
            if (this.blockClientTicks > 0 && --this.blockClientTicks == 0) this.blockAnimationState.stop();
            if (this.roarClientTicks > 0 && --this.roarClientTicks == 0) this.roarAnimationState.stop();
        }
    }

    @Override
    public void handleEntityEvent(byte id) {
        switch (id) {
            case EVENT_ATTACK -> { this.attackAnimationState.start(this.tickCount); this.attackClientTicks = 20; }
            case EVENT_BLOCK -> { this.blockAnimationState.start(this.tickCount); this.blockClientTicks = 16; }
            case EVENT_ROAR -> { this.roarAnimationState.start(this.tickCount); this.roarClientTicks = 40; }
            default -> super.handleEntityEvent(id);
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("UndyingUsed", this.undyingUsed);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.undyingUsed = tag.getBoolean("UndyingUsed");
    }

    @Override
    public MobType getMobType() {
        return MobType.UNDEAD;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        this.playSound(SoundEvents.IRON_GOLEM_STEP, 1.0F, 0.6F);
    }

    @Nullable
    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.WITHER_SKELETON_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.WITHER_SKELETON_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.WITHER_SKELETON_DEATH;
    }

    @Override
    public float getVoicePitch() {
        return 0.6F;
    }
}
