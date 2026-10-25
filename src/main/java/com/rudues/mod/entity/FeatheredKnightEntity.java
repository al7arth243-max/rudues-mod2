package com.rudues.mod.entity;

import com.rudues.mod.ability.HeroForm;
import com.rudues.mod.ability.SemiPrime;
import com.rudues.mod.network.Network;
import com.rudues.mod.registry.ModParticles;
import com.rudues.mod.world.CastleLayout;
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
    /** phase two: the clip she plays (index into SEMI_ANIMS, -1 = hover/fly by speed), and when it started */
    private static final EntityDataAccessor<Integer> ANIM = SynchedEntityData.defineId(FeatheredKnightEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ANIM_START = SynchedEntityData.defineId(FeatheredKnightEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> TALONS = SynchedEntityData.defineId(FeatheredKnightEntity.class, EntityDataSerializers.BOOLEAN);
    /** the Semi Prime scene: stage, its start, the clash gauge, and whose scene it is */
    private static final EntityDataAccessor<Integer> SCENE = SynchedEntityData.defineId(FeatheredKnightEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> SCENE_START = SynchedEntityData.defineId(FeatheredKnightEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> BALANCE = SynchedEntityData.defineId(FeatheredKnightEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> SCENE_PLAYER = SynchedEntityData.defineId(FeatheredKnightEntity.class, EntityDataSerializers.INT);

    public static final int MOVE_NONE = 0, MOVE_SIT = 1, MOVE_INTRO = 2, MOVE_BURST = 3, MOVE_AEROGA = 4, MOVE_PIERCE = 5, MOVE_BLADE = 6;

    private static final String P = "animation.feathered_knight.";
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop(P + "idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop(P + "move");
    private static final RawAnimation RUN = RawAnimation.begin().thenLoop(P + "run");
    private static final RawAnimation SIT = RawAnimation.begin().thenLoop(P + "sit");
    private static final RawAnimation INTRO = RawAnimation.begin().thenPlayAndHold(P + "intro");
    private static final String[] ONCE = {"wind_burst", "aeroga", "pierce", "wind_blade"};
    private static final String[] SEMI_ANIMS = {"semi_transform", "semi_ascend", "rubble_hold", "rubble_dive", "rubble_strain", "rubble_knocked",
            "rubble_release", "nosedive_rise", "dive", "swarm", "triple_aeroga", "slash_launch", "rain_volley", "grinder_screech", "grinder_charge",
            "grinder_carry", "grinder_grind", "grinder_hurl", "fly", "hover", "defeat_fall", "defeat_rise", "defeat_screech", "storm_call"};
    private static final java.util.Set<String> SEMI_LOOPS = java.util.Set.of("rubble_hold", "rubble_strain", "grinder_charge", "grinder_carry",
            "grinder_grind", "fly", "hover", "storm_call");
    private static final RawAnimation[] SEMI_RAW = new RawAnimation[SEMI_ANIMS.length];
    static {
        for (int i = 0; i < SEMI_ANIMS.length; i++) {
            String n = SEMI_ANIMS[i];
            SEMI_RAW[i] = SEMI_LOOPS.contains(n) ? RawAnimation.begin().thenLoop(P + n) : RawAnimation.begin().thenPlayAndHold(P + n);
        }
    }
    private static final RawAnimation HOVER = RawAnimation.begin().thenLoop(P + "hover");
    private static final RawAnimation FLY = RawAnimation.begin().thenLoop(P + "fly");
    /** below this share of her health she turns Semi Prime */
    public static final float SEMI_AT = 0.2F;

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
    // ---- phase two
    private boolean sceneDone, pendingScene, sceneWon, pendingDefeat;
    @Nullable
    private UUID sceneFoe;
    private int sceneT, pressesThisTick;
    private Vec3 sceneFrom = Vec3.ZERO;
    private float healFrom;
    @Nullable
    private RubbleEntity rubble;
    private int rainCooldown = 20 * 12, tripleCooldown;
    private Vec3 mFrom = Vec3.ZERO, mAim = Vec3.ZERO, mLock = Vec3.ZERO, mDir = Vec3.ZERO;
    private double arcA;
    private int arcDir = 1, grabTick = -1, missTick = -1;
    private boolean caught;
    /** set while she herself lets go of whoever she is carrying (so the dismount isn't refused) */
    public boolean letGo;
    private final AerogaOrbEntity[] triple = new AerogaOrbEntity[3];
    /** client: the ANIM_START last seen, to restart a clip asked for twice */
    private int seenAnimStart = Integer.MIN_VALUE;

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
        this.entityData.define(ANIM, -1);
        this.entityData.define(ANIM_START, 0);
        this.entityData.define(TALONS, false);
        this.entityData.define(SCENE, KnightScene.NONE);
        this.entityData.define(SCENE_START, 0);
        this.entityData.define(BALANCE, KnightScene.START_BALANCE);
        this.entityData.define(SCENE_PLAYER, -1);
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
        return this.entityData.get(TALONS);
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
            return !isSemi() && !isInMove() && getTarget() != null && getTarget().isAlive();
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
            // she always squares up to where she faces (the client would otherwise turn her body to her flight path)
            yBodyRot = getYRot();
            yHeadRot = getYRot();
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
        if (getMove() == MOVE_SCENE) {
            tickScene();
            return;
        }
        if (pendingScene && target instanceof ServerPlayer sp && sp.isAlive() && getMove() != MOVE_PIERCE) {
            startScene(sp);
            return;
        }
        if (isVehicle() && !grinding()) {
            letGo = true;
            ejectPassengers();
            letGo = false;
        }
        if (isSemi() && pendingDefeat && target instanceof ServerPlayer dp && dp.isAlive()) {
            if (getMove() >= MOVE_NOSEDIVE) {
                endSemiMove();
            }
            startDefeat(dp);
            return;
        }
        if (isSemi()) {
            if (rainCooldown > 0) {
                rainCooldown--;
            }
            if (tripleCooldown > 0) {
                tripleCooldown--;
            }
            if (getMove() >= MOVE_NOSEDIVE) {
                tickSemiMove(target);
            } else {
                tickFlight(target);
                if (cooldown > 0) {
                    cooldown--;
                } else if (target != null && target.isAlive()) {
                    startSemiMove(chooseSemi(target), target);
                }
            }
            return;
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

    private static final float[][] TRIPLE_ORBS = {{0.1F, 0.0F, 3.4F}, {0.3F, -1.5F, 2.7F}, {0.3F, 1.5F, 2.7F}};

    /** an orb's place while she holds it up: the single Aeroga's (slot -1) or one of Triple Aeroga's three */
    public Vec3 orbAnchor(int slot, float partialTick) {
        if (slot < 0) {
            return orbAnchor(partialTick);
        }
        float yaw = Mth.rotLerp(partialTick, yBodyRotO, yBodyRot);
        float[] o = TRIPLE_ORBS[Math.min(slot, 2)];
        return getPosition(partialTick).add(forward(yaw).scale(o[0])).add(right(yaw).scale(o[1])).add(0, o[2], 0);
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

    // ================================================================ phase two: the Semi Prime scene
    /** players held in her scene: hits on them are refused unless the scene lets one through */
    private static final java.util.Map<UUID, Boolean> SHIELDED = new java.util.concurrent.ConcurrentHashMap<>();

    /** true while this player is held in her scene and the scene isn't hitting them itself */
    public static boolean shielded(Player p) {
        Boolean b = SHIELDED.get(p.getUUID());
        return b != null && !b;
    }

    public static boolean inScene(Player p) {
        return SHIELDED.containsKey(p.getUUID());
    }

    public int sceneStage() {
        return this.entityData.get(SCENE);
    }

    public float sceneTime(float partialTick) {
        return (float) (level().getGameTime() - this.entityData.get(SCENE_START)) + partialTick;
    }

    public int scenePlayerId() {
        return this.entityData.get(SCENE_PLAYER);
    }

    public float balance() {
        return this.entityData.get(BALANCE);
    }

    private void setStage(int stage) {
        this.entityData.set(SCENE, stage);
        this.entityData.set(SCENE_START, (int) level().getGameTime());
        sceneT = 0;
    }

    private void setAnim(String name) {
        int i = java.util.Arrays.asList(SEMI_ANIMS).indexOf(name);
        this.entityData.set(ANIM, i);
        this.entityData.set(ANIM_START, (int) level().getGameTime());
    }

    private void clearAnim() {
        this.entityData.set(ANIM, -1);
    }

    @Nullable
    private ServerPlayer scenePlayer() {
        return sceneFoe != null && level() instanceof ServerLevel sl && sl.getPlayerByUUID(sceneFoe) instanceof ServerPlayer sp ? sp : null;
    }

    private void startScene(ServerPlayer p) {
        pendingScene = false;
        if (isInMove()) {
            endMove();
        }
        sceneFoe = p.getUUID();
        SHIELDED.put(p.getUUID(), false);
        this.entityData.set(SCENE_PLAYER, p.getId());
        this.entityData.set(BALANCE, KnightScene.START_BALANCE);
        HeroForm.revert(p);
        if (p.isPassenger()) {
            p.stopRiding();
        }
        // her spells go still
        for (net.minecraft.world.entity.projectile.Projectile pr : level().getEntitiesOfClass(net.minecraft.world.entity.projectile.Projectile.class,
                getBoundingBox().inflate(64), e -> e.getOwner() == this)) {
            pr.discard();
        }
        Vec3 o = KnightScene.origin();
        p.connection.teleport(o.x, o.y, o.z, KnightScene.PLAYER_YAW, 0.0F);
        p.setDeltaMovement(Vec3.ZERO);
        Vec3 s = KnightScene.start();
        noPhysics = true;
        setNoGravity(true);
        setPos(s.x, s.y, s.z);
        faceYaw(KnightScene.KNIGHT_YAW);
        getNavigation().stop();
        setMove(MOVE_SCENE);
        setStage(KnightScene.TRANSFORM);
        setAnim("semi_transform");
        playSound(SoundEvents.PHANTOM_HURT, 1.6F, 0.6F);
    }

    /** the player's attack-button presses during the clash */
    public void clashInput(ServerPlayer p, int presses) {
        if (sceneStage() != KnightScene.STRAIN || !p.getUUID().equals(sceneFoe) || sceneT < KnightScene.PROMPT_DELAY) {
            return;
        }
        int n = Math.min(presses, Math.max(0, 2 - pressesThisTick));
        pressesThisTick += n;
        this.entityData.set(BALANCE, Math.min(1.0F, balance() + n * KnightScene.PRESS));
    }

    private void holdPlayer(ServerPlayer p) {
        Vec3 o = KnightScene.origin();
        p.fallDistance = 0;
        p.setDeltaMovement(Vec3.ZERO);
        if (p.position().distanceToSqr(o) > 0.09) {
            p.connection.teleport(o.x, o.y, o.z, KnightScene.PLAYER_YAW, 0.0F);
        }
    }

    private boolean corrupt(ServerPlayer p) {
        return SemiPrime.engaged(p);
    }

    private void tickScene() {
        ServerPlayer p = scenePlayer();
        ServerLevel sl = (ServerLevel) level();
        if (p == null || !p.isAlive() || p.level() != level()) {
            abortScene();
            return;
        }
        pressesThisTick = 0;
        holdPlayer(p);
        setDeltaMovement(Vec3.ZERO);
        int stage = sceneStage();
        int t = sceneT++;
        Vec3 o = KnightScene.origin();
        switch (stage) {
            case KnightScene.TRANSFORM -> {
                faceYaw(KnightScene.KNIGHT_YAW);
                if (t == 8) {
                    playSound(SoundEvents.WITHER_HURT, 1.0F, 1.6F);
                }
                if (t >= 10 && t < KnightMoveData.SEMI_TRANSFORM_SEMI && t % 3 == 0) {
                    sl.sendParticles(GUST, getX(), getY() + 1.1, getZ(), 6, 0.3, 0.4, 0.3, 0.0);
                }
                if (t == KnightMoveData.SEMI_TRANSFORM_SEMI) {
                    this.entityData.set(SEMI, true);
                    playSound(SoundEvents.ENDER_DRAGON_GROWL, 1.4F, 1.7F);
                    playSound(SoundEvents.PHANTOM_AMBIENT, 2.0F, 0.6F);
                    playSound(SoundEvents.ENDER_DRAGON_FLAP, 2.0F, 0.8F);
                    sl.sendParticles(GUST, getX(), getY() + 1.4, getZ(), 120, 1.6, 1.0, 1.6, 0.0);
                    sl.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 1.4, getZ(), 40, 1.2, 0.8, 1.2, 0.18);
                    sl.sendParticles(ParticleTypes.END_ROD, getX(), getY() + 1.4, getZ(), 30, 1.0, 1.0, 1.0, 0.1);
                    p.displayClientMessage(Component.translatable("message.rudues.knight_semi"), true);
                }
                if (t >= KnightScene.length(stage)) {
                    setStage(KnightScene.ASCEND);
                    setAnim("semi_ascend");
                    this.entityData.set(TALONS, true);
                    rubble = new RubbleEntity(level(), this);
                    level().addFreshEntity(rubble);
                    Vec3 c = rubble.centre();
                    sl.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK,
                            net.minecraft.world.level.block.Blocks.STONE_BRICKS.defaultBlockState()), c.x, getY() + 0.2, c.z, 80, 1.4, 0.3, 1.4, 0.3);
                    playSound(SoundEvents.STONE_BREAK, 2.0F, 0.5F);
                    playSound(SoundEvents.GENERIC_EXPLODE, 1.0F, 0.6F);
                }
            }
            case KnightScene.ASCEND -> {
                Vec3 a = KnightScene.start(), b = KnightScene.high();
                double k = KnightScene.ease((t - KnightMoveData.SEMI_ASCEND_LIFT) / 33.0);
                Vec3 at = a.lerp(b, k);
                setPos(at.x, at.y, at.z);
                if (t % 12 == 4) {
                    playSound(SoundEvents.ENDER_DRAGON_FLAP, 1.6F, 0.9F);
                }
                if (t >= KnightScene.length(stage)) {
                    setAnim("rubble_hold");
                    if (SemiPrime.engaged(p)) {
                        beginDive(p);
                    } else {
                        setStage(KnightScene.PLAYER);
                        p.setYRot(KnightScene.PLAYER_YAW);
                        SemiPrime.begin(p);
                    }
                }
            }
            case KnightScene.PLAYER -> {
                Vec3 h = KnightScene.high().add(0, 0.25 * Math.sin(t * 0.15), 0);
                setPos(h.x, h.y, h.z);
                if (t % 15 == 0) {
                    playSound(SoundEvents.ENDER_DRAGON_FLAP, 1.2F, 0.9F);
                }
                if (t >= KnightScene.length(stage)) {
                    beginDive(p);
                }
            }
            case KnightScene.DIVE -> {
                Vec3 h = KnightScene.high(), k0 = KnightScene.knightStrain();
                Vec3 at;
                if (t < KnightMoveData.RUBBLE_DIVE_GO) {
                    at = h.add(0, 0.6 * KnightScene.ease(t / (double) KnightMoveData.RUBBLE_DIVE_GO), 0);
                } else {
                    double k = (t - KnightMoveData.RUBBLE_DIVE_GO) / (double) (KnightScene.length(stage) - KnightMoveData.RUBBLE_DIVE_GO);
                    k = Mth.clamp(k, 0, 1);
                    at = h.add(0, 0.6, 0).lerp(k0, k * k);
                    if (t == KnightMoveData.RUBBLE_DIVE_GO) {
                        playSound(SoundEvents.PHANTOM_SWOOP, 2.0F, 0.6F);
                    }
                }
                setPos(at.x, at.y, at.z);
                fistFlames(sl, p, KnightMoveData.PLAYER_CHARGE_FIST, 3);
                if (t >= KnightScene.length(stage)) {
                    setStage(KnightScene.STRAIN);
                    setAnim("rubble_strain");
                    Network.sendPuppet(p, "rubble_strain_p", KnightScene.STRAIN_MAX + 20, KnightScene.PLAYER_YAW, false, corrupt(p));
                    playSound(SoundEvents.ANVIL_LAND, 1.6F, 0.5F);
                    playSound(SoundEvents.GENERIC_EXPLODE, 1.2F, 0.9F);
                    Vec3 c = KnightScene.contact();
                    sl.sendParticles(ModParticles.PURPLE_FLAME_BIG.get(), c.x, c.y, c.z, 30, 0.4, 0.3, 0.4, 0.1);
                    sl.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y + 0.6, c.z, 2, 0.3, 0.3, 0.3, 0.0);
                }
            }
            case KnightScene.STRAIN -> {
                Vec3 k0 = KnightScene.knightStrain();
                double jx = (random.nextDouble() - 0.5) * 0.06, jy = (random.nextDouble() - 0.5) * 0.06;
                setPos(k0.x + jx, k0.y + jy, k0.z);
                fistFlames(sl, p, KnightMoveData.PLAYER_STRAIN_FIST, 4);
                Vec3 r = KnightScene.rubbleStrain();
                if (t % 2 == 0) {
                    sl.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK,
                            net.minecraft.world.level.block.Blocks.STONE_BRICKS.defaultBlockState()), r.x, r.y - 1.1, r.z, 3, 0.7, 0.1, 0.7, 0.1);
                    sl.sendParticles(GUST, r.x, r.y + 1.3, r.z, 3, 1.0, 0.3, 1.0, 0.0);
                }
                if (t % 10 == 0) {
                    playSound(SoundEvents.GRINDSTONE_USE, 1.0F, 0.5F);
                    playSound(SoundEvents.FIRECHARGE_USE, 0.6F, 0.6F);
                }
                if (t >= KnightScene.PROMPT_DELAY) {
                    this.entityData.set(BALANCE, balance() - KnightScene.decay(t));
                }
                float b = balance();
                if (b >= 1.0F || (t >= KnightScene.STRAIN_MAX && b >= 0.5F)) {
                    sceneWin(p, sl);
                } else if (b <= 0.0F || t >= KnightScene.STRAIN_MAX) {
                    sceneLose(p, sl);
                }
            }
            case KnightScene.WIN -> {
                Vec3 a = KnightScene.knightStrain(), b = KnightScene.winLanding();
                int land = KnightMoveData.RUBBLE_KNOCKED_LAND;
                if (t <= land) {
                    double k = t / (double) land;
                    Vec3 at = a.lerp(b, 1 - (1 - k) * (1 - k)).add(0, 4.5 * Math.sin(Math.PI * k), 0);
                    setPos(at.x, at.y, at.z);
                    if (t == land) {
                        playSound(SoundEvents.NETHERITE_BLOCK_FALL, 1.6F, 0.6F);
                        playSound(SoundEvents.GENERIC_EXPLODE, 0.6F, 1.2F);
                        sl.sendParticles(ParticleTypes.CLOUD, b.x, b.y + 0.2, b.z, 24, 1.0, 0.1, 1.0, 0.08);
                    }
                }
                if (t == 10) {
                    this.entityData.set(TALONS, false);
                }
                if (t >= KnightScene.length(stage)) {
                    beginReturn(p);
                }
            }
            case KnightScene.LOSE -> {
                Vec3 a = KnightScene.knightStrain(), b = KnightScene.loseBack();
                double k = KnightScene.ease(t / 28.0);
                Vec3 at = a.lerp(b, k);
                setPos(at.x, at.y, at.z);
                if (t == 6) {
                    if (rubble != null) {
                        rubble.shatter();
                        rubble = null;
                    }
                    SHIELDED.put(p.getUUID(), true);
                    p.invulnerableTime = 0;
                    p.hurt(damageSources().mobAttack(this), Math.max(16.0F, p.getMaxHealth() * 0.55F));
                    SHIELDED.put(p.getUUID(), false);
                    p.setDeltaMovement(Vec3.ZERO);
                    Network.sendPuppet(p, "rubble_crushed_p", 50, KnightScene.PLAYER_YAW, false, corrupt(p));
                    sl.sendParticles(ParticleTypes.EXPLOSION_EMITTER, o.x, o.y + 0.5, o.z, 1, 0, 0, 0, 0);
                }
                if (t == 16) {
                    this.entityData.set(TALONS, false);
                }
                if (t >= KnightScene.length(stage)) {
                    beginReturn(p);
                }
            }
            case KnightScene.RETURN -> {
                Vec3 a = sceneFrom, b = KnightScene.home();
                double k = KnightScene.ease(t / 22.0);
                Vec3 at = a.lerp(b, k).add(0, 0.12 * Math.sin(t * 0.25), 0);
                setPos(at.x, at.y, at.z);
                faceYaw(KnightScene.KNIGHT_YAW);
                float healK = Mth.clamp(t / 30.0F, 0.0F, 1.0F);
                setHealth(Mth.lerp(healK, healFrom, getMaxHealth()));
                if (t % 3 == 0 && t < 32) {
                    sl.sendParticles(GUST, getX(), getY() + 1.2, getZ(), 10, 0.8, 0.9, 0.8, 0.0);
                    sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, getX(), getY() + 1.2, getZ(), 3, 0.6, 0.8, 0.6, 0.0);
                }
                if (t >= KnightScene.length(stage)) {
                    endScene(p);
                }
            }
            case KnightScene.D_FALL -> {
                Vec3 a = KnightScene.home(), b = KnightScene.start();
                int land = KnightMoveData.DEFEAT_FALL_LAND;
                double k = Mth.clamp(t / (double) land, 0, 1);
                glideTo(a.lerp(b, k * k).add(0, 0.8 * Math.sin(Math.PI * k), 0));
                if (t == land) {
                    playSound(SoundEvents.NETHERITE_BLOCK_FALL, 2.0F, 0.5F);
                    playSound(SoundEvents.PHANTOM_HURT, 1.6F, 0.6F);
                    sl.sendParticles(ParticleTypes.CLOUD, b.x, b.y + 0.2, b.z, 30, 1.2, 0.1, 1.2, 0.08);
                }
                if (t >= KnightScene.length(stage)) {
                    setStage(KnightScene.D_RISE);
                    setAnim("defeat_rise");
                }
            }
            case KnightScene.D_RISE -> {
                glideTo(KnightScene.start());
                if (t == 10 || t == 46 || t == 62) {
                    playSound(SoundEvents.PHANTOM_HURT, 1.2F, 0.7F + random.nextFloat() * 0.2F);
                }
                if (t >= KnightScene.length(stage)) {
                    setStage(KnightScene.D_ASCEND);
                    setAnim("defeat_screech");
                }
            }
            case KnightScene.D_ASCEND -> {
                Vec3 a = KnightScene.start(), b = KnightScene.start().add(0, 30, 0);
                double k = KnightScene.ease((t - KnightMoveData.DEFEAT_SCREECH_LIFT) / 41.0);
                glideTo(a.lerp(b, k));
                if (t == KnightMoveData.DEFEAT_SCREECH_LIFT) {
                    playSound(SoundEvents.ENDER_DRAGON_FLAP, 3.0F, 0.7F);
                }
                if (t == KnightMoveData.DEFEAT_SCREECH_SCREECH) {
                    playSound(SoundEvents.PHANTOM_AMBIENT, 5.0F, 0.4F);
                    playSound(SoundEvents.ENDER_DRAGON_GROWL, 3.0F, 1.2F);
                }
                if (t >= KnightScene.length(stage)) {
                    setStage(KnightScene.D_STORM);
                    setAnim("storm_call");
                }
            }
            case KnightScene.D_STORM -> {
                Vec3 at = KnightScene.start().add(0, 30 + 0.4 * Math.sin(t * 0.15), 0);
                glideTo(at);
                if (t % 10 == 0) {
                    sl.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ELYTRA_FLYING, net.minecraft.sounds.SoundSource.HOSTILE,
                            1.0F + t / 40.0F, 0.5F + t / 300.0F);
                }
                if (t == 20 || t == 60) {
                    playSound(SoundEvents.PHANTOM_AMBIENT, 5.0F, 0.4F);
                }
                if (t % 3 == 0) {
                    sl.sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 2, p.getZ(), 10, 8, 4, 8, 0.4);
                }
                if (t == KnightScene.STORM_CARRY) {
                    carryToCrater(p);
                    return;
                }
            }
            default -> abortScene();
        }
    }

    private void glideTo(Vec3 v) {
        setPos(v.x, v.y, v.z);
    }

    private void startDefeat(ServerPlayer p) {
        pendingDefeat = false;
        sceneFoe = p.getUUID();
        SHIELDED.put(p.getUUID(), false);
        this.entityData.set(SCENE_PLAYER, p.getId());
        this.entityData.set(TALONS, false);
        if (p.isPassenger()) {
            p.stopRiding();
        }
        for (net.minecraft.world.entity.projectile.Projectile pr : level().getEntitiesOfClass(net.minecraft.world.entity.projectile.Projectile.class,
                getBoundingBox().inflate(64), e -> e.getOwner() == this)) {
            pr.discard();
        }
        Vec3 o = KnightScene.origin();
        p.connection.teleport(o.x, o.y, o.z, KnightScene.PLAYER_YAW, 0.0F);
        Vec3 h = KnightScene.home();
        noPhysics = true;
        setNoGravity(true);
        setPos(h.x, h.y, h.z);
        faceYaw(KnightScene.KNIGHT_YAW);
        setMove(MOVE_SCENE);
        setStage(KnightScene.D_FALL);
        setAnim("defeat_fall");
        playSound(SoundEvents.PHANTOM_DEATH, 2.0F, 0.8F);
    }

    /** the storm has taken the castle: the player wakes in the crater, Garuda waits */
    private void carryToCrater(ServerPlayer p) {
        net.minecraft.server.MinecraftServer server = p.getServer();
        if (server == null || !com.rudues.mod.castle.CraterManager.ensureBuilt(server)) {
            abortScene();
            return;
        }
        ServerLevel crater = com.rudues.mod.castle.CraterManager.craterLevel(server);
        GarudaEntity.shield(p);
        SHIELDED.remove(p.getUUID());
        com.rudues.mod.castle.CraterManager.bring(p);
        GarudaEntity g = com.rudues.mod.registry.ModEntities.GARUDA.get().create(crater);
        if (g != null) {
            Vec3 s = GarudaScene.flyFrom().add(0, 20, 30);
            g.moveTo(s.x, s.y, s.z, 180.0F, 0.0F);
            crater.addFreshEntity(g);
            g.beginArrival(p);
        }
        sceneFoe = null;
        discard();
    }

    private void beginDive(ServerPlayer p) {
        setStage(KnightScene.DIVE);
        setAnim("rubble_dive");
        Network.sendPuppet(p, "rubble_charge", 200, KnightScene.PLAYER_YAW, false, corrupt(p));
        playSound(SoundEvents.PHANTOM_AMBIENT, 2.0F, 0.5F);
    }

    private void sceneWin(ServerPlayer p, ServerLevel sl) {
        setStage(KnightScene.WIN);
        setAnim("rubble_knocked");
        if (rubble != null) {
            rubble.shatter();
            rubble = null;
        }
        Network.sendPuppet(p, "rubble_win_p", 40, KnightScene.PLAYER_YAW, false, corrupt(p));
        Vec3 c = KnightScene.contact();
        sl.sendParticles(ModParticles.PURPLE_FLAME_BIG.get(), c.x, c.y + 1.0, c.z, 60, 0.8, 0.8, 0.8, 0.2);
        sl.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y + 1.2, c.z, 1, 0, 0, 0, 0);
        playSound(SoundEvents.PHANTOM_DEATH, 1.6F, 0.7F);
        Network.sendBoom(sl, c.x, c.y + 1.3, c.z, KnightScene.OY, 1.2F);
        sceneWon = true;
    }

    private void sceneLose(ServerPlayer p, ServerLevel sl) {
        setStage(KnightScene.LOSE);
        setAnim("rubble_release");
        sceneWon = false;
        playSound(SoundEvents.PHANTOM_AMBIENT, 2.0F, 0.7F);
    }

    private void beginReturn(ServerPlayer p) {
        sceneFrom = position();
        healFrom = getHealth();
        setStage(KnightScene.RETURN);
        clearAnim();
        this.entityData.set(TALONS, false);
        playSound(SoundEvents.ENDER_DRAGON_GROWL, 1.2F, 1.8F);
        Network.sendPuppet(p, "", 0, 0.0F, false, false);
    }

    private void endScene(@Nullable ServerPlayer p) {
        setStage(KnightScene.NONE);
        this.entityData.set(SCENE_PLAYER, -1);
        this.entityData.set(TALONS, false);
        clearAnim();
        setMove(MOVE_NONE);
        sceneDone = true;
        setHealth(getMaxHealth());
        if (sceneFoe != null) {
            SHIELDED.remove(sceneFoe);
        }
        if (rubble != null) {
            rubble.discard();
            rubble = null;
        }
        cooldown = 24;
        rainCooldown = 20 * 12;
        noPhysics = false;
        setNoGravity(true);
        if (p != null) {
            setTarget(p);
        }
    }

    private void abortScene() {
        ServerPlayer p = scenePlayer();
        if (p != null) {
            Network.sendPuppet(p, "", 0, 0.0F, false, false);
        }
        this.entityData.set(SEMI, true);
        endScene(p);
    }

    /** the player's burning fist (their stand-in's fist, from its clip; they face +z) */
    private void fistFlames(ServerLevel sl, ServerPlayer p, float[] fru, int n) {
        Vec3 f = KnightScene.origin().add(-fru[1], fru[2], fru[0]);
        sl.sendParticles(ModParticles.PURPLE_FLAME.get(), f.x, f.y, f.z, n, 0.12, 0.12, 0.12, 0.02);
        sl.sendParticles(ModParticles.PURPLE_FLAME_BIG.get(), f.x, f.y, f.z, 1, 0.08, 0.08, 0.08, 0.02);
    }

    /** where the rubble is (its centre), wherever she holds it; null once she has let go */
    @Nullable
    public Vec3 rubbleCentre(float partialTick) {
        int stage = sceneStage();
        float t = sceneTime(partialTick);
        float yaw = Mth.rotLerp(partialTick, yBodyRotO, yBodyRot);
        Vec3 at = getPosition(partialTick);
        float[] g;
        switch (stage) {
            case KnightScene.STRAIN -> {
                Vec3 r = KnightScene.rubbleStrain();
                return r.add(0.03 * Mth.sin(t * 2.1F), 0.03 * Mth.sin(t * 2.7F), 0);
            }
            case KnightScene.LOSE -> {
                double k = Mth.clamp(t / 6.0, 0, 1);
                return KnightScene.rubbleStrain().lerp(KnightScene.origin().add(0, 1.0, 0.4), k * k);
            }
            case KnightScene.DIVE -> {
                float k = Mth.clamp((t - KnightMoveData.RUBBLE_DIVE_GO) / 6.0F, 0, 1);
                float[] a = KnightMoveData.SEMI_ASCEND_GRIP, b = KnightMoveData.RUBBLE_DIVE_GRIP;
                g = new float[]{Mth.lerp(k, a[0], b[0]), Mth.lerp(k, a[1], b[1]), Mth.lerp(k, a[2], b[2])};
            }
            case KnightScene.ASCEND, KnightScene.PLAYER -> g = KnightMoveData.SEMI_ASCEND_GRIP;
            default -> {
                return null;
            }
        }
        return at.add(forward(yaw).scale(g[0])).add(right(yaw).scale(g[1])).add(0, g[2], 0);
    }

    // ================================================================ phase two: flight and the Semi Prime moves
    public static final int MOVE_NOSEDIVE = 10, MOVE_SWARM = 11, MOVE_TRIPLE = 12, MOVE_LAUNCH = 13, MOVE_RAIN = 14, MOVE_GRINDER = 15, MOVE_SCENE = 20;
    private static final int RAIN_COOLDOWN = 20 * 40, TRIPLE_COOLDOWN = 20 * 8;
    public static final float DIVE_DAMAGE = 16.0F, RAKE_DAMAGE = 10.0F, SMASH_DAMAGE = 12.0F, SLAM_DAMAGE = 8.0F, GRIND_DAMAGE = 2.0F;
    private static final double HOVER_UP = 1.4;

    private static double clampX(double x) {
        return Mth.clamp(x, CastleLayout.KX0 + 2.5, CastleLayout.KX1 - 1.5);
    }

    private static double clampZ(double z) {
        return Mth.clamp(z, CastleLayout.KZ0 + 2.5, CastleLayout.KZ1 - 1.5);
    }

    private static double floorY() {
        return CastleLayout.ROOF;
    }

    /** between moves: hover at fencing distance, facing them */
    private void tickFlight(@Nullable LivingEntity t) {
        noPhysics = false;
        setNoGravity(true);
        fallDistance = 0;
        if (t == null) {
            setDeltaMovement(getDeltaMovement().scale(0.8));
            return;
        }
        Vec3 away = new Vec3(getX() - t.getX(), 0, getZ() - t.getZ());
        away = away.lengthSqr() < 1e-3 ? forward(getYRot()).scale(-1) : away.normalize();
        double want = 7.0;
        Vec3 goal = new Vec3(clampX(t.getX() + away.x * want), floorY() + HOVER_UP + 0.25 * Math.sin(tickCount * 0.08), clampZ(t.getZ() + away.z * want));
        Vec3 d = goal.subtract(position());
        double len = d.length(), sp = 0.32;
        Vec3 v = len > sp ? d.scale(sp / len) : d;
        setDeltaMovement(getDeltaMovement().scale(0.4).add(v.scale(0.6)));
        faceYaw(Mth.approachDegrees(getYRot(), yawTo(position(), t.position()), 12.0F));
    }

    private int chooseSemi(LivingEntity t) {
        double d = horizontalDistanceTo(t);
        java.util.List<Integer> pool = new java.util.ArrayList<>();
        pool.add(MOVE_NOSEDIVE);
        if (d > 3.5) {
            pool.add(MOVE_SWARM);
        }
        if (tripleCooldown <= 0) {
            pool.add(MOVE_TRIPLE);
        }
        if (d < 11.0) {
            pool.add(MOVE_LAUNCH);
            pool.add(MOVE_LAUNCH);
        }
        if (rainCooldown <= 0) {
            pool.add(MOVE_RAIN);
            pool.add(MOVE_RAIN);
        }
        if (d > 4.0) {
            pool.add(MOVE_GRINDER);
        }
        pool.removeIf(m -> m == lastMove && pool.size() > 1);
        return pool.get(random.nextInt(pool.size()));
    }

    private void startSemiMove(int move, LivingEntity t) {
        moveTick = 0;
        moveYaw = yawTo(position(), t.position());
        faceYaw(moveYaw);
        hit.clear();
        lastMove = move;
        setMove(move);
        mFrom = position();
        caught = false;
        grabTick = -1;
        missTick = -1;
        noPhysics = true;
        setNoGravity(true);
        setDeltaMovement(Vec3.ZERO);
        switch (move) {
            case MOVE_NOSEDIVE -> setAnim("nosedive_rise");
            case MOVE_SWARM -> {
                setAnim("swarm");
                arcDir = random.nextBoolean() ? 1 : -1;
            }
            case MOVE_TRIPLE -> {
                setAnim("triple_aeroga");
                tripleCooldown = TRIPLE_COOLDOWN;
            }
            case MOVE_LAUNCH -> {
                setAnim("slash_launch");
                this.entityData.set(TALONS, true);
            }
            case MOVE_RAIN -> {
                setAnim("fly");
                rainCooldown = RAIN_COOLDOWN;
                Vec3 c = KnightScene.origin();
                Vec3 out = new Vec3(getX() - c.x, 0, getZ() - c.z);
                arcA = out.lengthSqr() < 1e-3 ? 0 : Math.atan2(out.z, out.x);
                arcDir = random.nextBoolean() ? 1 : -1;
            }
            case MOVE_GRINDER -> {
                setAnim("grinder_screech");
                this.entityData.set(TALONS, true);
            }
            default -> {
            }
        }
    }

    private void endSemiMove() {
        LivingEntity grabbed = getFirstPassenger() instanceof LivingEntity le ? le : null;
        if (grabbed != null) {
            release(grabbed);
            if (grabbed instanceof ServerPlayer sp) {
                Network.sendPuppet(sp, "", 0, 0.0F, false, false);
            }
        }
        for (AerogaOrbEntity o : triple) {
            if (o != null && !o.launched()) {
                o.discard();
            }
        }
        java.util.Arrays.fill(triple, null);
        this.entityData.set(TALONS, false);
        clearAnim();
        setMove(MOVE_NONE);
        noPhysics = false;
        setNoGravity(true);
        cooldown = 12 + random.nextInt(14);
    }

    private void glide(Vec3 to) {
        setPos(to.x, to.y, to.z);
    }

    private void tickSemiMove(@Nullable LivingEntity t) {
        int m = moveTick++;
        boolean alive = t != null && t.isAlive();
        ServerLevel sl = (ServerLevel) level();
        setDeltaMovement(Vec3.ZERO);
        fallDistance = 0;
        switch (getMove()) {
            case MOVE_NOSEDIVE -> {
                if (m <= KnightMoveData.NOSEDIVE_RISE_END) {
                    glide(mFrom.lerp(new Vec3(mFrom.x, floorY() + 8.5, mFrom.z), KnightScene.ease(m / (double) KnightMoveData.NOSEDIVE_RISE_END)));
                    if (m == 2) {
                        playSound(SoundEvents.ENDER_DRAGON_FLAP, 1.6F, 1.1F);
                    }
                    if (m == KnightMoveData.NOSEDIVE_RISE_END) {
                        setAnim("dive");
                        mAim = position();
                    }
                } else if (tickDive(t, alive, sl, m - KnightMoveData.NOSEDIVE_RISE_END)) {
                    endSemiMove();
                }
            }
            case MOVE_SWARM -> tickSwarm(t, alive, sl, m);
            case MOVE_TRIPLE -> tickTriple(t, alive, sl, m);
            case MOVE_LAUNCH -> tickLaunch(t, alive, sl, m);
            case MOVE_RAIN -> tickRain(t, alive, sl, m);
            case MOVE_GRINDER -> tickGrinder(t, alive, sl, m);
            default -> endSemiMove();
        }
    }

    /** the dive: aim (marking the ground), fold and fall, the blow; true when finished. d: ticks into the "dive" clip */
    private boolean tickDive(@Nullable LivingEntity t, boolean alive, ServerLevel sl, int d) {
        int fold = KnightMoveData.DIVE_FOLD, hitT = KnightMoveData.DIVE_HIT, rise = KnightMoveData.DIVE_RISE, end = KnightMoveData.DIVE_END;
        if (d < fold) {
            glide(mAim.add(0, 0.1 * Math.sin(d * 0.5), 0));
            if (alive && d <= fold - 2) {
                mLock = new Vec3(t.getX(), floorY(), t.getZ());
                moveYaw = yawTo(position(), mLock);
                faceYaw(moveYaw);
            }
            mark(sl, mLock, 2.2, d);
            if (d == 0) {
                playSound(SoundEvents.PHANTOM_AMBIENT, 1.6F, 0.8F);
            }
        } else if (d < hitT) {
            if (d == fold) {
                playSound(SoundEvents.PHANTOM_SWOOP, 2.0F, 1.0F);
            }
            double k = (d - fold + 1) / (double) (hitT - fold);
            glide(mAim.lerp(mLock, k * k));
            sl.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 0.8, getZ(), 3, 0.2, 0.4, 0.2, 0.02);
        } else if (d == hitT) {
            glide(mLock);
            impact(sl, mLock, 3.6, DIVE_DAMAGE);
        } else if (d >= rise) {
            double k = KnightScene.ease((d - rise) / (double) (end - rise));
            glide(mLock.lerp(new Vec3(mLock.x, floorY() + HOVER_UP, mLock.z), k));
        }
        return d >= end;
    }

    /** a green ring on the ground where she will strike */
    private void mark(ServerLevel sl, Vec3 at, double r, int tick) {
        if (tick % 2 != 0) {
            return;
        }
        int n = 18;
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n + tick * 0.1;
            sl.sendParticles(GUST, at.x + Math.cos(a) * r, at.y + 0.08, at.z + Math.sin(a) * r, 1, 0, 0, 0, 0);
        }
        sl.sendParticles(GUST, at.x, at.y + 0.1, at.z, 4, 0.4, 0.02, 0.4, 0);
    }

    private void impact(ServerLevel sl, Vec3 at, double r, float dmg) {
        playSound(SoundEvents.GENERIC_EXPLODE, 1.4F, 1.1F);
        playSound(SoundEvents.NETHERITE_BLOCK_FALL, 1.6F, 0.6F);
        playSound(SoundEvents.TRIDENT_RIPTIDE_3, 1.4F, 0.8F);
        sl.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y + 0.3, at.z, 3, 0.8, 0.1, 0.8, 0);
        sl.sendParticles(ParticleTypes.CLOUD, at.x, at.y + 0.2, at.z, 40, 1.6, 0.1, 1.6, 0.18);
        sl.sendParticles(GUST, at.x, at.y + 0.3, at.z, 60, 2.2, 0.2, 2.2, 0);
        sl.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK,
                net.minecraft.world.level.block.Blocks.STONE_BRICKS.defaultBlockState()), at.x, at.y + 0.1, at.z, 50, 1.2, 0.1, 1.2, 0.3);
        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(r, 2.5, r), e -> e != this && e.isAlive())) {
            double dx = e.getX() - at.x, dz = e.getZ() - at.z;
            double dd = Math.sqrt(dx * dx + dz * dz);
            if (dd > r + e.getBbWidth() * 0.5) {
                continue;
            }
            if (e.hurt(damageSources().mobAttack(this), dmg * (float) (1.0 - 0.4 * Math.min(1.0, dd / r)))) {
                Vec3 away = dd < 1e-3 ? Vec3.ZERO : new Vec3(dx / dd, 0, dz / dd);
                e.setDeltaMovement(e.getDeltaMovement().add(away.x * 1.1, 0.7, away.z * 1.1));
                e.hurtMarked = true;
            }
        }
    }

    private void tickSwarm(@Nullable LivingEntity t, boolean alive, ServerLevel sl, int m) {
        int back = KnightMoveData.SWARM_BACK_END, arc = KnightMoveData.SWARM_ARC_END;
        if (m == 0 && alive) {
            mLock = t.position();
            Vec3 away = new Vec3(getX() - mLock.x, 0, getZ() - mLock.z);
            away = away.lengthSqr() < 1e-3 ? forward(getYRot()).scale(-1) : away.normalize();
            arcA = Math.atan2(away.z, away.x);
            playSound(SoundEvents.ENDER_DRAGON_FLAP, 1.6F, 1.0F);
        }
        double R = 8.5, y = floorY() + 2.6;
        if (m <= back) {
            Vec3 p0 = new Vec3(clampX(mLock.x + Math.cos(arcA) * R), y, clampZ(mLock.z + Math.sin(arcA) * R));
            glide(mFrom.lerp(p0, KnightScene.ease(m / (double) back)));
        } else if (m <= arc) {
            double k = (m - back) / (double) (arc - back);
            double a = arcA + arcDir * Math.PI * KnightScene.ease(k);
            glide(new Vec3(clampX(mLock.x + Math.cos(a) * R), y + 0.4 * Math.sin(Math.PI * k), clampZ(mLock.z + Math.sin(a) * R)));
        }
        if (alive) {
            faceYaw(Mth.approachDegrees(getYRot(), yawTo(position(), t.position()), 20.0F));
            moveYaw = getYRot();
        }
        if ((m == KnightMoveData.SWARM_SHOT1 || m == KnightMoveData.SWARM_SHOT2) && alive) {
            Vec3 hand = local(KnightMoveData.SWARM_HAND);
            Vec3 aim = t.position().add(0, t.getBbHeight() * 0.55, 0).add(t.getDeltaMovement().multiply(5, 0, 5));
            for (int i = -1; i <= 1; i += 2) {
                Vec3 dir = aim.subtract(hand).normalize();
                Vec3 side = new Vec3(-dir.z, 0, dir.x).scale(0.06 * i);
                level().addFreshEntity(new WindBoltEntity(level(), this, hand, dir.add(side).normalize().scale(1.15)));
            }
            playSound(SoundEvents.ENDER_DRAGON_FLAP, 1.2F, 1.7F);
            playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 1.0F, 1.5F);
        }
        if (m == KnightMoveData.SWARM_SLASH) {
            Vec3 at = position().add(forward(getYRot()).scale(1.4)).add(0, 1.15, 0);
            Vec3 dir = alive ? t.position().add(0, 0.9, 0).subtract(at).normalize() : forward(getYRot());
            level().addFreshEntity(new WindBladeEntity(level(), this, at, dir));
            playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 1.6F, 0.6F);
            playSound(SoundEvents.TRIDENT_THROW, 1.2F, 0.7F);
        }
        if (m >= KnightMoveData.SWARM_END) {
            endSemiMove();
        }
    }

    private void tickTriple(@Nullable LivingEntity t, boolean alive, ServerLevel sl, int m) {
        glide(mFrom.add(0, 0.15 * Math.sin(m * 0.2), 0));
        if (alive) {
            faceYaw(Mth.approachDegrees(getYRot(), yawTo(position(), t.position()), 10.0F));
        }
        if (m == KnightMoveData.TRIPLE_AEROGA_GATHER) {
            for (int i = 0; i < 3; i++) {
                triple[i] = new AerogaOrbEntity(level(), this, i);
                level().addFreshEntity(triple[i]);
            }
            playSound(SoundEvents.ILLUSIONER_PREPARE_MIRROR, 1.4F, 0.7F);
        }
        int[] at = {KnightMoveData.TRIPLE_AEROGA_THROW3, KnightMoveData.TRIPLE_AEROGA_THROW1, KnightMoveData.TRIPLE_AEROGA_THROW2};
        for (int i = 0; i < 3; i++) {
            if (m == at[i] && triple[i] != null) {
                Vec3 aim = alive ? t.position().add(t.getDeltaMovement().multiply(7, 0, 7)) : position().add(forward(getYRot()).scale(10));
                if (i != 0 && alive) {
                    // the side orbs land a little to either side of where you stand
                    Vec3 dir = t.position().subtract(position());
                    Vec3 side = new Vec3(-dir.z, 0, dir.x).normalize().scale(i == 1 ? -2.2 : 2.2);
                    aim = aim.add(side);
                }
                triple[i].launch(aim);
                triple[i] = null;
                playSound(SoundEvents.TRIDENT_RIPTIDE_2, 1.2F, 1.2F);
            }
        }
        if (m >= KnightMoveData.TRIPLE_AEROGA_END) {
            endSemiMove();
        }
    }

    private void tickLaunch(@Nullable LivingEntity t, boolean alive, ServerLevel sl, int m) {
        int dash = KnightMoveData.SLASH_LAUNCH_DASH, rake = KnightMoveData.SLASH_LAUNCH_RAKE, smash = KnightMoveData.SLASH_LAUNCH_SMASH;
        if (m == 0) {
            playSound(SoundEvents.ILLUSIONER_CAST_SPELL, 1.2F, 0.7F);
        }
        if (m < dash) {
            glide(mFrom);
            if (alive) {
                faceYaw(yawTo(position(), t.position()));
            }
        } else if (m < rake) {
            if (m == dash) {
                playSound(SoundEvents.PHANTOM_SWOOP, 1.6F, 1.3F);
                if (alive) {
                    Vec3 dir = new Vec3(t.getX() - getX(), 0, t.getZ() - getZ());
                    dir = dir.lengthSqr() < 1e-3 ? forward(getYRot()) : dir.normalize();
                    mLock = new Vec3(t.getX() - dir.x * 1.3, Math.max(floorY(), t.getY() - 0.2), t.getZ() - dir.z * 1.3);
                    moveYaw = yawTo(position(), t.position());
                } else {
                    mLock = position();
                }
                mAim = position();
            }
            double k = (m - dash + 1) / (double) (rake - dash);
            glide(mAim.lerp(mLock, k * (2 - k)));
            faceYaw(moveYaw);
        } else if (m == rake) {
            playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 1.6F, 0.8F);
            if (alive && horizontalDistanceTo(t) < 3.0 && Math.abs(t.getY() - getY()) < 2.5 && t.hurt(damageSources().mobAttack(this), RAKE_DAMAGE)) {
                t.setDeltaMovement(t.getDeltaMovement().x * 0.2, 1.45, t.getDeltaMovement().z * 0.2);
                t.hurtMarked = true;
                caught = true;
                sl.sendParticles(ParticleTypes.SWEEP_ATTACK, t.getX(), t.getY() + 1.0, t.getZ(), 2, 0.2, 0.3, 0.2, 0);
                sl.sendParticles(GUST, t.getX(), t.getY() + 0.6, t.getZ(), 30, 0.5, 0.8, 0.5, 0);
            }
            mAim = position();
        } else if (m < smash) {
            // up after them
            Vec3 goal = caught && alive ? t.position().add(forward(moveYaw).scale(-1.2)).add(0, 0.4, 0) : mAim.add(0, 6, 0);
            Vec3 at = position();
            glide(at.lerp(goal, 0.35));
            if (m == rake + 2) {
                playSound(SoundEvents.ENDER_DRAGON_FLAP, 1.6F, 1.2F);
            }
        } else if (m == smash) {
            playSound(SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.6F, 0.6F);
            playSound(SoundEvents.TRIDENT_RIPTIDE_1, 1.2F, 0.7F);
            if (caught && alive && distanceTo(t) < 4.0) {
                t.invulnerableTime = 0;
                if (t.hurt(damageSources().mobAttack(this), SMASH_DAMAGE)) {
                    t.setDeltaMovement(0, -2.2, 0);
                    t.hurtMarked = true;
                    t.fallDistance = 0;
                    sl.sendParticles(ParticleTypes.SWEEP_ATTACK, t.getX(), t.getY() + 1.0, t.getZ(), 3, 0.3, 0.3, 0.3, 0);
                    sl.sendParticles(GUST, t.getX(), t.getY() + 0.6, t.getZ(), 30, 0.5, 0.8, 0.5, 0);
                }
            }
            mAim = position();
        } else {
            double k = KnightScene.ease((m - smash) / 20.0);
            Vec3 down = new Vec3(clampX(mAim.x), floorY() + HOVER_UP, clampZ(mAim.z));
            glide(mAim.lerp(down, k));
        }
        if (m == KnightMoveData.SLASH_LAUNCH_DISMISS + 5) {
            this.entityData.set(TALONS, false);
        }
        if (m >= KnightMoveData.SLASH_LAUNCH_END) {
            endSemiMove();
        }
    }

    private void tickRain(@Nullable LivingEntity t, boolean alive, ServerLevel sl, int m) {
        Vec3 c = KnightScene.origin();
        double R = 26.0, y = floorY() + 16.0;
        int out = 25, circleEnd = 105;
        if (m <= out) {
            Vec3 p0 = new Vec3(c.x + Math.cos(arcA) * R, y, c.z + Math.sin(arcA) * R);
            glide(mFrom.lerp(p0, KnightScene.ease(m / (double) out)));
            if (m % 8 == 0) {
                playSound(SoundEvents.ENDER_DRAGON_FLAP, 2.0F, 1.0F);
            }
        } else if (m <= circleEnd) {
            double a = arcA + arcDir * Math.toRadians(250) * (m - out) / (double) (circleEnd - out);
            glide(new Vec3(c.x + Math.cos(a) * R, y + 0.6 * Math.sin(m * 0.12), c.z + Math.sin(a) * R));
            // flying round the keep: face along the way, a little in toward the roof
            Vec3 tangent = new Vec3(-Math.sin(a) * arcDir, 0, Math.cos(a) * arcDir);
            Vec3 in = c.subtract(position());
            faceYaw(yawTo(Vec3.ZERO, tangent.add(new Vec3(in.x, 0, in.z).normalize().scale(0.6))));
            int v = (m - out - 5) % 16;
            if (m - out >= 5 && m < circleEnd - 8) {
                if (v == 0) {
                    setAnim("rain_volley");
                }
                if (v == KnightMoveData.RAIN_VOLLEY_FIRE && alive) {
                    volley(t);
                }
                if (v == 15) {
                    setAnim("fly");
                }
            }
        } else {
            int d = m - circleEnd - 1;
            if (d == 0) {
                setAnim("dive");
                mAim = position();
            }
            if (d >= 0 && tickDive(t, alive, sl, d)) {
                endSemiMove();
            }
        }
    }

    /** six bolts flung down at the ground round the player */
    private void volley(LivingEntity t) {
        Vec3 from = position().add(0, 1.0, 0);
        for (int i = 0; i < 6; i++) {
            double a = random.nextDouble() * Math.PI * 2, r = i == 0 ? 0.0 : 1.0 + random.nextDouble() * 2.2;
            Vec3 aim = new Vec3(t.getX() + Math.cos(a) * r, t.getY() + 0.4, t.getZ() + Math.sin(a) * r);
            Vec3 dir = aim.subtract(from).normalize();
            level().addFreshEntity(new WindBoltEntity(level(), this, from.add(dir.scale(0.6)), dir.scale(1.25)));
        }
        playSound(SoundEvents.ENDER_DRAGON_FLAP, 1.6F, 1.6F);
        playSound(SoundEvents.ILLUSIONER_CAST_SPELL, 1.2F, 1.5F);
    }

    // ---- Grinder
    private static final int[][] TURRETS = {{CastleLayout.KX0, CastleLayout.KZ0}, {CastleLayout.KX1, CastleLayout.KZ0},
            {CastleLayout.KX0, CastleLayout.KZ1}, {CastleLayout.KX1, CastleLayout.KZ1}};

    private void tickGrinder(@Nullable LivingEntity t, boolean alive, ServerLevel sl, int m) {
        int charge = KnightMoveData.GRINDER_SCREECH_END;
        if (m < charge && grabTick < 0) {
            glide(mFrom.add(0, 2.5 * KnightScene.ease(m / 12.0), 0));
            if (alive) {
                faceYaw(Mth.approachDegrees(getYRot(), yawTo(position(), t.position()), 14.0F));
            }
            if (m == KnightMoveData.GRINDER_SCREECH_SCREECH) {
                playSound(SoundEvents.PHANTOM_AMBIENT, 3.0F, 0.5F);
                playSound(SoundEvents.ENDER_DRAGON_GROWL, 1.2F, 1.9F);
                sl.sendParticles(GUST, getX(), getY() + 1.6, getZ(), 40, 1.2, 0.6, 1.2, 0);
            }
            if (m == charge - 1) {
                Vec3 aim = alive ? t.position().add(0, 0.4, 0) : position().add(forward(getYRot()).scale(10));
                Vec3 d = aim.subtract(position());
                mDir = d.lengthSqr() < 1e-3 ? forward(getYRot()) : d.normalize();
                moveYaw = yawTo(position(), aim);
                faceYaw(moveYaw);
            }
            grabTick = -1;
            return;
        }
        if (grabTick < 0) {
            int c = m - charge;
            if (c == 0) {
                setAnim("grinder_charge");
                playSound(SoundEvents.PHANTOM_SWOOP, 2.0F, 0.8F);
            }
            Vec3 next = position().add(mDir.scale(1.35));
            BlockHitResult hb = level().clip(new ClipContext(position().add(0, 1.0, 0), next.add(0, 1.0, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
            boolean wall = hb.getType() != HitResult.Type.MISS || next.y < floorY();
            if (!wall) {
                glide(next);
            }
            sl.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 1.0, getZ(), 2, 0.3, 0.3, 0.3, 0.02);
            if (alive && t.position().add(0, t.getBbHeight() * 0.5, 0).distanceTo(position().add(forward(getYRot()).scale(0.6)).add(0, 0.9, 0)) < 1.7
                    && !(t instanceof Player pl && (pl.isCreative() || pl.isSpectator()))) {
                grab(t, sl);
                return;
            }
            if (wall || c > 22) {
                // missed: pull up and away
                if (missTick < 0) {
                    missTick = m;
                    clearAnim();
                    this.entityData.set(TALONS, false);
                }
            }
            if (missTick >= 0) {
                glide(position().lerp(new Vec3(clampX(getX()), floorY() + HOVER_UP + 1.0, clampZ(getZ())), 0.2));
                if (m - missTick > 16) {
                    missTick = -1;
                    endSemiMove();
                }
            }
            return;
        }
        // caught: carry to the tower, slam, grind up its face, hurl them back to the middle
        int g = m - grabTick;
        LivingEntity v = getFirstPassenger() instanceof LivingEntity le ? le : null;
        if (g < 16) {
            glide(mAim.lerp(mLock, KnightScene.ease((g + 1) / 16.0)));
        } else if (g == 16) {
            glide(mLock);
            setAnim("grinder_grind");
            if (v != null) {
                v.invulnerableTime = 0;
                v.hurt(damageSources().mobAttack(this), SLAM_DAMAGE);
            }
            playSound(SoundEvents.ANVIL_LAND, 1.6F, 0.5F);
            playSound(SoundEvents.STONE_BREAK, 2.0F, 0.6F);
            grindDust(sl, 30);
        } else if (g < 46) {
            glide(mLock.add(0, 8.0 * KnightScene.ease((g - 16) / 30.0), 0));
            if (g % 5 == 0 && v != null) {
                v.invulnerableTime = 0;
                v.hurt(damageSources().mobAttack(this), GRIND_DAMAGE);
            }
            if (g % 4 == 0) {
                playSound(SoundEvents.GRINDSTONE_USE, 1.4F, 0.6F);
            }
            grindDust(sl, 6);
        } else if (g == 46) {
            setAnim("grinder_hurl");
        } else if (g == 46 + KnightMoveData.GRINDER_HURL_THROW) {
            if (v != null) {
                release(v);
                Vec3 c = KnightScene.origin();
                Vec3 d = new Vec3(c.x - v.getX(), 0, c.z - v.getZ());
                d = d.lengthSqr() < 1e-3 ? Vec3.ZERO : d.normalize();
                v.setDeltaMovement(d.x * 1.7, 0.55, d.z * 1.7);
                v.hurtMarked = true;
                v.fallDistance = 0;
                if (v instanceof ServerPlayer sp) {
                    Network.sendPuppet(sp, "", 0, 0.0F, false, false);
                }
            }
            playSound(SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.6F, 0.5F);
            playSound(SoundEvents.ENDER_DRAGON_FLAP, 1.6F, 0.9F);
            mAim = position();
        } else if (g > 46 + KnightMoveData.GRINDER_HURL_THROW) {
            int h = g - 46 - KnightMoveData.GRINDER_HURL_THROW;
            Vec3 home = new Vec3(clampX(getX() + (KnightScene.OX - getX()) * 0.3), floorY() + HOVER_UP, clampZ(getZ() + (KnightScene.OZ - getZ()) * 0.3));
            glide(mAim.lerp(home, KnightScene.ease(h / 22.0)));
            if (h > 24) {
                grabTick = -1;
                endSemiMove();
            }
        }
    }

    private void grab(LivingEntity t, ServerLevel sl) {
        grabTick = moveTick - 1;
        setAnim("grinder_carry");
        caught = true;
        t.invulnerableTime = 0;
        t.hurt(damageSources().mobAttack(this), 4.0F);
        t.startRiding(this, true);
        // the nearest corner tower; pin them against its inside corner
        int[] best = TURRETS[0];
        double bd = Double.MAX_VALUE;
        for (int[] c : TURRETS) {
            double d = (c[0] + 0.5 - t.getX()) * (c[0] + 0.5 - t.getX()) + (c[1] + 0.5 - t.getZ()) * (c[1] + 0.5 - t.getZ());
            if (d < bd) {
                bd = d;
                best = c;
            }
        }
        double sx = Math.signum(KnightScene.OX - best[0]), sz = Math.signum(KnightScene.OZ - best[1]);
        // the turret's inner corner (its shell runs 3 blocks either side of the keep corner)
        Vec3 corner = new Vec3(best[0] + 0.5 + sx * 3.5, floorY(), best[1] + 0.5 + sz * 3.5);
        Vec3 in = new Vec3(sx, 0, sz).normalize();
        Vec3 victim = corner.add(in.scale(0.45));
        float[] gr = KnightMoveData.GRINDER_CARRY_GRIP;
        // she faces the corner: forward is -in; her feet sit behind the grip
        Vec3 her = victim.add(in.scale(gr[0])).add(0, 0.9 - gr[2] + 0.2, 0);
        mAim = position();
        mLock = her;
        moveYaw = yawTo(her, corner);
        faceYaw(moveYaw);
        playSound(SoundEvents.PHANTOM_BITE, 2.0F, 0.6F);
        sl.sendParticles(GUST, t.getX(), t.getY() + 1.0, t.getZ(), 30, 0.6, 0.6, 0.6, 0);
        if (t instanceof ServerPlayer sp) {
            Network.sendPuppet(sp, "grabbed_p", 90, moveYaw + 180.0F, false, SemiPrime.engaged(sp));
        }
    }

    private void release(net.minecraft.world.entity.Entity v) {
        letGo = true;
        v.stopRiding();
        letGo = false;
    }

    public boolean grinding() {
        return getMove() == MOVE_GRINDER && grabTick >= 0;
    }

    private void grindDust(ServerLevel sl, int n) {
        LivingEntity v = getFirstPassenger() instanceof LivingEntity le ? le : null;
        Vec3 at = v != null ? v.position().add(forward(getYRot()).scale(0.3)).add(0, 1.0, 0) : position();
        sl.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK,
                net.minecraft.world.level.block.Blocks.STONE_BRICKS.defaultBlockState()), at.x, at.y, at.z, n, 0.3, 0.6, 0.3, 0.2);
        sl.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, n / 2 + 1, 0.3, 0.6, 0.3, 0.3);
    }

    @Override
    protected void positionRider(net.minecraft.world.entity.Entity passenger, MoveFunction move) {
        if (!hasPassenger(passenger)) {
            return;
        }
        float[] g = KnightMoveData.GRINDER_CARRY_GRIP;
        float yaw = getYRot();
        Vec3 grip = position().add(forward(yaw).scale(g[0])).add(right(yaw).scale(g[1])).add(0, g[2], 0);
        move.accept(passenger, grip.x, grip.y - 0.9, grip.z);
    }

    @Override
    public boolean shouldRiderSit() {
        return false;
    }

    @Override
    public void die(DamageSource source) {
        setNoGravity(false);
        noPhysics = false;
        clearAnim();
        this.entityData.set(TALONS, false);
        letGo = true;
        ejectPassengers();
        letGo = false;
        super.die(source);
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
        if (getMove() == MOVE_SCENE && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return false;
        }
        if (!level().isClientSide && isSemi() && sceneDone && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            // she isn't killed in Semi Prime either: struck down at the last, she calls the storm
            if (getHealth() - amount <= 1.0F) {
                amount = Math.max(0.0F, getHealth() - 1.0F);
                pendingDefeat = true;
            }
        }
        if (!level().isClientSide && !isSemi() && !sceneDone && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            // she can't be finished in her first form: at a fifth of her health she turns Semi Prime
            float floor = getMaxHealth() * SEMI_AT;
            if (getHealth() - amount <= floor) {
                amount = Math.max(0.0F, getHealth() - floor - 0.01F);
                pendingScene = true;
            }
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
            if (sceneFoe != null) {
                SHIELDED.remove(sceneFoe);
            }
            if (rubble != null) {
                rubble.discard();
            }
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
            int a = this.entityData.get(ANIM);
            if (a >= 0 && a < SEMI_RAW.length) {
                int st = this.entityData.get(ANIM_START);
                if (st != seenAnimStart) {
                    seenAnimStart = st;
                    state.getController().forceAnimationReset();
                }
                return state.setAndContinue(SEMI_RAW[a]);
            }
            if (isSemi()) {
                double v = Math.sqrt((getX() - xo) * (getX() - xo) + (getZ() - zo) * (getZ() - zo)) * 20.0;
                return state.setAndContinue(v > 4.0 ? FLY : HOVER);
            }
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
        tag.putBoolean("SceneDone", sceneDone);
        if (foe != null) {
            tag.putUUID("Foe", foe);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.entityData.set(SEMI, tag.getBoolean("Semi"));
        sceneDone = tag.getBoolean("SceneDone") || isSemi();
        if (isSemi()) {
            setNoGravity(true);
        }
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
