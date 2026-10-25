package com.rudues.mod.ability;

import com.rudues.mod.registry.ModParticles;
import com.rudues.mod.entity.BeamEntity;
import com.rudues.mod.entity.DemonEntity;
import com.rudues.mod.entity.FiendEntity;
import com.rudues.mod.entity.RageBlastEntity;
import com.rudues.mod.network.Network;
import com.rudues.mod.registry.ModEffects;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Server side of the Hero of Hell mechanic.
 * phase 0: gauge is filling   phase 1: flame-pillar transformation   phase 2: demon form (boss bar + 4 moves)
 */
public class HeroForm {
    private static final UUID HEALTH_MOD = UUID.fromString("5d3f9f0e-2f55-4a0b-9d9e-2b1d6c0a7e11");
    /** Extra max health while a demon (boss-bar sized health pool). */
    private static final double BONUS_HEALTH = 180.0;
    private static final int TRANSFORM_TICKS = 60;


    private static class State {
        int gauge;
        int phase;
        int phaseTicks;
        int busy;
        int attackTicks;
        AbilityType attack;
        UUID avatar;
        UUID beam;
        int mPhase;
        int mTicks;
        UUID grabbed;
        boolean grabNoAi;
        double peakY;
        double lastX;
        double lastZ;
        ServerBossEvent bar;
        /** locked in an end-fight clash with the Infernal Hero: no moves, the clash drives the demon */
        boolean clash;
    }

    private static final Map<UUID, State> STATES = new HashMap<>();

    private static State state(ServerPlayer p) {
        return STATES.computeIfAbsent(p.getUUID(), k -> new State());
    }

    // ------------------------------------------------------------------ queries
    public static boolean isHero(net.minecraft.world.entity.player.Player p) {
        State s = STATES.get(p.getUUID());
        return s != null && s.phase == 2;
    }

    /** in the flame pillar, turning into the demon */
    public static boolean transforming(net.minecraft.world.entity.player.Player p) {
        State s = STATES.get(p.getUUID());
        return s != null && s.phase == 1;
    }

    /** carries the corruption right now: Semi Prime, or awakened in the trial */
    public static boolean corrupted(ServerPlayer p) {
        return SemiPrime.active(p) || com.rudues.mod.trial.TrialManager.isAwakened(p);
    }

    /** the Hero of Hell move: straight into the flame pillar. False if already transforming or transformed. */
    public static boolean transformByMove(ServerPlayer p) {
        State s = state(p);
        if (s.phase != 0) {
            return false;
        }
        startTransformation(p, s);
        sync(p);
        return true;
    }

    public static boolean canAct(ServerPlayer p) {
        State s = STATES.get(p.getUUID());
        return s != null && s.phase == 2 && s.busy <= 0 && !s.clash;
    }

    public static void sync(ServerPlayer p) {
        State s = STATES.get(p.getUUID());
        int gauge = s == null ? 0 : s.gauge;
        int remaining = (s != null && s.phase == 2) ? Math.max(0, HeroConstants.DEMON_TICKS - s.phaseTicks) : 0;
        Network.sendState(p, empowered(p), gauge, remaining, SemiPrime.engaged(p) ? SemiPrime.left(p) : 0);
    }

    // ------------------------------------------------------------------ the Infernal Hero's fight
    /** holds the power, or is borrowing the Hero of Hell form for the trial's last fight */
    public static boolean empowered(net.minecraft.world.entity.player.Player p) {
        return PowerData.has(p) || (p instanceof ServerPlayer sp
                && (com.rudues.mod.trial.TrialManager.heroGranted(sp) || com.rudues.mod.trial.TrialManager.isAwakened(sp)));
    }

    /** the Hero fights Titan against Titan: whoever it hunts is pulled into the Hero of Hell form */
    public static void forceForm(ServerPlayer p) {
        if (!empowered(p)) {
            return;
        }
        State s = state(p);
        if (s.phase == 0) {
            startTransformation(p, s);
            sync(p);
        }
    }

