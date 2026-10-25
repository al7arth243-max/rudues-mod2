package com.rudues.mod.ability;

import com.rudues.mod.registry.ModParticles;
import com.rudues.mod.entity.FiendEntity;
import com.rudues.mod.entity.RageBlastEntity;
import com.rudues.mod.entity.WindSlashEntity;
import com.rudues.mod.registry.ModEntities;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Server-side execution of all abilities. */
public class AbilityHandler {
    private static final Map<UUID, long[]> COOLDOWNS = new HashMap<>();

    public static void clear(UUID id) {
        COOLDOWNS.remove(id);
    }

    public static void tryCast(ServerPlayer player, int index) {
        AbilityType type = AbilityType.byIndex(index);
        if (type == null || !HeroForm.empowered(player)) {
            return;
        }
        // base moves only outside Hero of Hell, hero moves only during it
        if (type.hero != HeroForm.isHero(player)) {
            return;
        }
        if (type.hero && !HeroForm.canAct(player)) {
            return; // still busy with the previous demon move
        }
        if (Pummel.busy(player) || HeroForm.inClash(player) || HeroForm.transforming(player) || SemiPrime.transforming(player)
                || com.rudues.mod.entity.InfernalShadowEntity.AWAKENING.contains(player.getUUID())
                || com.rudues.mod.castle.CastleBoss.inIntro(player)
                || com.rudues.mod.entity.FeatheredKnightEntity.inScene(player)
                || player.getVehicle() instanceof com.rudues.mod.entity.FeatheredKnightEntity) {
            return;
        }
        if (type == AbilityType.HERO_OF_HELL && (!PowerData.has(player) || com.rudues.mod.trial.TrialManager.inTrial(player))) {
            // the trial decides for itself when you fight as the Hero of Hell
            player.displayClientMessage(Component.translatable("message.rudues.hero_unavailable"), true);
            return;
        }
        long now = player.level().getGameTime();
        long[] cds = COOLDOWNS.computeIfAbsent(player.getUUID(), k -> new long[AbilityType.values().length]);
        if (now < cds[index]) {
            long secs = (cds[index] - now + 19) / 20;
            player.displayClientMessage(Component.translatable("message.rudues.cooldown",
                    type.displayName(), String.valueOf(secs)), true);
            return;
        }
        cds[index] = now + type.cooldown;

        switch (type) {
            case RAGE_BLAST -> rageBlast(player);
            case OBLITERATOR -> punch(player, false);
            case FIEND_SUMMON -> fiendSummon(player);
            case VIOLENT_GALE -> violentGale(player);
            case STAND_GROUND -> standGround(player);
            case PUMMEL -> Pummel.start(player);
            case HERO_OF_HELL -> {
                if (!HeroForm.transformByMove(player)) {
                    cds[index] = now;           // already changing: no cooldown spent
                }
            }
            case CONCENTRATED_BEAM, OBLITERATOR_MAXIMUM, HELLFIRE, SLASH_COMBO, METEOR_IMPACT, TREE_OF_SIN, HEROS_RUSH -> HeroForm.beginMove(player, type);
        }

        if (!type.hero) {
            HeroForm.addGauge(player, type.gauge);
        }
    }

