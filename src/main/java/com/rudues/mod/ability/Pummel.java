package com.rudues.mod.ability;

import com.rudues.mod.entity.FiendEntity;
import com.rudues.mod.entity.InfernalHeroEntity;
import com.rudues.mod.entity.InfernalShadowEntity;
import com.rudues.mod.network.Network;
import com.rudues.mod.network.PummelPacket;
import com.rudues.mod.registry.ModParticles;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Pummel (base move): the player rushes forward and lays into the first enemy they reach with six haymakers, left
 * and right, then one wound-up overhand that sends it flying. While it is being beaten the enemy is frozen, standing
 * in reach facing the player; clients stagger it under every blow (PummelScript).
 */
public final class Pummel {
    private Pummel() {
    }

    private static final int RUSH_TICKS = 14;
    private static final double RUSH_SPEED = 1.15;
    private static final float PUNCH_DAMAGE = 4.0F, BIG_DAMAGE = 12.0F;
    /** the biggest thing that can be picked up */
    private static final float MAX_HEIGHT = 3.2F, MAX_WIDTH = 1.8F;

    private static final class State {
        int phase;          // 0 rushing, 1 holding
        int t;
        long start;
        float yaw;
        Vec3 dir = Vec3.ZERO;
        UUID victim;
        boolean noAi;
        boolean noGravity;
        boolean released;
        double groundY;
    }

    private static final Map<UUID, State> ACTIVE = new HashMap<>();

    public static boolean busy(Player p) {
        return ACTIVE.containsKey(p.getUUID());
    }

    private static float yawOf(Vec3 v) {
        return (float) (Mth.atan2(v.z, v.x) * Mth.RAD_TO_DEG) - 90.0F;
    }

    private static boolean corrupt(ServerPlayer p) {
        return com.rudues.mod.trial.TrialManager.isAwakened(p);
    }

