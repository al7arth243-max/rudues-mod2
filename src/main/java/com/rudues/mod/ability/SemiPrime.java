package com.rudues.mod.ability;

import com.rudues.mod.entity.AwakenData;
import com.rudues.mod.entity.FiendEntity;
import com.rudues.mod.network.Network;
import com.rudues.mod.registry.ModParticles;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Semi Prime: the corrupted form. Filling the gauge no longer summons the Hero of Hell - it corrupts you. You play the
 * corruption power-up from the awakening (the left arm erupts, then the legs, then the roar), and then fight wearing
 * the obsidian growths for {@link #DURATION} ticks. The moves are the base moves for now.
 */
public final class SemiPrime {
    private SemiPrime() {
    }

    /** the power-up: the awakening's corruption clip */
    public static final int TRANSFORM_TICKS = AwakenData.END_B;
    /** how long the form lasts once you are corrupted: 60 s */
    public static final int DURATION = 1200;
    public static final String CLIP = "awaken_player_b";

    private static final class State {
        /** ticks into the power-up, or -1 once it is done */
        int transform;
        /** ticks of the form left */
        int left;
        float yaw;
    }

    private static final Map<UUID, State> STATES = new HashMap<>();

    /** corrupted and fighting (the power-up has finished) */
    public static boolean active(Player p) {
        State s = STATES.get(p.getUUID());
        return s != null && s.transform < 0 && s.left > 0;
    }

    /** playing the power-up: rooted, can't cast */
    public static boolean transforming(Player p) {
        State s = STATES.get(p.getUUID());
        return s != null && s.transform >= 0;
    }

    /** in the form at all (powering up or corrupted): the gauge does not fill */
    public static boolean engaged(Player p) {
        return STATES.containsKey(p.getUUID());
    }

    public static int left(Player p) {
        State s = STATES.get(p.getUUID());
        return s == null ? 0 : s.transform >= 0 ? DURATION : s.left;
    }

    private static Vec3 forward(float yaw) {
        double r = Math.toRadians(yaw);
        return new Vec3(-Math.sin(r), 0, Math.cos(r));
    }

    private static Vec3 right(float yaw) {
        double r = Math.toRadians(yaw);
        return new Vec3(-Math.cos(r), 0, -Math.sin(r));
    }

    // ------------------------------------------------------------------ starting
    public static void begin(ServerPlayer p) {
        if (STATES.containsKey(p.getUUID())) {
            return;
        }
        State s = new State();
        s.transform = 0;
        s.left = DURATION;
        s.yaw = p.getYRot();
        STATES.put(p.getUUID(), s);
        root(p, TRANSFORM_TICKS + 4);
        p.setDeltaMovement(Vec3.ZERO);
        p.hurtMarked = true;
        Network.sendPuppet(p, CLIP, TRANSFORM_TICKS + 2, s.yaw, false, true);
        p.displayClientMessage(Component.translatable("message.rudues.semi_prime"), true);
        ServerLevel level = p.serverLevel();
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.2F, 0.4F);
        HeroForm.sync(p);
    }

    private static void root(ServerPlayer p, int ticks) {
        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 9, false, false, false));
        p.addEffect(new MobEffectInstance(MobEffects.JUMP, ticks, 128, false, false, false));
    }

    // ------------------------------------------------------------------ per tick
    public static void tick(ServerPlayer p) {
        State s = STATES.get(p.getUUID());
        if (s == null) {
            return;
        }
        ServerLevel level = p.serverLevel();
        if (s.transform >= 0) {
            tickTransform(p, s, level, s.transform++);
            if (s.transform > TRANSFORM_TICKS) {
                s.transform = -1;
                s.left = DURATION - 1;          // (the client counts down anything below the full time)
                p.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
                p.removeEffect(MobEffects.JUMP);
                PowerData.applyEffects(p);
                com.rudues.mod.trial.TrialManager.syncCorruptionNow(p.getServer());
                HeroForm.sync(p);
            }
            return;
        }
        if (--s.left <= 0) {
            end(p, true);
            return;
        }
        if (s.left % 100 == 0) {
            HeroForm.sync(p);           // keep the client's countdown honest
        }
    }

    private static void tickTransform(ServerPlayer p, State s, ServerLevel level, int t) {
        p.fallDistance = 0;
        Vec3 pp = p.position();
        Vec3 fw = forward(s.yaw), side = right(s.yaw);
        // the left arm burns until the growths burst out of it
        if (t < AwakenData.ARM) {
            int i = Mth.clamp(t, 0, AwakenData.FIRE_B.length / 6 - 1) * 6;
            float[] f = AwakenData.FIRE_B;
            Vec3 fist = pp.add(fw.scale(f[i])).add(side.scale(f[i + 1])).add(0, f[i + 2], 0);
            Vec3 elbow = pp.add(fw.scale(f[i + 3])).add(side.scale(f[i + 4])).add(0, f[i + 5], 0);
            for (int k = 0; k < 6; k++) {
                Vec3 q = elbow.lerp(fist, p.getRandom().nextDouble());
                level.sendParticles(k % 3 == 0 ? ModParticles.PURPLE_FLAME_BIG.get() : ModParticles.PURPLE_FLAME.get(), q.x, q.y, q.z, 1, 0.06, 0.06, 0.06, 0.02);
            }
        }
        if (t == AwakenData.ARM) {
            burst(level, pp.add(side.scale(-0.37)).add(0, 1.2, 0));
            level.playSound(null, pp.x, pp.y, pp.z, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 1.6F, 0.5F);
        }
        if (t == AwakenData.LEGS) {
            for (double k : new double[]{-0.13, 0.13}) {
                burst(level, pp.add(side.scale(k)).add(0, 0.4, 0));
            }
            level.playSound(null, pp.x, pp.y, pp.z, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 1.6F, 0.4F);
        }
        if (t > AwakenData.ARM && t % 3 == 0) {
            level.sendParticles(ModParticles.PURPLE_EMBER.get(), pp.x, pp.y + 1.0, pp.z, 4, 0.3, 0.6, 0.3, 0.02);
        }
        if (t == AwakenData.ROAR) {
            level.playSound(null, pp.x, pp.y, pp.z, SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 2.2F, 0.75F);
            level.playSound(null, pp.x, pp.y, pp.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.2F, 0.7F);
            level.sendParticles(ModParticles.PURPLE_FLAME_BIG.get(), pp.x, pp.y + 1.2, pp.z, 40, 0.5, 0.7, 0.5, 0.12);
            level.sendParticles(ParticleTypes.EXPLOSION, pp.x, pp.y + 1.0, pp.z, 2, 0.3, 0.3, 0.3, 0.0);
            // the roar throws back whoever is crowding you
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(6.0),
                    e -> e != p && e.isAlive() && !(e instanceof FiendEntity f && f.isOwner(p)))) {
                Vec3 away = new Vec3(e.getX() - p.getX(), 0, e.getZ() - p.getZ());
                away = away.lengthSqr() < 1.0E-4 ? fw : away.normalize();
                e.hurt(p.damageSources().playerAttack(p), 10.0F);
                e.setSecondsOnFire(5);
                e.setDeltaMovement(e.getDeltaMovement().add(away.x * 1.6, 0.6, away.z * 1.6));
                e.hurtMarked = true;
            }
        }
        int age = t - AwakenData.ROAR;
        if (age >= 0 && age <= 9) {
            double r = 0.8 + 6.2 * Math.sqrt(age / 9.0);
            int n = (int) (10 + r * 6);
            for (int i = 0; i < n; i++) {
                double a = Math.PI * 2 * (i + p.getRandom().nextDouble() * 0.5) / n;
                level.sendParticles(i % 3 == 0 ? ModParticles.PURPLE_FLAME_BIG.get() : ModParticles.PURPLE_FLAME.get(),
                        pp.x + Math.cos(a) * r, pp.y + 0.15, pp.z + Math.sin(a) * r, 1, 0, 0.05, 0, 0.02);
            }
        }
    }

    private static void burst(ServerLevel level, Vec3 a) {
        level.sendParticles(ModParticles.PURPLE_FLAME_BIG.get(), a.x, a.y, a.z, 10, 0.15, 0.15, 0.15, 0.06);
        level.sendParticles(ModParticles.PURPLE_SPARK.get(), a.x, a.y, a.z, 14, 0.1, 0.1, 0.1, 0.3);
        level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.OBSIDIAN)), a.x, a.y, a.z, 12, 0.12, 0.12, 0.12, 0.15);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, a.x, a.y, a.z, 4, 0.1, 0.1, 0.1, 0.03);
    }

    // ------------------------------------------------------------------ ending
    public static void end(ServerPlayer p, boolean natural) {
        State s = STATES.remove(p.getUUID());
        if (s == null) {
            return;
        }
        if (s.transform >= 0) {
            Network.sendPuppet(p, "", 0, 0.0F, false, false);
            p.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
            p.removeEffect(MobEffects.JUMP);
            PowerData.applyEffects(p);
        }
        if (natural) {
            ServerLevel level = p.serverLevel();
            FxUtil.purpleBurst(level, p.position().add(0, 1.0, 0), 60, 0.6);
            level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.OBSIDIAN)),
                    p.getX(), p.getY() + 0.8, p.getZ(), 30, 0.3, 0.5, 0.3, 0.12);
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 1.2F, 1.4F);
            p.displayClientMessage(Component.translatable("message.rudues.semi_prime_ended"), true);
        }
        if (p.getServer() != null) {
            com.rudues.mod.trial.TrialManager.syncCorruptionNow(p.getServer());
        }
        HeroForm.sync(p);
    }

    /** death / logout: drop it silently */
    public static void cleanup(ServerPlayer p) {
        if (STATES.containsKey(p.getUUID())) {
            end(p, false);
        }
    }
}