    // ---------------------------------------------------------------- Rage Blast
    private static void rageBlast(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Vec3 look = player.getLookAngle();
        RageBlastEntity blast = new RageBlastEntity(level, player, look.x, look.y, look.z);
        blast.setPos(player.getX() + look.x * 1.2, player.getEyeY() - 0.15 + look.y * 1.2, player.getZ() + look.z * 1.2);
        blast.setDeltaMovement(look.scale(1.4));
        level.addFreshEntity(blast);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.2F, 0.6F);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.0F, 0.5F);
        player.swing(InteractionHand.MAIN_HAND, true);
    }

    // ---------------------------------------------------------------- Obliterator / Abyssal Obliterator
    private static void punch(ServerPlayer player, boolean abyssal) {
        ServerLevel level = player.serverLevel();
        float damage = abyssal ? 60.0F : 30.0F;
        double reach = abyssal ? 5.0 : 4.0;
        double knockback = abyssal ? 3.6 : 2.6;
        int fireSeconds = abyssal ? 14 : 10;

        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        AABB box = player.getBoundingBox().expandTowards(look.scale(reach)).inflate(1.5);

        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, box,
                e -> e != player && e.isAlive() && !(e instanceof FiendEntity f && f.isOwner(player)))) {
            Vec3 to = target.getBoundingBox().getCenter().subtract(eye);
            if (to.length() > reach + 1.0 || to.normalize().dot(look) < 0.55) {
                continue;
            }
            target.hurt(player.damageSources().playerAttack(player), damage);
            target.setSecondsOnFire(fireSeconds);
            target.knockback(knockback, player.getX() - target.getX(), player.getZ() - target.getZ());
            target.setDeltaMovement(target.getDeltaMovement().add(0, abyssal ? 0.6 : 0.45, 0));
            target.hurtMarked = true;
            Vec3 c = target.getBoundingBox().getCenter();
            FxUtil.purpleBurst(level, c, abyssal ? 50 : 30, 0.4);
            if (abyssal) {
                level.sendParticles(ModParticles.PURPLE_SPARK.get(), c.x, c.y, c.z, 40, 0.6, 0.6, 0.6, 0.6);
            }
        }

        Vec3 fist = eye.add(look.scale(2.2));
        level.sendParticles(ParticleTypes.EXPLOSION, fist.x, fist.y, fist.z, 2, 0.2, 0.2, 0.2, 0);
        level.sendParticles(ModParticles.PURPLE_FLAME.get(), fist.x, fist.y, fist.z, 25, 0.3, 0.3, 0.3, 0.1);
        FxUtil.purpleBurst(level, fist, abyssal ? 70 : 40, 0.5);
        if (abyssal) {
            level.sendParticles(ModParticles.PURPLE_SPARK.get(), fist.x, fist.y, fist.z, 60, 0.8, 0.8, 0.8, 0.5);
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.6F, 1.4F);
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.8F, 1.3F);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1.0F, 0.6F);
        player.swing(InteractionHand.MAIN_HAND, true);
    }

    // ---------------------------------------------------------------- Fiend Summon
    private static void fiendSummon(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        for (int i = 0; i < 3; i++) {
            FiendEntity fiend = ModEntities.FIEND.get().create(level);
            if (fiend == null) {
                continue;
            }
            fiend.setOwner(player);
            double angle = (Math.PI * 2.0 / 3.0) * i + player.getRandom().nextDouble();
            fiend.moveTo(player.getX() + Math.cos(angle) * 1.5, player.getY() + 1.6, player.getZ() + Math.sin(angle) * 1.5,
                    player.getYRot(), 0.0F);
            level.addFreshEntity(fiend);
            FxUtil.purpleBurst(level, fiend.position().add(0, 0.4, 0), 12, 0.3);
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.EVOKER_CAST_SPELL, SoundSource.PLAYERS, 1.0F, 0.7F);
        player.swing(InteractionHand.MAIN_HAND, true);
    }

    // ---------------------------------------------------------------- Violent Gale
    private static void violentGale(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        WindSlashEntity slash = new WindSlashEntity(level, player);
        level.addFreshEntity(slash);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.2F, 0.6F);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, 0.8F, 1.4F);
        player.swing(InteractionHand.MAIN_HAND, true);
    }

    // ---------------------------------------------------------------- Stand Ground
    private static void standGround(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        player.removeEffect(MobEffects.DAMAGE_RESISTANCE);
        // Resistance V = 100% damage reduction for 8 seconds, plus 10 extra hearts
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 160, 4, false, true, true));
        player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 160, 4, false, true, true));
        FxUtil.purpleBurst(level, player.position().add(0, 1.0, 0), 80, 0.8);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0F, 0.6F);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.6F, 0.5F);
    }
}
