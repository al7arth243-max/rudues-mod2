package com.nazarick.entity;

import com.nazarick.world.NazarickRules;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Albedo, Overseer of the Floor Guardians.
 * To the ruler and staff bearers she is a courteous servant. Intruders who reach the Throne Room face her as a boss:
 * she draws her halberd, a boss bar appears, and she becomes vulnerable to them.
 */
public class AlbedoEntity extends NazarickNpcEntity {
    public static final byte EVENT_ATTACK = 106;
    private static final double GUARD_RANGE = 48.0D;

    private static final List<String> LINES = List.of(
            "The Floor Guardians stand ready. Nazarick awaits your command.",
            "Every corridor has been swept and every post is manned. Is there anything else you require?",
            "Should any intruder dare set foot in this hall, I will deal with them personally.",
            "Your presence honours the Throne Room. Shall I summon the others?",
            "I have reviewed every report. Nothing escapes our notice.");

    public final AnimationState attackAnimationState = new AnimationState();
    private int attackClientTicks;
    private final ServerBossEvent bossEvent = new ServerBossEvent(
            Component.literal("Albedo, Overseer of the Floor Guardians"), BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.PROGRESS);

    public AlbedoEntity(EntityType<? extends AlbedoEntity> type, Level level) {
        super(type, level);
        this.bossEvent.setVisible(false);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return createNpcAttributes()
                .add(Attributes.MAX_HEALTH, 300.0D)
                .add(Attributes.ATTACK_DAMAGE, 12.0D)
                .add(Attributes.ARMOR, 10.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.3D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.6D)
                .add(Attributes.FOLLOW_RANGE, 48.0D);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.25D, true));
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false, this::isIntruder));
    }

    @Override
    protected List<String> greetingLines() {
        return LINES;
    }

    /** An intruder: a non-creative player Nazarick doesn't protect, inside her hall. */
    public boolean isIntruder(LivingEntity entity) {
        if (!(entity instanceof Player player) || player.isCreative() || player.isSpectator()) return false;
        if (NazarickRules.isProtected(player)) return false;
        return !this.hasRestriction() || this.getRestrictCenter().distSqr(player.blockPosition()) < GUARD_RANGE * GUARD_RANGE;
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        if (source.getEntity() instanceof Player player && this.isIntruder(player)) {
            return false;
        }
        return super.isInvulnerableTo(source);
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        LivingEntity target = this.getTarget();
        if (target != null && !this.isIntruder(target)) {
            this.setTarget(null);
            target = null;
        }
        boolean fighting = target != null;
        this.bossEvent.setVisible(fighting);
        this.bossEvent.setProgress(this.getHealth() / this.getMaxHealth());
    }

    @Override
    public void setTarget(@Nullable LivingEntity target) {
        LivingEntity previous = this.getTarget();
        super.setTarget(target);
        if (this.getTarget() instanceof Player player && previous == null && !this.level().isClientSide) {
            player.sendSystemMessage(Component.literal("<Albedo> ").withStyle(ChatFormatting.GOLD)
                    .append(Component.literal("You dare trespass before the Throne of Nazarick? Kneel, or be cut down.").withStyle(ChatFormatting.RED)));
            this.playSound(SoundEvents.ENDER_DRAGON_GROWL, 0.6F, 1.4F);
        }
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        this.level().broadcastEntityEvent(this, EVENT_ATTACK);
        return super.doHurtTarget(target);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide && this.attackClientTicks > 0 && --this.attackClientTicks == 0) {
            this.attackAnimationState.stop();
        }
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == EVENT_ATTACK) {
            this.attackAnimationState.start(this.tickCount);
            this.attackClientTicks = 16;
        } else {
            super.handleEntityEvent(id);
        }
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        this.bossEvent.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        this.bossEvent.removePlayer(player);
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (!this.level().isClientSide && this.level().getServer() != null) {
            this.level().getServer().getPlayerList().broadcastSystemMessage(
                    Component.literal("Albedo, Overseer of the Floor Guardians, has fallen.").withStyle(ChatFormatting.DARK_PURPLE), false);
        }
    }
}
