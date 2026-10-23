package com.rudues.mod.entity;

import com.rudues.mod.ability.HeroForm;
import com.rudues.mod.ability.PowerData;
import com.rudues.mod.network.ClashPacket;
import com.rudues.mod.network.Network;
import com.rudues.mod.registry.ModParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
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
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
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
 * The Infernal Hero: a darker Titan, the size of the player's own Hero of Hell, burning a deeper purple. Whoever it
 * hunts is pulled into the Hero of Hell form, so the fight is Titan against Titan.
 * <p>
 * Moves: Hurl (one big fireball), Leap Slash (closes any far distance), Rend (three slashes), Hell Beam (long charge,
 * then a mouth beam that turns after you - run sideways to outpace it; 30 s cooldown) and Tail Sweep (two hooks and a
 * spinning tail). At 15% health it stops fighting and the end fight begins: two punch clashes and a beam clash.
 */
public class InfernalHeroEntity extends Monster implements GeoEntity {
    private static final EntityDataAccessor<Integer> MOVE = SynchedEntityData.defineId(InfernalHeroEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> MOVE_START = SynchedEntityData.defineId(InfernalHeroEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> BEAM_PITCH = SynchedEntityData.defineId(InfernalHeroEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> BEAM_LEN = SynchedEntityData.defineId(InfernalHeroEntity.class, EntityDataSerializers.FLOAT);

    public static final int MOVE_NONE = 0, MOVE_HURL = 1, MOVE_LEAP = 2, MOVE_REND = 3, MOVE_BEAM = 4, MOVE_TAIL = 5, MOVE_CLASH = 6;

    private static final RawAnimation GUARD = RawAnimation.begin().thenLoop("animation.infernal_hero.guard");
    private static final RawAnimation STALK = RawAnimation.begin().thenLoop("animation.infernal_hero.stalk");
    private static final String[] ONCE = {"hurl", "leap_slash", "rend", "hell_beam", "tail_sweep", "clash_win", "clash_lose",
            "clash_lose_far", "beam_win", "pound_thrown", "pound_kick"};
    private static final String[] HOLD = {"clash_charge", "beam_clash", "hero_fall", "pound_fall"};
    private static final String[] LOOPS = {"clash_strain", "beam_strain", "pound_block"};

    public static final float SCALE = 1.295F;
    public static final float HURL_TURN = 12.0F, BEAM_TURN = 0.8F, BEAM_PITCH_TURN = 1.4F;
    public static final double BEAM_RANGE = 48.0, BEAM_RADIUS = 1.4;
    public static final float LEAP_DAMAGE = 18.0F, LEAP_SLAM = 8.0F, REND_DAMAGE = 13.0F, REND_FINISHER = 20.0F,
            PUNCH_DAMAGE = 12.0F, TAIL_DAMAGE = 16.0F, BEAM_DAMAGE = 6.0F;
    private static final double FAR = 13.0, MELEE = 6.0;
    private static final int BEAM_COOLDOWN = 20 * 30;
    /** the end fight begins at this much health */
    public static final float FINALE_AT = 0.15F;
    public static final double MAX_HEALTH = 4000.0;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final ServerBossEvent bossBar = new ServerBossEvent(Component.translatable("entity.rudues.infernal_hero"),
            BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_10);

    private int moveTick = -1;
    private float moveYaw;
    private int cooldown = 40;
    private int beamCooldown = 20 * 12;
    private int plannedMove = MOVE_NONE;
    private int planTicks;
    private int lastMove = MOVE_NONE;
    private final Set<UUID> hit = new HashSet<>();
    private Vec3 leapFrom = Vec3.ZERO, leapTo = Vec3.ZERO;
    private double leapHeight;
    private float beamYaw, beamPitch;

    // ---- the end fight
    private boolean finale;
    private boolean finaleKill;
    @Nullable
    private UUID clashPlayer;
    private int stage, phase = ClashScript.PH_WAIT, result;
    private long phaseStart;
    private Vec3 origin = Vec3.ZERO;
    private float axisYaw;
    private float dHero, dPlayer;
    private float balance = 0.5F;
    /** ticks the gauge has been held above the win line */
    private int hold;
    private int pressesThisTick;

    public InfernalHeroEntity(EntityType<? extends InfernalHeroEntity> type, Level level) {
        super(type, level);
        this.xpReward = 400;
        this.setMaxUpStep(1.5F);
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, MAX_HEALTH)
                .add(Attributes.ARMOR, 10.0D)
                .add(Attributes.ATTACK_DAMAGE, 14.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.30D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
                .add(Attributes.FOLLOW_RANGE, 64.0D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(MOVE, MOVE_NONE);
        this.entityData.define(MOVE_START, 0);
        this.entityData.define(BEAM_PITCH, 0.0F);
        this.entityData.define(BEAM_LEN, 0.0F);
    }

    public int getMove() {
        return this.entityData.get(MOVE);
    }

    public boolean isInMove() {
        return getMove() != MOVE_NONE;
    }

    public float moveTime(float partialTick) {
        return (float) (level().getGameTime() - this.entityData.get(MOVE_START)) + partialTick;
    }

    private void setMove(int move) {
        this.entityData.set(MOVE, move);
        this.entityData.set(MOVE_START, (int) level().getGameTime());
    }

    public float beamPitch() {
        return this.entityData.get(BEAM_PITCH);
    }

    public float beamLength() {
        return this.entityData.get(BEAM_LEN);
    }

    /** the whole-body turn of the tail sweep's spin (degrees, to its left), drawn by the renderer */
    public float spinYaw(float partialTick) {
        if (getMove() != MOVE_TAIL) {
            return 0.0F;
        }
        float t = moveTime(partialTick);
        float[] a = HeroMoveData.TAIL_SWEEP_SPIN_YAW;
        int i = Mth.clamp((int) t, 0, a.length - 1);
        int j = Math.min(i + 1, a.length - 1);
        return Mth.lerp(Mth.clamp(t - i, 0, 1), a[i], a[j]);
    }

    // ------------------------------------------------------------ AI
    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new MoveGoal());
        this.goalSelector.addGoal(2, new ApproachGoal());
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 24.0F));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            clientFx();
            return;
        }
        LivingEntity target = getTarget();
        if (target instanceof ServerPlayer sp && sp.isAlive()) {
            if (tickCount % 20 == 0) {
                HeroForm.forceForm(sp);
            }
            HeroForm.sustain(sp);
        }
        if (finale) {
            tickFinale();
            return;
        }
        if (beamCooldown > 0 && getMove() != MOVE_BEAM) {
            beamCooldown--;
        }
        if (!isInMove()) {
            if (cooldown > 0) {
                cooldown--;
            } else {
                plan(target);
            }
        }
        if (target instanceof ServerPlayer sp && getHealth() <= getMaxHealth() * FINALE_AT && HeroForm.isHero(sp) && canStartFinale()) {
            startFinale(sp);
        }
    }

    private boolean canStartFinale() {
        return getMove() != MOVE_LEAP || moveTick < HeroMoveData.LEAP_SLASH_TAKEOFF || moveTick > HeroMoveData.LEAP_SLASH_LAND;
    }

    /** picks the next move: leap when far, the beam whenever it's ready at range, melee up close, fireballs between */
    private void plan(@Nullable LivingEntity t) {
        if (t == null || !t.isAlive()) {
            plannedMove = MOVE_NONE;
            return;
        }
        if (plannedMove != MOVE_NONE && ++planTicks < 80) {
            return;
        }
        planTicks = 0;
        double d = horizontalDistanceTo(t);
        float r = random.nextFloat();
        if (beamCooldown <= 0 && d > 5.0 && r < 0.35F) {
            plannedMove = MOVE_BEAM;
        } else if (d > FAR) {
            // far away: it closes the gap - or makes you come to it through a fireball
            plannedMove = r < 0.75F ? MOVE_LEAP : MOVE_HURL;
            if (lastMove == MOVE_LEAP && random.nextBoolean()) {
                plannedMove = MOVE_HURL;
            }
        } else if (d < MELEE) {
            // up close it mostly tears at you, but now and then hurls one point-blank
            plannedMove = r < 0.4F ? MOVE_REND : r < 0.8F ? MOVE_TAIL : MOVE_HURL;
        } else {
            plannedMove = r < 0.55F ? MOVE_HURL : (r < 0.8F ? MOVE_REND : MOVE_TAIL);
        }
        if (plannedMove == lastMove && plannedMove != MOVE_LEAP && plannedMove != MOVE_HURL && random.nextBoolean()) {
            plannedMove = MOVE_NONE;      // a little less repetition
        }
    }

    private double horizontalDistanceTo(Entity e) {
        double dx = e.getX() - getX(), dz = e.getZ() - getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    private boolean inRangeFor(int move, LivingEntity t) {
        double d = horizontalDistanceTo(t);
        return switch (move) {
            case MOVE_LEAP -> d > 8.0 && d < 34.0;
            case MOVE_HURL -> d > 2.5 && d < 40.0;
            case MOVE_BEAM -> d > 4.0 && d < BEAM_RANGE - 4;
            case MOVE_REND, MOVE_TAIL -> d < MELEE + 0.5;
            default -> false;
        };
    }

    /** the moves themselves */
    private class MoveGoal extends Goal {
        MoveGoal() {
            setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK, Goal.Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            LivingEntity t = getTarget();
            return !finale && t != null && t.isAlive() && plannedMove != MOVE_NONE && !isInMove() && onGround() && inRangeFor(plannedMove, t)
                    && hasLineOfSight(t);
        }

        @Override
        public boolean canContinueToUse() {
            return moveTick >= 0 && isAlive() && !finale;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void start() {
            startMove(plannedMove);
        }

        @Override
        public void tick() {
            if (moveTick >= 0) {
                tickMove(getTarget());
            }
        }

        @Override
        public void stop() {
            if (moveTick >= 0) {
                endMove();
            }
        }
    }

    /** walks in for melee and keeps a fighting distance otherwise */
    private class ApproachGoal extends Goal {
        private int repath;

        ApproachGoal() {
            setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity t = getTarget();
            return !finale && t != null && t.isAlive() && !isInMove();
        }

        @Override
        public void tick() {
            LivingEntity t = getTarget();
            if (t == null) {
                return;
            }
            getLookControl().setLookAt(t, 30.0F, 30.0F);
            double d = horizontalDistanceTo(t);
            boolean melee = plannedMove == MOVE_REND || plannedMove == MOVE_TAIL;
            double want = melee ? MELEE - 1.0 : 9.0;
            if (d > want) {
                if (--repath <= 0) {
                    repath = 8;
                    getNavigation().moveTo(t, 1.0);
                }
            } else {
                getNavigation().stop();
            }
        }

        @Override
        public void stop() {
            getNavigation().stop();
        }
    }

    // ------------------------------------------------------------ moves
    private static Vec3 forward(float yaw) {
        float r = yaw * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(r), 0, Mth.cos(r));
    }

    private static Vec3 right(float yaw) {
        float r = yaw * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.cos(r), 0, -Mth.sin(r));
    }

    private static float yawTo(Vec3 from, Vec3 to) {
        return (float) (Mth.atan2(to.z - from.z, to.x - from.x) * Mth.RAD_TO_DEG) - 90.0F;
    }

    private void faceYaw(float yaw) {
        setYRot(yaw);
        this.yRotO = yaw;
        this.yBodyRot = yaw;
        this.yBodyRotO = yaw;
        this.yHeadRot = yaw;
        this.yHeadRotO = yaw;
    }

    private void turnToward(@Nullable LivingEntity t, float maxStep) {
        if (t != null) {
            float want = yawTo(position(), t.position());
            moveYaw = Mth.approachDegrees(moveYaw, want, maxStep);
        }
        faceYaw(moveYaw);
    }

    /** a baked point of the current move in the world (facing moveYaw) */
    private Vec3 local(float[] f, float[] r, float[] u, int tick) {
        int i = Mth.clamp(tick, 0, f.length - 1);
        return position().add(forward(moveYaw).scale(f[i])).add(right(moveYaw).scale(r[i])).add(0, u[i], 0);
    }

    private String clipOf(int move) {
        return switch (move) {
            case MOVE_HURL -> "hurl";
            case MOVE_LEAP -> "leap_slash";
            case MOVE_REND -> "rend";
            case MOVE_BEAM -> "hell_beam";
            default -> "tail_sweep";
        };
    }

    private void startMove(int move) {
        LivingEntity t = getTarget();
        moveTick = 0;
        moveYaw = t != null ? yawTo(position(), t.position()) : getYRot();
        faceYaw(moveYaw);
        hit.clear();
        getNavigation().stop();
        setMove(move);
        triggerAnim("main", clipOf(move));
        if (move == MOVE_BEAM) {
            beamYaw = moveYaw;
            beamPitch = 0;
            playSound(SoundEvents.ENDER_DRAGON_GROWL, 2.0F, 0.45F);
        } else {
            playSound(SoundEvents.RAVAGER_ROAR, 1.4F, 0.6F);
        }
    }

    private int endTick(int move) {
        return switch (move) {
            case MOVE_HURL -> HeroMoveData.HURL_END;
            case MOVE_LEAP -> HeroMoveData.LEAP_SLASH_END;
            case MOVE_REND -> HeroMoveData.REND_END;
            case MOVE_BEAM -> HeroMoveData.HELL_BEAM_END;
            default -> HeroMoveData.TAIL_SWEEP_END;
        };
    }

    private void tickMove(@Nullable LivingEntity target) {
        int move = getMove();
        int t = moveTick;
        ServerLevel sl = (ServerLevel) level();
        if (move != MOVE_LEAP || t < HeroMoveData.LEAP_SLASH_TAKEOFF || t > HeroMoveData.LEAP_SLASH_LAND) {
            setDeltaMovement(0, getDeltaMovement().y, 0);
        }
        switch (move) {
            case MOVE_HURL -> tickHurl(sl, target, t);
            case MOVE_LEAP -> tickLeap(sl, target, t);
            case MOVE_REND -> tickRend(sl, target, t);
            case MOVE_BEAM -> tickBeam(sl, target, t);
            case MOVE_TAIL -> tickTail(sl, target, t);
            default -> {
            }
        }
        moveTick++;
        if (moveTick > endTick(move)) {
            endMove();
        }
    }

    private void endMove() {
        int move = getMove();
        moveTick = -1;
        setNoGravity(false);
        this.entityData.set(BEAM_LEN, 0.0F);
        if (move == MOVE_BEAM) {
            beamCooldown = BEAM_COOLDOWN;
        }
        lastMove = move;
        plannedMove = MOVE_NONE;
        cooldown = move == MOVE_BEAM || move == MOVE_LEAP ? 18 + random.nextInt(12) : 10 + random.nextInt(12);
        setMove(MOVE_NONE);
    }

    // ---- 1. Hurl
    private void tickHurl(ServerLevel sl, @Nullable LivingEntity target, int t) {
        if (t <= HeroMoveData.HURL_RELEASE) {
            turnToward(target, HURL_TURN);
        } else {
            faceYaw(moveYaw);
        }
        if (t >= HeroMoveData.HURL_GATHER && t < HeroMoveData.HURL_RELEASE) {
            Vec3 c = local(HeroMoveData.HURL_CLAW_R_F, HeroMoveData.HURL_CLAW_R_R, HeroMoveData.HURL_CLAW_R_U, t);
            float k = (t - HeroMoveData.HURL_GATHER) / (float) (HeroMoveData.HURL_RELEASE - HeroMoveData.HURL_GATHER);
            sl.sendParticles(ModParticles.DARK_FLAME_BIG.get(), c.x, c.y, c.z, 1 + (int) (3 * k), 0.15 + 0.4 * k, 0.15 + 0.4 * k, 0.15 + 0.4 * k, 0.01);
            sl.sendParticles(ModParticles.DARK_EMBER.get(), c.x, c.y, c.z, 3, 1.2, 1.2, 1.2, 0.02);
        }
        if (t == HeroMoveData.HURL_RELEASE) {
            Vec3 c = local(HeroMoveData.HURL_CLAW_R_F, HeroMoveData.HURL_CLAW_R_R, HeroMoveData.HURL_CLAW_R_U, t);
            Vec3 aim;
            if (target != null) {
                Vec3 at = target.position().add(0, target.getBbHeight() * 0.45, 0);
                double flight = at.distanceTo(c) / HeroFireballEntity.SPEED;
                at = at.add(target.getDeltaMovement().multiply(1, 0, 1).scale(Math.min(flight, 30) * 0.6));
                aim = at.subtract(c);
            } else {
                aim = forward(moveYaw);
            }
            level().addFreshEntity(new HeroFireballEntity(level(), this, c, aim));
            playSound(SoundEvents.BLAZE_SHOOT, 2.0F, 0.5F);
            playSound(SoundEvents.FIRECHARGE_USE, 1.5F, 0.6F);
        }
    }

    // ---- 2. Leap Slash
    private void tickLeap(ServerLevel sl, @Nullable LivingEntity target, int t) {
        int off = HeroMoveData.LEAP_SLASH_TAKEOFF, land = HeroMoveData.LEAP_SLASH_LAND;
        // the flight is timed a little ahead of the animation (clients draw mobs a few ticks late), so it is seen
        // leaving the ground as the legs fire and touching down exactly as the claw lands
        int go = off - 2, down = land - 3;
        if (t < go) {
            turnToward(target, 10.0F);
            if (t % 2 == 0) {
                sl.sendParticles(ModParticles.DARK_FLAME.get(), getX(), getY() + 0.3, getZ(), 6, 1.6, 0.2, 1.6, 0.04);
            }
            return;
        }
        if (t == go) {
            Vec3 aim = target != null ? target.position() : position().add(forward(moveYaw).scale(12));
            Vec3 dir = aim.subtract(position()).multiply(1, 0, 1);
            double d = Math.min(30.0, Math.max(0.0, dir.length() - (target != null ? target.getBbWidth() * 0.5 + 2.6 : 3.4)));   // lands just short of you
            leapFrom = position();
            leapTo = landingSpot(dir.lengthSqr() < 1e-4 ? position() : position().add(dir.normalize().scale(d)));
            leapHeight = 3.0 + d * 0.16;
            moveYaw = yawTo(position(), aim);
            setNoGravity(true);
        }
        if (t == off) {
            sl.sendParticles(ModParticles.DARK_FLAME_BIG.get(), leapFrom.x, leapFrom.y + 0.3, leapFrom.z, 24, 1.5, 0.3, 1.5, 0.08);
            sl.sendParticles(ParticleTypes.EXPLOSION, leapFrom.x, leapFrom.y + 0.2, leapFrom.z, 2, 1.0, 0.1, 1.0, 0);
            playSound(SoundEvents.GENERIC_EXPLODE, 1.2F, 0.6F);
        }
        faceYaw(moveYaw);
        if (t > go && t < down) {
            float u = (t - go) / (float) (down - go);
            // eases into the ground rather than dropping the last stretch in one tick
            Vec3 p = leapFrom.lerp(leapTo, u).add(0, leapHeight * 4 * u * (1 - u), 0);
            setPos(p.x, p.y, p.z);
            setDeltaMovement(Vec3.ZERO);
            if (t % 2 == 0) {
                sl.sendParticles(ModParticles.DARK_FLAME.get(), getX(), getY() + 3, getZ(), 4, 0.8, 1.5, 0.8, 0.02);
            }
        }
        if (t >= down && t < land) {
            setPos(leapTo.x, leapTo.y, leapTo.z);        // down: held there until the claw lands
            setDeltaMovement(Vec3.ZERO);
        }
        if (t == land) {
            setPos(leapTo.x, leapTo.y, leapTo.z);
            setNoGravity(false);
            Vec3 claw = local(HeroMoveData.LEAP_SLASH_CLAW_R_F, HeroMoveData.LEAP_SLASH_CLAW_R_R, HeroMoveData.LEAP_SLASH_CLAW_R_U, t);
            strikeArc(6.5, 75.0, LEAP_DAMAGE, 1.6, 0.5, 5);
            ring(sl, position(), 4.5, LEAP_SLAM, 0.9);
            sl.sendParticles(ModParticles.DARK_FLAME_BIG.get(), claw.x, getY() + 0.2, claw.z, 30, 2.0, 0.3, 2.0, 0.1);
            sl.sendParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), getY(), getZ(), 1, 0, 0, 0, 0);
            playSound(SoundEvents.GENERIC_EXPLODE, 2.0F, 0.5F);
            playSound(SoundEvents.ANVIL_LAND, 1.2F, 0.4F);
        }
    }

    // ---- 3. Rend
    private void tickRend(ServerLevel sl, @Nullable LivingEntity target, int t) {
        int h1 = HeroMoveData.REND_HIT1, h2 = HeroMoveData.REND_HIT2, h3 = HeroMoveData.REND_HIT3;
        boolean winding = t < h1 - 2 || (t > h1 + 2 && t < h2 - 2) || (t > h2 + 2 && t < h3 - 3);
        turnToward(winding ? target : null, 7.0F);
        // a step into each slash
        boolean step = (t >= h1 - 4 && t <= h1 + 1) || (t >= h2 - 4 && t <= h2 + 1) || (t >= h3 - 5 && t <= h3);
        if (step && (target == null || horizontalDistanceTo(target) > 3.6)) {
            Vec3 f = forward(moveYaw).scale(0.16);
            move(net.minecraft.world.entity.MoverType.SELF, new Vec3(f.x, 0, f.z));
        }
        if (t == h1 || t == h2) {
            hit.clear();
            strikeArc(6.0, 80.0, REND_DAMAGE, 1.0, 0.3, 0);
            Vec3 c = t == h1 ? local(HeroMoveData.REND_CLAW_R_F, HeroMoveData.REND_CLAW_R_R, HeroMoveData.REND_CLAW_R_U, t)
                    : local(HeroMoveData.REND_CLAW_L_F, HeroMoveData.REND_CLAW_L_R, HeroMoveData.REND_CLAW_L_U, t);
            slashFx(sl, t);
            playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 2.0F, 0.5F);
            sl.sendParticles(ModParticles.DARK_SPARK.get(), c.x, c.y, c.z, 10, 0.4, 0.4, 0.4, 0.3);
        }
        if (t == h3) {
            hit.clear();
            strikeArc(6.5, 45.0, REND_FINISHER, 1.5, 0.6, 4);
            Vec3 c = local(HeroMoveData.REND_CLAW_R_F, HeroMoveData.REND_CLAW_R_R, HeroMoveData.REND_CLAW_R_U, t);
            ring(sl, new Vec3(c.x, getY(), c.z), 3.6, 8.0F, 0.8);
            sl.sendParticles(ModParticles.DARK_FLAME_BIG.get(), c.x, getY() + 0.2, c.z, 26, 1.6, 0.3, 1.6, 0.1);
            sl.sendParticles(ParticleTypes.EXPLOSION, c.x, getY() + 0.3, c.z, 3, 1.0, 0.2, 1.0, 0);
            playSound(SoundEvents.GENERIC_EXPLODE, 1.6F, 0.55F);
        }
    }

    private void slashFx(ServerLevel sl, int t) {
        float[] f, r, u;
        if (t == HeroMoveData.REND_HIT2) {
            f = HeroMoveData.REND_CLAW_L_F; r = HeroMoveData.REND_CLAW_L_R; u = HeroMoveData.REND_CLAW_L_U;
        } else {
            f = HeroMoveData.REND_CLAW_R_F; r = HeroMoveData.REND_CLAW_R_R; u = HeroMoveData.REND_CLAW_R_U;
        }
        for (int k = -4; k <= 1; k++) {
            Vec3 p = local(f, r, u, t + k);
            sl.sendParticles(ModParticles.DARK_FLAME.get(), p.x, p.y, p.z, 3, 0.2, 0.2, 0.2, 0.02);
        }
    }

    // ---- 4. Hell Beam
    private void tickBeam(ServerLevel sl, @Nullable LivingEntity target, int t) {
        int fire = HeroMoveData.HELL_BEAM_FIRE, stop = HeroMoveData.HELL_BEAM_STOP;
        Vec3 mouth = local(HeroMoveData.HELL_BEAM_MOUTH_F, HeroMoveData.HELL_BEAM_MOUTH_R, HeroMoveData.HELL_BEAM_MOUTH_U, t);
        if (t < fire) {
            // the charge: full turning, darkness drawn into its jaws
            turnToward(target, 6.0F);
            beamYaw = moveYaw;
            if (target != null) {
                beamPitch = pitchTo(mouth, target);
            }
            float k = t / (float) fire;
            for (int i = 0; i < 2 + (int) (6 * k); i++) {
                double a = random.nextDouble() * Math.PI * 2, b = (random.nextDouble() - 0.5) * Math.PI;
                double rad = 4.0 + 3.0 * random.nextDouble();
                Vec3 from = mouth.add(Math.cos(a) * Math.cos(b) * rad, Math.sin(b) * rad, Math.sin(a) * Math.cos(b) * rad);
                Vec3 v = mouth.subtract(from).scale(0.12);
                sl.sendParticles(ModParticles.DARK_FLAME.get(), from.x, from.y, from.z, 0, v.x, v.y, v.z, 1.0);
            }
            if (t == fire / 2 || t == fire - 12) {
                playSound(SoundEvents.ENDER_DRAGON_GROWL, 1.6F, 0.4F + k * 0.2F);
            }
            this.entityData.set(BEAM_PITCH, beamPitch);
            return;
        }
        if (t == fire) {
            if (target != null) {           // locks on as it fires...
                beamYaw = yawTo(position(), target.position());
                beamPitch = pitchTo(mouth, target);
            }
            playSound(SoundEvents.WITHER_SHOOT, 2.0F, 0.4F);
            playSound(SoundEvents.GENERIC_EXPLODE, 1.4F, 0.5F);
        }
        if (t <= stop) {
            // ...then can only drag after you: run sideways and it falls behind
            if (target != null) {
                beamYaw = Mth.approachDegrees(beamYaw, yawTo(position(), target.position()), BEAM_TURN);
                beamPitch = Mth.approach(beamPitch, pitchTo(mouth, target), BEAM_PITCH_TURN);
            }
            moveYaw = beamYaw;
            faceYaw(beamYaw);
            Vec3 dir = Vec3.directionFromRotation(beamPitch, beamYaw);
            Vec3 end = mouth.add(dir.scale(BEAM_RANGE));
            BlockHitResult bh = level().clip(new ClipContext(mouth, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
            double len = bh.getType() == HitResult.Type.MISS ? BEAM_RANGE : mouth.distanceTo(bh.getLocation());
            this.entityData.set(BEAM_LEN, (float) len);
            this.entityData.set(BEAM_PITCH, beamPitch);
            if ((t - fire) % 4 == 0) {
                Vec3 tip = mouth.add(dir.scale(len));
                AABB box = new AABB(mouth, tip).inflate(BEAM_RADIUS + 1);
                for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, box, e -> e != this && e.isAlive())) {
                    Vec3 c = e.position().add(0, e.getBbHeight() * 0.5, 0);
                    double along = c.subtract(mouth).dot(dir);
                    if (along < 0 || along > len) {
                        continue;
                    }
                    double off = c.subtract(mouth.add(dir.scale(along))).length() - e.getBbWidth() * 0.5;
                    if (off < BEAM_RADIUS && e.hurt(damageSources().mobAttack(this), BEAM_DAMAGE)) {
                        ShadowFire.ignite(e, 3);
                    }
                }
                sl.sendParticles(ModParticles.DARK_FLAME_BIG.get(), tip.x, tip.y, tip.z, 6, 0.6, 0.6, 0.6, 0.06);
                sl.sendParticles(ModParticles.DARK_SPARK.get(), tip.x, tip.y, tip.z, 4, 0.4, 0.4, 0.4, 0.3);
            }
            if (t == stop) {
                this.entityData.set(BEAM_LEN, 0.0F);
            }
        } else {
            faceYaw(moveYaw);
        }
    }

    private static float pitchTo(Vec3 from, LivingEntity t) {
        Vec3 to = t.position().add(0, t.getBbHeight() * 0.5, 0);
        double dx = to.x - from.x, dz = to.z - from.z, dy = to.y - from.y;
        return (float) (-Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * Mth.RAD_TO_DEG);
    }

    // ---- 5. Tail Sweep
    private void tickTail(ServerLevel sl, @Nullable LivingEntity target, int t) {
        int p1 = HeroMoveData.TAIL_SWEEP_PUNCH1, p2 = HeroMoveData.TAIL_SWEEP_PUNCH2;
        int s0 = HeroMoveData.TAIL_SWEEP_SPIN, s1 = HeroMoveData.TAIL_SWEEP_SPIN_END;
        turnToward(t < p1 - 1 || (t > p1 && t < p2 - 1) ? target : null, 9.0F);
        if (t == p1 || t == p2) {
            hit.clear();
            strikeArc(5.2, 60.0, PUNCH_DAMAGE, 1.1, 0.3, 0);
            Vec3 f = t == p1 ? local(HeroMoveData.TAIL_SWEEP_FIST_L_F, HeroMoveData.TAIL_SWEEP_FIST_L_R, HeroMoveData.TAIL_SWEEP_FIST_L_U, t)
                    : local(HeroMoveData.TAIL_SWEEP_FIST_R_F, HeroMoveData.TAIL_SWEEP_FIST_R_R, HeroMoveData.TAIL_SWEEP_FIST_R_U, t);
            sl.sendParticles(ModParticles.DARK_FLAME_BIG.get(), f.x, f.y, f.z, 6, 0.3, 0.3, 0.3, 0.04);
            playSound(SoundEvents.PLAYER_ATTACK_STRONG, 2.0F, 0.5F);
        }
        if (t == s0) {
            hit.clear();
            playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 2.0F, 0.4F);
        }
        if (t > s0 && t <= s1 + 1) {
            // the tail tip's arc this tick: anything it passes through within its reach is struck
            Vec3 a = local(HeroMoveData.TAIL_SWEEP_TAIL_END_F, HeroMoveData.TAIL_SWEEP_TAIL_END_R, HeroMoveData.TAIL_SWEEP_TAIL_END_U, t - 1);
            Vec3 b = local(HeroMoveData.TAIL_SWEEP_TAIL_END_F, HeroMoveData.TAIL_SWEEP_TAIL_END_R, HeroMoveData.TAIL_SWEEP_TAIL_END_U, t);
            double a0 = Math.atan2(a.z - getZ(), a.x - getX()), a1 = Math.atan2(b.z - getZ(), b.x - getX());
            double reach = Math.max(Math.hypot(a.x - getX(), a.z - getZ()), Math.hypot(b.x - getX(), b.z - getZ())) + 1.2;
            double da = Mth.wrapDegrees((float) Math.toDegrees(a1 - a0));
            for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(reach, 1.0, reach),
                    e -> e != this && e.isAlive() && !hit.contains(e.getUUID()))) {
                double ex = e.getX() - getX(), ez = e.getZ() - getZ();
                double r = Math.hypot(ex, ez);
                if (r > reach + e.getBbWidth() * 0.5 || r < 1.5 || e.getY() > getY() + 4.5) {
                    continue;
                }
                double ae = Mth.wrapDegrees((float) Math.toDegrees(Math.atan2(ez, ex) - a0));
                boolean inside = da >= 0 ? ae >= -12 && ae <= da + 12 : ae <= 12 && ae >= da - 12;
                if (inside) {
                    hit.add(e.getUUID());
                    if (e.hurt(damageSources().mobAttack(this), TAIL_DAMAGE)) {
                        e.setDeltaMovement(ex / r * 1.8, 0.55, ez / r * 1.8);
                        e.hurtMarked = true;
                    }
                }
            }
            sl.sendParticles(ModParticles.DARK_FLAME.get(), b.x, b.y, b.z, 4, 0.3, 0.3, 0.3, 0.02);
        }
    }

    // ---- shared
    private void strikeArc(double reach, double halfAngle, float damage, double knock, double up, int fireSeconds) {
        Vec3 f = forward(moveYaw);
        double cos = Math.cos(Math.toRadians(halfAngle));
        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(reach, 1.5, reach),
                e -> e != this && e.isAlive() && !hit.contains(e.getUUID()))) {
            Vec3 d = e.position().subtract(position()).multiply(1, 0, 1);
            double len = d.length() - e.getBbWidth() * 0.5;
            if (len > reach || (len > 1.0 && d.normalize().dot(f) < cos)) {
                continue;
            }
            hit.add(e.getUUID());
            if (e.hurt(damageSources().mobAttack(this), damage)) {
                e.setDeltaMovement(f.x * knock, up, f.z * knock);
                e.hurtMarked = true;
                if (fireSeconds > 0) {
                    ShadowFire.ignite(e, fireSeconds);
                }
            }
        }
    }

    private void ring(ServerLevel sl, Vec3 c, double radius, float damage, double knock) {
        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(radius, 2.0, radius),
                e -> e != this && e.isAlive() && !hit.contains(e.getUUID()))) {
            Vec3 d = e.position().subtract(c).multiply(1, 0, 1);
            if (d.length() > radius) {
                continue;
            }
            hit.add(e.getUUID());
            if (e.hurt(damageSources().mobAttack(this), damage)) {
                Vec3 n = d.lengthSqr() < 1e-4 ? forward(moveYaw) : d.normalize();
                e.setDeltaMovement(n.x * knock, 0.5, n.z * knock);
                e.hurtMarked = true;
            }
        }
        int n = (int) (radius * 8);
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n;
            sl.sendParticles(ModParticles.DARK_FLAME.get(), c.x + Math.cos(a) * radius, c.y + 0.15, c.z + Math.sin(a) * radius, 1, 0, 0.05, 0, 0.02);
        }
    }

    /** where a leap comes down: the highest floor under its whole footprint (what its body would actually rest on) */
    private Vec3 landingSpot(Vec3 p) {
        double half = getBbWidth() * 0.5 - 0.3;
        double top = -1e9;
        for (double dx : new double[]{-half, 0, half}) {
            for (double dz : new double[]{-half, 0, half}) {
                Vec3 g = groundBelow(new Vec3(p.x + dx, p.y + 3, p.z + dz));
                top = Math.max(top, g.y);
            }
        }
        return new Vec3(p.x, top > -1e8 ? top : p.y, p.z);
    }

    private Vec3 groundBelow(Vec3 p) {
        BlockPos.MutableBlockPos pos = BlockPos.containing(p.x, p.y + 0.5, p.z).mutable();
        for (int i = 0; i < 24; i++) {
            BlockPos below = pos.below();
            VoxelShape shape = level().getBlockState(below).getCollisionShape(level(), below);
            if (!shape.isEmpty()) {
                return new Vec3(p.x, below.getY() + shape.max(Direction.Axis.Y), p.z);
            }
            pos.move(Direction.DOWN);
        }
        return p;
    }

    // ------------------------------------------------------------ the end fight
    private void startFinale(ServerPlayer p) {
        if (moveTick >= 0) {
            endMove();
        }
        getNavigation().stop();
        finale = true;
        clashPlayer = p.getUUID();
        HeroForm.setClash(p, true);
        bossBar.setName(Component.translatable("entity.rudues.infernal_hero.finale"));
        setMove(MOVE_CLASH);
        beginStage(ClashScript.STAGE_PUNCH1, p);
    }

    @Nullable
    private ServerPlayer clashPlayer() {
        if (clashPlayer == null || !(level() instanceof ServerLevel sl)) {
            return null;
        }
        Player p = sl.getPlayerByUUID(clashPlayer);
        return p instanceof ServerPlayer sp ? sp : null;
    }

    /** lines both fighters up and starts a clash: punch clashes from a fixed distance, the beam clash where they stand */
    private void beginStage(int newStage, ServerPlayer p) {
        stage = newStage;
        result = ClashScript.RESULT_NONE;
        balance = 0.5F;
        hold = 0;
        Vec3 hp = position(), pp = p.position();
        Vec3 u = pp.subtract(hp).multiply(1, 0, 1);
        if (u.lengthSqr() < 1e-4) {
            u = forward(getYRot());
        }
        u = u.normalize();
        axisYaw = ClashScript.yawOf(u);
        if (stage == ClashScript.STAGE_BEAM) {
            double gap = Mth.clamp(hp.distanceTo(pp), 14.0, 30.0);
            origin = groundBelow(hp.add(u.scale(gap * 0.5)).add(0, 2, 0));
            dHero = (float) (gap * 0.5);
            dPlayer = (float) (gap * 0.5);
        } else if (stage == ClashScript.STAGE_POUND) {
            origin = groundBelow(hp.add(0, 2, 0));          // the player comes to it: it holds its ground
        } else {
            origin = groundBelow(hp.add(pp).scale(0.5).add(0, 2, 0));
        }
        setPhase(ClashScript.PH_CHARGE);
        // both start exactly where the script says (a flash of fire covers the cut)
        Vec3 h = heroAt(0), q = playerAt(0);
        setPos(h.x, h.y, h.z);
        faceYaw(axisYaw);
        p.teleportTo((ServerLevel) level(), q.x, q.y, q.z, axisYaw + 180.0F, 0.0F);
        ServerLevel sl = (ServerLevel) level();
        sl.sendParticles(ModParticles.DARK_FLAME_BIG.get(), h.x, h.y + 3, h.z, 30, 1.5, 2.5, 1.5, 0.05);
        sl.sendParticles(ModParticles.PURPLE_FLAME_BIG.get(), q.x, q.y + 3, q.z, 30, 1.5, 2.5, 1.5, 0.05);
        playSound(SoundEvents.ENDER_DRAGON_GROWL, 3.0F, stage == ClashScript.STAGE_BEAM ? 0.35F : 0.55F);
    }

    private void setPhase(int ph) {
        phase = ph;
        phaseStart = level().getGameTime();
        ServerPlayer p = clashPlayer();
        String[] clips = ClashScript.clips(stage, ph, result);
        if (clips[0] != null) {
            triggerAnim("main", clips[0]);
        }
        if (p != null && clips[1] != null) {
            HeroForm.triggerAvatar(p, clips[1]);
        }
        sendClash(true);
    }

    private void sendClash(boolean active) {
        ServerPlayer p = clashPlayer();
        ClashPacket pkt = new ClashPacket(getId(), p != null ? p.getId() : -1, active, stage, phase, result, phaseStart,
                origin.x, origin.y, origin.z, axisYaw, dHero, dPlayer, balance, hold / (float) ClashScript.HOLD_TICKS);
        Network.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> this), pkt);
        if (p != null) {
            Network.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), pkt);
        }
    }

    private Vec3 heroAt(float t) {
        Vec3 u = ClashScript.axis(axisYaw);
        float d = ClashScript.dist(true, stage, phase, result, t, dHero, dPlayer);
        return ClashScript.at(origin, u, true, d, ClashScript.height(true, stage, phase, result, t));
    }

    private Vec3 playerAt(float t) {
        Vec3 u = ClashScript.axis(axisYaw);
        return ClashScript.at(origin, u, false, ClashScript.dist(false, stage, phase, result, t, dHero, dPlayer),
                ClashScript.height(false, stage, phase, result, t));
    }

    /** presses from the clash player (rate-limited) */
    public void clashInput(ServerPlayer p, int presses) {
        if (!finale || phase != ClashScript.PH_STRAIN || !p.getUUID().equals(clashPlayer)) {
            return;
        }
        if (level().getGameTime() - phaseStart < ClashScript.PROMPT_DELAY) {
            return;
        }
        int n = Math.min(presses, Math.max(0, 2 - pressesThisTick));
        pressesThisTick += n;
        balance = Math.min(1.0F, balance + n * (stage == ClashScript.STAGE_BEAM ? ClashScript.PRESS_BEAM : ClashScript.PRESS_PUNCH));
    }

    private void tickFinale() {
        ServerPlayer p = clashPlayer();
        if (p == null || !p.isAlive() || !HeroForm.isHero(p) || p.level() != level()) {
            abortFinale();
            return;
        }
        pressesThisTick = 0;
        ServerLevel sl = (ServerLevel) level();
        int t = (int) (level().getGameTime() - phaseStart);
        setDeltaMovement(Vec3.ZERO);
        setNoGravity(true);
        if (phase != ClashScript.PH_WAIT) {
            Vec3 h = heroAt(t);
            setPos(h.x, h.y, h.z);
            faceYaw(axisYaw);
        }
        Vec3 u = ClashScript.axis(axisYaw);
        Vec3 contact = origin.add(0, ClashData.FIST_HEIGHT, 0);
        switch (phase) {
            case ClashScript.PH_CHARGE -> {
                if (stage == ClashScript.STAGE_BEAM) {
                    if (t == ClashData.BEAM_CLASH_FIRE) {
                        playSound(SoundEvents.WITHER_SHOOT, 3.0F, 0.4F);
                        playSound(SoundEvents.GENERIC_EXPLODE, 3.0F, 0.5F);
                    }
                } else if (stage == ClashScript.STAGE_POUND) {
                    poundChargeFx(sl, t, u);
                } else if (t == ClashScript.chargeTicks(stage) - 1) {
                    // the fists meet
                    sl.sendParticles(ParticleTypes.EXPLOSION_EMITTER, contact.x, contact.y, contact.z, 1, 0, 0, 0, 0);
                    sl.sendParticles(ModParticles.PURPLE_FLAME_BIG.get(), contact.x, contact.y, contact.z, 30, 0.6, 1.5, 0.6, 0.25);
                    sl.sendParticles(ModParticles.DARK_FLAME_BIG.get(), contact.x, contact.y, contact.z, 30, 0.6, 1.5, 0.6, 0.25);
                    playSound(SoundEvents.GENERIC_EXPLODE, 3.0F, 0.6F);
                    playSound(SoundEvents.ANVIL_LAND, 2.0F, 0.5F);
                }
                if (t >= ClashScript.chargeTicks(stage)) {
                    setPhase(ClashScript.PH_STRAIN);
                }
            }
            case ClashScript.PH_STRAIN -> {
                if (t >= ClashScript.PROMPT_DELAY) {
                    balance = Math.max(0.0F, balance - (stage == ClashScript.STAGE_BEAM ? ClashScript.DECAY_BEAM : ClashScript.DECAY_PUNCH));
                }
                if (t % 2 == 0) {
                    sendClash(true);
                }
                if (stage == ClashScript.STAGE_POUND) {
                    // every blow lands on its guard
                    int k = t % 20;
                    if (k == ClashData.POUND_HIT_A || k == ClashData.POUND_HIT_B) {
                        Vec3 f = poundFace(u);
                        sl.sendParticles(ModParticles.PURPLE_FLAME_BIG.get(), f.x, f.y, f.z, 8, 0.3, 0.2, 0.3, 0.12);
                        sl.sendParticles(ModParticles.DARK_SPARK.get(), f.x, f.y, f.z, 10, 0.4, 0.3, 0.4, 0.35);
                        playSound(SoundEvents.PLAYER_ATTACK_STRONG, 2.0F, 0.55F);
                        playSound(SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, 1.2F, 0.5F);
                    }
                } else {
                    Vec3 at = stage == ClashScript.STAGE_BEAM ? beamMeet() : contact;
                    sl.sendParticles(t % 2 == 0 ? ModParticles.PURPLE_SPARK.get() : ModParticles.DARK_SPARK.get(), at.x, at.y, at.z, 3, 0.4, 0.6, 0.4, 0.4);
                }
                if (stage != ClashScript.STAGE_POUND && t % 10 == 0) {
                    playSound(stage == ClashScript.STAGE_BEAM ? SoundEvents.BEACON_AMBIENT : SoundEvents.ANVIL_PLACE, 1.5F, 0.5F);
                }
                // won by holding the gauge above the line long enough (a dip below costs some of the hold, not all)
                if (t >= ClashScript.PROMPT_DELAY) {
                    hold = balance >= ClashScript.WIN_LINE ? hold + 1 : Math.max(0, hold - 3);
                }
                if (hold >= ClashScript.HOLD_TICKS) {
                    resolve(ClashScript.RESULT_WIN);
                } else if (t >= ClashScript.strainTicks(stage)) {
                    resolve(balance >= ClashScript.WIN_LINE ? ClashScript.RESULT_WIN : ClashScript.RESULT_LOSE);
                } else if (balance <= 0.0F) {
                    resolve(ClashScript.RESULT_LOSE);       // overpowered
                }
            }
            case ClashScript.PH_RESOLVE -> {
                if (result == ClashScript.RESULT_LOSE && t == (stage == ClashScript.STAGE_POUND ? ClashData.POUND_LOSE_KICK : 2)) {
                    float dmg = p.getMaxHealth() * (stage == ClashScript.STAGE_BEAM ? 0.25F : 0.18F);
                    p.hurt(damageSources().mobAttack(this), dmg);
                }
                if (stage == ClashScript.STAGE_POUND) {
                    poundResolveFx(sl, t, u, p);
                }
                if (result == ClashScript.RESULT_WIN && stage == ClashScript.STAGE_BEAM && t % 3 == 0) {
                    sl.sendParticles(ModParticles.PURPLE_FLAME_BIG.get(), getX(), getY() + 3, getZ(), 10, 1.4, 2.4, 1.4, 0.08);
                }
                if (t >= ClashScript.resolveTicks(stage, result)) {
                    if (result == ClashScript.RESULT_LOSE) {
                        HeroForm.setClash(p, false);
                        setPhase(ClashScript.PH_WAIT);
                    } else if (stage == ClashScript.STAGE_BEAM) {
                        finish(p);
                    } else {
                        beginStage(stage + 1, p);
                    }
                }
            }
            default -> {
                // after a lost clash: a breather, then it comes again
                setNoGravity(false);
                turnTowardPlayer(p);
                if (t >= ClashScript.RETRY_TICKS) {
                    HeroForm.setClash(p, true);
                    beginStage(stage, p);
                }
            }
        }
    }

    // ---- clash 2's impacts
    private Vec3 poundFace(Vec3 u) {
        return ClashScript.along(origin, u, ClashData.POUND_FACE_ALONG, ClashData.POUND_FACE_UP + 0.5F);
    }

    private void poundChargeFx(ServerLevel sl, int t, Vec3 u) {
        if (t == ClashData.POUND_CHARGE_HIT) {
            // the uppercut connects
            Vec3 c = ClashScript.along(origin, u, ClashData.POUND_CHIN_ALONG, ClashData.POUND_CHIN_UP);
            sl.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y, c.z, 1, 0, 0, 0, 0);
            sl.sendParticles(ModParticles.PURPLE_FLAME_BIG.get(), c.x, c.y, c.z, 30, 0.4, 0.8, 0.4, 0.3);
            sl.sendParticles(ModParticles.DARK_SPARK.get(), c.x, c.y, c.z, 25, 0.3, 0.6, 0.3, 0.5);
            playSound(SoundEvents.GENERIC_EXPLODE, 3.0F, 0.7F);
            playSound(SoundEvents.ANVIL_LAND, 2.0F, 0.6F);
        } else if (t == ClashData.POUND_CHARGE_HERO_DOWN) {
            // it crashes down on its back
            Vec3 c = ClashScript.along(origin, u, ClashData.POUND_CHEST_ALONG, 0.2F);
            sl.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y, c.z, 5, 2.0, 0.2, 1.0, 0);
            sl.sendParticles(ModParticles.DARK_FLAME_BIG.get(), c.x, c.y, c.z, 30, 2.2, 0.2, 1.2, 0.05);
            playSound(SoundEvents.GENERIC_EXPLODE, 2.5F, 0.45F);
        } else if (t == ClashData.POUND_CHARGE_MOUNT) {
            Vec3 c = ClashScript.along(origin, u, ClashData.POUND_MOUNT, 0.2F);
            sl.sendParticles(ModParticles.PURPLE_FLAME_BIG.get(), c.x, c.y, c.z, 20, 1.2, 0.2, 1.2, 0.05);
            playSound(SoundEvents.IRON_GOLEM_DAMAGE, 2.0F, 0.5F);
        }
    }

    private void poundResolveFx(ServerLevel sl, int t, Vec3 u, ServerPlayer p) {
        if (result == ClashScript.RESULT_WIN) {
            if (t == ClashData.POUND_WIN_SLAM) {
                // both fists down into its chest: the ground caves under it
                Vec3 c = ClashScript.along(origin, u, ClashData.POUND_CHEST_ALONG, ClashData.POUND_CHEST_UP);
                sl.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y, c.z, 1, 0, 0, 0, 0);
                sl.sendParticles(ModParticles.PURPLE_FLAME_BIG.get(), c.x, c.y - 1.2, c.z, 60, 2.5, 0.2, 2.5, 0.12);
                sl.sendParticles(ModParticles.DARK_FLAME_BIG.get(), c.x, c.y - 1.2, c.z, 40, 2.5, 0.2, 2.5, 0.08);
                playSound(SoundEvents.GENERIC_EXPLODE, 3.0F, 0.4F);
                playSound(SoundEvents.ANVIL_LAND, 2.0F, 0.4F);
            } else if (t == ClashData.POUND_WIN_RELEASE) {
                sl.sendParticles(ModParticles.PURPLE_FLAME_BIG.get(), getX(), getY() + 2, getZ(), 30, 1.5, 1.0, 1.5, 0.2);
                playSound(SoundEvents.ENDER_DRAGON_FLAP, 3.0F, 0.5F);
                p.level().playSound(null, p.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 2.0F, 0.7F);
            } else if (t > ClashData.POUND_WIN_RELEASE && t < ClashData.POUND_WIN_LAND && t % 2 == 0) {
                sl.sendParticles(ModParticles.DARK_FLAME.get(), getX(), getY() + 2, getZ(), 6, 0.8, 0.8, 0.8, 0.02);
            } else if (t == ClashData.POUND_WIN_LAND) {
                sl.sendParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), getY() + 0.5, getZ(), 1, 0, 0, 0, 0);
                sl.sendParticles(ModParticles.DARK_FLAME_BIG.get(), getX(), getY() + 0.3, getZ(), 50, 2.5, 0.2, 2.5, 0.08);
                playSound(SoundEvents.GENERIC_EXPLODE, 3.0F, 0.5F);
            } else if (t > ClashData.POUND_WIN_LAND && t < ClashData.POUND_WIN_LAND + 8) {
                sl.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, getX(), getY() + 0.2, getZ(), 2, 1.0, 0.1, 1.0, 0.01);
            }
        } else if (t == ClashData.POUND_LOSE_KICK) {
            Vec3 c = ClashScript.along(origin, u, ClashData.POUND_MOUNT, 2.0F);
            sl.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y, c.z, 2, 0.3, 0.3, 0.3, 0);
            sl.sendParticles(ModParticles.DARK_FLAME_BIG.get(), c.x, c.y, c.z, 30, 0.6, 0.6, 0.6, 0.25);
            playSound(SoundEvents.GENERIC_EXPLODE, 2.5F, 0.6F);
        }
    }

    private void turnTowardPlayer(ServerPlayer p) {
        faceYaw(yawTo(position(), p.position()));
    }

    private Vec3 beamMeet() {
        Vec3 u = ClashScript.axis(axisYaw);
        Vec3 hm = origin.add(u.scale(-dHero + ClashData.HERO_MOUTH_F)).add(0, ClashData.HERO_MOUTH_U, 0);
        Vec3 pm = origin.add(u.scale(dPlayer - ClashData.PLAYER_MOUTH_F)).add(0, ClashData.PLAYER_MOUTH_U, 0);
        return hm.lerp(pm, 1.0F - balance);
    }

    private void resolve(int r) {
        result = r;
        ServerLevel sl = (ServerLevel) level();
        Vec3 at = stage == ClashScript.STAGE_BEAM ? beamMeet()
                : stage == ClashScript.STAGE_POUND ? poundFace(ClashScript.axis(axisYaw)) : origin.add(0, ClashData.FIST_HEIGHT, 0);
        sl.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y, at.z, 1, 0, 0, 0, 0);
        sl.sendParticles(r == ClashScript.RESULT_WIN ? ModParticles.PURPLE_FLAME_BIG.get() : ModParticles.DARK_FLAME_BIG.get(),
                at.x, at.y, at.z, 50, 1.0, 1.5, 1.0, 0.3);
        playSound(SoundEvents.GENERIC_EXPLODE, 3.0F, 0.45F);
        setPhase(ClashScript.PH_RESOLVE);
    }

    private void finish(ServerPlayer p) {
        HeroForm.setClash(p, false);
        sendClash(false);
        ServerLevel sl = (ServerLevel) level();
        sl.sendParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), getY() + 2, getZ(), 3, 1.5, 1.5, 1.5, 0);
        sl.sendParticles(ModParticles.DARK_FLAME_BIG.get(), getX(), getY() + 3, getZ(), 120, 2.0, 3.0, 2.0, 0.15);
        playSound(SoundEvents.WITHER_DEATH, 3.0F, 0.6F);
        finaleKill = true;
        setNoGravity(false);
        hurt(damageSources().playerAttack(p), Float.MAX_VALUE);
    }

    private void abortFinale() {
        ServerPlayer p = clashPlayer();
        if (p != null) {
            HeroForm.setClash(p, false);
        }
        sendClash(false);
        finale = false;
        clashPlayer = null;
        phase = ClashScript.PH_WAIT;
        setNoGravity(false);
        setMove(MOVE_NONE);
        bossBar.setName(getDisplayName());
    }

    public boolean inFinale() {
        return finale;
    }

    // ------------------------------------------------------------ damage
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (finale && !finaleKill && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return false;
        }
        if (getMaxHealth() < MAX_HEALTH - 1.0) {
            amount *= (float) (getMaxHealth() / MAX_HEALTH);     // the health cap couldn't be lifted: same fight length
        }
        if (!finaleKill && !level().isClientSide && getTarget() instanceof ServerPlayer sp && HeroForm.empowered(sp)) {
            // it can't be finished off by plain damage: at 15% the end fight takes over
            float floor = getMaxHealth() * FINALE_AT;
            if (getHealth() - amount < floor) {
                amount = Math.max(0.0F, getHealth() - floor + 0.01F);
            }
        }
        return super.hurt(source, amount);
    }

    @Override
    public void knockback(double strength, double x, double z) {
        if (!isInMove() && !finale) {
            super.knockback(strength, x, z);
        }
    }

    @Override
    public boolean isPushable() {
        return !finale && super.isPushable();
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
        if (finale) {
            sendClash(true);
        }
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        bossBar.removePlayer(player);
    }

    @Override
    public void remove(RemovalReason reason) {
        if (finale && !level().isClientSide) {
            ServerPlayer p = clashPlayer();
            if (p != null) {
                HeroForm.setClash(p, false);
            }
            sendClash(false);
        }
        super.remove(reason);
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        AABB box = super.getBoundingBoxForCulling();
        if (getMove() == MOVE_BEAM || finale) {
            return box.inflate(BEAM_RANGE);
        }
        return box.inflate(4.0);
    }

    // ------------------------------------------------------------ client
    private void clientFx() {
        if (random.nextInt(2) == 0) {
            level().addParticle(ModParticles.DARK_EMBER.get(), getRandomX(1.2), getY() + random.nextDouble() * 6.5, getRandomZ(1.2), 0, 0.03, 0);
        }
        if (random.nextInt(6) == 0) {
            level().addParticle(ModParticles.DARK_FLAME.get(), getRandomX(0.9), getY() + 2.5 + random.nextDouble() * 3.5, getRandomZ(0.9), 0, 0.02, 0);
        }
    }

    // ------------------------------------------------------------ GeckoLib
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        AnimationController<InfernalHeroEntity> c = new AnimationController<>(this, "main", 4, state -> {
            if (state.isMoving() && !isInMove()) {
                return state.setAndContinue(STALK);
            }
            return state.setAndContinue(GUARD);
        });
        for (String n : ONCE) {
            c.triggerableAnim(n, RawAnimation.begin().thenPlay("animation.infernal_hero." + n));
        }
        for (String n : HOLD) {
            c.triggerableAnim(n, RawAnimation.begin().thenPlayAndHold("animation.infernal_hero." + n));
        }
        for (String n : LOOPS) {
            c.triggerableAnim(n, RawAnimation.begin().thenLoop("animation.infernal_hero." + n));
        }
        c.setAnimationSpeedHandler(e -> {
            if (e.isInMove()) {
                return 1.0;
            }
            double v = Math.sqrt((e.getX() - e.xo) * (e.getX() - e.xo) + (e.getZ() - e.zo) * (e.getZ() - e.zo)) * 20.0;
            return v < 0.05 ? 1.0 : Mth.clamp(v / HeroMoveData.STALK_SPEED, 0.6, 2.5);
        });
        controllers.add(c);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    // ------------------------------------------------------------ misc
    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.RAVAGER_AMBIENT;
    }

    @Override
    public float getVoicePitch() {
        return 0.5F;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.RAVAGER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.WITHER_DEATH;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean fireImmune() {
        return true;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("BeamCooldown", beamCooldown);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        beamCooldown = tag.getInt("BeamCooldown");
        if (hasCustomName()) {
            bossBar.setName(getDisplayName());
        }
    }
}
