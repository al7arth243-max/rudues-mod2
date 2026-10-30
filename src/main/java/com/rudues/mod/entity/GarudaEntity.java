package com.rudues.mod.entity;

import com.rudues.mod.ability.HeroForm;
import com.rudues.mod.ability.SemiPrime;
import com.rudues.mod.castle.CraterManager;
import com.rudues.mod.network.Network;
import com.rudues.mod.registry.ModBlocks;
import com.rudues.mod.registry.ModParticles;
import com.rudues.mod.world.CraterLayout;
import net.minecraft.core.BlockPos;
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
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Garuda: Benedikta, become a monster of wind the size of the Hero of Hell. Fought in the storm crater by the Hero of
 * Hell. Four wings and hooked wing-hands; she hangs in the air and fights at range and in sudden rushes:
 * <ol>
 *   <li>Barrage - a stream of great wind bolts from her hands;</li>
 *   <li>Claws - in close: a hooked wing-hand, the other, then her feet;</li>
 *   <li>Tornadoes - three slow, huge twisters rolled out across the crater at once, to be slipped between;</li>
 *   <li>Wind Shot - one fast bolt;</li>
 *   <li>Screech - a scream, then a straight-line rush through him.</li>
 * </ol>
 * At 5% the end fight takes over ({@link GarudaScene}): two clashes, then the hellfire.
 */