    /** while the Hero lives the form doesn't run out (it never drops below 20 s left) */
    public static void sustain(ServerPlayer p) {
        State s = STATES.get(p.getUUID());
        if (s == null || s.phase != 2) {
            return;
        }
        if (s.phaseTicks > HeroConstants.DEMON_TICKS - 400) {
            s.phaseTicks = HeroConstants.DEMON_TICKS - 400;
        }
        if (p.tickCount % 20 == 0) {
            // the client counts the form down on its own: tell it the form is still on, or its moves lock out
            sync(p);
            // and keep the form's own Speed II / Jump Boost IX from running out with the original timer
            MobEffectInstance speed = p.getEffect(MobEffects.MOVEMENT_SPEED);
            if (speed == null || speed.getDuration() < 100) {
                p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 440, 1, false, false, true));
            }
            MobEffectInstance jump = p.getEffect(MobEffects.JUMP);
            if (s.attack == null && (jump == null || jump.getDuration() < 100)) {
                p.addEffect(new MobEffectInstance(MobEffects.JUMP, 440, 8, false, false, true));
            }
        }
    }

    public static void setClash(ServerPlayer p, boolean on) {
        State s = STATES.get(p.getUUID());
        if (s == null) {
            return;
        }
        s.clash = on;
        if (on) {
            s.attack = null;
            s.busy = 0;
            discardBeam(p, s);
            releaseGrab(p.serverLevel(), s);
        }
    }

    public static boolean inClash(net.minecraft.world.entity.player.Player p) {
        State s = STATES.get(p.getUUID());
        return s != null && s.clash;
    }

    /** plays a clip on the player's demon */
    public static void triggerAvatar(ServerPlayer p, String anim) {
        State s = STATES.get(p.getUUID());
        if (s != null) {
            trigger(p, s, anim);
        }
    }

    // ------------------------------------------------------------------ gauge
    public static void addGauge(ServerPlayer p, int amount) {
        if (!PowerData.has(p)) {
            return;         // the moves borrowed in the trial don't build toward the Hero of Hell form
        }
        State s = state(p);
        if (amount <= 0 || SemiPrime.engaged(p)) {
            return;
        }
        s.gauge = Math.min(HeroConstants.GAUGE_MAX, s.gauge + amount);
        if (s.gauge >= HeroConstants.GAUGE_MAX) {
            // a full gauge corrupts you: Semi Prime
            s.gauge = 0;
            SemiPrime.begin(p);
        }
        sync(p);
    }

    private static void startTransformation(ServerPlayer p, State s) {
        s.phase = 1;
        s.phaseTicks = 0;
        p.setInvulnerable(true);
        // root the player for the transformation
        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, TRANSFORM_TICKS + 10, 9, false, false, false));
        p.addEffect(new MobEffectInstance(MobEffects.JUMP, TRANSFORM_TICKS + 10, 128, false, false, false));
        p.displayClientMessage(Component.translatable("message.rudues.hero_awakened"), true);
    }

    // ------------------------------------------------------------------ per-tick
    public static void tick(ServerPlayer p) {
        State s = STATES.get(p.getUUID());
        if (s == null || s.phase == 0) {
            return;
        }
        ServerLevel level = p.serverLevel();
        if (s.phase == 1) {
            tickTransform(p, s, level);
        } else {
            tickDemon(p, s, level);
        }
    }

    private static void tickTransform(ServerPlayer p, State s, ServerLevel level) {
        int t = ++s.phaseTicks;
        p.fallDistance = 0;

        // pillar of purple flame engulfing the player
        double h = Math.min(24.0, t * 3.0);
        for (double y = 0; y <= h; y += 1.6) {
            double spread = 1.8 - Math.min(0.8, y / 30.0);
            level.sendParticles(ModParticles.PURPLE_FLAME_BIG.get(), p.getX(), p.getY() + y, p.getZ(), 4, spread, 0.4, spread, 0.02);
            level.sendParticles(ModParticles.PURPLE_FLAME.get(), p.getX(), p.getY() + y, p.getZ(), 2, spread, 0.4, spread, 0.04);
            if (((int) (y / 1.6)) % 2 == 0) {
                level.sendParticles(ModParticles.PURPLE_EMBER.get(), p.getX(), p.getY() + y, p.getZ(), 1, spread, 0.4, spread, 0.03);
            }
        }
        if (t == 1) {
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 1.2F, 0.7F);
        }
        if (t % 10 == 0) {
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.0F, 0.4F);
        }

        // the demon rises inside the pillar
        if (t == 45) {
            p.addEffect(new MobEffectInstance(ModEffects.HERO_OF_HELL.get(), -1, 0, false, false, false));
            p.refreshDimensions();
            DemonEntity demon = new DemonEntity(level, p, true);
            level.addFreshEntity(demon);
            s.avatar = demon.getUUID();
        }

        // pillar fades, blast wave, demon is out
        if (t >= TRANSFORM_TICKS) {
            s.phase = 2;
            s.phaseTicks = 0;
            s.busy = 0;
            s.attack = null;
            p.setInvulnerable(false);

            AttributeInstance hp = p.getAttribute(Attributes.MAX_HEALTH);
            if (hp != null && hp.getModifier(HEALTH_MOD) == null) {
                hp.addTransientModifier(new AttributeModifier(HEALTH_MOD, "rudues_hero_of_hell", BONUS_HEALTH,
                        AttributeModifier.Operation.ADDITION));
            }
            p.setHealth(p.getMaxHealth());
            p.removeEffect(MobEffects.JUMP);
            p.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
            p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, HeroConstants.DEMON_TICKS + 20, 1, false, false, true));
            // Jump Boost IX: roughly a 9 block leap for a 7 block demon
            p.addEffect(new MobEffectInstance(MobEffects.JUMP, HeroConstants.DEMON_TICKS + 20, 8, false, false, true));

            s.bar = new ServerBossEvent(Component.translatable("gui.rudues.hero_of_hell"),
                    BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.PROGRESS);
            s.bar.setDarkenScreen(false);
            s.bar.setProgress(1.0F);
            s.bar.addPlayer(p);

            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(15.0),
                    e -> e != p && !(e instanceof FiendEntity f && f.isOwner(p)))) {
                Vec3 away = new Vec3(e.getX() - p.getX(), 0, e.getZ() - p.getZ());
                away = away.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : away.normalize();
                e.hurt(p.damageSources().playerAttack(p), 20.0F);
                e.setSecondsOnFire(8);
                e.setDeltaMovement(e.getDeltaMovement().add(away.x * 2.2, 0.9, away.z * 2.2));
                e.hurtMarked = true;
            }
            FxUtil.purpleBurst(level, p.position().add(0, 3.0, 0), 340, 4.5);
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, p.getX(), p.getY() + 1.0, p.getZ(), 1, 0, 0, 0, 0);
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.5F, 0.6F);
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 1.5F, 0.5F);
            sync(p);
        }
    }

    private static void tickDemon(ServerPlayer p, State s, ServerLevel level) {
        s.phaseTicks++;
        if (s.busy > 0) {
            s.busy--;
        }
        if (s.bar != null) {
            s.bar.setProgress(Mth.clamp(p.getHealth() / p.getMaxHealth(), 0.0F, 1.0F));
        }
        if (s.phaseTicks >= HeroConstants.DEMON_TICKS) {
            endForm(p, s, true);
            return;
        }

        // keep the visual demon alive (e.g. after changing dimension)
        Entity existing = s.avatar == null ? null : level.getEntity(s.avatar);
        if (existing == null || existing.isRemoved()) {
            DemonEntity demon = new DemonEntity(level, p, false);
            level.addFreshEntity(demon);
            s.avatar = demon.getUUID();
        }

        // ember aura
        if (p.tickCount % 3 == 0) {
            level.sendParticles(ModParticles.PURPLE_FLAME.get(), p.getX(), p.getY() + 3.5, p.getZ(), 8, 1.8, 3.0, 1.8, 0.02);
            level.sendParticles(ModParticles.PURPLE_EMBER.get(), p.getX(), p.getY() + 0.3, p.getZ(), 2, 1.2, 0.1, 1.2, 0.02);
        }

        if (s.attack != null) {
            s.attackTicks++;
            switch (s.attack) {
                case CONCENTRATED_BEAM -> tickBeam(p, s, level);
                case OBLITERATOR_MAXIMUM -> tickPunch(p, s, level);
                case HELLFIRE -> tickHellfire(p, s, level);
                case SLASH_COMBO -> tickSlash(p, s, level);
                case METEOR_IMPACT -> tickMeteor(p, s, level);
                case TREE_OF_SIN -> tickTree(p, s, level);
                case HEROS_RUSH -> tickHerosRush(p, s, level);
                default -> s.attack = null;
            }
        }
    }

    // ------------------------------------------------------------------ ending
    /** out of the Hero of Hell form (or its flame pillar) at once, for a scripted scene */
    public static void revert(ServerPlayer p) {
        State s = STATES.get(p.getUUID());
        if (s != null && s.phase != 0) {
            endForm(p, s, false);
        }
    }

    private static void endForm(ServerPlayer p, State s, boolean natural) {
        cleanupVisuals(p, s);
        s.phase = 0;
        s.phaseTicks = 0;
        s.attack = null;
        s.busy = 0;
        if (natural) {
            ServerLevel level = p.serverLevel();
            FxUtil.purpleBurst(level, p.position().add(0, 2.0, 0), 160, 2.0);
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 1.0F, 1.5F);
            p.displayClientMessage(Component.translatable("message.rudues.hero_ended"), true);
        }
        sync(p);
    }

    private static void cleanupVisuals(ServerPlayer p, State s) {
        p.removeEffect(ModEffects.HERO_OF_HELL.get());
        p.removeEffect(MobEffects.MOVEMENT_SPEED);
        p.removeEffect(MobEffects.JUMP);
        p.setInvulnerable(false);
        discardBeam(p, s);
        releaseGrab(p.serverLevel(), s);
        AttributeInstance hp = p.getAttribute(Attributes.MAX_HEALTH);
        if (hp != null) {
            hp.removeModifier(HEALTH_MOD);
        }
        if (p.getHealth() > p.getMaxHealth()) {
            p.setHealth(p.getMaxHealth());
        }
        if (s.bar != null) {
            s.bar.removeAllPlayers();
            s.bar = null;
        }
        if (s.avatar != null) {
            Entity e = p.serverLevel().getEntity(s.avatar);
            if (e != null) {
                e.discard();
            }
            s.avatar = null;
        }
        p.refreshDimensions();
    }

    private static void discardBeam(ServerPlayer p, State s) {
        if (s.beam != null) {
            Entity e = p.serverLevel().getEntity(s.beam);
            if (e != null) {
                e.discard();
            }
            s.beam = null;
        }
    }

    /** Death / logout: drop everything silently. */
    public static void cleanup(ServerPlayer p) {
        State s = STATES.remove(p.getUUID());
        if (s != null) {
            cleanupVisuals(p, s);
        }
    }

    // ------------------------------------------------------------------ move dispatch
    public static void beginMove(ServerPlayer p, AbilityType type) {
        State s = STATES.get(p.getUUID());
        if (s == null || s.phase != 2) {
            return;
        }
        s.attack = type;
        s.attackTicks = 0;
        String anim;
        switch (type) {
            case CONCENTRATED_BEAM -> {
                s.busy = BEAM_TOTAL;
                anim = "beam";
                rootPlayer(p, BEAM_TOTAL);
            }
            case OBLITERATOR_MAXIMUM -> {
                s.busy = 18;
                anim = "obliterator";
            }
            case HELLFIRE -> {
                s.busy = 74;
                anim = "hellfire";
                rootPlayer(p, 74);
            }
            case METEOR_IMPACT -> {
                s.busy = 220;
                anim = "meteor_rush";
                s.mPhase = 1;
                s.mTicks = 0;
                s.grabbed = null;
            }
            case HEROS_RUSH -> {
                s.busy = 230;
                anim = "hr_rush";
                s.mPhase = 21;
                s.mTicks = 0;
                s.grabbed = null;
            }
            case TREE_OF_SIN -> {
                s.busy = 240;
                anim = "tree_rush";
                s.mPhase = 11;
                s.mTicks = 0;
                s.grabbed = null;
            }
            default -> {
                s.busy = 56;   // three heavy slashes: 2.7 s
                anim = "slash";
            }
        }
        if (s.avatar != null && p.serverLevel().getEntity(s.avatar) instanceof DemonEntity d) {
            d.triggerAnim("attack", anim);
        }
    }

    private static void rootPlayer(ServerPlayer p, int ticks) {
        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 9, false, false, false));
    }

    // ------------------------------------------------------------------ 1. Concentrated Beam (1s charge + 3s beam)
    private static final int BEAM_START = 22;      // 1.10 s
    private static final int BEAM_STOP = 82;       // 4.10 s: the beam itself lasts 3 seconds
    private static final int BEAM_AFTERGLOW = 84;  // 4.20 s
    private static final int BEAM_TOTAL = 101;     // 5.06 s: the whole animation

    private static void tickBeam(ServerPlayer p, State s, ServerLevel level) {
        int a = s.attackTicks;
        Vec3 look = p.getLookAngle();
        Vec3 origin = p.position().add(0, HeroConstants.BEAM_Y, 0).add(look.scale(HeroConstants.BEAM_FORWARD));

        // timing follows the mouth_beam animation: strain 0.18-1.10 s, beam 1.10-4.10 s, afterglow to 4.20 s
        if (a < BEAM_START) {
            if (a == 4) {
                level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.CONDUIT_ACTIVATE, SoundSource.PLAYERS, 1.5F, 0.6F);
            }
            if (a == 12) {
                level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 1.2F, 1.4F);
            }
            if (a >= 4 && a % 2 == 0) {
                // power gathers in toward his maw as he strains
                double r = 3.4 * (1.0 - (a - 4) / (double) (BEAM_START - 4)) + 0.4;
                for (int i = 0; i < 10; i++) {
                    Vec3 dir = new Vec3(p.getRandom().nextGaussian(), p.getRandom().nextGaussian(), p.getRandom().nextGaussian()).normalize();
                    Vec3 c = origin.add(dir.scale(r));
                    level.sendParticles(ModParticles.PURPLE_FLAME.get(), c.x, c.y, c.z, 1, 0, 0, 0, 0);
                }
                level.sendParticles(ModParticles.PURPLE_FLAME_BIG.get(), origin.x, origin.y, origin.z, 2, 0.2, 0.2, 0.2, 0);
            }
            return;
        }
        if (a >= BEAM_STOP) {
            discardBeam(p, s);
            if (a < BEAM_AFTERGLOW) {
                level.sendParticles(ModParticles.PURPLE_FLAME_BIG.get(), origin.x, origin.y, origin.z, 4, 0.4, 0.4, 0.4, 0.03);
                level.sendParticles(ModParticles.PURPLE_EMBER.get(), origin.x, origin.y, origin.z, 6, 0.6, 0.6, 0.6, 0.06);
            } else {
                s.attack = null;   // the recovery plays out under the move's busy time
            }
            return;
        }
        if (a == BEAM_START) {
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENDER_DRAGON_SHOOT, SoundSource.PLAYERS, 2.0F, 0.6F);
        }
        if ((a - BEAM_START) % 15 == 0) {
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.8F, 1.5F);
        }

        double range = 36.0;
        Vec3 target = origin.add(look.scale(range));
        BlockHitResult hit = level.clip(new ClipContext(origin, target, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        boolean hitBlock = hit.getType() != HitResult.Type.MISS;
        Vec3 end = hitBlock ? hit.getLocation() : target;
        double len = end.subtract(origin).length();

        // the visible beam itself
        Entity be = s.beam == null ? null : level.getEntity(s.beam);
        BeamEntity beam;
        if (be instanceof BeamEntity existing && !existing.isRemoved()) {
            beam = existing;
        } else {
            beam = new BeamEntity(level, p);
            level.addFreshEntity(beam);
            s.beam = beam.getUUID();
        }
        beam.aim(origin, look, (float) len);

        // crackling extras along it
        int i = 0;
        for (double d = 0; d <= len; d += 2.4, i++) {
            Vec3 pt = origin.add(look.scale(d));
            level.sendParticles(ModParticles.PURPLE_SPARK.get(), pt.x, pt.y, pt.z, 1, 0.6, 0.6, 0.6, 0.4);
            if (i % 2 == 0) {
                level.sendParticles(ModParticles.PURPLE_EMBER.get(), pt.x, pt.y, pt.z, 2, 0.6, 0.6, 0.6, 0.05);
            }
        }
        level.sendParticles(ModParticles.PURPLE_FLAME_BIG.get(), origin.x, origin.y, origin.z, 2, 0.3, 0.3, 0.3, 0.02);
        if (hitBlock) {
            FxUtil.purpleBurst(level, end, 14, 0.8);
        }

        if ((a - BEAM_START) % 5 == 0) {
            AABB box = new AABB(origin, end).inflate(3.0);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, e -> validTarget(e, p))) {
                Vec3 to = e.getBoundingBox().getCenter().subtract(origin);
                double proj = to.dot(look);
                if (proj < 0 || proj > len + 1.0 || to.subtract(look.scale(proj)).length() > 2.6) {
                    continue;
                }
                e.invulnerableTime = 0;
                e.hurt(p.damageSources().playerAttack(p), 9.0F);
                e.setSecondsOnFire(8);
                Vec3 c = e.getBoundingBox().getCenter();
                level.sendParticles(ModParticles.PURPLE_SPARK.get(), c.x, c.y, c.z, 20, 0.4, 0.5, 0.4, 0.4);
            }
        }
        if (hitBlock && (a - BEAM_START) % 10 == 2) {
            level.explode(p, end.x, end.y, end.z, 2.5F, true, Level.ExplosionInteraction.NONE);
        }
    }

    // ------------------------------------------------------------------ 2. Obliterator Maximum
    private static void tickPunch(ServerPlayer p, State s, ServerLevel level) {
        int a = s.attackTicks;
        Vec3 look = p.getLookAngle();
        Vec3 fist = chest(p).add(0, 0.7, 0).add(look.scale(4.0));   // where the fist lands in the animation
        if (a < 9) {
            if (a % 2 == 0) {
                level.sendParticles(ModParticles.PURPLE_FLAME.get(), fist.x, fist.y, fist.z, 6, 0.6, 0.6, 0.6, 0.02);
            }
            return;
        }
        if (a == 9) {
            cone(p, level, 11.0, 0.3, 100.0F, 5.0, 15, true);
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, fist.x, fist.y, fist.z, 1, 0, 0, 0, 0);
            level.sendParticles(ModParticles.PURPLE_FLAME.get(), fist.x, fist.y, fist.z, 60, 1.0, 1.0, 1.0, 0.15);
            level.sendParticles(ModParticles.PURPLE_SPARK.get(), fist.x, fist.y, fist.z, 100, 1.5, 1.5, 1.5, 0.7);
            FxUtil.purpleBurst(level, fist, 150, 1.5);
            level.explode(p, fist.x, fist.y, fist.z, 3.0F, true, Level.ExplosionInteraction.NONE);
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.5F, 0.7F);
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 1.0F, 1.2F);
        }
        if (a >= 13) {
            s.attack = null;
        }
    }

    // ------------------------------------------------------------------ 3. Hellfire (3s roar, fire fountains, massive blast)
    private static void tickHellfire(ServerPlayer p, State s, ServerLevel level) {
        int a = s.attackTicks;
        if (a < 60) {
            if (a == 1 || a % 20 == 0) {
                level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 3.0F, 0.5F);
                level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 2.0F, 0.3F);
            }
            if (a % 5 == 0) {
                level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.FIRE_AMBIENT, SoundSource.PLAYERS, 1.5F, 0.6F);
            }
            // fountains of fire erupt around him, more and more as the roar builds
            int jets = 2 + a / 7;
            for (int j = 0; j < jets; j++) {
                double ang = p.getRandom().nextDouble() * Math.PI * 2.0;
                double dist = 4.0 + p.getRandom().nextDouble() * 20.0;
                double jx = p.getX() + Math.cos(ang) * dist;
                double jz = p.getZ() + Math.sin(ang) * dist;
                double jy = findFloor(level, jx, p.getY(), jz);
                for (int k = 0; k < 7; k++) {
                    level.sendParticles(ModParticles.PURPLE_FLAME_BIG.get(), jx, jy + 0.2, jz, 0,
                            (p.getRandom().nextDouble() - 0.5) * 0.25, 0.9 + p.getRandom().nextDouble() * 0.8,
                            (p.getRandom().nextDouble() - 0.5) * 0.25, 1.0);
                }
                for (int k = 0; k < 3; k++) {
                    level.sendParticles(ModParticles.PURPLE_SPARK.get(), jx, jy + 0.5, jz, 0,
                            (p.getRandom().nextDouble() - 0.5) * 0.5, 0.9 + p.getRandom().nextDouble() * 0.5,
                            (p.getRandom().nextDouble() - 0.5) * 0.5, 1.0);
                }
                level.sendParticles(ModParticles.PURPLE_EMBER.get(), jx, jy + 1.0, jz, 3, 0.4, 1.0, 0.4, 0.08);
                if (a % 4 == 0) {
                    AABB jetBox = new AABB(jx - 2.4, jy - 0.5, jz - 2.4, jx + 2.4, jy + 10.0, jz + 2.4);
                    for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, jetBox, x -> validTarget(x, p))) {
                        e.invulnerableTime = 0;
                        e.hurt(p.damageSources().playerAttack(p), 12.0F);
                        e.setSecondsOnFire(8);
                        e.setDeltaMovement(e.getDeltaMovement().add(0, 0.7, 0));
                        e.hurtMarked = true;
                    }
                }
            }
            // heat haze around the roaring demon
            if (a % 2 == 0) {
                level.sendParticles(ModParticles.PURPLE_FLAME.get(), p.getX(), p.getY() + 0.3, p.getZ(), 14, 7.0, 0.2, 7.0, 0.03);
            }
            return;
        }
        if (a == 60) {
            // MASSIVE EXPLOSION centred on his own eye line (so he is not flung), he is shielded for the instant of the blast
            double cx = p.getX(), cy = p.getEyeY(), cz = p.getZ();
            p.setInvulnerable(true);
            level.explode(p, cx, cy, cz, 15.0F, true, Level.ExplosionInteraction.BLOCK);
            p.setInvulnerable(false);

            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(36.0), x -> validTarget(x, p))) {
                double dist = e.distanceTo(p);
                if (dist > 36.0) {
                    continue;
                }
                Vec3 away = new Vec3(e.getX() - p.getX(), 0, e.getZ() - p.getZ());
                away = away.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : away.normalize();
                e.invulnerableTime = 0;
                e.hurt(p.damageSources().playerAttack(p), dist < 18.0 ? 170.0F : 100.0F);
                e.setSecondsOnFire(20);
                e.setDeltaMovement(e.getDeltaMovement().add(away.x * 3.0, 1.1, away.z * 3.0));
                e.hurtMarked = true;
            }
            // expanding ring of flame, pillar, lightning and a huge flash
            for (int deg = 0; deg < 360; deg += 8) {
                double rad = Math.toRadians(deg);
                for (int ring = 1; ring <= 3; ring++) {
                    level.sendParticles(ModParticles.PURPLE_FLAME_BIG.get(), p.getX(), p.getY() + 0.4, p.getZ(), 0,
                            Math.cos(rad) * ring * 1.1, 0.14 * ring, Math.sin(rad) * ring * 1.1, 1.0);
                }
            }
            for (int k = 0; k < 40; k++) {
                level.sendParticles(ModParticles.PURPLE_FLAME_BIG.get(), p.getX(), p.getY() + 0.3, p.getZ(), 0,
                        (p.getRandom().nextDouble() - 0.5) * 0.5, 0.8 + p.getRandom().nextDouble() * 1.2,
                        (p.getRandom().nextDouble() - 0.5) * 0.5, 1.0);
            }
            // the rendered blast: fireball, mushroom column, shock rings, flash and camera shake
            com.rudues.mod.network.Network.sendBoom(level, p.getX(), p.getY() + 4.0, p.getZ(), p.getY(), 1.0F);
            level.sendParticles(ModParticles.PURPLE_SPARK.get(), cx, cy, cz, 300, 12.0, 6.0, 12.0, 0.9);
            level.sendParticles(ModParticles.PURPLE_EMBER.get(), cx, cy, cz, 220, 14.0, 6.0, 14.0, 0.15);
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, cx, cy - 4.0, cz, 6, 8.0, 0.5, 8.0, 0);
            FxUtil.purpleBurst(level, new Vec3(cx, cy - 3.0, cz), 420, 8.0);
            level.playSound(null, cx, cy, cz, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 4.0F, 0.5F);
            level.playSound(null, cx, cy, cz, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 3.0F, 0.6F);
            level.playSound(null, cx, cy, cz, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 3.0F, 0.4F);
        }
        if (a >= 66) {
            s.attack = null;
        }
    }

    private static double findFloor(ServerLevel level, double x, double y, double z) {
        net.minecraft.core.BlockPos.MutableBlockPos pos = new net.minecraft.core.BlockPos.MutableBlockPos(x, y + 3, z);
        for (int i = 0; i < 12; i++) {
            if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
                return pos.getY() + 1.0;
            }
            pos.move(0, -1, 0);
        }
        return y;
    }

    // ------------------------------------------------------------------ 4. Slash Combo
    private static void tickSlash(ServerPlayer p, State s, ServerLevel level) {
        int a = s.attackTicks;
        // three heavy slashes; these ticks are the moments the claws pass through the target in the animation
        int idx = switch (a) {
            case 11 -> 0;   // near claw, high right to low left
            case 26 -> 1;   // far claw, high left to low right
            case 42 -> 2;   // two-handed overhead cleave
            default -> -1;
        };
        if (idx >= 0) {
            boolean finisher = idx == 2;
            cone(p, level, 10.0, 0.15, finisher ? 60.0F : 35.0F, finisher ? 3.4 : 1.4, finisher ? 10 : 6, false);
            slashArc(p, level, idx, finisher);
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.8F, finisher ? 0.45F : 0.55F);
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1.4F, finisher ? 0.5F : 0.6F);
            if (finisher) {
                level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.2F, 0.9F);
                Vec3 g = p.position().add(p.getLookAngle().multiply(1, 0, 1).normalize().scale(3.0));
                level.sendParticles(ParticleTypes.EXPLOSION, g.x, g.y + 0.3, g.z, 3, 1.0, 0.2, 1.0, 0);
            }
        }
        if (a >= 54) {
            s.attack = null;
        }
    }

    private static void slashArc(ServerPlayer p, ServerLevel level, int idx, boolean finisher) {
        Vec3 look = p.getLookAngle();
        Vec3 right = look.cross(new Vec3(0, 1, 0));
        right = right.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : right.normalize();
        Vec3 center = chest(p);
        double tilt = (idx % 2 == 0) ? 1.0 : -1.0;
        double scale = finisher ? 2.0 : 1.7;
        for (int i = -10; i <= 10; i++) {
            double t = i / 10.0;
            // the finisher is a vertical cleave from overhead down to the ground; the others cut diagonally across
            Vec3 pt = finisher
                    ? center.add(look.scale((2.6 - 1.0 * t * t) * scale)).add(0, -t * 3.2, 0)
                    : center.add(look.scale((3.2 - 1.4 * t * t) * scale))
                        .add(right.scale(t * 3.4 * scale)).add(0, tilt * t * 1.8 * scale, 0);
            level.sendParticles(ModParticles.PURPLE_FLAME_BIG.get(), pt.x, pt.y, pt.z, 1, 0.1, 0.1, 0.1, 0);
            if (i % 2 == 0) {
                level.sendParticles(ModParticles.PURPLE_SPARK.get(), pt.x, pt.y, pt.z, 2, 0.2, 0.2, 0.2, 0.3);
                level.sendParticles(ModParticles.PURPLE_FLAME.get(), pt.x, pt.y, pt.z, 1, 0.1, 0.1, 0.1, 0.02);
            }
            if (i % 5 == 0) {
                level.sendParticles(ParticleTypes.SWEEP_ATTACK, pt.x, pt.y, pt.z, 1, 0, 0, 0, 0);
            }
        }
    }

    // ------------------------------------------------------------------ 5. Meteor Impact (rush, grab, leap, slam)
    private static void trigger(ServerPlayer p, State s, String anim) {
        if (s.avatar != null && p.serverLevel().getEntity(s.avatar) instanceof DemonEntity d) {
            d.triggerAnim("attack", anim);
        }
    }

    /** Tells every client to draw the dragged victim on its back (or stand it back up when target is null). */
    private static void setDragVisual(ServerLevel level, State s, LivingEntity target) {
        if (s.avatar != null && level.getEntity(s.avatar) instanceof DemonEntity d) {
            int id = target == null ? -1 : target.getId();
            if (d.getDraggedId() != id) {
                d.setDragged(id, level.getGameTime());
            }
        }
    }

    private static void releaseGrab(ServerLevel level, State s) {
        setDragVisual(level, s, null);
        if (s.grabbed == null) {
            return;
        }
        if (level.getEntity(s.grabbed) instanceof LivingEntity le) {
            le.setInvulnerable(false);
            if (le instanceof net.minecraft.world.entity.Mob mob) {
                mob.setNoAi(s.grabNoAi);
            } else {
                le.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
                le.removeEffect(MobEffects.WEAKNESS);
            }
        }
        s.grabbed = null;
    }

    /**
     * Places a held victim relative to the demon: forward, sideways (positive = the player's right) and height.
     * GeckoLib mirrors the model's X axis, so the bone named "right_hand" is drawn on the player's LEFT (negative
     * lateral) and "left_hand" on the player's right (positive lateral).
     */
    private static void holdTarget(ServerPlayer p, LivingEntity t, Vec3 flat, ServerLevel level, double dist, double lateral, double height) {
        double x = p.getX() + flat.x * dist + (-flat.z) * lateral;
        double y = p.getY() + height;
        double z = p.getZ() + flat.z * dist + flat.x * lateral;
        if (t instanceof ServerPlayer tp) {
            tp.teleportTo(level, x, y, z, tp.getYRot(), tp.getXRot());
        } else {
            t.setPos(x, y, z);
        }
        t.setDeltaMovement(Vec3.ZERO);
        t.fallDistance = 0;
        t.hurtMarked = true;
    }

    private static void endMeteor(ServerPlayer p, State s, ServerLevel level, int busyAfter, boolean recoverAnim) {
        releaseGrab(level, s);
        s.attack = null;
        s.mPhase = 0;
        s.busy = busyAfter;
        if (recoverAnim) {
            trigger(p, s, "meteor_recover");
        }
    }

    private static void tickMeteor(ServerPlayer p, State s, ServerLevel level) {
        s.mTicks++;
        Vec3 look = p.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        flat = flat.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : flat.normalize();
        LivingEntity target = (s.grabbed != null && level.getEntity(s.grabbed) instanceof LivingEntity le) ? le : null;

        switch (s.mPhase) {
            // ---- 1: rush forward until something is in his claws
            case 1 -> {
                if (s.mTicks == 1) {
                    level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 2.0F, 1.5F);
                }
                p.setDeltaMovement(flat.x * 1.7, -0.08, flat.z * 1.7);
                p.hurtMarked = true;
                p.fallDistance = 0;
                level.sendParticles(ModParticles.PURPLE_FLAME_BIG.get(), p.getX() - flat.x * 1.5, p.getY() + 0.5, p.getZ() - flat.z * 1.5, 3, 1.0, 0.3, 1.0, 0.02);
                level.sendParticles(ModParticles.PURPLE_SPARK.get(), p.getX(), p.getY() + 1.5, p.getZ(), 2, 1.2, 1.2, 1.2, 0.2);

                AABB box = p.getBoundingBox().inflate(1.4, 0.0, 1.4).move(flat.scale(1.8));
                LivingEntity hit = null;
                double best = Double.MAX_VALUE;
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, x -> validTarget(x, p))) {
                    double d = e.distanceToSqr(p);
                    if (d < best) {
                        best = d;
                        hit = e;
                    }
                }
                if (hit != null) {
                    // GRABBED: freeze them so they cannot attack, then leap
                    s.grabbed = hit.getUUID();
                    if (hit instanceof net.minecraft.world.entity.Mob mob) {
                        s.grabNoAi = mob.isNoAi();
                        mob.setNoAi(true);
                    } else {
                        hit.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 240, 10, false, false, false));
                        hit.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 240, 10, false, false, false));
                    }
                    hit.setInvulnerable(true);
                    hit.setDeltaMovement(Vec3.ZERO);
                    s.mPhase = 2;
                    s.mTicks = 0;
                    s.peakY = p.getY();
                    p.setDeltaMovement(flat.x * 0.3, 1.55, flat.z * 0.3);
                    p.hurtMarked = true;
                    trigger(p, s, "meteor_leap");
                    FxUtil.purpleBurst(level, hit.getBoundingBox().getCenter(), 60, 0.8);
                    level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.2F, 1.4F);
                } else if (s.mTicks >= 16) {
                    p.setDeltaMovement(flat.x * 0.2, 0, flat.z * 0.2);
                    p.hurtMarked = true;
                    endMeteor(p, s, level, 14, true); // whiffed
                }
            }
            // ---- 2: airborne with the victim held in front of him
            case 2 -> {
                p.fallDistance = 0;
                if (target == null || !target.isAlive()) {
                    endMeteor(p, s, level, 10, true);
                    return;
                }
                double rise = Math.min(1.0, s.mTicks / 8.0);
                // hanging by the neck from the "right_hand" claw (drawn on the player's left), hoisted overhead as he rises
                holdTarget(p, target, flat, level, 3.6 - 3.3 * rise, -0.05 - 1.0 * rise, 2.85 + 3.35 * rise);
                s.peakY = Math.max(s.peakY, p.getY());
                if (s.mTicks % 2 == 0) {
                    level.sendParticles(ModParticles.PURPLE_FLAME_BIG.get(), p.getX(), p.getY() + 0.5, p.getZ(), 3, 1.0, 0.3, 1.0, 0.02);
                }
                if (s.mTicks > 6 && (p.getY() < s.peakY - 0.3 || s.mTicks > 45)) {
                    // top of the leap: SLAM
                    s.mPhase = 3;
                    s.mTicks = 0;
                    trigger(p, s, "meteor_slam");
                    p.setDeltaMovement(flat.x * 0.3, -2.8, flat.z * 0.3);
                    p.hurtMarked = true;
                    level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 2.5F, 0.6F);
                }
            }
            // ---- 3: plummeting; impact the moment he touches ground
            case 3 -> {
                p.fallDistance = 0;
                if (target != null && target.isAlive()) {
                    double drop = Math.min(1.0, s.mTicks / 6.0);
                    holdTarget(p, target, flat, level, 0.3 + 2.7 * drop, -1.05 - 0.6 * drop, Math.max(0.0, 6.2 - 6.2 * drop));
                }
                if (s.mTicks < 4) {
                    p.setDeltaMovement(flat.x * 0.3, -2.8, flat.z * 0.3);
                    p.hurtMarked = true;
                }
                level.sendParticles(ModParticles.PURPLE_FLAME_BIG.get(), p.getX(), p.getY() + 3.0, p.getZ(), 4, 1.2, 1.5, 1.2, 0.02);
                level.sendParticles(ModParticles.PURPLE_SPARK.get(), p.getX(), p.getY() + 3.0, p.getZ(), 3, 1.2, 1.5, 1.2, 0.3);
                if ((s.mTicks >= 3 && p.onGround()) || s.mTicks > 40) {
                    meteorImpact(p, s, level, target, flat);
                }
            }
            default -> s.attack = null;
        }
    }

    private static void meteorImpact(ServerPlayer p, State s, ServerLevel level, LivingEntity target, Vec3 flat) {
        Vec3 c = p.position();
        releaseGrab(level, s);
        if (target != null && target.isAlive()) {
            double lat = -1.7; // under the "right_hand" claw at the moment of impact (drawn on the player's left)
            double gx = c.x + flat.x * 3.0 + (-flat.z) * lat, gz = c.z + flat.z * 3.0 + flat.x * lat;
            if (target instanceof ServerPlayer tp) {
                tp.teleportTo(level, gx, c.y, gz, tp.getYRot(), tp.getXRot());
            } else {
                target.setPos(gx, c.y, gz);
            }
            target.invulnerableTime = 0;
            target.hurt(p.damageSources().playerAttack(p), 170.0F);
            target.setSecondsOnFire(15);
        }
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(16.0),
                x -> validTarget(x, p) && x != target)) {
            double d = e.distanceTo(p);
            if (d > 16.0) {
                continue;
            }
            Vec3 away = new Vec3(e.getX() - p.getX(), 0, e.getZ() - p.getZ());
            away = away.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : away.normalize();
            e.invulnerableTime = 0;
            e.hurt(p.damageSources().playerAttack(p), 75.0F);
            e.setSecondsOnFire(10);
            e.setDeltaMovement(e.getDeltaMovement().add(away.x * 2.4, 0.9, away.z * 2.4));
            e.hurtMarked = true;
        }
        p.setInvulnerable(true);
        level.explode(p, c.x, c.y + 0.3, c.z, 6.0F, true, Level.ExplosionInteraction.BLOCK);
        p.setInvulnerable(false);
        p.setDeltaMovement(0, 0, 0);
        p.hurtMarked = true;
        p.fallDistance = 0;

        for (int deg = 0; deg < 360; deg += 10) {
            double rad = Math.toRadians(deg);
            for (int ring = 1; ring <= 3; ring++) {
                level.sendParticles(ModParticles.PURPLE_FLAME_BIG.get(), c.x, c.y + 0.4, c.z, 0,
                        Math.cos(rad) * ring * 0.9, 0.12 * ring, Math.sin(rad) * ring * 0.9, 1.0);
            }
        }
        level.sendParticles(ModParticles.PURPLE_SPARK.get(), c.x, c.y + 1.0, c.z, 140, 6.0, 1.5, 6.0, 0.8);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y + 0.5, c.z, 3, 3.0, 0.3, 3.0, 0);
        FxUtil.purpleBurst(level, c.add(0, 1.0, 0), 220, 4.0);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 3.5F, 0.5F);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 2.0F, 0.4F);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 2.0F, 0.7F);

        s.attack = null;
        s.mPhase = 0;
        s.busy = 14;
    }

    // ------------------------------------------------------------------ 6. Tree of Sin (root axe, rush, grab, savage combo)
    private static final int[] TREE_HITS = {20, 44, 68, 92, 120};   // 1.0 / 2.2 / 3.4 / 4.6 / 6.0 seconds: slow and heavy
    private static final BlockParticleOption WOOD_CHIPS =
            new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DARK_OAK_LOG.defaultBlockState());

    private static void tickTree(ServerPlayer p, State s, ServerLevel level) {
        s.mTicks++;
        Vec3 look = p.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        flat = flat.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : flat.normalize();
        LivingEntity target = (s.grabbed != null && level.getEntity(s.grabbed) instanceof LivingEntity le) ? le : null;

        if (s.mPhase == 11) {
            // ---- the axe of roots bursts from the ground into his fist as he charges
            if (s.mTicks == 1) {
                level.sendParticles(WOOD_CHIPS, p.getX() + flat.x * 1.5, p.getY() + 1.0, p.getZ() + flat.z * 1.5, 60, 1.2, 1.0, 1.2, 0.3);
                FxUtil.purpleBurst(level, chest(p), 50, 1.2);
                level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.WOOD_BREAK, SoundSource.PLAYERS, 2.0F, 0.5F);
                level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 2.0F, 1.2F);
            }
            p.setDeltaMovement(flat.x * 1.7, -0.08, flat.z * 1.7);
            p.hurtMarked = true;
            p.fallDistance = 0;
            level.sendParticles(ModParticles.PURPLE_FLAME_BIG.get(), p.getX() - flat.x * 1.5, p.getY() + 0.5, p.getZ() - flat.z * 1.5, 3, 1.0, 0.3, 1.0, 0.02);
            level.sendParticles(WOOD_CHIPS, p.getX() - flat.x, p.getY() + 0.3, p.getZ() - flat.z, 4, 0.8, 0.2, 0.8, 0.1);

            AABB box = p.getBoundingBox().inflate(1.4, 0.0, 1.4).move(flat.scale(1.8));
            LivingEntity hit = null;
            double best = Double.MAX_VALUE;
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, x -> validTarget(x, p))) {
                double d = e.distanceToSqr(p);
                if (d < best) {
                    best = d;
                    hit = e;
                }
            }
            if (hit != null) {
                s.grabbed = hit.getUUID();
                if (hit instanceof net.minecraft.world.entity.Mob mob) {
                    s.grabNoAi = mob.isNoAi();
                    mob.setNoAi(true);
                } else {
                    hit.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 200, 10, false, false, false));
                    hit.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200, 10, false, false, false));
                }
                hit.setInvulnerable(true);
                hit.setDeltaMovement(Vec3.ZERO);
                s.mPhase = 12;
                s.mTicks = 0;
                p.setDeltaMovement(0, 0, 0);
                p.hurtMarked = true;
                trigger(p, s, "tree_combo");
                rootPlayer(p, 140);   // planted: the swings are heavy
                level.sendParticles(WOOD_CHIPS, hit.getX(), hit.getY() + 1.0, hit.getZ(), 40, 0.6, 0.8, 0.6, 0.2);
                FxUtil.purpleBurst(level, hit.getBoundingBox().getCenter(), 60, 0.8);
                level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.0F, 1.6F);
            } else if (s.mTicks >= 16) {
                p.setDeltaMovement(flat.x * 0.2, 0, flat.z * 0.2);
                p.hurtMarked = true;
                trigger(p, s, "tree_miss");
                witherAxe(p, level);
                endMeteor(p, s, level, 14, false); // whiffed: the axe crumbles
            }
            return;
        }

        // ---- phase 12: held in the claws, carved up by the root axe
        if (target == null || !target.isAlive()) {
            trigger(p, s, "tree_miss");
            witherAxe(p, level);
            endMeteor(p, s, level, 10, false);
            return;
        }
        // gripped in the "left_hand" claw (drawn on the player's right); the axe is in the other hand
        holdTarget(p, target, flat, level, 2.6, 1.2, 2.7);   // the animation pins that claw at (2.6, 1.2, 4.2): neck in the claw
        p.fallDistance = 0;
        if (s.mTicks % 3 == 0) {
            level.sendParticles(ModParticles.PURPLE_EMBER.get(), target.getX(), target.getY() + 1.0, target.getZ(), 2, 0.5, 0.6, 0.5, 0.05);
        }
        for (int i = 0; i < TREE_HITS.length; i++) {
            if (s.mTicks == TREE_HITS[i]) {
                treeSlash(p, s, level, target, flat, i);
            }
        }
        if (s.mTicks >= 136) {
            // axe withers, victim is cast to the ground
            witherAxe(p, level);
            if (target.isAlive()) {
                double gx = p.getX() + flat.x * 3.2, gz = p.getZ() + flat.z * 3.2;
                releaseGrab(level, s);
                if (target instanceof ServerPlayer tp) {
                    tp.teleportTo(level, gx, p.getY(), gz, tp.getYRot(), tp.getXRot());
                } else {
                    target.setPos(gx, p.getY(), gz);
                }
                target.setDeltaMovement(flat.x * 1.4, 0.4, flat.z * 1.4);
                target.hurtMarked = true;
            }
            endMeteor(p, s, level, 12, false);
        }
    }

    private static void treeSlash(ServerPlayer p, State s, ServerLevel level, LivingEntity target, Vec3 flat, int idx) {
        boolean finisher = idx == TREE_HITS.length - 1;
        target.setInvulnerable(false);
        target.invulnerableTime = 0;
        target.hurt(p.damageSources().playerAttack(p), finisher ? 170.0F : 65.0F);
        if (target.isAlive()) {
            target.setInvulnerable(true);
        }
        // anything else near the swing gets caught by it too
        cone(p, level, 9.0, 0.2, 30.0F, 0.9, 5, false);

        Vec3 c = target.getBoundingBox().getCenter();
        level.sendParticles(WOOD_CHIPS, c.x, c.y, c.z, finisher ? 90 : 45, 0.7, 0.7, 0.7, 0.3);
        FxUtil.purpleBurst(level, c, finisher ? 110 : 45, 0.8);
        level.sendParticles(ModParticles.PURPLE_SPARK.get(), c.x, c.y, c.z, finisher ? 60 : 24, 0.8, 0.8, 0.8, 0.4);
        slashArc(p, level, idx, finisher);
        double gx = p.getX() + flat.x * 3.2 + (-flat.z) * (idx % 2 == 0 ? 0.0 : 0.5);
        double gz = p.getZ() + flat.z * 3.2 + flat.x * (idx % 2 == 0 ? 0.0 : 0.5);
        level.sendParticles(groundChips(level, gx, p.getY(), gz), gx, p.getY() + 0.3, gz, finisher ? 120 : 60, 1.4, 0.3, 1.4, 0.3);
        level.sendParticles(ParticleTypes.EXPLOSION, gx, p.getY() + 0.4, gz, finisher ? 4 : 1, 0.6, 0.2, 0.6, 0);
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, finisher ? 1.8F : 1.1F, finisher ? 0.4F : 0.55F);
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.AXE_STRIP, SoundSource.PLAYERS, 1.6F, finisher ? 0.5F : 0.8F);
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.5F, 0.6F);
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.WOOD_BREAK, SoundSource.PLAYERS, 1.2F, finisher ? 0.5F : 0.9F);
        if (finisher) {
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.5F, 0.7F);
        }
    }

    /** the root axe crumbles into splinters and embers (the model itself shrinks away via animation) */
    private static void witherAxe(ServerPlayer p, ServerLevel level) {
        Vec3 hand = chest(p).add(p.getLookAngle().scale(2.5));
        level.sendParticles(WOOD_CHIPS, hand.x, hand.y, hand.z, 80, 1.2, 1.2, 1.2, 0.25);
        level.sendParticles(ModParticles.PURPLE_EMBER.get(), hand.x, hand.y, hand.z, 50, 1.2, 1.2, 1.2, 0.08);
        FxUtil.purpleBurst(level, hand, 40, 1.0);
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.WOOD_BREAK, SoundSource.PLAYERS, 2.0F, 0.4F);
    }

    // ------------------------------------------------------------------ 7. Hero's Rush (one-handed grab, ground slam, drag, throw)
    private static BlockParticleOption groundChips(ServerLevel level, double x, double y, double z) {
        net.minecraft.world.level.block.state.BlockState st = level.getBlockState(net.minecraft.core.BlockPos.containing(x, y - 0.5, z));
        if (st.isAir()) {
            st = Blocks.DIRT.defaultBlockState();
        }
        return new BlockParticleOption(ParticleTypes.BLOCK, st);
    }

    private static void hurtHeld(ServerPlayer p, LivingEntity t, float dmg) {
        t.setInvulnerable(false);
        t.invulnerableTime = 0;
        t.hurt(p.damageSources().playerAttack(p), dmg);
        if (t.isAlive()) {
            t.setInvulnerable(true);
        }
    }

    /** Keeps the victim in his right hand, using the hand positions baked from the animation. */
    private static void holdFromHand(ServerPlayer p, LivingEntity t, Vec3 flat, ServerLevel level, int k) {
        int i = Math.min(Math.max(k, 0), HeroRushData.END_TICK);
        double fwd = HeroRushData.FWD[i], lat = HeroRushData.LAT[i], up = HeroRushData.UP[i];
        double x = p.getX() + flat.x * fwd + (-flat.z) * lat;
        double z = p.getZ() + flat.z * fwd + flat.x * lat;
        double y = Math.max(p.getY(), p.getY() + up - t.getBbHeight() * 0.5);
        if (t instanceof ServerPlayer tp) {
            tp.teleportTo(level, x, y, z, tp.getYRot(), tp.getXRot());
        } else {
            t.setPos(x, y, z);
        }
        t.setDeltaMovement(Vec3.ZERO);
        t.fallDistance = 0;
        t.hurtMarked = true;
    }

    private static void tickHerosRush(ServerPlayer p, State s, ServerLevel level) {
        s.mTicks++;
        Vec3 look = p.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        flat = flat.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : flat.normalize();
        LivingEntity target = (s.grabbed != null && level.getEntity(s.grabbed) instanceof LivingEntity le) ? le : null;

        // ---- 21: charge, right hand outstretched, until it closes on something
        if (s.mPhase == 21) {
            if (s.mTicks == 1) {
                level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 2.0F, 1.6F);
            }
            p.setDeltaMovement(flat.x * 1.7, -0.08, flat.z * 1.7);
            p.hurtMarked = true;
            p.fallDistance = 0;
            level.sendParticles(ModParticles.PURPLE_FLAME_BIG.get(), p.getX() - flat.x * 1.5, p.getY() + 0.5, p.getZ() - flat.z * 1.5, 3, 1.0, 0.3, 1.0, 0.02);
            level.sendParticles(ModParticles.PURPLE_SPARK.get(), p.getX(), p.getY() + 1.5, p.getZ(), 2, 1.2, 1.2, 1.2, 0.2);

            AABB box = p.getBoundingBox().inflate(1.4, 0.0, 1.4).move(flat.scale(1.8));
            LivingEntity hit = null;
            double best = Double.MAX_VALUE;
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, x -> validTarget(x, p))) {
                double d = e.distanceToSqr(p);
                if (d < best) {
                    best = d;
                    hit = e;
                }
            }
            if (hit != null) {
                s.grabbed = hit.getUUID();
                if (hit instanceof net.minecraft.world.entity.Mob mob) {
                    s.grabNoAi = mob.isNoAi();
                    mob.setNoAi(true);
                } else {
                    hit.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 240, 10, false, false, false));
                    hit.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 240, 10, false, false, false));
                }
                hit.setInvulnerable(true);
                hit.setDeltaMovement(Vec3.ZERO);
                s.mPhase = 22;
                s.mTicks = 0;
                rootPlayer(p, HeroRushData.END_TICK + 15);   // covers the whole (longer) drag
                trigger(p, s, "hr_combo");
                FxUtil.purpleBurst(level, hit.getBoundingBox().getCenter(), 60, 0.8);
                level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.2F, 1.6F);
            } else if (s.mTicks >= 16) {
                p.setDeltaMovement(flat.x * 0.2, 0, flat.z * 0.2);
                p.hurtMarked = true;
                endMeteor(p, s, level, 14, true);
            }
            return;
        }

        // ---- 22: the whole combo, driven tick by tick from the animation's baked data
        int k = s.mTicks;
        int ki = Math.min(k, HeroRushData.END_TICK);
        double step = HeroRushData.STEP[ki];
        p.setDeltaMovement(flat.x * step, -0.08, flat.z * step);   // feet match the ground
        p.hurtMarked = true;
        p.fallDistance = 0;

        boolean holding = target != null && target.isAlive() && k < HeroRushData.THROW_TICK;
        if (holding) {
            holdFromHand(p, target, flat, level, k);
        }
        // from just before the slam until the throw, the victim is drawn flat on its back, dragged by the neck
        if (holding && k >= HeroRushData.IMPACT_TICK - 2) {
            setDragVisual(level, s, target);
        } else if (k >= HeroRushData.THROW_TICK - 1) {
            setDragVisual(level, s, null);
        }

        // slam: the instant of impact (the animation freezes for a beat here)
        if (k == HeroRushData.IMPACT_TICK) {
            Vec3 at = (target != null) ? target.position()
                    : new Vec3(p.getX() + flat.x * 2.5, p.getY(), p.getZ() + flat.z * 2.5);
            if (holding) {
                hurtHeld(p, target, 90.0F);
            }
            level.sendParticles(groundChips(level, at.x, at.y, at.z), at.x, at.y + 0.3, at.z, 110, 1.8, 0.4, 1.8, 0.4);
            FxUtil.purpleBurst(level, at.add(0, 0.5, 0), 110, 1.4);
            level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y + 0.3, at.z, 4, 1.0, 0.2, 1.0, 0);
            for (int deg = 0; deg < 360; deg += 15) {
                double rad = Math.toRadians(deg);
                level.sendParticles(ModParticles.PURPLE_FLAME_BIG.get(), at.x, at.y + 0.3, at.z, 0,
                        Math.cos(rad) * 1.1, 0.10, Math.sin(rad) * 1.1, 1.0);
            }
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(7.0), x -> validTarget(x, p) && x != target)) {
                Vec3 away = new Vec3(e.getX() - at.x, 0, e.getZ() - at.z);
                away = away.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : away.normalize();
                e.invulnerableTime = 0;
                e.hurt(p.damageSources().playerAttack(p), 40.0F);
                e.setDeltaMovement(e.getDeltaMovement().add(away.x * 1.6, 0.7, away.z * 1.6));
                e.hurtMarked = true;
            }
            level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 2.5F, 0.6F);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 2.0F, 0.4F);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 1.2F, 1.0F);
        }

        // drag: dirt sprays, sparks fly, footfalls kick up dust
        if (k > HeroRushData.DRAG_START_TICK && k < HeroRushData.THROW_TICK) {
            if (holding) {
                double vx = target.getX(), vy = target.getY(), vz = target.getZ();
                level.sendParticles(groundChips(level, vx, vy, vz), vx, vy + 0.2, vz, 7, 0.5, 0.2, 0.5, 0.3);
                level.sendParticles(ModParticles.PURPLE_SPARK.get(), vx, vy + 0.3, vz, 3, 0.5, 0.2, 0.5, 0.3);
                if (k % 3 == 0) {
                    level.sendParticles(ModParticles.PURPLE_FLAME_BIG.get(), vx - flat.x * 0.8, vy + 0.2, vz - flat.z * 0.8, 2, 0.4, 0.2, 0.4, 0.02);
                }
                if (k % 5 == 0) {
                    level.playSound(null, vx, vy, vz, SoundEvents.GRAVEL_BREAK, SoundSource.PLAYERS, 1.8F, 0.55F);
                }
                if (k % 4 == 0) {
                    hurtHeld(p, target, 14.0F);
                }
            }
            if (k % 5 == 0) {   // a heavy footfall every ~quarter second
                level.sendParticles(groundChips(level, p.getX(), p.getY(), p.getZ()), p.getX(), p.getY() + 0.2, p.getZ(), 14, 1.0, 0.1, 1.0, 0.25);
                level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.7F, 0.45F);
            }
        }

        // throw: the hand opens and he hurls them away
        if (k == HeroRushData.THROW_TICK) {
            if (target != null && target.isAlive()) {
                holdFromHand(p, target, flat, level, k);
                releaseGrab(level, s);
                target.invulnerableTime = 0;
                target.hurt(p.damageSources().playerAttack(p), 100.0F);
                target.setSecondsOnFire(15);
                target.setDeltaMovement(flat.x * 2.8, 0.8, flat.z * 2.8);
                target.hurtMarked = true;
                FxUtil.purpleBurst(level, target.getBoundingBox().getCenter(), 90, 1.0);
                level.sendParticles(ModParticles.PURPLE_SPARK.get(), target.getX(), target.getY() + 1.0, target.getZ(), 50, 0.8, 0.8, 0.8, 0.5);
            } else {
                releaseGrab(level, s);
            }
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 2.0F, 0.5F);
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 1.8F, 1.3F);
        }

        if (k >= HeroRushData.END_TICK) {
            releaseGrab(level, s);
            s.attack = null;
            s.mPhase = 0;
            s.busy = 10;
        }
    }

    // ------------------------------------------------------------------ helpers
    /** Roughly the demon's chest: attacks are measured from here, not from his eyes 6 blocks up. */
    private static Vec3 chest(ServerPlayer p) {
        return p.position().add(0, 3.0, 0);
    }

    private static boolean validTarget(LivingEntity e, ServerPlayer p) {
        return e != p && e.isAlive() && !(e instanceof FiendEntity f && f.isOwner(p));
    }

    private static void cone(ServerPlayer p, ServerLevel level, double reach, double minDot,
                             float damage, double knockback, int fireSeconds, boolean electric) {
        Vec3 eye = chest(p);
        Vec3 look = p.getLookAngle();
        AABB box = p.getBoundingBox().expandTowards(look.scale(reach)).inflate(2.5);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, x -> validTarget(x, p))) {
            Vec3 center = e.getBoundingBox().getCenter();
            Vec3 to = center.subtract(eye);
            double dist = to.length();
            if (dist > reach + 2.0 || to.normalize().dot(look) < minDot) {
                continue;
            }
            e.invulnerableTime = 0;
            e.hurt(p.damageSources().playerAttack(p), damage);
            e.setSecondsOnFire(fireSeconds);
            e.knockback(knockback, p.getX() - e.getX(), p.getZ() - e.getZ());
            e.setDeltaMovement(e.getDeltaMovement().add(0, 0.5, 0));
            e.hurtMarked = true;
            FxUtil.purpleBurst(level, center, 40, 0.5);
            if (electric) {
                level.sendParticles(ModParticles.PURPLE_SPARK.get(), center.x, center.y, center.z, 50, 0.7, 0.7, 0.7, 0.6);
            }
        }
    }
}
