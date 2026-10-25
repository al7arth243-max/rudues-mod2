package com.rudues.mod.entity;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * The Feathered Knight, mistress of the Feathered Hold: a fencer who fights with a rapier and wind magic, waiting on
 * the keep roof. Her normal form's moves:
 * <ul>
 *   <li>Wind Burst - her open hand thrust out, five spreading green gusts, and the recoil;</li>
 *   <li>Aeroga - a ball of wind gathered above her hand and hurled; where it bursts it leaves a tornado for 5 s;</li>
 *   <li>Pierce - a leap back, up into the air, the rapier drawn back beside her head, then she zooms in point first;</li>
 *   <li>Wind Blade - a wide green slash formed in front of her over 1.5 s, then swept out flying.</li>
 * </ul>
 * Until the fight starts she plays her entrance ({@link KnightIntro}) and can't be hurt.
 */
public class FeatheredKnightEntity extends Monster implements GeoEntity {
    private static final EntityDataAccessor<Integer> MOVE = SynchedEntityData.defineId(FeatheredKnightEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> MOVE_START = SynchedEntityData.defineId(FeatheredKnightEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> SEMI = SynchedEntityData.defineId(FeatheredKnightEntity.class, EntityDataSerializers.BOOLEAN);

    public static final int MOVE_NONE = 0, MOVE_SIT = 1, MOVE_INTRO = 2, MOVE_BURST = 3, MOVE_AEROGA = 4, MOVE_PIERCE = 5, MOVE_BLADE = 6;

    private static final String P = "animation.feathered_knight.";
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop(P + "idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop(P + "move");
    private static final RawAnimation RUN = RawAnimation.begin().thenLoop(P + "run");
    private static final RawAnimation SIT = RawAnimation.begin().thenLoop(P + "sit");
    private static final RawAnimation INTRO = RawAnimation.begin().thenPlayAndHold(P + "intro");
    private static final String[] ONCE = {"wind_burst", "aeroga", "pierce", "wind_blade"};

    public static final double MAX_HEALTH = 700.0;
    public static final float BOLT_DAMAGE = 6.0F, ORB_DAMAGE = 10.0F, PIERCE_DAMAGE = 16.0F, BLADE_DAMAGE = 14.0F;
    private static final double FAR = 13.0, CLOSE = 4.5;
    private static final int AEROGA_COOLDOWN = 20 * 7;
    private static final DustParticleOptions GUST = new DustParticleOptions(new Vector3f(0.55F, 1.0F, 0.72F), 1.4F);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final ServerBossEvent bossBar = new ServerBossEvent(Component.translatable("entity.rudues.feathered_knight"),
            BossEvent.BossBarColor.GREEN, BossEvent.BossBarOverlay.NOTCHED_10);

    /** ticks into the entrance, or -1 once she is fighting (or was never given one) */
    private int introT = -1;
    @Nullable
    private UUID foe;
    private int moveTick;
    private float moveYaw;
    private int cooldown = 20;
    private int aerogaCooldown = 60;
    private int lastMove = MOVE_NONE;
    private final Set<UUID> hit = new HashSet<>();
    // Pierce
    private Vec3 pFrom = Vec3.ZERO, pBack = Vec3.ZERO, pHover = Vec3.ZERO, pLock = Vec3.ZERO, pGround = Vec3.ZERO;
    // the spells she is holding
    @Nullable
    private AerogaOrbEntity orb;
    @Nullable
    private WindBladeEntity blade;

    public FeatheredKnightEntity(EntityType<? extends FeatheredKnightEntity> type, Level level) {
        super(type, level);
        this.xpReward = 300;
        this.setPersistenceRequired();
        this.setMaxUpStep(1.0F);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, MAX_HEALTH)
                .add(Attributes.ARMOR, 8.0D)
                .add(Attributes.ATTACK_DAMAGE, 10.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.32D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.9D)
                .add(Attributes.FOLLOW_RANGE, 64.0D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(MOVE, MOVE_NONE);
        this.entityData.define(MOVE_START, 0);
        this.entityData.define(SEMI, false);
    }

    public int getMove() {
        return this.entityData.get(MOVE);
    }

    public boolean isInMove() {
        return getMove() != MOVE_NONE;
    }

    public boolean inIntro() {
        int m = getMove();
        return m == MOVE_SIT || m == MOVE_INTRO;
    }

    /** her Semi Prime form (wings, pale skin, the green glow) - the second phase */
    public boolean isSemi() {
        return this.entityData.get(SEMI);
    }

    /** whether the summoned talons are out (the Semi Prime talon moves) */
    public boolean talonsOut() {
        return false;
    }

    public float moveTime(float partialTick) {
        return (float) (level().getGameTime() - this.entityData.get(MOVE_START)) + partialTick;
    }

    private void setMove(int move) {
        this.entityData.set(MOVE, move);
        this.entityData.set(MOVE_START, (int) level().getGameTime());
    }

    @Nullable
    public UUID foe() {
        return foe;
    }

    /** ticks into the entrance (-1 when there is none running) */
    public int introTick() {
        return introT;
    }

    /** start the entrance for this player: she sits on the wall until her cue */
    public void beginIntro(ServerPlayer player) {
        foe = player.getUUID();
        introT = 0;
        setMove(MOVE_SIT);
        setTarget(player);
        bossBar.setVisible(false);
        faceYaw(KnightIntro.KNIGHT_YAW);
    }

    /** skip the rest of the entrance */
    public void endIntro() {
        if (introT >= 0) {
            introT = -1;
            setMove(MOVE_NONE);
            bossBar.setVisible(true);
            cooldown = 16;
        }
    }

    // ------------------------------------------------------------ AI
    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new DuelGoal());
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
    }

    /** between moves she keeps to fencing distance: closes in when far, circles when near */
    private final class DuelGoal extends Goal {
        private int strafeDir = 1, strafeTicks;

        DuelGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return !isInMove() && getTarget() != null && getTarget().isAlive();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void stop() {
            getNavigation().stop();
            getMoveControl().strafe(0, 0);
        }

        @Override
        public void tick() {
            LivingEntity t = getTarget();
            if (t == null) {
                return;
            }
            getLookControl().setLookAt(t, 40.0F, 40.0F);
            double d = horizontalDistanceTo(t);
            if (d > 9.0) {
                getNavigation().moveTo(t, d > 16.0 ? 1.25 : 1.0);
            } else {
                getNavigation().stop();
                if (--strafeTicks <= 0) {
                    strafeTicks = 30 + random.nextInt(40);
                    strafeDir = random.nextBoolean() ? 1 : -1;
                }
                getMoveControl().strafe(d < 5.0 ? -0.35F : 0.0F, 0.45F * strafeDir);
                faceYaw(yawTo(position(), t.position()));
            }
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            clientFx();
            return;
        }
        if (introT >= 0) {
            tickIntro();
            return;
        }
        LivingEntity target = getTarget();
        if (aerogaCooldown > 0) {
            aerogaCooldown--;
        }
        if (isInMove()) {
            tickMove(target);
        } else if (cooldown > 0) {
            cooldown--;
        } else if (target != null && target.isAlive()) {
            startMove(choose(target));
        }
    }

    private void tickIntro() {
        introT++;
        faceYaw(KnightIntro.KNIGHT_YAW);
        setDeltaMovement(Vec3.ZERO);
        getNavigation().stop();
        if (introT == KnightIntro.CLIP_AT) {
            setMove(MOVE_INTRO);
        }
        ServerLevel sl = (ServerLevel) level();
        if (introT == KnightIntro.PUSH) {
            playSound(SoundEvents.ARMOR_EQUIP_ELYTRA, 1.0F, 1.2F);
        }
        if (introT == KnightIntro.LAND) {
            playSound(SoundEvents.NETHERITE_BLOCK_STEP, 1.4F, 0.6F);
            playSound(SoundEvents.ENDER_DRAGON_FLAP, 0.8F, 1.5F);
            sl.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 0.1, getZ(), 18, 0.6, 0.05, 0.6, 0.06);
            sl.sendParticles(GUST, getX(), getY() + 0.2, getZ(), 24, 0.9, 0.1, 0.9, 0.0);
        }
        if (introT == KnightIntro.LAND + 12) {
            playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 1.0F, 1.3F);    // the flourish
        }
        if (introT >= KnightIntro.RELEASE) {
            endIntro();
        }
    }

    private int choose(LivingEntity t) {
        double d = horizontalDistanceTo(t);
        float r = random.nextFloat();
        int m;
        if (d > FAR) {
            m = r < 0.4F ? MOVE_PIERCE : r < 0.7F ? MOVE_AEROGA : MOVE_BURST;
        } else if (d < CLOSE) {
            m = r < 0.45F ? MOVE_PIERCE : r < 0.85F ? MOVE_BLADE : MOVE_BURST;
        } else {
            m = r < 0.3F ? MOVE_BURST : r < 0.55F ? MOVE_BLADE : r < 0.8F ? MOVE_AEROGA : MOVE_PIERCE;
        }
        if (m == MOVE_AEROGA && aerogaCooldown > 0) {
            m = d > FAR ? MOVE_PIERCE : MOVE_BURST;
        }
        if (m == lastMove) {
            m = switch (m) {
                case MOVE_BURST -> MOVE_BLADE;
                case MOVE_BLADE -> d > FAR ? MOVE_PIERCE : MOVE_BURST;
                case MOVE_PIERCE -> MOVE_BURST;
                default -> MOVE_BLADE;
            };
        }
        return m;
    }

    private static String clipOf(int move) {
        return switch (move) {
            case MOVE_BURST -> "wind_burst";
            case MOVE_AEROGA -> "aeroga";
            case MOVE_PIERCE -> "pierce";
            default -> "wind_blade";
        };
    }

    private static int endOf(int move) {
        return switch (move) {
            case MOVE_BURST -> KnightMoveData.WIND_BURST_END;
            case MOVE_AEROGA -> KnightMoveData.AEROGA_END;
            case MOVE_PIERCE -> KnightMoveData.PIERCE_END;
            default -> KnightMoveData.WIND_BLADE_END;
        };
    }

    private void startMove(int move) {
        LivingEntity t = getTarget();
        moveTick = 0;
        moveYaw = t != null ? yawTo(position(), t.position()) : getYRot();
        faceYaw(moveYaw);
        hit.clear();
        getNavigation().stop();
        getMoveControl().strafe(0, 0);
        setDeltaMovement(0, getDeltaMovement().y, 0);
        setMove(move);
        triggerAnim("main", clipOf(move));
        lastMove = move;
        if (move == MOVE_AEROGA) {
            aerogaCooldown = AEROGA_COOLDOWN;
        }
        if (move == MOVE_PIERCE) {
            planPierce(t);
        }
    }

    private void endMove() {
        if (getMove() == MOVE_PIERCE) {
            noPhysics = false;
            setNoGravity(false);
        }
        if (orb != null && !orb.launched()) {
            orb.discard();
        }
        if (blade != null && !blade.fired()) {
            blade.discard();
        }
        orb = null;
        blade = null;
        setMove(MOVE_NONE);
        cooldown = 14 + random.nextInt(18);
    }

    private void tickMove(@Nullable LivingEntity t) {
        int move = getMove();
        moveTick++;
        boolean alive = t != null && t.isAlive();
        switch (move) {
            case MOVE_BURST -> tickBurst(t, alive);
            case MOVE_AEROGA -> tickAeroga(t, alive);
            case MOVE_PIERCE -> tickPierce(t, alive);
            case MOVE_BLADE -> tickBlade(t, alive);
            default -> {
            }
        }
        if (moveTick >= endOf(move)) {
            endMove();
        }
    }

    // ---- Wind Burst
    private void tickBurst(@Nullable LivingEntity t, boolean alive) {
        if (alive && moveTick < KnightMoveData.WIND_BURST_FIRE) {
            turnTo(t, 20.0F);
        }
        if (moveTick == 4) {
            playSound(SoundEvents.ILLUSIONER_PREPARE_BLINDNESS, 0.9F, 1.5F);
        }
        if (moveTick == KnightMoveData.WIND_BURST_FIRE) {
            Vec3 hand = local(KnightMoveData.WIND_BURST_HAND);
            Vec3 aim = alive ? t.position().add(0, t.getBbHeight() * 0.55, 0) : hand.add(forward(moveYaw).scale(10));
            Vec3 dir = aim.subtract(hand).normalize();
            float yaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
            float pitch = (float) Math.toDegrees(Math.asin(Mth.clamp(dir.y, -1, 1)));
            for (int i = -2; i <= 2; i++) {
                float y = (yaw + i * 13.0F) * Mth.DEG_TO_RAD, pp = (pitch + Math.abs(i) * 1.5F) * Mth.DEG_TO_RAD;
                Vec3 v = new Vec3(-Mth.sin(y) * Mth.cos(pp), Mth.sin(pp), Mth.cos(y) * Mth.cos(pp)).scale(1.05 - 0.04 * Math.abs(i));
                WindBoltEntity b = new WindBoltEntity(level(), this, hand.add(v.scale(0.4)), v);
                level().addFreshEntity(b);
            }
            playSound(SoundEvents.ENDER_DRAGON_FLAP, 1.2F, 1.7F);
            playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 1.0F, 1.4F);
            ((ServerLevel) level()).sendParticles(GUST, hand.x, hand.y, hand.z, 14, 0.2, 0.2, 0.2, 0.0);
            ((ServerLevel) level()).sendParticles(ParticleTypes.CLOUD, hand.x, hand.y, hand.z, 6, 0.1, 0.1, 0.1, 0.08);
        }
    }

    // ---- Aeroga
    private void tickAeroga(@Nullable LivingEntity t, boolean alive) {
        if (alive && moveTick < KnightMoveData.AEROGA_LAUNCH) {
            turnTo(t, 8.0F);
        }
        if (moveTick == KnightMoveData.AEROGA_GATHER) {
            orb = new AerogaOrbEntity(level(), this);
            level().addFreshEntity(orb);
            playSound(SoundEvents.ILLUSIONER_PREPARE_MIRROR, 1.2F, 0.8F);
        }
        if (moveTick == KnightMoveData.AEROGA_LAUNCH && orb != null) {
            Vec3 aim = alive ? t.position().add(t.getDeltaMovement().multiply(6, 0, 6)) : position().add(forward(moveYaw).scale(10));
            orb.launch(aim);
            orb = null;
            playSound(SoundEvents.TRIDENT_RIPTIDE_3, 1.2F, 1.1F);
        }
    }

    /** the orb's place while she holds it up, in the world */
    public Vec3 orbAnchor(float partialTick) {
        float yaw = Mth.rotLerp(partialTick, yBodyRotO, yBodyRot);
        return getPosition(partialTick).add(local(KnightMoveData.AEROGA_ORB, yaw).subtract(position()));
    }

    // ---- Pierce
    private void planPierce(@Nullable LivingEntity t) {
        pFrom = position();
        Vec3 back = forward(moveYaw).scale(-1);
        // leap back as far as the roof allows
        double dist = KnightMoveData.PIERCE_LEAP_BACK;
        BlockHitResult h = level().clip(new ClipContext(pFrom.add(0, 1.0, 0), pFrom.add(back.scale(dist + 1.0)).add(0, 1.0, 0),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        if (h.getType() != HitResult.Type.MISS) {
            dist = Math.max(0.0, h.getLocation().subtract(pFrom.add(0, 1.0, 0)).horizontalDistance() - 1.2);
        }
        pBack = pFrom.add(back.scale(dist));
        pHover = pBack.add(0, KnightMoveData.PIERCE_RISE, 0);
        BlockHitResult up = level().clip(new ClipContext(pBack.add(0, 1.0, 0), pHover.add(0, 2.2, 0),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        if (up.getType() != HitResult.Type.MISS) {
            pHover = new Vec3(pBack.x, Math.max(pBack.y, up.getLocation().y - 2.2), pBack.z);
        }
        pLock = t != null ? t.position() : pFrom;
        noPhysics = true;
        setNoGravity(true);
    }

    private static double ease(double k) {
        k = Mth.clamp(k, 0, 1);
        return k * k * (3 - 2 * k);
    }

    private void tickPierce(@Nullable LivingEntity t, boolean alive) {
        int m = moveTick;
        setDeltaMovement(Vec3.ZERO);
        fallDistance = 0;
        Vec3 at = position();
        if (m == KnightMoveData.PIERCE_LEAP) {
            playSound(SoundEvents.ENDER_DRAGON_FLAP, 1.0F, 1.6F);
        }
        if (m <= KnightMoveData.PIERCE_LEAP) {
            at = pFrom;
        } else if (m <= KnightMoveData.PIERCE_LEAP_END) {
            double k = (m - KnightMoveData.PIERCE_LEAP) / (double) (KnightMoveData.PIERCE_LEAP_END - KnightMoveData.PIERCE_LEAP);
            double e = 1 - (1 - k) * (1 - k);
            at = pFrom.lerp(pBack, e).add(0, 1.3 * Math.sin(Math.PI * k) + 0.9 * k, 0);
        } else if (m <= KnightMoveData.PIERCE_RISE_END) {
            double k = (m - KnightMoveData.PIERCE_LEAP_END) / (double) (KnightMoveData.PIERCE_RISE_END - KnightMoveData.PIERCE_LEAP_END);
            Vec3 a = pBack.add(0, 0.9, 0);
            at = a.lerp(pHover, ease(k));
            if (m % 2 == 0) {
                ((ServerLevel) level()).sendParticles(GUST, getX(), getY() + 0.2, getZ(), 4, 0.3, 0.1, 0.3, 0.0);
            }
        } else if (m < KnightMoveData.PIERCE_WINDUP_END) {
            at = pHover.add(0, 0.12 * Math.sin((m - KnightMoveData.PIERCE_RISE_END) * 0.5), 0);
            if (alive) {
                turnTo(t, 15.0F);
                if (m <= KnightMoveData.PIERCE_WINDUP_END - 2) {
                    pLock = t.position().add(0, 0.2, 0);      // sighting along the blade; locked just before she goes
                }
            }
            if (m == KnightMoveData.PIERCE_RISE_END + 2) {
                playSound(SoundEvents.TRIDENT_RETURN, 1.0F, 0.8F);
            }
            if (m == KnightMoveData.PIERCE_WINDUP_END - 1) {
                // the path in, stopped short of walls
                Vec3 eye = pHover.add(0, 1.0, 0), goal = pLock.add(0, 1.0, 0);
                BlockHitResult h = level().clip(new ClipContext(eye, goal, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
                if (h.getType() != HitResult.Type.MISS) {
                    Vec3 d = goal.subtract(eye).normalize();
                    pLock = h.getLocation().subtract(d.scale(0.8)).add(0, -1.0, 0);
                }
                pGround = groundBelow(pLock);
                moveYaw = yawTo(pHover, pLock);
                faceYaw(moveYaw);
            }
        } else if (m <= KnightMoveData.PIERCE_HIT) {
            if (m == KnightMoveData.PIERCE_WINDUP_END) {
                playSound(SoundEvents.PHANTOM_SWOOP, 1.4F, 1.4F);
                playSound(SoundEvents.TRIDENT_THROW, 1.2F, 0.7F);
            }
            double k = (m - KnightMoveData.PIERCE_WINDUP_END + 1) / (double) (KnightMoveData.PIERCE_HIT - KnightMoveData.PIERCE_WINDUP_END + 1);
            Vec3 prev = at;
            at = pHover.lerp(pLock, k * k * 0.4 + k * 0.6);
            stab(prev, at);
            ((ServerLevel) level()).sendParticles(GUST, at.x, at.y + 1.0, at.z, 8, 0.3, 0.3, 0.3, 0.0);
            ((ServerLevel) level()).sendParticles(ParticleTypes.CLOUD, at.x, at.y + 1.0, at.z, 3, 0.2, 0.2, 0.2, 0.02);
        } else if (m <= KnightMoveData.PIERCE_LAND) {
            double k = (m - KnightMoveData.PIERCE_HIT) / (double) (KnightMoveData.PIERCE_LAND - KnightMoveData.PIERCE_HIT);
            at = pLock.lerp(pGround, k * k);
            if (m == KnightMoveData.PIERCE_LAND) {
                playSound(SoundEvents.NETHERITE_BLOCK_STEP, 1.2F, 0.7F);
                ((ServerLevel) level()).sendParticles(ParticleTypes.CLOUD, at.x, at.y + 0.1, at.z, 10, 0.5, 0.05, 0.5, 0.05);
                noPhysics = false;
                setNoGravity(false);
            }
        } else {
            return;     // landed: the recovery plays out on her feet
        }
        setPos(at.x, at.y, at.z);
    }

    /** anything along the stab's path this tick, point first */
    private void stab(Vec3 from, Vec3 to) {
        Vec3 a = from.add(0, 1.1, 0), b = to.add(0, 1.1, 0);
        Vec3 ab = b.subtract(a);
        AABB box = new AABB(a, b).inflate(1.6);
        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, box, e -> e != this && e.isAlive() && !hit.contains(e.getUUID()))) {
            Vec3 c = e.position().add(0, e.getBbHeight() * 0.5, 0);
            double s = ab.lengthSqr() < 1e-6 ? 0 : Mth.clamp(c.subtract(a).dot(ab) / ab.lengthSqr(), 0, 1);
            double off = c.distanceTo(a.add(ab.scale(s)));
            if (off < 1.0 + e.getBbWidth() * 0.5) {
                hit.add(e.getUUID());
                if (e.hurt(damageSources().mobAttack(this), PIERCE_DAMAGE)) {
                    Vec3 push = new Vec3(ab.x, 0, ab.z).normalize().scale(1.3);
                    e.setDeltaMovement(e.getDeltaMovement().add(push.x, 0.4, push.z));
                    e.hurtMarked = true;
                    playSound(SoundEvents.PLAYER_ATTACK_CRIT, 1.2F, 0.9F);
                    ((ServerLevel) level()).sendParticles(ParticleTypes.CRIT, c.x, c.y, c.z, 12, 0.3, 0.3, 0.3, 0.3);
                }
            }
        }
    }

    private Vec3 groundBelow(Vec3 p) {
        BlockHitResult h = level().clip(new ClipContext(p.add(0, 0.5, 0), p.add(0, -12, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        return h.getType() == HitResult.Type.MISS ? p : h.getLocation();
    }

    // ---- Wind Blade
    private void tickBlade(@Nullable LivingEntity t, boolean alive) {
        if (alive && moveTick < KnightMoveData.WIND_BLADE_RELEASE) {
            turnTo(t, moveTick < KnightMoveData.WIND_BLADE_FORM ? 20.0F : 5.0F);
        }
        if (moveTick == KnightMoveData.WIND_BLADE_FORM) {
            blade = new WindBladeEntity(level(), this);
            level().addFreshEntity(blade);
            playSound(SoundEvents.ILLUSIONER_CAST_SPELL, 1.2F, 1.3F);
        }
        if (blade != null && moveTick % 8 == 0 && moveTick < KnightMoveData.WIND_BLADE_RELEASE) {
            playSound(SoundEvents.ELYTRA_FLYING, 0.25F, 1.6F);
        }
        if (moveTick == KnightMoveData.WIND_BLADE_RELEASE && blade != null) {
            Vec3 from = blade.position();
            Vec3 aim = alive ? t.position().add(0, 0.9, 0) : from.add(forward(moveYaw).scale(10));
            Vec3 d = aim.subtract(from);
            d = new Vec3(d.x, Mth.clamp(d.y, -0.25 * d.horizontalDistance(), 0.25 * d.horizontalDistance()), d.z).normalize();
            blade.fire(d);
            blade = null;
            playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 1.4F, 0.6F);
            playSound(SoundEvents.TRIDENT_THROW, 1.0F, 0.6F);
        }
    }

    /** where the blade forms: in front of her at chest height */
    public Vec3 bladeAnchor(float partialTick) {
        float yaw = Mth.rotLerp(partialTick, yBodyRotO, yBodyRot);
        return getPosition(partialTick).add(forward(yaw).scale(1.7)).add(0, 1.15, 0);
    }

    // ------------------------------------------------------------ helpers
    static Vec3 forward(float yaw) {
        float r = yaw * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(r), 0, Mth.cos(r));
    }

    static Vec3 right(float yaw) {
        float r = yaw * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.cos(r), 0, -Mth.sin(r));
    }

    private Vec3 local(float[] fru) {
        return local(fru, yBodyRot);
    }

    private Vec3 local(float[] fru, float yaw) {
        return position().add(forward(yaw).scale(fru[0])).add(right(yaw).scale(fru[1])).add(0, fru[2], 0);
    }

    static float yawTo(Vec3 from, Vec3 to) {
        return (float) Math.toDegrees(Math.atan2(-(to.x - from.x), to.z - from.z));
    }

    private void faceYaw(float yaw) {
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
    }

    private void turnTo(LivingEntity t, float max) {
        float want = yawTo(position(), t.position());
        moveYaw = Mth.approachDegrees(moveYaw, want, max);
        faceYaw(moveYaw);
    }

    private double horizontalDistanceTo(LivingEntity e) {
        double dx = e.getX() - getX(), dz = e.getZ() - getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    // ------------------------------------------------------------ damage
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (inIntro() && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return false;
        }
        if (source.is(DamageTypeTags.IS_FALL)) {
            return false;
        }
        return super.hurt(source, amount);
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public void knockback(double strength, double x, double z) {
        if (!isInMove()) {
            super.knockback(strength * 0.4, x, z);
        }
    }

    @Override
    public boolean isPushable() {
        return !isInMove() && super.isPushable();
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        bossBar.setProgress(getHealth() / getMaxHealth());
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        bossBar.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        bossBar.removePlayer(player);
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide) {
            if (orb != null && !orb.launched()) {
                orb.discard();
            }
            if (blade != null && !blade.fired()) {
                blade.discard();
            }
        }
        super.remove(reason);
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        // drawn sat on the wall three blocks behind her feet during the entrance
        return super.getBoundingBoxForCulling().inflate(inIntro() ? 4.0 : 2.0);
    }

    // ------------------------------------------------------------ client
    private void clientFx() {
        if (inIntro()) {
            return;
        }
        if (random.nextInt(5) == 0) {
            level().addParticle(GUST, getRandomX(0.8), getY() + 0.1 + random.nextDouble() * 0.3, getRandomZ(0.8), 0, 0.02, 0);
        }
        if (getMove() == MOVE_PIERCE && moveTime(0) > KnightMoveData.PIERCE_LEAP && moveTime(0) < KnightMoveData.PIERCE_LAND) {
            level().addParticle(ParticleTypes.CLOUD, getRandomX(0.5), getY() + random.nextDouble() * 1.8, getRandomZ(0.5), 0, 0, 0);
        }
    }

    // ------------------------------------------------------------ GeckoLib
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        AnimationController<FeatheredKnightEntity> c = new AnimationController<>(this, "main", 4, state -> {
            int m = getMove();
            if (m == MOVE_SIT) {
                return state.setAndContinue(SIT);
            }
            if (m == MOVE_INTRO) {
                return state.setAndContinue(INTRO);
            }
            if (state.isMoving() && !isInMove()) {
                double v = Math.sqrt((getX() - xo) * (getX() - xo) + (getZ() - zo) * (getZ() - zo)) * 20.0;
                return state.setAndContinue(v > 5.0 ? RUN : WALK);
            }
            return state.setAndContinue(IDLE);
        });
        for (String n : ONCE) {
            c.triggerableAnim(n, RawAnimation.begin().thenPlay(P + n));
        }
        controllers.add(c);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    // ------------------------------------------------------------ misc
    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.PLAYER_HURT;
    }

    @Override
    public float getVoicePitch() {
        return 1.3F;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.PHANTOM_DEATH;
    }

    @Override
    protected float getSoundVolume() {
        return 0.8F;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Semi", isSemi());
        if (foe != null) {
            tag.putUUID("Foe", foe);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.entityData.set(SEMI, tag.getBoolean("Semi"));
        if (tag.hasUUID("Foe")) {
            foe = tag.getUUID("Foe");
        }
        if (hasCustomName()) {
            bossBar.setName(getDisplayName());
        }
    }

    @Override
    public void setCustomName(@Nullable Component name) {
        super.setCustomName(name);
        bossBar.setName(getDisplayName());
    }

    /** green sparks of wind, for the spells */
    public static DustParticleOptions gust() {
        return GUST;
    }

    public static void windSound(Level level, Vec3 p, SoundEvent s, float vol, float pitch) {
        level.playSound(null, p.x, p.y, p.z, s, SoundSource.HOSTILE, vol, pitch);
    }
}