public class GarudaEntity extends Monster implements GeoEntity {
    private static final EntityDataAccessor<Integer> ANIM = SynchedEntityData.defineId(GarudaEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ANIM_START = SynchedEntityData.defineId(GarudaEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> STAGE = SynchedEntityData.defineId(GarudaEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> STAGE_START = SynchedEntityData.defineId(GarudaEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> BALANCE = SynchedEntityData.defineId(GarudaEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> SCENE_PLAYER = SynchedEntityData.defineId(GarudaEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> HIDDEN = SynchedEntityData.defineId(GarudaEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> BURNT = SynchedEntityData.defineId(GarudaEntity.class, EntityDataSerializers.BOOLEAN);
    /** the first BALL of the end fight: his arm is growing back up there (the camera stays on him) */
    private static final EntityDataAccessor<Boolean> REGROW = SynchedEntityData.defineId(GarudaEntity.class, EntityDataSerializers.BOOLEAN);
    private static final net.minecraft.core.particles.DustParticleOptions ICHOR =
            new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(0.42F, 0.02F, 0.06F), 1.6F);
    private static final net.minecraft.core.particles.BlockParticleOption RUBBLE =
            new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, net.minecraft.world.level.block.Blocks.BLACKSTONE.defaultBlockState());

    public static final float SCALE = 1.5F;
    public static final double MAX_HEALTH = 3500.0;
    public static final float FINALE_AT = 0.05F;
    public static final float BOLT_DAMAGE = 9.0F, SHOT_DAMAGE = 14.0F, CLAW_DAMAGE = 14.0F, KICK_DAMAGE = 18.0F, ZOOM_DAMAGE = 20.0F,
            TORNADO_DAMAGE = 5.0F;
    private static final double HOVER = 2.4;

    public static final int MOVE_NONE = 0, MOVE_BARRAGE = 1, MOVE_CLAWS = 2, MOVE_TORNADOES = 3, MOVE_SHOT = 4, MOVE_SCREECH = 5, MOVE_SCENE = 9;
    private static final String P = "animation.garuda.";
    private static final String[] ANIMS = {"idle", "fly", "flyby", "land_face", "barrage", "claws", "tornadoes", "quick_shot", "screech", "zoom",
            "tornado_cast", "windball_form", "windball_strain", "windball_blast", "lie", "neck_held", "neck_kick", "arms_held", "blown_away",
            "wind_blast", "snatch"};
    private static final java.util.Set<String> LOOPS = java.util.Set.of("idle", "fly", "zoom", "windball_strain", "lie", "neck_held", "arms_held");
    private static final RawAnimation[] RAW = new RawAnimation[ANIMS.length];
    static {
        for (int i = 0; i < ANIMS.length; i++) {
            RAW[i] = LOOPS.contains(ANIMS[i]) ? RawAnimation.begin().thenLoop(P + ANIMS[i]) : RawAnimation.begin().thenPlayAndHold(P + ANIMS[i]);
        }
    }
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop(P + "idle");
    private static final RawAnimation FLY = RawAnimation.begin().thenLoop(P + "fly");

    /** players held in one of her scenes: hits on them are refused unless the scene lets one through */
    private static final Map<UUID, Boolean> SHIELDED = new ConcurrentHashMap<>();
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    public static void shield(Player p) {
        SHIELDED.put(p.getUUID(), false);
    }

    public static boolean shielded(Player p) {
        Boolean b = SHIELDED.get(p.getUUID());
        return b != null && !b;
    }

    public static boolean inScene(Player p) {
        return SHIELDED.containsKey(p.getUUID());
    }

    /**
     * A scene shield only lasts while the Garuda playing that scene is alive and ticking. If she is gone or stalled
     * (unloaded, removed by something else) the player is let go after five seconds rather than left untouchable.
     */
    public static void watch(ServerLevel level, ServerPlayer p) {
        if (!SHIELDED.containsKey(p.getUUID())) {
            return;
        }
        long now = level.getGameTime();
        for (GarudaEntity g : level.getEntities(com.rudues.mod.registry.ModEntities.GARUDA.get(), e -> e.isAlive() && p.getUUID().equals(e.foe))) {
            if (now - g.lastTicked < 100) {
                return;
            }
        }
        SHIELDED.remove(p.getUUID());
        LOGGER.warn("Garuda: no scene is running for {}; released them", p.getGameProfile().getName());
    }

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final ServerBossEvent bossBar = new ServerBossEvent(Component.translatable("entity.rudues.garuda"),
            BossEvent.BossBarColor.GREEN, BossEvent.BossBarOverlay.NOTCHED_10);
    @Nullable
    private UUID foe;
    private int stageT, moveTick, cooldown = 30, tornadoCooldown, lastMove, pressesThisTick, seenAnimStart = Integer.MIN_VALUE;
    private boolean pendingFinale, ended, warnedFree;
    private long lastTicked;
    private float moveYaw;
    private Vec3 mFrom = Vec3.ZERO, mLock = Vec3.ZERO, mDir = Vec3.ZERO;
    private final java.util.Set<UUID> hit = new java.util.HashSet<>();
    @Nullable
    private WindBallEntity ball, blastOrb;

    public GarudaEntity(EntityType<? extends GarudaEntity> type, Level level) {
        super(type, level);
        this.xpReward = 600;
        this.setPersistenceRequired();
        this.setNoGravity(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, MAX_HEALTH)
                .add(Attributes.ARMOR, 10.0D)
                .add(Attributes.ATTACK_DAMAGE, 14.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.3D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
                .add(Attributes.FOLLOW_RANGE, 96.0D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(ANIM, -1);
        this.entityData.define(ANIM_START, 0);
        this.entityData.define(STAGE, GarudaScene.NONE);
        this.entityData.define(STAGE_START, 0);
        this.entityData.define(BALANCE, GarudaScene.START_BALANCE);
        this.entityData.define(SCENE_PLAYER, -1);
        this.entityData.define(HIDDEN, false);
        this.entityData.define(BURNT, false);
        this.entityData.define(REGROW, false);
    }

    // ------------------------------------------------------------ queries (both sides)
    public int stage() {
        return this.entityData.get(STAGE);
    }

    public float stageTime(float pt) {
        return (float) (level().getGameTime() - this.entityData.get(STAGE_START)) + pt;
    }

    public float balance() {
        return this.entityData.get(BALANCE);
    }

    public int scenePlayerId() {
        return this.entityData.get(SCENE_PLAYER);
    }

    public boolean hidden() {
        return this.entityData.get(HIDDEN);
    }

    public boolean regrowing() {
        return this.entityData.get(REGROW);
    }

    /** after the hellfire: charred, her arms gone */
    public boolean burnt() {
        return this.entityData.get(BURNT);
    }

    private void setStage(int s) {
        this.entityData.set(STAGE, s);
        this.entityData.set(STAGE_START, (int) level().getGameTime());
        stageT = 0;
    }

    private void setAnim(String name) {
        this.entityData.set(ANIM, java.util.Arrays.asList(ANIMS).indexOf(name));
        this.entityData.set(ANIM_START, (int) level().getGameTime());
    }

    private void clearAnim() {
        this.entityData.set(ANIM, -1);
    }

    @Nullable
    private ServerPlayer foePlayer() {
        return foe != null && level() instanceof ServerLevel sl && sl.getPlayerByUUID(foe) instanceof ServerPlayer sp ? sp : null;
    }

    // ------------------------------------------------------------ the arrival
    /** set up in the crater for this player: hidden far behind them until her cue */
    public void beginArrival(ServerPlayer p) {
        foe = p.getUUID();
        lastTicked = level().getGameTime();
        shield(p);
        this.entityData.set(SCENE_PLAYER, p.getId());
        this.entityData.set(HIDDEN, true);
        Vec3 s = GarudaScene.flyFrom();
        setPos(s.x, s.y, s.z);
        setMove(MOVE_SCENE);
        setStage(GarudaScene.ARRIVE);
        bossBar.setVisible(false);
        setTarget(p);
        noPhysics = true;
        if (SemiPrime.engaged(p)) {
            SemiPrime.end(p, false);
        }
        HeroForm.forceForm(p);
    }

    private int move;

    private void setMove(int m) {
        move = m;
    }

    public boolean inScene() {
        return move == MOVE_SCENE;
    }

    private void holdPlayer(ServerPlayer p, Vec3 at, float yaw) {
        p.fallDistance = 0;
        p.setDeltaMovement(Vec3.ZERO);
        if (p.position().distanceToSqr(at) > 0.09) {
            p.connection.teleport(at.x, at.y, at.z, yaw, 0.0F);
        }
    }

    /**
     * Out of her scenes: clear everything the castle fight and the cinematics put on the player (scene shields, a
     * Semi Prime power-up, a clash lock, the transformation's invulnerability). True if anything was still set.
     */
    private boolean freePlayer(ServerPlayer p) {
        boolean fixed = SHIELDED.remove(p.getUUID()) != null;
        fixed |= FeatheredKnightEntity.release(p);
        if (SemiPrime.transforming(p)) {
            SemiPrime.end(p, false);
            fixed = true;
        }
        if (HeroForm.inClash(p)) {
            HeroForm.setClash(p, false);
            fixed = true;
        }
        if (p.isInvulnerable() && !HeroForm.transforming(p) && !p.isCreative() && !p.isSpectator()) {
            p.setInvulnerable(false);
            fixed = true;
        }
        if (hidden()) {
            this.entityData.set(HIDDEN, false);
            fixed = true;
        }
        return fixed;
    }

    private void tickArrive(ServerPlayer p) {
        int t = stageT++;
        Vec3 a = GarudaScene.heroArrive();
        holdPlayer(p, a, 180.0F);
        HeroForm.forceForm(p);
        HeroForm.sustain(p);
        if (t == GarudaScene.SCREECH) {
            playSound(SoundEvents.PHANTOM_AMBIENT, 4.0F, 0.4F);
            playSound(SoundEvents.ENDER_DRAGON_GROWL, 2.0F, 1.3F);
        }
        if (t == GarudaScene.FLY_START) {
            this.entityData.set(HIDDEN, false);
            setAnim("flyby");
            glide(GarudaScene.flyFrom());
            faceYaw(180.0F);
        }
        if (t > GarudaScene.FLY_START && t <= GarudaScene.FLY_END) {
            double k = (t - GarudaScene.FLY_START) / (double) (GarudaScene.FLY_END - GarudaScene.FLY_START);
            glide(GarudaScene.flyFrom().lerp(GarudaScene.flyTo(), k));
            faceYaw(180.0F);
            if (t == GarudaScene.FLY_START + 12) {
                playSound(SoundEvents.ENDER_DRAGON_FLAP, 4.0F, 0.6F);
                playSound(SoundEvents.PHANTOM_SWOOP, 4.0F, 0.5F);
                ((ServerLevel) level()).sendParticles(ParticleTypes.CLOUD, a.x + 4, a.y + 1, a.z, 60, 2.0, 1.0, 2.0, 0.2);
            }
        }
        if (t == GarudaScene.FLY_END + 1) {
            setAnim("land_face");
        }
        if (t > GarudaScene.FLY_END) {
            double k = GarudaScene.ease((t - GarudaScene.FLY_END) / 18.0);
            glide(GarudaScene.flyTo().lerp(GarudaScene.faceAt(), k));
            faceYaw(Mth.lerp((float) k, 180.0F, 360.0F));
            if (t == GarudaScene.FLY_END + 1 + GarudaMoveData.LAND_FACE_SCREECH) {
                playSound(SoundEvents.PHANTOM_AMBIENT, 4.0F, 0.5F);
                playSound(SoundEvents.ENDER_DRAGON_GROWL, 2.4F, 1.5F);
                ((ServerLevel) level()).sendParticles(FeatheredKnightEntity.gust(), getX(), getY() + 6, getZ(), 120, 3.0, 2.0, 3.0, 0);
            }
        }
        if (t >= GarudaScene.ARRIVE_END) {
            setStage(GarudaScene.NONE);
            this.entityData.set(SCENE_PLAYER, -1);
            this.entityData.set(HIDDEN, false);
            SHIELDED.remove(p.getUUID());
            freePlayer(p);
            HeroForm.sync(p);
            clearAnim();
            setMove(MOVE_NONE);
            noPhysics = false;
            bossBar.setVisible(true);
            cooldown = 30;
            p.displayClientMessage(Component.translatable("message.rudues.garuda_arrives"), true);
        }
    }

    // ------------------------------------------------------------ the fight
    @Override
    protected void registerGoals() {
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            yBodyRot = getYRot();
            yHeadRot = getYRot();
            if (!hidden() && random.nextInt(3) == 0) {
                level().addParticle(FeatheredKnightEntity.gust(), getRandomX(2.0), getY() + random.nextDouble() * 8, getRandomZ(2.0), 0, 0.03, 0);
            }
            return;
        }
        setNoGravity(true);
        fallDistance = 0;
        lastTicked = level().getGameTime();
        ServerPlayer p = foePlayer();
        if (ended) {
            tickEnded();
            return;
        }
        if (p == null || !p.isAlive() || p.level() != level()) {
            if (tickCount > 40) {
                vanish();
            }
            return;
        }
        if (getTarget() != p) {
            setTarget(p);
        }
        if (move == MOVE_SCENE) {
            setDeltaMovement(Vec3.ZERO);
            if (stage() == GarudaScene.ARRIVE) {
                tickArrive(p);
            } else {
                tickFinale(p);
            }
            return;
        }
        // the Hero of Hell form holds while she lives
        if (tickCount % 20 == 0) {
            HeroForm.forceForm(p);
        }
        // and nothing from the castle or her scenes may still hold him: he must be able to hit and be hit
        if (tickCount % 10 == 0 && freePlayer(p) && !warnedFree) {
            warnedFree = true;
            LOGGER.warn("Garuda: {} was still held by a finished scene; released", p.getGameProfile().getName());
        }
        HeroForm.sustain(p);
        if (tornadoCooldown > 0) {
            tornadoCooldown--;
        }
        if (pendingFinale && move == MOVE_NONE) {
            startFinale(p);
            return;
        }
        if (move != MOVE_NONE) {
            tickMove(p);
        } else {
            tickFlight(p);
            if (--cooldown <= 0) {
                startMove(choose(p), p);
            }
        }
    }

    private void vanish() {
        if (ball != null) {
            ball.discard();
        }
        discard();
    }

    private void glide(Vec3 to) {
        setPos(to.x, to.y, to.z);
    }

    private void faceYaw(float yaw) {
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
    }

    static float yawTo(Vec3 from, Vec3 to) {
        return (float) Math.toDegrees(Math.atan2(-(to.x - from.x), to.z - from.z));
    }

    static Vec3 forward(float yaw) {
        float r = yaw * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(r), 0, Mth.cos(r));
    }

    static Vec3 right(float yaw) {
        float r = yaw * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.cos(r), 0, -Mth.sin(r));
    }

    private double floorY(double x, double z) {
        return CraterManager.floorAt(x, z);
    }

    private double flatDist(LivingEntity e) {
        double dx = e.getX() - getX(), dz = e.getZ() - getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** between moves: hang in the air at range, circling him */
    private void tickFlight(ServerPlayer t) {
        noPhysics = false;
        Vec3 away = new Vec3(getX() - t.getX(), 0, getZ() - t.getZ());
        away = away.lengthSqr() < 1e-3 ? forward(getYRot()).scale(-1) : away.normalize();
        Vec3 side = new Vec3(-away.z, 0, away.x).scale(0.25);
        Vec3 dir = away.add(side).normalize();
        double want = 14.0;
        double gx = t.getX() + dir.x * want, gz = t.getZ() + dir.z * want;
        double r = Math.sqrt(gx * gx + gz * gz);
        if (r > CraterLayout.BOUNDARY_R - 6) {
            gx *= (CraterLayout.BOUNDARY_R - 6) / r;
            gz *= (CraterLayout.BOUNDARY_R - 6) / r;
        }
        Vec3 goal = new Vec3(gx, floorY(gx, gz) + HOVER + 0.4 * Math.sin(tickCount * 0.07), gz);
        Vec3 d = goal.subtract(position());
        double len = d.length(), sp = 0.36;
        setDeltaMovement(getDeltaMovement().scale(0.4).add((len > sp ? d.scale(sp / len) : d).scale(0.6)));
        faceYaw(Mth.approachDegrees(getYRot(), yawTo(position(), t.position()), 10.0F));
    }

    private int choose(ServerPlayer t) {
        double d = flatDist(t);
        java.util.List<Integer> pool = new java.util.ArrayList<>(java.util.List.of(MOVE_BARRAGE, MOVE_SHOT, MOVE_SCREECH));
        if (d < 20) {
            pool.add(MOVE_CLAWS);
            pool.add(MOVE_CLAWS);
        }
        if (tornadoCooldown <= 0) {
            pool.add(MOVE_TORNADOES);
        }
        pool.removeIf(m -> m == lastMove && pool.size() > 1);
        return pool.get(random.nextInt(pool.size()));
    }

    private void startMove(int m, ServerPlayer t) {
        move = m;
        lastMove = m;
        moveTick = 0;
        hit.clear();
        mFrom = position();
        moveYaw = yawTo(position(), t.position());
        faceYaw(moveYaw);
        setDeltaMovement(Vec3.ZERO);
        noPhysics = true;
        switch (m) {
            case MOVE_BARRAGE -> setAnim("barrage");
            case MOVE_CLAWS -> setAnim("fly");
            case MOVE_TORNADOES -> {
                setAnim("tornadoes");
                tornadoCooldown = 20 * 10;
            }
            case MOVE_SHOT -> setAnim("quick_shot");
            default -> setAnim("screech");
        }
    }

    private void endMove() {
        move = MOVE_NONE;
        clearAnim();
        noPhysics = false;
        cooldown = 16 + random.nextInt(22);
    }

    /** her hands (scaled model): a little ahead, out to either side, chest high */
    private Vec3 hand(boolean right) {
        float y = getYRot();
        return position().add(forward(y).scale(2.6)).add(right(y).scale(right ? 2.0 : -2.0)).add(0, 4.6, 0);
    }

    private void bolt(Vec3 from, Vec3 aim, float size, double speed, float dmg) {
        Vec3 dir = aim.subtract(from).normalize();
        level().addFreshEntity(new WindBoltEntity(level(), this, from, dir.scale(speed)).sized(size, dmg));
    }

    private void tickMove(ServerPlayer t) {
        int m = moveTick++;
        ServerLevel sl = (ServerLevel) level();
        Vec3 chest = t.position().add(0, t.getBbHeight() * 0.55, 0);
        switch (move) {
            case MOVE_BARRAGE -> {
                glide(mFrom.add(0, 0.2 * Math.sin(m * 0.2), 0));
                faceYaw(Mth.approachDegrees(getYRot(), yawTo(position(), t.position()), 6.0F));
                for (int i = 0; i < GarudaMoveData.BARRAGE_SHOTS.length; i++) {
                    if (m == (int) GarudaMoveData.BARRAGE_SHOTS[i]) {
                        Vec3 spread = new Vec3(random.nextGaussian(), random.nextGaussian() * 0.4, random.nextGaussian()).scale(1.6);
                        bolt(hand(i % 2 == 0), chest.add(t.getDeltaMovement().scale(6)).add(spread), 2.2F, 1.35, BOLT_DAMAGE);
                        playSound(SoundEvents.ENDER_DRAGON_FLAP, 1.6F, 1.5F);
                    }
                }
                if (m >= GarudaMoveData.BARRAGE_END) {
                    endMove();
                }
            }
            case MOVE_SHOT -> {
                glide(mFrom);
                faceYaw(Mth.approachDegrees(getYRot(), yawTo(position(), t.position()), 20.0F));
                if (m == GarudaMoveData.QUICK_SHOT_SHOT) {
                    bolt(hand(true), chest.add(t.getDeltaMovement().scale(8)), 2.8F, 2.3, SHOT_DAMAGE);
                    playSound(SoundEvents.TRIDENT_RIPTIDE_3, 2.0F, 1.3F);
                }
                if (m >= GarudaMoveData.QUICK_SHOT_END) {
                    endMove();
                }
            }
            case MOVE_TORNADOES -> {
                glide(mFrom.add(0, 0.2 * Math.sin(m * 0.2), 0));
                faceYaw(Mth.approachDegrees(getYRot(), yawTo(position(), t.position()), 8.0F));
                if (m == GarudaMoveData.TORNADOES_CAST) {
                    float base = yawTo(position(), t.position());
                    for (int i = -1; i <= 1; i++) {
                        Vec3 dir = forward(base + i * 24.0F);
                        Vec3 at = position().add(dir.scale(5.0));
                        at = new Vec3(at.x, floorY(at.x, at.z), at.z);
                        level().addFreshEntity(new TornadoEntity(level(), this, at).big(2.6F, 20 * 9, dir.scale(0.17), TORNADO_DAMAGE));
                    }
                    playSound(SoundEvents.ELYTRA_FLYING, 3.0F, 0.6F);
                    playSound(SoundEvents.ENDER_DRAGON_FLAP, 3.0F, 0.6F);
                }
                if (m >= GarudaMoveData.TORNADOES_END) {
                    endMove();
                }
            }
            case MOVE_CLAWS -> tickClaws(t, sl, m);
            default -> tickScreech(t, sl, m);
        }
    }

    private static final int CLAW_DASH = 14;

    private void tickClaws(ServerPlayer t, ServerLevel sl, int m) {
        if (m < CLAW_DASH) {
            Vec3 to = t.position().add(forward(yawTo(t.position(), position())).scale(5.0));
            to = new Vec3(to.x, floorY(to.x, to.z) + 1.2, to.z);
            glide(position().lerp(to, 0.22));
            faceYaw(yawTo(position(), t.position()));
            return;
        }
        int c = m - CLAW_DASH;
        if (c == 0) {
            setAnim("claws");
            mFrom = position();
        }
        glide(mFrom);
        faceYaw(Mth.approachDegrees(getYRot(), yawTo(position(), t.position()), 6.0F));
        if (c == GarudaMoveData.CLAWS_HIT1 || c == GarudaMoveData.CLAWS_HIT2) {
            swipe(t, CLAW_DAMAGE, 8.0, 0.8);
            playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 2.0F, 0.5F);
        }
        if (c == GarudaMoveData.CLAWS_KICK) {
            swipe(t, KICK_DAMAGE, 7.0, 1.6);
            playSound(SoundEvents.PLAYER_ATTACK_KNOCKBACK, 2.0F, 0.5F);
        }
        if (c >= GarudaMoveData.CLAWS_END) {
            endMove();
        }
    }

    private void swipe(LivingEntity t, float dmg, double reach, double knock) {
        Vec3 f = forward(getYRot());
        Vec3 to = t.position().subtract(position());
        double along = to.dot(f);
        if (along > -1.0 && along < reach && Math.abs(to.dot(right(getYRot()))) < reach * 0.7 && Math.abs(to.y) < 6
                && t.hurt(damageSources().mobAttack(this), dmg)) {
            t.setDeltaMovement(t.getDeltaMovement().add(f.x * knock, 0.4, f.z * knock));
            t.hurtMarked = true;
            ((ServerLevel) level()).sendParticles(ParticleTypes.SWEEP_ATTACK, t.getX(), t.getY() + 3, t.getZ(), 3, 1, 1, 1, 0);
        }
    }

    private void tickScreech(ServerPlayer t, ServerLevel sl, int m) {
        int end = GarudaMoveData.SCREECH_END;
        if (m < end) {
            glide(mFrom.add(0, 1.6 * GarudaScene.ease(m / 10.0), 0));
            faceYaw(Mth.approachDegrees(getYRot(), yawTo(position(), t.position()), 12.0F));
            if (m == GarudaMoveData.SCREECH_SCREECH) {
                playSound(SoundEvents.PHANTOM_AMBIENT, 4.0F, 0.45F);
                playSound(SoundEvents.ENDER_DRAGON_GROWL, 2.0F, 1.6F);
            }
            if (m == end - 1) {
                Vec3 aim = t.position().add(0, 1.0, 0);
                Vec3 d = aim.subtract(position());
                mDir = d.lengthSqr() < 1e-3 ? forward(getYRot()) : d.normalize();
                mLock = aim.add(mDir.scale(12));
                faceYaw(yawTo(position(), aim));
            }
            return;
        }
        int z = m - end;
        if (z == 0) {
            setAnim("zoom");
            playSound(SoundEvents.PHANTOM_SWOOP, 4.0F, 0.6F);
        }
        Vec3 next = position().add(mDir.scale(2.3));
        double r = Math.sqrt(next.x * next.x + next.z * next.z);
        boolean done = z > 22 || r > CraterLayout.BOUNDARY_R - 3 || position().distanceTo(mLock) < 2.5;
        if (!done) {
            glide(new Vec3(next.x, Math.max(next.y, floorY(next.x, next.z) + 0.6), next.z));
            sl.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 4, getZ(), 6, 1.5, 1.5, 1.5, 0.05);
            if (!hit.contains(t.getUUID()) && t.position().add(0, 3, 0).distanceTo(position().add(0, 4, 0)) < 5.0) {
                hit.add(t.getUUID());
                if (t.hurt(damageSources().mobAttack(this), ZOOM_DAMAGE)) {
                    t.setDeltaMovement(t.getDeltaMovement().add(mDir.x * 1.8, 0.7, mDir.z * 1.8));
                    t.hurtMarked = true;
                    playSound(SoundEvents.PLAYER_ATTACK_CRIT, 2.0F, 0.6F);
                }
            }
        } else {
            endMove();
        }
    }

    // ------------------------------------------------------------ the end fight
    private void startFinale(ServerPlayer p) {
        pendingFinale = false;
        move = MOVE_SCENE;
        shield(p);
        HeroForm.setClash(p, true);          // whatever move he was in the middle of stops; the scene drives the demon
        this.entityData.set(SCENE_PLAYER, p.getId());
        this.entityData.set(BALANCE, GarudaScene.START_BALANCE);
        for (net.minecraft.world.entity.projectile.Projectile pr : level().getEntitiesOfClass(net.minecraft.world.entity.projectile.Projectile.class,
                getBoundingBox().inflate(96), e -> e.getOwner() == this)) {
            pr.discard();
        }
        for (TornadoEntity tw : level().getEntitiesOfClass(TornadoEntity.class, getBoundingBox().inflate(96))) {
            tw.discard();
        }
        beginBlast(p);
    }

    /** the end fight opens on her wind blast taking his arm */
    private void beginBlast(ServerPlayer p) {
        Vec3 h = GarudaScene.heroSpot(), g = GarudaScene.herSpot();
        p.connection.teleport(h.x, h.y, h.z, GarudaScene.HERO_YAW, 0.0F);
        noPhysics = true;
        glide(g.add(0, 2.4, 0));
        faceYaw(GarudaScene.HER_YAW);
        setStage(GarudaScene.BLAST);
        setAnim("wind_blast");
        HeroForm.triggerAvatar(p, "hg_arm_blown");
        playSound(SoundEvents.PHANTOM_AMBIENT, 3.0F, 0.6F);
    }

    private void beginBall(boolean first) {
        setStage(GarudaScene.BALL);
        setAnim("windball_form");
        this.entityData.set(REGROW, first);
        if (ball != null) {
            ball.discard();
        }
        ball = new WindBallEntity(level(), GarudaScene.ballLow());
        level().addFreshEntity(ball);
        playSound(SoundEvents.ILLUSIONER_PREPARE_MIRROR, 3.0F, 0.5F);
    }

    private void beginCast(ServerPlayer p) {
        Vec3 h = GarudaScene.heroSpot(), g = GarudaScene.herSpot();
        p.connection.teleport(h.x, h.y, h.z, GarudaScene.HERO_YAW, 0.0F);
        noPhysics = true;
        glide(g.add(0, 0.8, 0));
        faceYaw(GarudaScene.HER_YAW);
        setStage(GarudaScene.CAST);
        setAnim("tornado_cast");
        this.entityData.set(REGROW, false);
        this.entityData.set(BALANCE, GarudaScene.START_BALANCE);
        playSound(SoundEvents.PHANTOM_AMBIENT, 3.0F, 0.5F);
    }

    /** the attack button during a clash */
    public void clashInput(ServerPlayer p, int presses) {
        int st = stage();
        if ((st != GarudaScene.STRAIN1 && st != GarudaScene.STRAIN2) || !p.getUUID().equals(foe) || stageT < GarudaScene.PROMPT_DELAY) {
            return;
        }
        int n = Math.min(presses, Math.max(0, 2 - pressesThisTick));
        pressesThisTick += n;
        this.entityData.set(BALANCE, Math.min(1.0F, balance() + n * GarudaScene.PRESS));
    }

    private Vec3 herHung() {
        Vec3 grab = GarudaScene.herSpot().add(0, 0, 4.4);
        return grab.add(0, 0.8, -3.5);
    }

    private Vec3 herHeld() {
        Vec3 grab = GarudaScene.herSpot().add(0, 0, 4.4);
        return grab.add(0, 0.4, -3.7);
    }

    private void tickFinale(ServerPlayer p) {
        pressesThisTick = 0;
        int st = stage();
        int t = stageT++;
        ServerLevel sl = (ServerLevel) level();
        Vec3 h = GarudaScene.heroSpot(), g = GarudaScene.herSpot();
        holdPlayer(p, h, GarudaScene.HERO_YAW);
        HeroForm.forceForm(p);
        HeroForm.sustain(p);
        Vec3 hero = GarudaScene.heroAt(st, t, balance());
        switch (st) {
            case GarudaScene.BLAST -> tickBlast(p, sl, t);
            case GarudaScene.SNATCH -> tickSnatch(p, sl, t);
            case GarudaScene.CAST -> {
                glide(g.add(0, 0.8 + 0.1 * Math.sin(t * 0.2), 0));
                if (t == GarudaMoveData.TORNADO_CAST_CAST) {
                    HeroForm.triggerAvatar(p, "hg_launched");
                    sl.sendParticles(ParticleTypes.CLOUD, h.x, h.y + 1, h.z, 120, 2.5, 1.0, 2.5, 0.3);
                    sl.sendParticles(FeatheredKnightEntity.gust(), h.x, h.y + 4, h.z, 200, 3.0, 5.0, 3.0, 0);
                    playSound(SoundEvents.ELYTRA_FLYING, 4.0F, 0.5F);
                    playSound(SoundEvents.TRIDENT_RIPTIDE_3, 3.0F, 0.6F);
                }
                if (t > GarudaMoveData.TORNADO_CAST_CAST && t % 2 == 0 && hero != null) {
                    sl.sendParticles(FeatheredKnightEntity.gust(), hero.x, hero.y - 2, hero.z, 30, 2.0, 3.0, 2.0, 0);
                }
                if (t >= GarudaScene.length(st)) {
                    beginBall(false);
                }
            }
            case GarudaScene.BALL -> {
                glide(g.add(0, 0.3, 0));
                faceYaw(GarudaScene.HER_YAW);
                int shoot = GarudaMoveData.WINDBALL_FORM_SHOOT;
                if (regrowing() && hero != null) {
                    tickRegrow(p, sl, t, hero);
                }
                if (t > 6 && t < shoot && t % 2 == 0) {
                    // the wind drawn in from all round into the ball
                    Vec3 c = GarudaScene.ballLow();
                    double a = random.nextDouble() * Math.PI * 2, r = 5 + random.nextDouble() * 4;
                    sl.sendParticles(FeatheredKnightEntity.gust(), c.x + Math.cos(a) * r, c.y - 2 + random.nextDouble() * 4, c.z + Math.sin(a) * r,
                            0, -Math.cos(a), 0.05, -Math.sin(a), 0.6);
                    sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, getX(), getY() + 4, getZ(), 2, 2.5, 2.0, 2.5, 0.0);
                }
                if (ball != null) {
                    float grow = Mth.clamp(t / (float) shoot, 0, 1);
                    ball.setRadius(0.5F + 3.2F * grow);
                    double k = GarudaScene.ease((t - shoot) / 8.0);
                    ball.place(GarudaScene.ballLow().lerp(GarudaScene.ballStrain(GarudaScene.START_BALANCE), k));
                }
                if (t == shoot) {
                    playSound(SoundEvents.TRIDENT_RIPTIDE_3, 4.0F, 0.5F);
                }
                if (t >= GarudaScene.length(st)) {
                    setStage(GarudaScene.STRAIN1);
                    setAnim("windball_strain");
                    HeroForm.triggerAvatar(p, "hg_push");
                    playSound(SoundEvents.GENERIC_EXPLODE, 2.0F, 0.6F);
                }
            }
            case GarudaScene.STRAIN1 -> {
                glide(g.add(0, 0.3, 0));
                if (t >= GarudaScene.PROMPT_DELAY) {
                    this.entityData.set(BALANCE, balance() - GarudaScene.decay(t));
                }
                float b = balance();
                if (ball != null) {
                    ball.place(GarudaScene.ballStrain(b).add(0.08 * Math.sin(t * 2.1), 0.08 * Math.sin(t * 2.7), 0));
                    // his push sets it alight: the longer and harder, the more it burns
                    ball.setFire(Math.max(ball.fire(), Mth.clamp((t - 10) / 120.0F + (b - 0.5F) * 1.2F, 0.0F, 1.0F)));
                }
                if (t % 8 == 0) {
                    playSound(SoundEvents.FIRECHARGE_USE, 1.4F, 0.5F);
                    playSound(SoundEvents.ELYTRA_FLYING, 1.0F, 0.6F);
                }
                if (b >= 1.0F || (t >= GarudaScene.STRAIN_MAX && b >= 0.5F)) {
                    setStage(GarudaScene.WIN1);
                    HeroForm.triggerAvatar(p, "hg_push_win");
                    if (ball != null) {
                        ball.setFire(1.0F);
                    }
                } else if (b <= 0.0F || t >= GarudaScene.STRAIN_MAX) {
                    setStage(GarudaScene.LOSE1);
                    HeroForm.triggerAvatar(p, "hg_push_lose");
                }
            }
            case GarudaScene.WIN1 -> {
                if (ball != null && t <= 8) {
                    ball.place(GarudaScene.ballStrain(1.0F).lerp(g.add(0, 2.5, 0), t / 8.0));
                }
                if (t == 8) {
                    Vec3 c = g.add(0, 2.5, 0);
                    if (ball != null) {
                        ball.discard();
                        ball = null;
                    }
                    Network.sendBoom(sl, c.x, c.y, c.z, g.y, 2.4F);
                    sl.sendParticles(ModParticles.PURPLE_FLAME_BIG.get(), c.x, c.y, c.z, 200, 3.5, 2.5, 3.5, 0.25);
                    playSound(SoundEvents.GENERIC_EXPLODE, 4.0F, 0.5F);
                    playSound(SoundEvents.PHANTOM_DEATH, 3.0F, 0.5F);
                    setAnim("windball_blast");
                }
                glide(g);
                if (t >= GarudaScene.length(st)) {
                    setStage(GarudaScene.GRAB);
                    setAnim("lie");
                    HeroForm.triggerAvatar(p, "hg_neck_grab");
                }
            }
            case GarudaScene.LOSE1 -> {
                if (t == 4) {
                    Vec3 c = ball != null ? ball.position() : h.add(0, 8, 0);
                    if (ball != null) {
                        ball.discard();
                        ball = null;
                    }
                    Network.sendBoom(sl, c.x, c.y, c.z, h.y, 1.8F);
                    sl.sendParticles(ParticleTypes.CLOUD, c.x, c.y, c.z, 120, 3, 3, 3, 0.3);
                    playSound(SoundEvents.GENERIC_EXPLODE, 3.0F, 0.8F);
                    SHIELDED.put(p.getUUID(), true);
                    p.invulnerableTime = 0;
                    p.hurt(damageSources().mobAttack(this), p.getMaxHealth() * 0.25F);
                    SHIELDED.put(p.getUUID(), false);
                }
                glide(g.add(0, 0.6, 0));
                if (t == 10) {
                    setAnim("idle");
                }
                if (t >= GarudaScene.length(st) && p.isAlive()) {
                    beginCast(p);
                }
            }
            case GarudaScene.GRAB -> {
                if (t < 11) {
                    glide(g);
                } else {
                    double k = GarudaScene.ease((t - 11) / 12.0);
                    glide(g.lerp(herHung(), k));
                    faceYaw(GarudaScene.HER_YAW);
                    if (t == 11) {
                        setAnim("neck_held");
                        playSound(SoundEvents.PHANTOM_HURT, 2.0F, 0.5F);
                    }
                }
                if (t >= GarudaScene.length(st)) {
                    setStage(GarudaScene.PUNCH);
                    HeroForm.triggerAvatar(p, "hg_neck_punch");
                }
            }
            case GarudaScene.PUNCH -> {
                int ph = t % 12;
                double jolt = ph >= 3 && ph < 7 ? 0.35 * Math.sin((ph - 3) / 4.0 * Math.PI) : 0;
                glide(herHung().add(0, 0, -jolt));
                if (ph == 3) {
                    Vec3 c = herHung().add(0, 4.2, 0);
                    sl.sendParticles(ModParticles.PURPLE_FLAME_BIG.get(), c.x, c.y, c.z, 40, 0.8, 0.8, 0.8, 0.12);
                    sl.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y, c.z, 1, 0, 0, 0, 0);
                    playSound(SoundEvents.PLAYER_ATTACK_CRIT, 2.0F, 0.6F);
                    playSound(SoundEvents.BLAZE_SHOOT, 1.5F, 0.6F);
                    playSound(SoundEvents.PHANTOM_HURT, 1.6F, 0.5F + random.nextFloat() * 0.2F);
                }
                if (t >= GarudaScene.length(st)) {
                    setStage(GarudaScene.KICK);
                    setAnim("neck_kick");
                    HeroForm.triggerAvatar(p, "hg_kicked");
                }
            }
            case GarudaScene.KICK -> {
                Vec3 from = herHung(), free = herHung().add(0, 1.4, -0.8);
                if (t < GarudaMoveData.NECK_KICK_KICK) {
                    glide(from);
                } else if (t < 20) {
                    glide(from.lerp(free, GarudaScene.ease((t - GarudaMoveData.NECK_KICK_KICK) / 6.0)));
                } else {
                    glide(free.lerp(herHeld(), GarudaScene.ease((t - 20) / 6.0)));
                }
                if (t == GarudaMoveData.NECK_KICK_KICK) {
                    playSound(SoundEvents.PLAYER_ATTACK_KNOCKBACK, 2.4F, 0.5F);
                    sl.sendParticles(ParticleTypes.CLOUD, from.x, from.y + 2, from.z + 2, 40, 1, 1, 1, 0.2);
                }
                if (t == 20) {
                    setAnim("arms_held");
                    playSound(SoundEvents.PHANTOM_AMBIENT, 3.0F, 0.6F);
                }
                if (t >= GarudaScene.length(st)) {
                    setStage(GarudaScene.STRAIN2);
                    HeroForm.triggerAvatar(p, "hg_burn");
                    this.entityData.set(BALANCE, GarudaScene.START_BALANCE);
                }
            }
            case GarudaScene.STRAIN2 -> {
                glide(herHeld().add((random.nextDouble() - 0.5) * 0.12, (random.nextDouble() - 0.5) * 0.12, 0));
                Vec3 c = herHeld().add(0, 4, 0);
                sl.sendParticles(ModParticles.PURPLE_FLAME.get(), c.x, c.y, c.z, 10, 1.2, 2.0, 1.2, 0.05);
                if (t % 3 == 0) {
                    sl.sendParticles(ModParticles.PURPLE_FLAME_BIG.get(), c.x, c.y + 1, c.z, 6, 1.4, 1.6, 1.4, 0.08);
                }
                if (t % 10 == 0) {
                    playSound(SoundEvents.BLAZE_BURN, 2.0F, 0.6F);
                    playSound(SoundEvents.PHANTOM_HURT, 1.8F, 0.5F);
                }
                if (t >= GarudaScene.PROMPT_DELAY) {
                    this.entityData.set(BALANCE, balance() - GarudaScene.decay(t));
                }
                float b = balance();
                if (b >= 1.0F || (t >= GarudaScene.STRAIN_MAX && b >= 0.5F)) {
                    setStage(GarudaScene.HELL);
                    HeroForm.triggerAvatar(p, "hg_hellfire");
                    mFrom = position();
                } else if (b <= 0.0F || t >= GarudaScene.STRAIN_MAX) {
                    setStage(GarudaScene.LOSE2);
                    setAnim("neck_kick");
                    HeroForm.triggerAvatar(p, "hg_burn_lose");
                }
            }
            case GarudaScene.LOSE2 -> {
                glide(herHeld().lerp(g.add(0, 1.2, 0), GarudaScene.ease(t / 20.0)));
                if (t == GarudaMoveData.NECK_KICK_KICK) {
                    SHIELDED.put(p.getUUID(), true);
                    p.invulnerableTime = 0;
                    p.hurt(damageSources().mobAttack(this), p.getMaxHealth() * 0.15F);
                    SHIELDED.put(p.getUUID(), false);
                    playSound(SoundEvents.PLAYER_ATTACK_KNOCKBACK, 2.4F, 0.5F);
                }
                if (t == 20) {
                    setAnim("idle");
                }
                if (t >= GarudaScene.length(st)) {
                    setStage(GarudaScene.GRAB);
                    setAnim("idle");
                    HeroForm.triggerAvatar(p, "hg_neck_grab");
                }
            }
            case GarudaScene.HELL -> tickHell(p, sl, t);
            default -> {
            }
        }
    }

    // ------------------------------------------------------------ the opening: his arm, her talons, the throw
    private void tickBlast(ServerPlayer p, ServerLevel sl, int t) {
        Vec3 g = GarudaScene.herSpot();
        glide(g.add(0, 2.4 + 0.12 * Math.sin(t * 0.2), 0));
        faceYaw(GarudaScene.HER_YAW);
        Vec3 hand = GarudaScene.blastHand(), stump = GarudaScene.blastStump();
        int fire = GarudaMoveData.WIND_BLAST_FIRE, impact = GarudaMoveData.WIND_BLAST_IMPACT;
        if (t == 2) {
            playSound(SoundEvents.ELYTRA_FLYING, 2.0F, 0.4F);
        }
        if (t > 4 && t < fire) {
            // the wind knots in her hand
            double a = random.nextDouble() * Math.PI * 2, r = 2.0 + random.nextDouble() * 1.5;
            sl.sendParticles(FeatheredKnightEntity.gust(), hand.x + Math.cos(a) * r, hand.y + (random.nextDouble() - 0.5) * 2, hand.z + Math.sin(a) * r,
                    0, -Math.cos(a), 0.0, -Math.sin(a), 0.4);
            sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, hand.x, hand.y, hand.z, 2, 0.4, 0.4, 0.4, 0.0);
        }
        if (t == fire) {
            blastOrb = new WindBallEntity(level(), hand);
            blastOrb.setRadius(0.9F);
            level().addFreshEntity(blastOrb);
            playSound(SoundEvents.TRIDENT_RIPTIDE_3, 4.0F, 0.8F);
            playSound(SoundEvents.ENDER_DRAGON_SHOOT, 2.0F, 1.4F);
        }
        if (blastOrb != null && t > fire && t <= impact) {
            double k = (t - fire) / (double) (impact - fire);
            Vec3 at = hand.lerp(stump, k);
            blastOrb.place(at);
            sl.sendParticles(ParticleTypes.CLOUD, at.x, at.y, at.z, 4, 0.3, 0.3, 0.3, 0.02);
        }
        if (t == impact) {
            if (blastOrb != null) {
                blastOrb.discard();
                blastOrb = null;
            }
            sl.sendParticles(ParticleTypes.EXPLOSION, stump.x, stump.y, stump.z, 3, 0.4, 0.4, 0.4, 0);
            sl.sendParticles(FeatheredKnightEntity.gust(), stump.x, stump.y, stump.z, 60, 1.2, 1.2, 1.2, 0.0);
            sl.sendParticles(ICHOR, stump.x, stump.y, stump.z, 60, 0.6, 0.6, 0.6, 0.0);
            sl.sendParticles(ModParticles.PURPLE_FLAME_BIG.get(), stump.x, stump.y, stump.z, 40, 0.5, 0.5, 0.5, 0.12);
            playSound(SoundEvents.GENERIC_EXPLODE, 3.0F, 1.2F);
            playSound(SoundEvents.PLAYER_ATTACK_CRIT, 3.0F, 0.5F);
            p.serverLevel().playSound(null, stump.x, stump.y, stump.z, SoundEvents.RAVAGER_HURT, net.minecraft.sounds.SoundSource.PLAYERS, 3.0F, 0.5F);
        }
        if (t > impact && t % 2 == 0) {
            Vec3 s = stump.add(0, 0, 0.9 * Math.min(1.0, (t - impact) / 12.0));
            sl.sendParticles(ICHOR, s.x, s.y, s.z, 3, 0.15, 0.1, 0.15, 0.0);
            sl.sendParticles(ModParticles.PURPLE_FLAME.get(), s.x, s.y, s.z, 2, 0.1, 0.1, 0.1, 0.02);
        }
        if (t == 49) {
            p.serverLevel().playSound(null, stump.x, stump.y, stump.z, SoundEvents.RAVAGER_ROAR, net.minecraft.sounds.SoundSource.PLAYERS, 3.0F, 0.6F);
            p.serverLevel().playSound(null, stump.x, stump.y, stump.z, SoundEvents.ENDER_DRAGON_GROWL, net.minecraft.sounds.SoundSource.PLAYERS, 2.0F, 0.7F);
        }
        if (t >= GarudaScene.length(GarudaScene.BLAST)) {
            setStage(GarudaScene.SNATCH);
            setAnim("snatch");
            HeroForm.triggerAvatar(p, "hg_snatched");
            playSound(SoundEvents.PHANTOM_AMBIENT, 3.0F, 0.5F);
        }
    }