    public static void start(ServerPlayer p) {
        Vec3 look = p.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        flat = flat.lengthSqr() < 1.0E-4 ? PummelScript.forward(p.getYRot()) : flat.normalize();
        State s = new State();
        s.dir = flat;
        s.yaw = yawOf(flat);
        ACTIVE.put(p.getUUID(), s);
        Network.sendPuppet(p, "pummel_rush", RUSH_TICKS + 2, s.yaw, false, corrupt(p));
        ServerLevel level = p.serverLevel();
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0F, 0.6F);
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.6F, 0.7F);
    }

    private static boolean grabbable(LivingEntity e, ServerPlayer p) {
        if (e == p || !e.isAlive() || e instanceof ArmorStand || e instanceof InfernalHeroEntity) {
            return false;
        }
        if (e instanceof FiendEntity f && f.isOwner(p)) {
            return false;
        }
        if (e instanceof Player pl && (pl.isCreative() || pl.isSpectator() || busy(pl))) {
            return false;
        }
        if (e instanceof InfernalShadowEntity sh && sh.isInMove()) {
            return false;
        }
        if (ACTIVE.values().stream().anyMatch(st -> e.getUUID().equals(st.victim))) {
            return false;
        }
        return e.getBbHeight() <= MAX_HEIGHT && e.getBbWidth() <= MAX_WIDTH;
    }

    public static void tick(ServerPlayer p) {
        State s = ACTIVE.get(p.getUUID());
        if (s == null) {
            return;
        }
        ServerLevel level = p.serverLevel();
        if (s.phase == 0) {
            s.t++;
            p.setDeltaMovement(s.dir.x * RUSH_SPEED, p.getDeltaMovement().y, s.dir.z * RUSH_SPEED);
            p.hurtMarked = true;
            p.fallDistance = 0;
            level.sendParticles(ModParticles.PURPLE_FLAME.get(), p.getX() - s.dir.x * 0.6, p.getY() + 0.3, p.getZ() - s.dir.z * 0.6, 3, 0.25, 0.2, 0.25, 0.01);
            AABB box = p.getBoundingBox().inflate(0.8, 0.2, 0.8).move(s.dir.scale(1.0));
            LivingEntity hit = null;
            double best = Double.MAX_VALUE;
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, e -> grabbable(e, p))) {
                double d = e.distanceToSqr(p);
                if (d < best) {
                    best = d;
                    hit = e;
                }
            }
            if (hit != null) {
                grab(p, s, hit, level);
            } else if (s.t >= RUSH_TICKS) {
                ACTIVE.remove(p.getUUID());
                p.setDeltaMovement(s.dir.x * 0.2, p.getDeltaMovement().y, s.dir.z * 0.2);
                p.hurtMarked = true;
                Network.sendPuppet(p, "", 0, 0.0F, false, false);       // nothing in reach
            }
            return;
        }
        LivingEntity v = level.getEntity(s.victim) instanceof LivingEntity le ? le : null;
        int t = (int) (level.getGameTime() - s.start);
        if (v == null || !v.isAlive() || v.level() != p.level()) {
            finish(p, s, v);
            return;
        }
        // the player stays put, the enemy stands in reach facing them
        p.setDeltaMovement(0, Math.min(p.getDeltaMovement().y, 0.0), 0);
        p.hurtMarked = true;
        p.fallDistance = 0;
        Vec3 f = PummelScript.forward(s.yaw);
        float dist = PummelScript.distance(v.getBbWidth());
        if (!s.released) {
            double x = p.getX() + f.x * dist, z = p.getZ() + f.z * dist;
            float face = s.yaw + 180.0F;
            if (v instanceof ServerPlayer tp) {
                tp.teleportTo(level, x, s.groundY, z, face, tp.getXRot());
            } else {
                v.setPos(x, s.groundY, z);
                v.setYRot(face);
                v.setYBodyRot(face);
                v.setYHeadRot(face);
            }
            v.setDeltaMovement(Vec3.ZERO);
            v.fallDistance = 0;
            v.hurtMarked = true;
        }
        Vec3 head = new Vec3(p.getX() + f.x * (dist - 0.3), s.groundY + v.getBbHeight() * 0.82, p.getZ() + f.z * (dist - 0.3));
        for (int i = 0; i < PummelData.PUNCHES.length; i++) {
            if (t == PummelData.PUNCHES[i]) {
                hit(p, v, PUNCH_DAMAGE);
                level.sendParticles(ModParticles.PURPLE_FLAME.get(), head.x, head.y, head.z, 10, 0.12, 0.12, 0.12, 0.08);
                level.sendParticles(ParticleTypes.CRIT, head.x, head.y, head.z, 8, 0.2, 0.2, 0.2, 0.3);
                level.playSound(null, head.x, head.y, head.z, SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1.0F, 0.75F + i * 0.04F);
                p.swing(PummelData.RIGHT[i] ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND, true);
            }
        }
        if (t == PummelData.BIG && !s.released) {
            hit(p, v, BIG_DAMAGE);
            release(v, s);
            v.setDeltaMovement(s.dir.x * 2.3, 0.55, s.dir.z * 2.3);
            v.hurtMarked = true;
            v.setSecondsOnFire(3);
            level.sendParticles(ParticleTypes.EXPLOSION, head.x, head.y, head.z, 1, 0, 0, 0, 0);
            level.sendParticles(ModParticles.PURPLE_FLAME_BIG.get(), head.x, head.y, head.z, 24, 0.2, 0.2, 0.2, 0.15);
            level.sendParticles(ModParticles.PURPLE_SPARK.get(), head.x, head.y, head.z, 20, 0.2, 0.2, 0.2, 0.5);
            level.playSound(null, head.x, head.y, head.z, SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.5F, 0.55F);
            level.playSound(null, head.x, head.y, head.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.8F, 1.2F);
            p.swing(InteractionHand.MAIN_HAND, true);
        }
        if (t >= PummelData.END) {
            finish(p, s, v);
        }
    }

    private static void hit(ServerPlayer p, LivingEntity v, float damage) {
        v.invulnerableTime = 0;
        v.hurt(p.damageSources().playerAttack(p), damage);
    }

    private static void grab(ServerPlayer p, State s, LivingEntity v, ServerLevel level) {
        s.phase = 1;
        s.start = level.getGameTime();
        Vec3 d = v.position().subtract(p.position()).multiply(1, 0, 1);
        if (d.lengthSqr() > 1.0E-3) {
            s.dir = d.normalize();
            s.yaw = yawOf(s.dir);
        }
        s.victim = v.getUUID();
        if (v instanceof Mob mob) {
            s.noAi = mob.isNoAi();
            mob.setNoAi(true);
        } else {
            v.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, PummelData.BIG + 4, 10, false, false, false));
            v.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, PummelData.BIG + 4, 10, false, false, false));
        }
        s.noGravity = v.isNoGravity();
        v.setNoGravity(true);
        s.groundY = v.onGround() ? v.getY() : p.getY();
        p.setDeltaMovement(0, p.getDeltaMovement().y, 0);
        p.hurtMarked = true;
        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, PummelData.END + 2, 9, false, false, false));
        p.setYRot(s.yaw);
        p.setYHeadRot(s.yaw);
        PummelPacket pkt = new PummelPacket(p.getId(), v.getId(), s.start, s.yaw, true);
        Network.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p), pkt);
        Network.sendPuppet(p, "pummel", PummelData.END + 2, s.yaw, false, corrupt(p));
        Vec3 c = v.getBoundingBox().getCenter();
        level.sendParticles(ModParticles.PURPLE_FLAME_BIG.get(), c.x, c.y, c.z, 12, 0.3, 0.4, 0.3, 0.05);
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1.0F, 0.5F);
    }

    private static void release(LivingEntity v, State s) {
        if (s.released) {
            return;
        }
        s.released = true;
        if (v instanceof Mob mob) {
            mob.setNoAi(s.noAi);
        }
        v.setNoGravity(s.noGravity);
    }

    private static void finish(ServerPlayer p, State s, LivingEntity v) {
        ACTIVE.remove(p.getUUID());
        if (v != null) {
            release(v, s);
        }
        MobEffectInstance slow = p.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
        if (slow != null && slow.getAmplifier() == 9 && !slow.isVisible()) {
            p.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        }
    }

    /** death, logout: let go */
    public static void cancel(ServerPlayer p) {
        State s = ACTIVE.get(p.getUUID());
        if (s == null) {
            return;
        }
        LivingEntity v = s.victim != null && p.serverLevel().getEntity(s.victim) instanceof LivingEntity le ? le : null;
        if (s.phase == 1) {
            Network.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p),
                    new PummelPacket(p.getId(), v == null ? -1 : v.getId(), s.start, s.yaw, false));
        }
        Network.sendPuppet(p, "", 0, 0.0F, false, false);
        finish(p, s, v);
    }
}