    private void tickSnatch(ServerPlayer p, ServerLevel sl, int t) {
        glide(GarudaScene.herSnatch(t));
        faceYaw(GarudaScene.HER_YAW);
        Vec3 hero = GarudaScene.heroAt(GarudaScene.SNATCH, t, balance());
        int grab = GarudaMoveData.SNATCH_GRAB, rel = GarudaMoveData.SNATCH_RELEASE;
        if (t == 2) {
            playSound(SoundEvents.PHANTOM_SWOOP, 4.0F, 0.5F);
        }
        if (t < grab) {
            sl.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 3, getZ(), 4, 1.0, 1.0, 1.0, 0.05);
        }
        if (t == grab) {
            Vec3 c = position().add(0, 2.5, 1.5);
            playSound(SoundEvents.PLAYER_ATTACK_KNOCKBACK, 3.0F, 0.5F);
            playSound(SoundEvents.PHANTOM_BITE, 3.0F, 0.6F);
            sl.sendParticles(FeatheredKnightEntity.gust(), c.x, c.y, c.z, 50, 1.2, 1.2, 1.2, 0.0);
            sl.sendParticles(ICHOR, c.x, c.y, c.z, 20, 0.5, 0.5, 0.5, 0.0);
        }
        boolean carrying = (t > grab && t < GarudaMoveData.SNATCH_SLAM1 - 6) || (t > GarudaMoveData.SNATCH_LIFT2 && t < GarudaMoveData.SNATCH_SLAM2 - 6)
                || (t > 104 && t < rel + 4);
        if (carrying && t % 11 == 0) {
            playSound(SoundEvents.ENDER_DRAGON_FLAP, 3.0F, 0.6F);
            sl.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 1, getZ(), 30, 3.0, 0.6, 3.0, 0.08);
        }
        if (t == GarudaMoveData.SNATCH_SLAM1 || t == GarudaMoveData.SNATCH_SLAM2) {
            Vec3 c = new Vec3(getX(), GarudaScene.heroSpot().y, getZ() + 1.0);
            Network.sendBoom(sl, c.x, c.y + 0.5, c.z, c.y, 1.6F);
            sl.sendParticles(RUBBLE, c.x, c.y + 0.4, c.z, 140, 2.6, 0.4, 2.6, 0.3);
            sl.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y + 0.5, c.z, 4, 1.5, 0.3, 1.5, 0);
            sl.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, c.x, c.y + 0.5, c.z, 30, 3.0, 0.3, 3.0, 0.02);
            playSound(SoundEvents.GENERIC_EXPLODE, 4.0F, 0.6F);
            playSound(SoundEvents.IRON_GOLEM_DAMAGE, 3.0F, 0.5F);
            p.serverLevel().playSound(null, c.x, c.y, c.z, SoundEvents.RAVAGER_HURT, net.minecraft.sounds.SoundSource.PLAYERS, 2.4F, 0.5F);
        }
        if (t == GarudaMoveData.SNATCH_SCREAM) {
            playSound(SoundEvents.PHANTOM_AMBIENT, 5.0F, 0.4F);
            playSound(SoundEvents.ENDER_DRAGON_GROWL, 2.6F, 1.4F);
            sl.sendParticles(FeatheredKnightEntity.gust(), getX(), getY() + 6, getZ(), 120, 3.0, 2.0, 3.0, 0);
        }
        if (t == rel) {
            playSound(SoundEvents.ENDER_DRAGON_FLAP, 5.0F, 0.5F);
            playSound(SoundEvents.TRIDENT_RIPTIDE_3, 3.0F, 0.5F);
            sl.sendParticles(FeatheredKnightEntity.gust(), getX(), getY() + 3, getZ(), 150, 3.0, 3.0, 3.0, 0);
        }
        if (t > rel && hero != null && t % 2 == 0) {
            sl.sendParticles(ParticleTypes.CLOUD, hero.x, hero.y + 2, hero.z, 6, 1.0, 1.5, 1.0, 0.05);
        }
        if (t % 3 == 0) {
            Vec3 s = GarudaScene.stumpSnatch(t);
            sl.sendParticles(ICHOR, s.x, s.y, s.z, 2, 0.1, 0.1, 0.1, 0.0);
            sl.sendParticles(ParticleTypes.SMOKE, s.x, s.y, s.z, 1, 0.1, 0.1, 0.1, 0.01);
        }
        if (t >= GarudaScene.length(GarudaScene.SNATCH)) {
            beginBall(true);
            HeroForm.triggerAvatar(p, "hg_regrow");
        }
    }

    /** the arm burns back out of the stump, then his roar */
    private void tickRegrow(ServerPlayer p, ServerLevel sl, int t, Vec3 hero) {
        Vec3 arm = GarudaScene.regrowArm(hero, t);
        int g0 = GarudaMoveData.REGROW_GROW0, g1 = GarudaMoveData.REGROW_GROW1;
        if (t < g0 && t % 3 == 0) {
            sl.sendParticles(ParticleTypes.SMOKE, arm.x, arm.y, arm.z, 2, 0.1, 0.1, 0.1, 0.01);
            sl.sendParticles(ICHOR, arm.x, arm.y, arm.z, 1, 0.1, 0.1, 0.1, 0.0);
        }
        if (t == g0) {
            p.serverLevel().playSound(null, arm.x, arm.y, arm.z, SoundEvents.BLAZE_SHOOT, net.minecraft.sounds.SoundSource.PLAYERS, 3.0F, 0.5F);
            p.serverLevel().playSound(null, arm.x, arm.y, arm.z, SoundEvents.FIRECHARGE_USE, net.minecraft.sounds.SoundSource.PLAYERS, 3.0F, 0.5F);
        }
        if (t >= g0 && t <= g1 + 4) {
            sl.sendParticles(ModParticles.PURPLE_FLAME_BIG.get(), arm.x, arm.y, arm.z, 8, 0.5, 0.5, 0.5, 0.06);
            sl.sendParticles(ModParticles.PURPLE_FLAME.get(), arm.x, arm.y, arm.z, 6, 0.8, 0.8, 0.8, 0.04);
            if (t % 6 == 0) {
                p.serverLevel().playSound(null, arm.x, arm.y, arm.z, SoundEvents.BLAZE_BURN, net.minecraft.sounds.SoundSource.PLAYERS, 2.0F, 0.6F);
            }
        }
        if (t == g1 + 6) {
            Vec3 c = hero.add(0, 5, 0);
            p.serverLevel().playSound(null, c.x, c.y, c.z, SoundEvents.RAVAGER_ROAR, net.minecraft.sounds.SoundSource.PLAYERS, 4.0F, 0.55F);
            p.serverLevel().playSound(null, c.x, c.y, c.z, SoundEvents.ENDER_DRAGON_GROWL, net.minecraft.sounds.SoundSource.PLAYERS, 2.4F, 0.6F);
            sl.sendParticles(ModParticles.PURPLE_FLAME_BIG.get(), c.x, c.y, c.z, 120, 2.0, 2.0, 2.0, 0.15);
        }
    }

    private void tickHell(ServerPlayer p, ServerLevel sl, int t) {
        Vec3 from = herHeld(), land = GarudaScene.herSpot().add(0, 0, -26);
        land = new Vec3(land.x, floorY(land.x, land.z), land.z);
        if (t == 3) {
            Vec3 c = from.add(0, 4, 0);
            Network.sendBoom(sl, c.x, c.y, c.z, GarudaScene.heroSpot().y, 3.0F);
            sl.sendParticles(ModParticles.PURPLE_FLAME_BIG.get(), c.x, c.y, c.z, 400, 5, 4, 5, 0.35);
            sl.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y, c.z, 3, 2, 2, 2, 0);
            playSound(SoundEvents.GENERIC_EXPLODE, 5.0F, 0.4F);
            playSound(SoundEvents.ENDER_DRAGON_GROWL, 3.0F, 0.5F);
            playSound(SoundEvents.PHANTOM_DEATH, 3.0F, 0.4F);
            this.entityData.set(BURNT, true);
            setAnim("blown_away");
        }
        if (t >= 3) {
            int landT = GarudaMoveData.BLOWN_AWAY_LAND;
            double k = Mth.clamp((t - 3) / (double) landT, 0, 1);
            glide(from.lerp(land, 1 - (1 - k) * (1 - k)).add(0, 9.0 * Math.sin(Math.PI * k), 0));
            if (t - 3 == landT) {
                playSound(SoundEvents.NETHERITE_BLOCK_FALL, 3.0F, 0.5F);
                sl.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, land.x, land.y + 1, land.z, 40, 3, 0.5, 3, 0.02);
            }
            if (t % 2 == 0) {
                sl.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 3, getZ(), 6, 1.5, 1.5, 1.5, 0.02);
            }
        } else {
            glide(from);
        }
        if (t >= GarudaScene.length(GarudaScene.HELL)) {
            finish(p, sl);
        }
    }

    /** won: the sword stands in the crater's heart to take you home */
    private void finish(ServerPlayer p, ServerLevel sl) {
        setStage(GarudaScene.NONE);
        this.entityData.set(SCENE_PLAYER, -1);
        SHIELDED.remove(p.getUUID());
        HeroForm.setClash(p, false);
        ended = true;
        move = MOVE_NONE;
        BlockPos c = CraterManager.centre();
        sl.setBlock(c, ModBlocks.EMBEDDED_SWORD.get().defaultBlockState(), Block.UPDATE_ALL);
        sl.sendParticles(ModParticles.PURPLE_FLAME_BIG.get(), c.getX() + 0.5, c.getY() + 1, c.getZ() + 0.5, 80, 0.5, 1.5, 0.5, 0.05);
        p.displayClientMessage(Component.translatable("message.rudues.garuda_defeated"), true);
        setLastHurtByPlayer(p);
        setLastHurtByMob(p);
        hurtTime = 0;
        setHealth(0.0F);
        die(damageSources().playerAttack(p));
    }

    private void tickEnded() {
        setDeltaMovement(Vec3.ZERO);
    }

    // ------------------------------------------------------------ damage
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (move == MOVE_SCENE || hidden()) {
            return source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && super.hurt(source, amount);
        }
        if (source.is(DamageTypeTags.IS_FALL)) {
            return false;
        }
        if (!level().isClientSide && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            float floor = getMaxHealth() * FINALE_AT;
            if (getHealth() - amount <= floor) {
                amount = Math.max(0.0F, getHealth() - floor - 0.01F);
                pendingFinale = true;
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
    }

    @Override
    public boolean isPushable() {
        return false;
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
            if (foe != null) {
                SHIELDED.remove(foe);
                if (level().getServer() != null && level().getServer().getPlayerList().getPlayer(foe) instanceof ServerPlayer sp) {
                    HeroForm.setClash(sp, false);
                }
            }
            if (ball != null) {
                ball.discard();
            }
            if (blastOrb != null) {
                blastOrb.discard();
            }
        }
        super.remove(reason);
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return super.getBoundingBoxForCulling().inflate(9.0, 6.0, 9.0);
    }

    // ------------------------------------------------------------ GeckoLib
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 4, state -> {
            int a = this.entityData.get(ANIM);
            if (a >= 0 && a < RAW.length) {
                int st = this.entityData.get(ANIM_START);
                if (st != seenAnimStart) {
                    seenAnimStart = st;
                    state.getController().forceAnimationReset();
                }
                return state.setAndContinue(RAW[a]);
            }
            double v = Math.sqrt((getX() - xo) * (getX() - xo) + (getZ() - zo) * (getZ() - zo)) * 20.0;
            return state.setAndContinue(v > 5.0 ? FLY : IDLE);
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    // ------------------------------------------------------------ misc
    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.PHANTOM_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.PHANTOM_DEATH;
    }

    @Override
    public float getVoicePitch() {
        return 0.5F;
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
        if (foe != null) {
            tag.putUUID("Foe", foe);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("Foe")) {
            foe = tag.getUUID("Foe");
        }
    }
}
