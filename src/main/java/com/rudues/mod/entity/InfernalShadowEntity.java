package com.rudues.mod.entity;

import com.rudues.mod.registry.ModParticles;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import com.rudues.mod.world.ArenaLayout;
import com.rudues.mod.world.ModDimensions;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The Infernal Shadow: a dark copy of the chosen player in netherite, carrying a netherite sword.
 *
 * Move 1, "Hellscrape": it sprints at its target dragging the sword tip along the floor (sparks, scraping), brakes
 * into a coil and tears the blade upward in a rising slash that launches whatever it hits. The run steers until the
 * brake and its speed is adjusted so the slash lands at the target's feet. Gameplay timings and the blade-tip path
 * come from {@link ShadowMoveData}, baked from the animation.
 *
 * Move 2, "Hammer and Anvil": it leaps and vanishes, re-forms high above its target and a red circle appears under
 * the target. The circle follows them for the first second, then locks; two seconds after it appeared the Shadow
 * crashes down and drives its fist into the centre (full damage inside the circle, a lighter blast just outside).
 * While it hovers it cannot be hit. The entity itself waits on the ground at the circle and is drawn raised by the
 * animation, so the drop lands exactly on the frame the fist hits. Timings: {@link HammerMoveData}.
 *
 * Assets: geo/entity/infernal_shadow.geo.json, animations/entity/infernal_shadow.animation.json,
 * textures/entity/infernal_shadow.png (fallback skin; normally it wears the copied player's skin).
 */
public class InfernalShadowEntity extends Monster implements GeoEntity {
    private static final EntityDataAccessor<Optional<UUID>> COPY_OF =
            SynchedEntityData.defineId(InfernalShadowEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    /** the move being played (MOVE_*): the animation runs at its authored speed and the walk loop is not used */
    private static final EntityDataAccessor<Integer> MOVE =
            SynchedEntityData.defineId(InfernalShadowEntity.class, EntityDataSerializers.INT);
    /** game time the current move started (clients time the telegraph circle from it) */
    private static final EntityDataAccessor<Integer> MOVE_START =
            SynchedEntityData.defineId(InfernalShadowEntity.class, EntityDataSerializers.INT);
    public static final int MOVE_NONE = 0;
    public static final int MOVE_HELLSCRAPE = 1;
    public static final int MOVE_HAMMER = 2;
    public static final int MOVE_COMBO = 3;
    public static final int MOVE_VORTEX = 4;
    public static final int MOVE_FIREBALL = 5;
    public static final int MOVE_CRESCENT = 6;
    /** at half health: the corruption tears out through its armour (left arm, both legs) and it roars */
    public static final int MOVE_PHASE_SHIFT = 7;
    /** 1, or 2 once the corruption has taken it */
    private static final EntityDataAccessor<Integer> PHASE =
            SynchedEntityData.defineId(InfernalShadowEntity.class, EntityDataSerializers.INT);

    private static final RawAnimation READY = RawAnimation.begin().thenLoop("animation.infernal_shadow.ready");
    private static final RawAnimation STALK = RawAnimation.begin().thenLoop("animation.infernal_shadow.stalk");
    private static final RawAnimation HELLSCRAPE = RawAnimation.begin().thenPlay("animation.infernal_shadow.hellscrape");
    private static final RawAnimation HAMMER = RawAnimation.begin().thenPlay("animation.infernal_shadow.hammer_anvil");
    private static final RawAnimation COMBO = RawAnimation.begin().thenPlay("animation.infernal_shadow.combo");
    private static final RawAnimation VORTEX = RawAnimation.begin().thenPlay("animation.infernal_shadow.vortex_start")
            .thenLoop("animation.infernal_shadow.vortex_loop");
    private static final RawAnimation VORTEX_END = RawAnimation.begin().thenPlay("animation.infernal_shadow.vortex_end");
    private static final RawAnimation FIREBALL = RawAnimation.begin().thenPlay("animation.infernal_shadow.fireball_volley");
    private static final RawAnimation CRESCENT = RawAnimation.begin().thenPlay("animation.infernal_shadow.crescent");
    private static final RawAnimation PHASE_SHIFT = RawAnimation.begin().thenPlay("animation.infernal_shadow.phase_shift");

    public static final float HELLSCRAPE_DAMAGE = 12.0F;
    public static final float HAMMER_DAMAGE = 16.0F;
    public static final float HAMMER_OUTER_DAMAGE = 5.0F;
    /** Hammer and Anvil reaches this far (blocks) */
    private static final double HAMMER_RANGE = 22.0;
    public static final float COMBO_SLASH_DAMAGE = 9.0F;
    public static final float COMBO_PUNCH_DAMAGE = 13.0F;
    private static final double COMBO_RANGE = 3.2;
    /** the tornado burns this much every half second while you are inside it */
    public static final float VORTEX_DAMAGE = 3.0F;
    /** the Inferno Vortex can't come back for 40 seconds */
    private static final int VORTEX_COOLDOWN = 20 * 40;
    public static final float CRESCENT_DAMAGE = 14.0F;
    private static final double CRESCENT_RANGE = 4.5;
    private static final double FIREBALL_RANGE = 18.0;
    /** Hellscrape and Hammer and Anvil are only used on a target this far away (or one that runs off) */
    private static final double FAR_RANGE = 8.0;
    /** where the slash should land, measured from the target (blocks) */
    private static final double STOP_SHORT = 1.9;
    // trigger range: keeps the sprint between ~0.75x and ~1.45x of the speed the legs are animated for
    private static final double MIN_RANGE = 7.5;
    private static final double MAX_RANGE = 12.5;
    /** phase 2: everything it deals is this much harder, it is quicker on its feet and between its quick moves */
    private static final float PHASE_TWO_POWER = 1.2F;
    private static final double PHASE_TWO_SPEED = 0.15;
    private static final UUID PHASE_SPEED_ID = UUID.fromString("5d0c3c8e-6a39-4f7e-9d0b-2f61a7c4e2b1");
    public static final float SHOCKWAVE_DAMAGE = 4.0F;
    public static final double SHOCKWAVE_RADIUS = 7.0;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final ServerBossEvent bossBar = new ServerBossEvent(
            net.minecraft.network.chat.Component.translatable("entity.rudues.infernal_shadow"),
            BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_10);

    /** Hellscrape state: tick into the move (-1 = not performing) */
    private int moveTick = -1;
    private float moveYaw;
    private double speedScale = 1.0;
    private int cooldown = 30;
    private final Set<UUID> hitThisMove = new HashSet<>();
    /** the move chosen for when the cooldown is over (MOVE_*), how long it has waited to get into range, the last one */
    private int plannedMove = MOVE_NONE;
    private int planTicks;
    private int lastMove = MOVE_NONE;
    /** Hammer and Anvil: tick into the move (-1 = not performing), where the fist will land */
    private int hammerTick = -1;
    private Vec3 hammerCenter = Vec3.ZERO;
    /** the combo, the fireball volley and the crescent slash */
    private int comboTick = -1;
    private int fireTick = -1;
    private double takeoffY;
    private int crescentTick = -1;
    /** the phase shift: waiting for the current move to end, tick into the shift */
    private boolean pendingShift;
    private int shiftTick = -1;
    /** Inferno Vortex */
    private int vortexTick = -1;
    private int vortexCooldown = 20 * 15;
    private int nextGeyser;
    private Vec3 vortexCenter = Vec3.ZERO;
    @Nullable
    private Vec3 homePos;

    public InfernalShadowEntity(EntityType<? extends InfernalShadowEntity> type, Level level) {
        super(type, level);
        this.xpReward = 50;
        this.setMaxUpStep(1.0F);
        this.setPersistenceRequired();
        equipNetherite();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 300.0D)
                .add(Attributes.ATTACK_DAMAGE, 10.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.27D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.8D)
                .add(Attributes.FOLLOW_RANGE, 48.0D);
    }

    private void equipNetherite() {
        setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.NETHERITE_HELMET));
        setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.NETHERITE_CHESTPLATE));
        setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.NETHERITE_LEGGINGS));
        setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.NETHERITE_BOOTS));
        setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.NETHERITE_SWORD));
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            setDropChance(slot, 0.0F);
        }
    }

    // ------------------------------------------------------------ who it copies
    public void setCopyOf(@Nullable Player player) {
        this.entityData.set(COPY_OF, player == null ? Optional.empty() : Optional.of(player.getUUID()));
        if (player != null) {
            setCustomName(net.minecraft.network.chat.Component.translatable("entity.rudues.infernal_shadow.of",
                    player.getDisplayName()));
            bossBar.setName(getDisplayName());
        }
    }

    @Nullable
    public UUID getCopyOf() {
        return this.entityData.get(COPY_OF).orElse(null);
    }

    public boolean isInMove() {
        return getMove() != MOVE_NONE;
    }

    public int getMove() {
        return this.entityData.get(MOVE);
    }

    /** ticks since the current move began (with the partial tick, for rendering) */
    public float moveTime(float partialTick) {
        return (float) (level().getGameTime() - this.entityData.get(MOVE_START)) + partialTick;
    }

    private void setMove(int move) {
        this.entityData.set(MOVE, move);
        this.entityData.set(MOVE_START, (int) level().getGameTime());
    }

    /** Hammer and Anvil, between the teleport and the impact: it is up in the air (only drawn there) */
    public boolean isAirborne() {
        if (getMove() != MOVE_HAMMER) {
            return false;
        }
        float t = moveTime(0);
        return t >= HammerMoveData.TELEPORT_TICK && t < HammerMoveData.IMPACT_TICK;
    }

    /** how high the body is drawn above the entity's feet right now (blocks) */
    public double visualLift(float partialTick) {
        if (getMove() != MOVE_HAMMER) {
            return 0;
        }
        float t = moveTime(partialTick);
        if (t < HammerMoveData.TELEPORT_TICK || t >= HammerMoveData.IMPACT_TICK) {
            return 0;
        }
        if (t < HammerMoveData.DIVE_TICK) {
            return HammerMoveData.HOVER_HEIGHT;
        }
        float u = (t - HammerMoveData.DIVE_TICK) / (HammerMoveData.IMPACT_TICK - HammerMoveData.DIVE_TICK);
        return HammerMoveData.HOVER_HEIGHT * (1 - u * u);
    }

    /** centre of the red circle: where the fist lands, from the entity's feet and facing */
    public Vec3 hammerTarget() {
        float yaw = this.yBodyRot;
        return position().add(forward(yaw).scale(HammerMoveData.FIST_F)).add(right(yaw).scale(HammerMoveData.FIST_R));
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(COPY_OF, Optional.empty());
        this.entityData.define(MOVE, MOVE_NONE);
        this.entityData.define(MOVE_START, 0);
        this.entityData.define(PHASE, 1);
    }

    // ------------------------------------------------------------ AI
    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(0, new PhaseShiftGoal());
        this.goalSelector.addGoal(1, new HellscrapeGoal());
        this.goalSelector.addGoal(1, new HammerGoal());
        this.goalSelector.addGoal(1, new ComboGoal());
        this.goalSelector.addGoal(1, new VortexGoal());
        this.goalSelector.addGoal(1, new FireballGoal());
        this.goalSelector.addGoal(1, new CrescentGoal());
        this.goalSelector.addGoal(2, new SpacingGoal());
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 16.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            // a thin shadow aura (follows the body when it is drawn up in the air; none while it is gone)
            float mt = moveTime(0);
            boolean gone = (getMove() == MOVE_HAMMER && mt >= HammerMoveData.LEAP_TICK + 2 && mt < HammerMoveData.APPEAR_TICK)
                    || (getMove() == MOVE_VORTEX && mt >= VortexShape.TELEPORT_TICK - 1 && mt < VortexShape.APPEAR_TICK);
            double lift = visualLift(0);
            if (!gone && random.nextInt(3) == 0) {
                level().addParticle(ModParticles.PURPLE_EMBER.get(), getRandomX(0.5), getY() + lift + random.nextDouble() * 1.9,
                        getRandomZ(0.5), 0, 0.02, 0);
            }
            if (!gone && random.nextInt(5) == 0) {
                level().addParticle(ParticleTypes.SMOKE, getRandomX(0.4), getY() + lift + random.nextDouble() * 1.6, getRandomZ(0.4), 0, 0.01, 0);
            }
            if (getMove() == MOVE_VORTEX) {
                vortexParticles();
            }
            if (!gone && isPhaseTwo() && (getMove() != MOVE_PHASE_SHIFT || mt > PhaseMoveData.ARM_TICK)) {
                corruptionParticles(lift);
            }
        } else {
            if (!isPhaseTwo() && !pendingShift && getMove() != MOVE_PHASE_SHIFT && getHealth() <= getMaxHealth() * 0.5F) {
                pendingShift = true;
            }
            if (homePos == null) {
                homePos = position();
            }
            if (vortexCooldown > 0 && getMove() != MOVE_VORTEX) {
                vortexCooldown--;
            }
        }
        if (!level().isClientSide && !isInMove()) {
            if (cooldown > 0) {
                cooldown--;
            } else {
                planMove();
            }
        }
    }

    /** picks the next move once the cooldown is over: Hellscrape needs the 7.5-12.5 block run-up, the hammer doesn't */
    private void planMove() {
        LivingEntity t = getTarget();
        if (t == null || !t.isAlive()) {
            plannedMove = MOVE_NONE;
            return;
        }
        double d = distanceTo(t);
        if (plannedMove != MOVE_NONE) {
            boolean farMove = plannedMove == MOVE_HELLSCRAPE || plannedMove == MOVE_HAMMER;
            // the long-range moves are dropped as soon as the target comes back; anything stuck re-plans
            if ((farMove && d <= FAR_RANGE - 1.0) || ++planTicks > 60) {
                plannedMove = MOVE_NONE;
            }
            return;
        }
        planTicks = 0;
        float r = random.nextFloat();
        // the Inferno Vortex: whenever its 40 s cooldown allows (never twice running)
        if (vortexCooldown <= 0 && lastMove != MOVE_VORTEX && r < 0.3F) {
            plannedMove = MOVE_VORTEX;
            return;
        }
        r = random.nextFloat();
        if (d > FAR_RANGE) {
            // the target strayed off: run it down dragging the blade, or drop on it from above
            boolean runUp = d >= MIN_RANGE && d <= MAX_RANGE;
            if (runUp && r < 0.45F) {
                plannedMove = MOVE_HELLSCRAPE;
            } else if (d <= HAMMER_RANGE && r < 0.8F) {
                plannedMove = MOVE_HAMMER;
            } else if (d <= FIREBALL_RANGE) {
                plannedMove = MOVE_FIREBALL;
            } else {
                plannedMove = MOVE_HELLSCRAPE;     // closes into run-up range first
            }
        } else if (d <= COMBO_RANGE) {
            plannedMove = r < 0.45F ? MOVE_COMBO : r < 0.8F ? MOVE_CRESCENT : MOVE_FIREBALL;
        } else if (d <= 5.0) {
            plannedMove = r < 0.5F ? MOVE_CRESCENT : r < 0.75F ? MOVE_FIREBALL : MOVE_COMBO;
        } else {
            plannedMove = r < 0.55F ? MOVE_FIREBALL : r < 0.8F ? MOVE_CRESCENT : MOVE_COMBO;
        }
        if (plannedMove == lastMove && random.nextBoolean()) {
            plannedMove = MOVE_NONE;      // a little less repetition: think again next tick
        }
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

    /** the Shadow keeps its footing while it performs a move */
    @Override
    public void knockback(double strength, double x, double z) {
        if (!isInMove()) {
            super.knockback(strength, x, z);
        }
    }

    // ------------------------------------------------------------ Hellscrape
    private static Vec3 forward(float yaw) {
        float r = yaw * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(r), 0, Mth.cos(r));
    }

    private static Vec3 right(float yaw) {
        float r = yaw * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.cos(r), 0, -Mth.sin(r));
    }

    /** world position of the blade tip at a tick of the move */
    private Vec3 tipAt(int tick) {
        int i = Mth.clamp(tick, 0, ShadowMoveData.END_TICK);
        return position().add(forward(moveYaw).scale(ShadowMoveData.TIP_F[i]))
                .add(right(moveYaw).scale(ShadowMoveData.TIP_R[i])).add(0, ShadowMoveData.TIP_U[i], 0);
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
        setXRot(0);
    }

    private double remainingNominal(int fromTick) {
        double sum = 0;
        for (int i = Math.max(0, fromTick); i < ShadowMoveData.SPEED.length && i < ShadowMoveData.BRAKE_TICK; i++) {
            sum += ShadowMoveData.SPEED[i];
        }
        return sum;
    }

    private void startHellscrape(LivingEntity target) {
        moveTick = 0;
        moveYaw = yawTo(position(), target.position());
        faceYaw(moveYaw);
        hitThisMove.clear();
        // only the sprint is scaled; the skid and the lunge into the slash always cover the same ground
        speedScale = Mth.clamp((distanceTo(target) - STOP_SHORT - tailTravel()) / remainingNominal(0), 0.55, 1.5);
        getNavigation().stop();
        setMove(MOVE_HELLSCRAPE);
        triggerAnim("main", "hellscrape");
        playSound(SoundEvents.WITHER_SKELETON_AMBIENT, 1.2F, 0.55F);
    }

    private void tickHellscrape(@Nullable LivingEntity target) {
        int t = moveTick;
        ServerLevel sl = (ServerLevel) level();

        // steer at the target until the brake; keep the speed tuned so the slash lands at its feet
        if (target != null && target.isAlive() && t < ShadowMoveData.BRAKE_TICK) {
            float want = yawTo(position(), target.position());
            moveYaw += Mth.clamp(Mth.wrapDegrees(want - moveYaw), -7.0F, 7.0F);
            if (t >= ShadowMoveData.SPRINT_TICK) {
                double rem = remainingNominal(t);
                if (rem > 0.1) {
                    double need = Mth.clamp((distanceTo(target) - STOP_SHORT - tailTravel()) / rem, 0.45, 1.7);
                    speedScale += (need - speedScale) * 0.25;
                }
            }
        }
        faceYaw(moveYaw);

        double v = (t < ShadowMoveData.SPEED.length ? ShadowMoveData.SPEED[t] : 0) * (t < ShadowMoveData.BRAKE_TICK ? speedScale : 1.0);
        Vec3 f = forward(moveYaw);
        setDeltaMovement(f.x * v, getDeltaMovement().y, f.z * v);

        // the blade scraping the floor
        if (t < ShadowMoveData.CONTACT.length && ShadowMoveData.CONTACT[t]) {
            Vec3 tip = tipAt(t);
            sl.sendParticles(ModParticles.PURPLE_SPARK.get(), tip.x, tip.y + 0.05, tip.z, 4, 0.08, 0.02, 0.08, 0.12);
            sl.sendParticles(ModParticles.PURPLE_FLAME.get(), tip.x, tip.y + 0.05, tip.z, 1, 0.05, 0.0, 0.05, 0.01);
            if (random.nextInt(3) == 0) {
                sl.sendParticles(ParticleTypes.CRIT, tip.x, tip.y + 0.1, tip.z, 2, 0.05, 0.05, 0.05, 0.25);
            }
            if (t % 3 == 0) {
                playSound(SoundEvents.GRINDSTONE_USE, 0.55F, 1.45F + random.nextFloat() * 0.3F);
            }
        }
        if (t == ShadowMoveData.SPRINT_TICK) {
            playSound(SoundEvents.NETHERITE_BLOCK_STEP, 1.0F, 0.6F);
        }
        if (t == ShadowMoveData.BRAKE_TICK) {
            playSound(SoundEvents.GRAVEL_STEP, 1.2F, 0.6F);
            playSound(SoundEvents.NETHERITE_BLOCK_STEP, 1.0F, 0.5F);
            sl.sendParticles(ParticleTypes.POOF, getX() + f.x * 0.6, getY() + 0.1, getZ() + f.z * 0.6, 6, 0.3, 0.05, 0.3, 0.02);
        }
        if (t == ShadowMoveData.RELEASE_TICK) {
            Vec3 tip = tipAt(t);
            sl.sendParticles(ModParticles.PURPLE_FLAME_BIG.get(), tip.x, tip.y + 0.1, tip.z, 8, 0.2, 0.05, 0.2, 0.05);
            sl.sendParticles(ModParticles.PURPLE_SPARK.get(), tip.x, tip.y + 0.1, tip.z, 4, 0.1, 0.0, 0.1, 0.2);
            playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 1.4F, 0.55F);
        }
        // the rising slash
        if (t >= ShadowMoveData.SLASH_FROM - 1 && t <= ShadowMoveData.SLASH_TO) {
            Vec3 tip = tipAt(t);
            sl.sendParticles(ParticleTypes.SWEEP_ATTACK, tip.x, tip.y, tip.z, 1, 0, 0, 0, 0);
            sl.sendParticles(ModParticles.PURPLE_FLAME.get(), tip.x, tip.y, tip.z, 6, 0.15, 0.15, 0.15, 0.03);
            slashHit(f);
        }
        if (t == ShadowMoveData.APEX_TICK) {
            playSound(SoundEvents.PLAYER_ATTACK_STRONG, 1.2F, 0.6F);
        }
        moveTick++;
    }

    /** nominal distance still covered after the brake (the skid and the lunge into the slash) */
    private static double tailTravel() {
        double sum = 0;
        for (int i = ShadowMoveData.BRAKE_TICK; i < ShadowMoveData.SPEED.length; i++) {
            sum += ShadowMoveData.SPEED[i];
        }
        return sum;
    }

    private void slashHit(Vec3 f) {
        Vec3 r = right(moveYaw);
        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(3.5, 2.0, 3.5),
                e -> e != this && e.isAlive() && !(e instanceof InfernalShadowEntity))) {
            if (hitThisMove.contains(e.getUUID())) {
                continue;
            }
            Vec3 d = e.position().subtract(position());
            double fwd = d.dot(f), side = d.dot(r), up = e.getY() - getY();
            boolean inside = fwd > -0.3 && fwd < 3.2 && Math.abs(side) < 1.7 + e.getBbWidth() * 0.5 && up > -1.0 && up < 3.6;
            if (!inside) {
                continue;
            }
            hitThisMove.add(e.getUUID());
            if (e.hurt(damageSources().mobAttack(this), HELLSCRAPE_DAMAGE * power())) {
                e.setDeltaMovement(f.x * 0.45, 0.95, f.z * 0.45);
                e.hurtMarked = true;
                playSound(SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.2F, 0.7F);
            }
        }
    }

    private void endHellscrape() {
        moveTick = -1;
        cooldown = 20 + random.nextInt(15);
        lastMove = MOVE_HELLSCRAPE;
        plannedMove = MOVE_NONE;
        setMove(MOVE_NONE);
    }

    private class HellscrapeGoal extends Goal {
        HellscrapeGoal() {
            setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK, Goal.Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            LivingEntity t = getTarget();
            if (t == null || !t.isAlive() || plannedMove != MOVE_HELLSCRAPE || isInMove() || !onGround()) {
                return false;
            }
            double d = distanceTo(t);
            return d >= MIN_RANGE && d <= MAX_RANGE && hasLineOfSight(t) && Math.abs(t.getY() - getY()) < 3.0;
        }

        @Override
        public boolean canContinueToUse() {
            return moveTick >= 0 && moveTick <= ShadowMoveData.END_TICK && isAlive();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void start() {
            LivingEntity t = getTarget();
            if (t != null) {
                startHellscrape(t);
            }
        }

        @Override
        public void tick() {
            if (moveTick >= 0) {
                tickHellscrape(getTarget());
            }
        }

        @Override
        public void stop() {
            endHellscrape();
        }
    }

    /** Between moves: closes in when far, backs off when crowded, otherwise holds its guard and stares. */
    private class SpacingGoal extends Goal {
        private int repath;

        SpacingGoal() {
            setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity t = getTarget();
            return t != null && t.isAlive() && !isInMove();
        }

        @Override
        public void stop() {
            getNavigation().stop();
        }

        @Override
        public void tick() {
            LivingEntity t = getTarget();
            if (t == null) {
                return;
            }
            getLookControl().setLookAt(t, 30.0F, 30.0F);
            double d = distanceTo(t);
            // between moves it keeps a menacing distance; waiting to Hellscrape it backs into run-up range
            // gets into range for whatever it plans next
            double near, far;
            switch (plannedMove) {
                case MOVE_HELLSCRAPE -> { near = MIN_RANGE + 0.5; far = MAX_RANGE - 1.0; }
                case MOVE_HAMMER -> { near = 0.0; far = HAMMER_RANGE - 4.0; }
                case MOVE_COMBO -> { near = 0.0; far = COMBO_RANGE - 0.6; }
                case MOVE_CRESCENT -> { near = 0.0; far = CRESCENT_RANGE - 0.6; }
                case MOVE_FIREBALL -> { near = 3.0; far = FIREBALL_RANGE - 3.0; }
                default -> { near = 3.5; far = 10.0; }
            }
            if (far < 4.0) {
                repath = Math.min(repath, 4);     // closing in for a strike: keep the path fresh
            }
            if (--repath > 0) {
                return;
            }
            repath = 10;
            if (d > far) {
                getNavigation().moveTo(t, far < 4.0 ? 1.3D : 1.0D);
            } else if (d < near) {
                Vec3 away = position().subtract(t.position()).normalize().scale(4.0).add(position());
                getNavigation().moveTo(away.x, away.y, away.z, 1.15D);
            } else {
                getNavigation().stop();
            }
        }
    }

    // ------------------------------------------------------------ Hammer and Anvil
    /** the top of the floor under a point (searches down a few blocks; the point itself if there is none) */
    private Vec3 groundBelow(Vec3 p) {
        BlockPos.MutableBlockPos pos = BlockPos.containing(p.x, p.y + 0.5, p.z).mutable();
        for (int i = 0; i < 16; i++) {
            BlockPos below = pos.below();
            VoxelShape shape = level().getBlockState(below).getCollisionShape(level(), below);
            if (!shape.isEmpty()) {
                return new Vec3(p.x, below.getY() + shape.max(Direction.Axis.Y), p.z);
            }
            pos.move(Direction.DOWN);
        }
        return p;
    }

    /** stands so that the fist lands on the circle's centre */
    private void standForCircle() {
        Vec3 feet = hammerCenter.subtract(forward(moveYaw).scale(HammerMoveData.FIST_F)).subtract(right(moveYaw).scale(HammerMoveData.FIST_R));
        setPos(feet.x, hammerCenter.y, feet.z);
        setDeltaMovement(Vec3.ZERO);
        faceYaw(moveYaw);
    }

    private void startHammer(LivingEntity target) {
        hammerTick = 0;
        moveYaw = yawTo(position(), target.position());
        faceYaw(moveYaw);
        hammerCenter = groundBelow(target.position());
        getNavigation().stop();
        setMove(MOVE_HAMMER);
        triggerAnim("main", "hammer_anvil");
        playSound(SoundEvents.WITHER_SKELETON_AMBIENT, 1.2F, 0.5F);
    }

    private void tickHammer(@Nullable LivingEntity target) {
        int t = hammerTick;
        ServerLevel sl = (ServerLevel) level();
        if (t < HammerMoveData.TELEPORT_TICK) {
            faceYaw(moveYaw);
            setDeltaMovement(0, getDeltaMovement().y, 0);
            if (t == HammerMoveData.LEAP_TICK) {
                playSound(SoundEvents.GENERIC_SMALL_FALL, 1.0F, 0.5F);
                sl.sendParticles(ParticleTypes.POOF, getX(), getY() + 0.1, getZ(), 8, 0.3, 0.05, 0.3, 0.03);
            }
        } else if (t == HammerMoveData.TELEPORT_TICK) {
            // gone in a burst of smoke; the circle appears under the target
            sl.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 2.2, getZ(), 30, 0.4, 0.6, 0.4, 0.04);
            sl.sendParticles(ModParticles.PURPLE_FLAME_BIG.get(), getX(), getY() + 2.2, getZ(), 14, 0.3, 0.5, 0.3, 0.02);
            sl.playSound(null, getX(), getY(), getZ(), SoundEvents.ENDERMAN_TELEPORT, getSoundSource(), 1.2F, 0.6F);
            if (target != null && target.isAlive()) {
                hammerCenter = groundBelow(target.position());
            }
            moveYaw = yawTo(position(), hammerCenter);
            standForCircle();
        } else if (t < HammerMoveData.IMPACT_TICK) {
            // the circle follows the target until it locks
            if (t < HammerMoveData.LOCK_TICK && target != null && target.isAlive()) {
                Vec3 want = groundBelow(target.position());
                Vec3 step = want.subtract(hammerCenter);
                double len = step.length();
                if (len > 0.6) {
                    step = step.scale(0.6 / len);
                }
                hammerCenter = hammerCenter.add(step.scale(0.45));
            }
            standForCircle();
            Vec3 above = hammerCenter.add(0, HammerMoveData.HOVER_HEIGHT + 1.0, 0);
            if (t == HammerMoveData.APPEAR_TICK) {
                sl.sendParticles(ModParticles.PURPLE_FLAME_BIG.get(), above.x, above.y, above.z, 24, 0.5, 0.6, 0.5, 0.04);
                sl.sendParticles(ParticleTypes.LARGE_SMOKE, above.x, above.y, above.z, 16, 0.4, 0.5, 0.4, 0.02);
                playSound(SoundEvents.ENDERMAN_TELEPORT, 1.2F, 0.5F);
            }
            if (t == HammerMoveData.LOCK_TICK) {
                playSound(SoundEvents.ANVIL_PLACE, 1.0F, 0.5F);
            }
            if (t == HammerMoveData.DIVE_TICK) {
                playSound(SoundEvents.PHANTOM_SWOOP, 1.6F, 0.6F);
            }
            if (t > HammerMoveData.APPEAR_TICK && t < HammerMoveData.DIVE_TICK && t % 2 == 0) {
                sl.sendParticles(ModParticles.PURPLE_EMBER.get(), above.x, above.y, above.z, 2, 0.3, 0.4, 0.3, 0.01);
            }
        } else if (t == HammerMoveData.IMPACT_TICK) {
            standForCircle();
            hammerImpact(sl);
        }
        hammerTick++;
    }

    private void hammerImpact(ServerLevel sl) {
        Vec3 c = hammerCenter;
        sl.playSound(null, c.x, c.y, c.z, SoundEvents.ANVIL_LAND, getSoundSource(), 2.0F, 0.5F);
        sl.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE, getSoundSource(), 1.6F, 0.75F);
        sl.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y + 0.3, c.z, 2, 0.3, 0.1, 0.3, 0.0);
        sl.sendParticles(ModParticles.PURPLE_FLAME_BIG.get(), c.x, c.y + 0.2, c.z, 30, 0.8, 0.1, 0.8, 0.06);
        BlockPos floor = BlockPos.containing(c.x, c.y - 0.5, c.z);
        BlockState floorState = sl.getBlockState(floor);
        // a ring of dust racing outward (count 0 = the offsets are the particle's velocity)
        for (int i = 0; i < 32; i++) {
            double a = i * Math.PI * 2 / 32;
            double vx = Math.cos(a), vz = Math.sin(a);
            sl.sendParticles(ParticleTypes.CLOUD, c.x + vx * 0.6, c.y + 0.15, c.z + vz * 0.6, 0, vx * 0.45, 0.02, vz * 0.45, 1.0);
            if (!floorState.isAir()) {
                sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, floorState), c.x + vx * 1.5, c.y + 0.1, c.z + vz * 1.5,
                        3, 0.3, 0.05, 0.3, 0.2);
            }
        }
        double r = HammerMoveData.RADIUS, outer = HammerMoveData.OUTER_RADIUS;
        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class,
                new AABB(c.x - outer, c.y - 1.5, c.z - outer, c.x + outer, c.y + 3.0, c.z + outer),
                e -> e != this && e.isAlive() && !(e instanceof InfernalShadowEntity))) {
            Vec3 d = new Vec3(e.getX() - c.x, 0, e.getZ() - c.z);
            double dist = d.length();
            if (dist > outer + e.getBbWidth() * 0.5) {
                continue;
            }
            Vec3 out = dist < 0.05 ? forward(moveYaw) : d.scale(1.0 / dist);
            boolean inside = dist <= r + e.getBbWidth() * 0.5;
            if (e.hurt(damageSources().mobAttack(this), (inside ? HAMMER_DAMAGE : HAMMER_OUTER_DAMAGE) * power())) {
                double k = inside ? 1.15 : 0.7;
                e.setDeltaMovement(out.x * k, inside ? 0.6 : 0.35, out.z * k);
                e.hurtMarked = true;
            }
        }
    }

    private void endHammer() {
        hammerTick = -1;
        cooldown = 20 + random.nextInt(15);
        lastMove = MOVE_HAMMER;
        plannedMove = MOVE_NONE;
        setMove(MOVE_NONE);
    }

    private class HammerGoal extends Goal {
        HammerGoal() {
            setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK, Goal.Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            LivingEntity t = getTarget();
            return t != null && t.isAlive() && plannedMove == MOVE_HAMMER && !isInMove() && onGround()
                    && distanceTo(t) <= HAMMER_RANGE;
        }

        @Override
        public boolean canContinueToUse() {
            return hammerTick >= 0 && hammerTick <= HammerMoveData.END_TICK && isAlive();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void start() {
            LivingEntity t = getTarget();
            if (t != null) {
                startHammer(t);
            }
        }

        @Override
        public void tick() {
            if (hammerTick >= 0) {
                tickHammer(getTarget());
            }
        }

        @Override
        public void stop() {
            endHammer();
        }
    }

    // ------------------------------------------------------------ the close-range combo
    private void startCombo(LivingEntity target) {
        comboTick = 0;
        moveYaw = yawTo(position(), target.position());
        faceYaw(moveYaw);
        hitThisMove.clear();
        getNavigation().stop();
        setMove(MOVE_COMBO);
        triggerAnim("main", "combo");
    }

    private Vec3 comboPoint(float[] f, float[] r, float[] u, int tick) {
        int i = Mth.clamp(tick, 0, f.length - 1);
        return position().add(forward(moveYaw).scale(f[i])).add(right(moveYaw).scale(r[i])).add(0, u[i], 0);
    }

    private void tickCombo(@Nullable LivingEntity target) {
        int t = comboTick;
        ServerLevel sl = (ServerLevel) level();
        if (target != null && target.isAlive() && t < ComboMoveData.PUNCH_TICK) {
            float want = yawTo(position(), target.position());
            moveYaw += Mth.clamp(Mth.wrapDegrees(want - moveYaw), -9.0F, 9.0F);
        }
        faceYaw(moveYaw);
        // small steps into each strike, but never through the target
        Vec3 f = forward(moveYaw);
        double step = t < ComboMoveData.STEP.length ? ComboMoveData.STEP[t] : 0.0;
        if (target != null && distanceTo(target) < 1.3) {
            step = 0.0;
        }
        setDeltaMovement(f.x * step, getDeltaMovement().y, f.z * step);

        if (t == ComboMoveData.SLASH1_TICK - 2 || t == ComboMoveData.SLASH2_TICK - 2) {
            playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 1.2F, t < ComboMoveData.SLASH2_TICK - 2 ? 0.75F : 0.9F);
        }
        for (int hit : new int[]{ComboMoveData.SLASH1_TICK, ComboMoveData.SLASH2_TICK}) {
            if (t >= hit - 1 && t <= hit + 1) {
                Vec3 tip = comboPoint(ComboMoveData.TIP_F, ComboMoveData.TIP_R, ComboMoveData.TIP_U, t);
                sl.sendParticles(ParticleTypes.SWEEP_ATTACK, tip.x, tip.y, tip.z, 1, 0, 0, 0, 0);
                sl.sendParticles(ModParticles.PURPLE_FLAME.get(), tip.x, tip.y, tip.z, 4, 0.12, 0.12, 0.12, 0.02);
            }
            if (t == hit) {
                hitThisMove.clear();
                strikeArc(3.3, 75.0, COMBO_SLASH_DAMAGE, 0.5, 0.15, 0);
            }
        }
        // fire gathers in the left fist, then the punch
        if (t >= ComboMoveData.PUNCH_CHARGE_TICK && t < ComboMoveData.PUNCH_TICK) {
            Vec3 fist = comboPoint(ComboMoveData.FIST_F, ComboMoveData.FIST_R, ComboMoveData.FIST_U, t);
            sl.sendParticles(ModParticles.PURPLE_FLAME.get(), fist.x, fist.y, fist.z, 4, 0.08, 0.08, 0.08, 0.01);
            if (t == ComboMoveData.PUNCH_CHARGE_TICK) {
                playSound(SoundEvents.FIRECHARGE_USE, 0.9F, 0.7F);
            }
        }
        if (t == ComboMoveData.PUNCH_TICK) {
            Vec3 fist = comboPoint(ComboMoveData.FIST_F, ComboMoveData.FIST_R, ComboMoveData.FIST_U, t);
            playSound(SoundEvents.BLAZE_SHOOT, 1.3F, 0.6F);
            playSound(SoundEvents.PLAYER_ATTACK_STRONG, 1.2F, 0.7F);
            for (int i = 0; i < 18; i++) {            // a cone of flame blasting forward (count 0: offsets = velocity)
                double sx = (random.nextDouble() - 0.5) * 0.25, sy = (random.nextDouble() - 0.5) * 0.25;
                sl.sendParticles(ModParticles.PURPLE_FLAME.get(), fist.x, fist.y, fist.z, 0, f.x * 0.45 + sx, sy, f.z * 0.45 + sx, 1.0);
            }
            sl.sendParticles(ModParticles.PURPLE_FLAME_BIG.get(), fist.x + f.x * 0.6, fist.y, fist.z + f.z * 0.6, 8, 0.2, 0.2, 0.2, 0.03);
            sl.sendParticles(ModParticles.PURPLE_SPARK.get(), fist.x, fist.y, fist.z, 5, 0.1, 0.1, 0.1, 0.25);
            hitThisMove.clear();
            strikeArc(3.3, 40.0, COMBO_PUNCH_DAMAGE, 1.4, 0.35, 5);
        }
        comboTick++;
    }

    /** hits everything in front within reach and a half-angle (each target once per strike) */
    private void strikeArc(double reach, double halfAngle, float damage, double knock, double up, int fireSeconds) {
        Vec3 f = forward(moveYaw);
        double cosMax = Math.cos(Math.toRadians(halfAngle));
        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(reach, 1.5, reach),
                e -> e != this && e.isAlive() && !(e instanceof InfernalShadowEntity))) {
            if (hitThisMove.contains(e.getUUID())) {
                continue;
            }
            Vec3 d = new Vec3(e.getX() - getX(), 0, e.getZ() - getZ());
            double dist = d.length() - e.getBbWidth() * 0.5;
            if (dist > reach || (d.length() > 0.3 && d.normalize().dot(f) < cosMax) || Math.abs(e.getY() - getY()) > 2.5) {
                continue;
            }
            hitThisMove.add(e.getUUID());
            if (e.hurt(damageSources().mobAttack(this), damage * power())) {
                e.setDeltaMovement(f.x * knock, up, f.z * knock);
                e.hurtMarked = true;
                if (fireSeconds > 0) {
                    ShadowFire.ignite(e, fireSeconds);
                }
            }
        }
    }

    private void endCombo() {
        comboTick = -1;
        finishQuickMove(MOVE_COMBO);
    }

    private class ComboGoal extends Goal {
        ComboGoal() {
            setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK, Goal.Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            LivingEntity t = getTarget();
            return t != null && t.isAlive() && plannedMove == MOVE_COMBO && !isInMove() && onGround()
                    && distanceTo(t) <= COMBO_RANGE && hasLineOfSight(t);
        }

        @Override
        public boolean canContinueToUse() {
            return comboTick >= 0 && comboTick <= ComboMoveData.END_TICK && isAlive();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void start() {
            LivingEntity t = getTarget();
            if (t != null) {
                startCombo(t);
            }
        }

        @Override
        public void tick() {
            if (comboTick >= 0) {
                tickCombo(getTarget());
            }
        }

        @Override
        public void stop() {
            endCombo();
        }
    }

    // ------------------------------------------------------------ Inferno Vortex
    /** the middle of the arena (in the trial), otherwise where it was first placed */
    private Vec3 arenaCenter() {
        if (level().dimension() == ModDimensions.TRIAL) {
            return groundBelow(new Vec3(0.5, ArenaLayout.FLOOR + 8, 0.5));
        }
        if (homePos == null) {
            homePos = position();
        }
        return groundBelow(homePos.add(0, 2, 0));
    }

    private void startVortex(LivingEntity target) {
        vortexTick = 0;
        moveYaw = yawTo(position(), target.position());
        faceYaw(moveYaw);
        vortexCenter = arenaCenter();
        nextGeyser = VortexShape.GEYSERS_FROM;
        getNavigation().stop();
        setMove(MOVE_VORTEX);
        triggerAnim("main", "vortex");
        playSound(SoundEvents.WITHER_SKELETON_AMBIENT, 1.3F, 0.45F);
    }

    private void tickVortex(@Nullable LivingEntity target) {
        int t = vortexTick;
        ServerLevel sl = (ServerLevel) level();
        setDeltaMovement(0, getDeltaMovement().y, 0);
        if (t == VortexShape.TELEPORT_TICK) {
            sl.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 1.0, getZ(), 30, 0.4, 0.6, 0.4, 0.04);
            sl.sendParticles(ModParticles.PURPLE_FLAME_BIG.get(), getX(), getY() + 1.0, getZ(), 14, 0.3, 0.5, 0.3, 0.02);
            sl.playSound(null, getX(), getY(), getZ(), SoundEvents.ENDERMAN_TELEPORT, getSoundSource(), 1.2F, 0.6F);
            if (target != null) {
                moveYaw = yawTo(vortexCenter, target.position());
            }
        }
        if (t >= VortexShape.TELEPORT_TICK) {
            setPos(vortexCenter.x, vortexCenter.y, vortexCenter.z);
        }
        faceYaw(moveYaw);
        if (t == VortexShape.APPEAR_TICK) {
            sl.sendParticles(ModParticles.PURPLE_FLAME_BIG.get(), getX(), getY() + 1.0, getZ(), 24, 0.5, 0.6, 0.5, 0.04);
            playSound(SoundEvents.ENDERMAN_TELEPORT, 1.2F, 0.5F);
        }
        if (t == VortexShape.IGNITE_TICK) {
            sl.playSound(null, getX(), getY(), getZ(), SoundEvents.BLAZE_SHOOT, getSoundSource(), 2.0F, 0.4F);
            sl.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE, getSoundSource(), 1.0F, 0.6F);
            sl.sendParticles(ModParticles.PURPLE_SPARK.get(), getX(), getY() + 0.5, getZ(), 30, 1.0, 0.3, 1.0, 0.3);
            sl.sendParticles(ModParticles.PURPLE_FLAME.get(), getX(), getY() + 0.3, getZ(), 60, 1.5, 0.2, 1.5, 0.15);
        }
        if (t > VortexShape.IGNITE_TICK && t < VortexShape.GONE_TICK && t % 30 == 0) {
            sl.playSound(null, getX(), getY(), getZ(), SoundEvents.FIRE_AMBIENT, getSoundSource(), 2.0F, 0.5F);
            sl.playSound(null, getX(), getY(), getZ(), SoundEvents.ELYTRA_FLYING, getSoundSource(), 0.6F, 0.5F);
        }
        float s = VortexShape.strength(t);
        if (s > 0) {
            for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(VortexShape.PULL_RANGE, 16, VortexShape.PULL_RANGE),
                    e -> e != this && e.isAlive() && !(e instanceof InfernalShadowEntity))) {
                double dx = e.getX() - getX(), dz = e.getZ() - getZ(), dist = Math.sqrt(dx * dx + dz * dz);
                // players are pulled on their own client (smooth, no rubber-banding); everything else here
                if (!(e instanceof Player) && dist > 0.4) {
                    double a = VortexShape.pull(dist, e.onGround(), s);
                    e.setDeltaMovement(e.getDeltaMovement().add(-dx / dist * a, 0, -dz / dist * a));
                }
                if (t % 10 == 0 && VortexShape.inside(dx, e.getY() - getY(), dz, t, e.getBbWidth() * 0.5)) {
                    if (e.hurt(damageSources().mobAttack(this), VORTEX_DAMAGE * power())) {
                        ShadowFire.ignite(e, 4);
                    }
                }
            }
        }
        if (t >= nextGeyser && t <= VortexShape.GEYSERS_TO) {
            spawnGeyser();
            nextGeyser = t + 7 + random.nextInt(6);
        }
        if (t == VortexShape.COLLAPSE_TICK) {
            triggerAnim("main", "vortex_end");
            sl.playSound(null, getX(), getY(), getZ(), SoundEvents.FIRE_EXTINGUISH, getSoundSource(), 1.5F, 0.5F);
        }
        vortexTick++;
    }

    /** a geyser somewhere around the arena: often right under a player, so they keep moving */
    private void spawnGeyser() {
        Vec3 c = position();
        Vec3 at = null;
        List<Player> players = level().getEntitiesOfClass(Player.class, getBoundingBox().inflate(40, 16, 40),
                p -> p.isAlive() && !p.isSpectator() && !p.isCreative());
        if (!players.isEmpty() && random.nextFloat() < 0.4F) {
            Player p = players.get(random.nextInt(players.size()));
            at = p.position().add((random.nextDouble() - 0.5) * 1.5, 0, (random.nextDouble() - 0.5) * 1.5);
        }
        for (int tries = 0; at == null && tries < 8; tries++) {
            double a = random.nextDouble() * Math.PI * 2, r = 5.0 + random.nextDouble() * 23.0;
            at = c.add(Math.cos(a) * r, 0, Math.sin(a) * r);
        }
        double dx = at.x - c.x, dz = at.z - c.z;
        if (dx * dx + dz * dz < 4.5 * 4.5) {
            return;     // never inside the tornado itself
        }
        Vec3 ground = groundBelow(new Vec3(at.x, Math.max(at.y, c.y) + 3, at.z));
        level().addFreshEntity(new FireGeyserEntity(level(), ground, this));
    }

    private void endVortex() {
        vortexTick = -1;
        cooldown = 30 + random.nextInt(15);
        vortexCooldown = VORTEX_COOLDOWN;
        lastMove = MOVE_VORTEX;
        plannedMove = MOVE_NONE;
        setMove(MOVE_NONE);
    }

    private class VortexGoal extends Goal {
        VortexGoal() {
            setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK, Goal.Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            LivingEntity t = getTarget();
            return t != null && t.isAlive() && plannedMove == MOVE_VORTEX && !isInMove() && onGround();
        }

        @Override
        public boolean canContinueToUse() {
            return vortexTick >= 0 && vortexTick <= VortexShape.END_TICK && isAlive();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void start() {
            LivingEntity t = getTarget();
            if (t != null) {
                startVortex(t);
            }
        }

        @Override
        public void tick() {
            if (vortexTick >= 0) {
                tickVortex(getTarget());
            }
        }

        @Override
        public void stop() {
            endVortex();
        }
    }

    /** client: the tornado's flames (the funnel itself is drawn by the renderer) */
    private void vortexParticles() {
        float t = moveTime(0);
        float s = VortexShape.strength(t);
        if (s <= 0) {
            return;
        }
        for (int i = 0; i < 12; i++) {
            float dy = random.nextFloat() * VortexShape.HEIGHT * s;
            double a = random.nextDouble() * Math.PI * 2;
            double r = VortexShape.radiusAt(dy, s) * (0.8 + random.nextDouble() * 0.3);
            double x = getX() + VortexShape.swayX(dy, t) + Math.cos(a) * r, z = getZ() + VortexShape.swayZ(dy, t) + Math.sin(a) * r;
            // flames ride the spin (counter-clockwise from above) and climb
            level().addParticle(i % 4 == 0 ? ModParticles.PURPLE_FLAME_BIG.get() : ModParticles.PURPLE_FLAME.get(), x, getY() + dy, z,
                    -Math.sin(a) * 0.22, 0.08 + random.nextDouble() * 0.06, Math.cos(a) * 0.22);
        }
        if (random.nextInt(2) == 0) {
            double a = random.nextDouble() * Math.PI * 2;
            level().addParticle(ParticleTypes.LARGE_SMOKE, getX() + Math.cos(a) * 4, getY() + VortexShape.HEIGHT * s, getZ() + Math.sin(a) * 4, 0, 0.08, 0);
        }
        if (random.nextInt(3) == 0) {
            double a = random.nextDouble() * Math.PI * 2;
            level().addParticle(ModParticles.PURPLE_EMBER.get(), getX() + Math.cos(a) * 1.8, getY() + 0.2, getZ() + Math.sin(a) * 1.8, 0, 0.06, 0);
        }
    }

    // ------------------------------------------------------------ Hellfire Volley (fireballs from the air)
    /** room to fly straight up */
    private boolean hasHeadroom() {
        return level().noCollision(this, getBoundingBox().expandTowards(0, 6.0, 0));
    }

    private void startFireball(LivingEntity target) {
        fireTick = 0;
        moveYaw = yawTo(position(), target.position());
        faceYaw(moveYaw);
        takeoffY = getY();
        getNavigation().stop();
        setMove(MOVE_FIREBALL);
        triggerAnim("main", "fireball_volley");
    }

    private void tickFireball(@Nullable LivingEntity target) {
        int t = fireTick;
        ServerLevel sl = (ServerLevel) level();
        if (target != null && target.isAlive()) {
            float want = yawTo(position(), target.position());
            moveYaw += Mth.clamp(Mth.wrapDegrees(want - moveYaw), -12.0F, 12.0F);
        }
        faceYaw(moveYaw);
        // flies along the animation's height curve, two ticks ahead so the client's smoothing lands on time
        float[] h = FireballMoveData.HEIGHT;
        float up = h[Math.min(t + 2, h.length - 1)];
        boolean flying = t + 2 > FireballMoveData.TAKEOFF_TICK && t + 2 < FireballMoveData.LAND_TICK;
        setNoGravity(flying);
        if (flying || up > 0.0F) {
            setPos(getX(), takeoffY + up, getZ());
            setDeltaMovement(Vec3.ZERO);
            this.fallDistance = 0;
        }
        if (t == FireballMoveData.TAKEOFF_TICK - 1) {
            playSound(SoundEvents.GENERIC_SMALL_FALL, 1.0F, 0.5F);
            sl.sendParticles(ParticleTypes.POOF, getX(), getY() + 0.1, getZ(), 10, 0.4, 0.05, 0.4, 0.03);
            sl.sendParticles(ModParticles.PURPLE_FLAME_BIG.get(), getX(), getY() + 0.2, getZ(), 8, 0.3, 0.1, 0.3, 0.02);
        }
        for (int shot : FireballMoveData.SHOT_TICKS) {
            if (t == shot - 4) {
                Vec3 fist = localPoint(FireballMoveData.FIST_F, FireballMoveData.FIST_R, FireballMoveData.FIST_U, t);
                sl.sendParticles(ModParticles.PURPLE_FLAME.get(), fist.x, fist.y, fist.z, 8, 0.1, 0.1, 0.1, 0.02);
            }
            if (t == shot) {
                Vec3 fist = localPoint(FireballMoveData.FIST_F, FireballMoveData.FIST_R, FireballMoveData.FIST_U, t);
                Vec3 aim;
                if (target != null && target.isAlive()) {
                    // aimed at the body with a little lead: keep moving and it misses
                    Vec3 body = target.position().add(0, target.getBbHeight() * 0.5, 0);
                    double time = body.distanceTo(fist) / ShadowFireballEntity.SPEED;
                    aim = body.add(target.getDeltaMovement().multiply(1, 0, 1).scale(time * 0.5)).subtract(fist);
                } else {
                    aim = forward(moveYaw).add(0, -0.5, 0);
                }
                level().addFreshEntity(new ShadowFireballEntity(level(), this, fist, aim));
                playSound(SoundEvents.BLAZE_SHOOT, 1.4F, 0.6F + 0.1F * random.nextFloat());
                sl.sendParticles(ModParticles.PURPLE_FLAME_BIG.get(), fist.x, fist.y, fist.z, 6, 0.15, 0.15, 0.15, 0.03);
            }
        }
        if (t == FireballMoveData.LAND_TICK - 2) {
            playSound(SoundEvents.NETHERITE_BLOCK_STEP, 1.2F, 0.5F);
            sl.sendParticles(ParticleTypes.POOF, getX(), takeoffY + 0.1, getZ(), 10, 0.4, 0.05, 0.4, 0.03);
        }
        fireTick++;
    }

    /** a point baked in the Shadow's own frame (forward, right, up), in the world */
    private Vec3 localPoint(float[] f, float[] r, float[] u, int tick) {
        int i = Mth.clamp(tick, 0, f.length - 1);
        return position().add(forward(moveYaw).scale(f[i])).add(right(moveYaw).scale(r[i])).add(0, u[i], 0);
    }

    private void endFireball() {
        fireTick = -1;
        setNoGravity(false);
        finishQuickMove(MOVE_FIREBALL);
    }

    // ------------------------------------------------------------ Infernal Crescent (charged sweeping slash)
    private void startCrescent(LivingEntity target) {
        crescentTick = 0;
        moveYaw = yawTo(position(), target.position());
        faceYaw(moveYaw);
        hitThisMove.clear();
        getNavigation().stop();
        setMove(MOVE_CRESCENT);
        triggerAnim("main", "crescent");
    }

    private void tickCrescent(@Nullable LivingEntity target) {
        int t = crescentTick;
        ServerLevel sl = (ServerLevel) level();
        // turns to follow you while charging; the crescent locks just before the release
        if (target != null && target.isAlive() && t < CrescentMoveData.RELEASE_TICK - 3) {
            float want = yawTo(position(), target.position());
            moveYaw += Mth.clamp(Mth.wrapDegrees(want - moveYaw), -10.0F, 10.0F);
        }
        faceYaw(moveYaw);
        Vec3 f = forward(moveYaw);
        double step = t >= 1 && t <= 4 ? -0.08 : t >= CrescentMoveData.RELEASE_TICK && t < CrescentMoveData.RELEASE_TICK + 4 ? 0.2 : 0.0;
        if (step > 0 && target != null && distanceTo(target) < 1.6) {
            step = 0.0;
        }
        setDeltaMovement(f.x * step, getDeltaMovement().y, f.z * step);
        if (t == CrescentMoveData.CHARGE_TICK) {
            playSound(SoundEvents.FIRECHARGE_USE, 1.0F, 0.5F);
        }
        if (t >= CrescentMoveData.CHARGE_TICK && t < CrescentMoveData.RELEASE_TICK) {
            Vec3 tip = localPoint(CrescentMoveData.TIP_F, CrescentMoveData.TIP_R, CrescentMoveData.TIP_U, t);
            sl.sendParticles(t % 2 == 0 ? ModParticles.PURPLE_SPARK.get() : ModParticles.PURPLE_FLAME.get(), tip.x, tip.y, tip.z, 2, 0.08, 0.08, 0.08, 0.01);
            if (t % 6 == 0) {
                playSound(SoundEvents.FIRE_AMBIENT, 0.8F, 1.4F);
            }
        }
        if (t == CrescentMoveData.RELEASE_TICK) {
            playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 1.6F, 0.5F);
        }
        if (t >= CrescentMoveData.RELEASE_TICK && t <= CrescentMoveData.RELEASE_END_TICK) {
            Vec3 tip = localPoint(CrescentMoveData.TIP_F, CrescentMoveData.TIP_R, CrescentMoveData.TIP_U, t);
            sl.sendParticles(ParticleTypes.SWEEP_ATTACK, tip.x, tip.y, tip.z, 1, 0, 0, 0, 0);
        }
        if (t == CrescentMoveData.HIT_TICK) {
            crescentHit(sl, f);
        }
        crescentTick++;
    }

    private void crescentHit(ServerLevel sl, Vec3 f) {
        playSound(SoundEvents.BLAZE_SHOOT, 1.5F, 0.5F);
        playSound(SoundEvents.PLAYER_ATTACK_STRONG, 1.3F, 0.6F);
        // the crescent of ground in front bursts into flame
        double half = Math.toRadians(CrescentMoveData.HALF_ANGLE);
        double base = Math.atan2(f.z, f.x);
        for (int i = 0; i <= 18; i++) {
            double a = base - half + 2 * half * i / 18;
            for (double r = 1.5; r <= CrescentMoveData.REACH; r += 1.3) {
                double x = getX() + Math.cos(a) * r, z = getZ() + Math.sin(a) * r;
                sl.sendParticles(ModParticles.PURPLE_FLAME.get(), x, getY() + 0.2, z, 2, 0.15, 0.05, 0.15, 0.04);
            }
            if (i % 3 == 0) {
                double x = getX() + Math.cos(a) * 3.2, z = getZ() + Math.sin(a) * 3.2;
                sl.sendParticles(ParticleTypes.SWEEP_ATTACK, x, getY() + 1.1, z, 1, 0, 0, 0, 0);
            }
        }
        double cosMax = Math.cos(half);
        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(CrescentMoveData.REACH + 1, 2.5, CrescentMoveData.REACH + 1),
                e -> e != this && e.isAlive() && !(e instanceof InfernalShadowEntity))) {
            Vec3 d = new Vec3(e.getX() - getX(), 0, e.getZ() - getZ());
            double dist = d.length();
            if (dist - e.getBbWidth() * 0.5 > CrescentMoveData.REACH || Math.abs(e.getY() - getY()) > 3.0
                    || (dist > 0.4 && d.scale(1.0 / dist).dot(f) < cosMax)) {
                continue;
            }
            if (e.hurt(damageSources().mobAttack(this), CRESCENT_DAMAGE * power())) {
                Vec3 out = dist > 0.4 ? d.scale(1.0 / dist) : f;
                e.setDeltaMovement(out.x * 1.2, 0.35, out.z * 1.2);
                e.hurtMarked = true;
                ShadowFire.ignite(e, 4);
            }
        }
    }

    private void endCrescent() {
        crescentTick = -1;
        finishQuickMove(MOVE_CRESCENT);
    }

    /** the close and mid-range moves (combo, crescent, fireballs) have no cooldown: only a short breath after */
    private void finishQuickMove(int move) {
        cooldown = isPhaseTwo() ? 4 + random.nextInt(6) : 8 + random.nextInt(10);
        lastMove = move;
        plannedMove = MOVE_NONE;
        setMove(MOVE_NONE);
    }

    private class FireballGoal extends Goal {
        FireballGoal() {
            setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK, Goal.Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            LivingEntity t = getTarget();
            if (t == null || !t.isAlive() || plannedMove != MOVE_FIREBALL || isInMove() || !onGround()) {
                return false;
            }
            double d = distanceTo(t);
            return d >= 2.5 && d <= FIREBALL_RANGE && hasLineOfSight(t) && hasHeadroom();
        }

        @Override
        public boolean canContinueToUse() {
            return fireTick >= 0 && fireTick <= FireballMoveData.END_TICK && isAlive();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void start() {
            LivingEntity t = getTarget();
            if (t != null) {
                startFireball(t);
            }
        }

        @Override
        public void tick() {
            if (fireTick >= 0) {
                tickFireball(getTarget());
            }
        }

        @Override
        public void stop() {
            endFireball();
        }
    }

    private class CrescentGoal extends Goal {
        CrescentGoal() {
            setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK, Goal.Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            LivingEntity t = getTarget();
            return t != null && t.isAlive() && plannedMove == MOVE_CRESCENT && !isInMove() && onGround()
                    && distanceTo(t) <= CRESCENT_RANGE && hasLineOfSight(t);
        }

        @Override
        public boolean canContinueToUse() {
            return crescentTick >= 0 && crescentTick <= CrescentMoveData.END_TICK && isAlive();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void start() {
            LivingEntity t = getTarget();
            if (t != null) {
                startCrescent(t);
            }
        }

        @Override
        public void tick() {
            if (crescentTick >= 0) {
                tickCrescent(getTarget());
            }
        }

        @Override
        public void stop() {
            endCrescent();
        }
    }

    // ------------------------------------------------------------ phase 2: the corruption
    public boolean isPhaseTwo() {
        return this.entityData.get(PHASE) >= 2;
    }

    /** damage multiplier for everything it deals */
    public float power() {
        return isPhaseTwo() ? PHASE_TWO_POWER : 1.0F;
    }

    /** the multiplier of whatever Shadow made a fireball or geyser (1 for anything else) */
    public static float powerOf(@Nullable Entity owner) {
        return owner instanceof InfernalShadowEntity s ? s.power() : 1.0F;
    }

    /** client: draw the growths (not in the first ticks of the shift, while the animation is still blending in) */
    public boolean corruptionShown(float partialTick) {
        return isPhaseTwo() && !(getMove() == MOVE_PHASE_SHIFT && moveTime(partialTick) < 4.0F);
    }

    /** client: the netherite over the left arm and the legs is gone (during the shift the animation bursts it off) */
    public boolean armourStripped() {
        return isPhaseTwo() && getMove() != MOVE_PHASE_SHIFT;
    }

    /** client: how hot the growths' veins burn right now (flares at each eruption and at the roar) */
    public float corruptionGlow(float partialTick) {
        float pulse = 0.66F + 0.14F * Mth.sin((tickCount + partialTick) * 0.11F);
        if (getMove() != MOVE_PHASE_SHIFT) {
            return pulse;
        }
        float t = moveTime(partialTick);
        float flare = 0.0F;
        for (int at : new int[]{PhaseMoveData.ARM_TICK, PhaseMoveData.LEGS_TICK, PhaseMoveData.ROAR_TICK}) {
            if (t >= at) {
                flare = Math.max(flare, (float) Math.exp(-(t - at) / 5.0F));
            }
        }
        return Math.min(1.0F, pulse + flare * 0.6F);
    }

    private Vec3 local(double f, double r, double u) {
        return position().add(forward(moveYaw).scale(f)).add(right(moveYaw).scale(r)).add(0, u, 0);
    }

    private void startPhaseShift() {
        pendingShift = false;
        shiftTick = 0;
        plannedMove = MOVE_NONE;
        hitThisMove.clear();
        getNavigation().stop();
        setNoGravity(false);
        clearFire();
        LivingEntity target = getTarget();
        moveYaw = target != null ? yawTo(position(), target.position()) : getYRot();
        faceYaw(moveYaw);
        this.entityData.set(PHASE, 2);
        AttributeInstance speed = getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null && speed.getModifier(PHASE_SPEED_ID) == null) {
            speed.addPermanentModifier(new AttributeModifier(PHASE_SPEED_ID, "Infernal Shadow phase two", PHASE_TWO_SPEED,
                    AttributeModifier.Operation.MULTIPLY_BASE));
        }
        setMove(MOVE_PHASE_SHIFT);
        triggerAnim("main", "phase_shift");
        playSound(SoundEvents.WITHER_HURT, 1.6F, 0.45F);
    }

    private void tickPhaseShift() {
        int t = shiftTick;
        ServerLevel sl = (ServerLevel) level();
        setDeltaMovement(0, getDeltaMovement().y, 0);
        faceYaw(moveYaw);
        if (t == PhaseMoveData.ARM_TICK) {             // left arm: shoulder and forearm
            armourBurst(sl, local(0.0, -0.37, 1.3));
            armourBurst(sl, local(0.15, -0.4, 0.85));
            playSound(SoundEvents.AMETHYST_CLUSTER_BREAK, 1.6F, 0.5F);
        }
        if (t == PhaseMoveData.LEGS_TICK) {
            for (double side : new double[]{-0.13, 0.13}) {
                armourBurst(sl, local(0.0, side, 0.5));
                armourBurst(sl, local(0.0, side, 0.15));
            }
            playSound(SoundEvents.AMETHYST_CLUSTER_BREAK, 1.6F, 0.4F);
        }
        if (t == PhaseMoveData.ARM_TICK || t == PhaseMoveData.LEGS_TICK) {
            playSound(SoundEvents.ANVIL_DESTROY, 1.0F, 0.6F);
        }
        if (t == PhaseMoveData.ROAR_TICK) {
            shockwave(sl);
        }
        int age = t - PhaseMoveData.ROAR_TICK;
        if (age >= 0 && age <= 9) {                 // the blast ring rolling out across the floor
            double r = 0.8 + (SHOCKWAVE_RADIUS - 0.8) * Math.sqrt(age / 9.0);
            int n = (int) (10 + r * 6);
            for (int i = 0; i < n; i++) {
                double a = Math.PI * 2 * (i + random.nextDouble() * 0.5) / n;
                sl.sendParticles(i % 3 == 0 ? ModParticles.PURPLE_FLAME_BIG.get() : ModParticles.PURPLE_FLAME.get(),
                        getX() + Math.cos(a) * r, getY() + 0.15, getZ() + Math.sin(a) * r, 1, 0, 0.05, 0, 0.02);
            }
        }
        shiftTick++;
    }

    /** netherite shards and purple fire bursting off a limb */
    private void armourBurst(ServerLevel sl, Vec3 p) {
        sl.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.NETHERITE_SCRAP)), p.x, p.y, p.z,
                14, 0.12, 0.12, 0.12, 0.2);
        sl.sendParticles(ModParticles.PURPLE_FLAME_BIG.get(), p.x, p.y, p.z, 6, 0.15, 0.15, 0.15, 0.05);
        sl.sendParticles(ModParticles.PURPLE_SPARK.get(), p.x, p.y, p.z, 12, 0.1, 0.1, 0.1, 0.3);
        sl.sendParticles(ParticleTypes.LARGE_SMOKE, p.x, p.y, p.z, 4, 0.1, 0.1, 0.1, 0.03);
    }

    /** the roar: everything close is blasted away and scorched */
    private void shockwave(ServerLevel sl) {
        sl.playSound(null, getX(), getY(), getZ(), SoundEvents.RAVAGER_ROAR, getSoundSource(), 2.2F, 0.55F);
        sl.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE, getSoundSource(), 1.2F, 0.6F);
        sl.sendParticles(ModParticles.PURPLE_FLAME_BIG.get(), getX(), getY() + 1.2, getZ(), 40, 0.5, 0.7, 0.5, 0.12);
        sl.sendParticles(ParticleTypes.EXPLOSION, getX(), getY() + 1.0, getZ(), 2, 0.3, 0.3, 0.3, 0.0);
        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(SHOCKWAVE_RADIUS, 2.5, SHOCKWAVE_RADIUS),
                e -> e != this && e.isAlive() && !(e instanceof InfernalShadowEntity))) {
            Vec3 d = e.position().subtract(position()).multiply(1, 0, 1);
            double dist = d.length();
            if (dist > SHOCKWAVE_RADIUS) {
                continue;
            }
            Vec3 dir = dist < 0.01 ? forward(moveYaw) : d.scale(1.0 / dist);
            double k = 0.5 + 1.3 * (1.0 - dist / SHOCKWAVE_RADIUS);
            if (e.hurt(damageSources().mobAttack(this), SHOCKWAVE_DAMAGE)) {
                ShadowFire.ignite(e, 3);
            }
            e.setDeltaMovement(dir.x * k, 0.45, dir.z * k);
            e.hurtMarked = true;
        }
    }

    private void endPhaseShift() {
        shiftTick = -1;
        cooldown = 10;
        lastMove = MOVE_NONE;
        plannedMove = MOVE_NONE;
        setMove(MOVE_NONE);
    }

    /** takes over from whatever it is doing (but lets the hammer dive and the fireball flight land first) */
    private class PhaseShiftGoal extends Goal {
        PhaseShiftGoal() {
            setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return pendingShift && isAlive() && getMove() != MOVE_HAMMER && getMove() != MOVE_FIREBALL;
        }

        @Override
        public boolean canContinueToUse() {
            return shiftTick >= 0 && shiftTick <= PhaseMoveData.END_TICK && isAlive();
        }

        @Override
        public boolean isInterruptable() {
            return false;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void start() {
            startPhaseShift();
        }

        @Override
        public void tick() {
            if (shiftTick >= 0) {
                tickPhaseShift();
            }
        }

        @Override
        public void stop() {
            if (shiftTick >= 0) {
                endPhaseShift();
            }
        }
    }

    /** client: the growths smoulder (embers off the left arm and the greaves) */
    private void corruptionParticles(double lift) {
        float yaw = this.yBodyRot;
        if (random.nextInt(2) == 0) {
            boolean arm = random.nextBoolean();
            double r = arm ? -0.37 - random.nextDouble() * 0.15 : (random.nextBoolean() ? -0.13 : 0.13);
            double u = arm ? 0.8 + random.nextDouble() * 0.75 : random.nextDouble() * 0.75;
            Vec3 p = position().add(forward(yaw).scale((random.nextDouble() - 0.5) * 0.3)).add(right(yaw).scale(r)).add(0, u + lift, 0);
            level().addParticle(random.nextInt(4) == 0 ? ModParticles.PURPLE_FLAME.get() : ModParticles.PURPLE_EMBER.get(),
                    p.x, p.y, p.z, 0, 0.02 + random.nextDouble() * 0.02, 0);
        }
    }

    // while it is up in the air it cannot be hit, pushed or bumped into (the entity waits on the ground below)
    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean bypass = source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
        if ((isAirborne() || getMove() == MOVE_PHASE_SHIFT) && !bypass) {
            return false;
        }
        if (!isPhaseTwo() && !bypass && !level().isClientSide && amount >= getHealth()) {
            amount = Math.max(0.0F, getHealth() - 1.0F);     // it never dies before the corruption takes it
        }
        return super.hurt(source, amount);
    }

    @Override
    public boolean isPickable() {
        return !isAirborne() && super.isPickable();
    }

    @Override
    public boolean isPushable() {
        return !isAirborne() && super.isPushable();
    }

    @Override
    protected void pushEntities() {
        if (!isAirborne()) {
            super.pushEntities();
        }
    }

    @Override
    public void push(Entity entity) {
        if (!isAirborne()) {
            super.push(entity);
        }
    }

    /** keep rendering while it is drawn high above its feet */
    @Override
    public AABB getBoundingBoxForCulling() {
        AABB box = super.getBoundingBoxForCulling();
        if (getMove() == MOVE_VORTEX) {
            return box.expandTowards(0, VortexShape.HEIGHT + 1.0, 0).inflate(VortexShape.TOP_RADIUS + 1.0, 0, VortexShape.TOP_RADIUS + 1.0);
        }
        return getMove() == MOVE_HAMMER ? box.expandTowards(0, HammerMoveData.HOVER_HEIGHT + 3.0, 0).inflate(HammerMoveData.OUTER_RADIUS, 0, HammerMoveData.OUTER_RADIUS) : box;
    }

    // ------------------------------------------------------------ GeckoLib
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 3, state -> {
            if (state.isMoving() && !isInMove()) {
                return state.setAndContinue(STALK);
            }
            return state.setAndContinue(READY);
        }).triggerableAnim("hellscrape", HELLSCRAPE).triggerableAnim("hammer_anvil", HAMMER)
                .triggerableAnim("combo", COMBO).triggerableAnim("vortex", VORTEX).triggerableAnim("vortex_end", VORTEX_END)
                .triggerableAnim("fireball_volley", FIREBALL).triggerableAnim("crescent", CRESCENT)
                .triggerableAnim("phase_shift", PHASE_SHIFT)
                .setAnimationSpeedHandler(e -> {
            if (e.isInMove()) {
                return 1.0;
            }
            // walk loop matched to the actual ground speed
            double v = Math.sqrt((e.getX() - e.xo) * (e.getX() - e.xo) + (e.getZ() - e.zo) * (e.getZ() - e.zo)) * 20.0;
            return v < 0.05 ? 1.0 : Mth.clamp(v / ShadowMoveData.STALK_SPEED, 0.6, 3.0);
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    // ------------------------------------------------------------ misc
    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.WITHER_SKELETON_AMBIENT;
    }

    @Override
    public float getVoicePitch() {
        return 0.6F;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.PLAYER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.WITHER_SKELETON_DEATH;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public void setCustomName(@Nullable net.minecraft.network.chat.Component name) {
        super.setCustomName(name);
        bossBar.setName(getDisplayName());
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        UUID id = getCopyOf();
        if (id != null) {
            tag.putUUID("CopyOf", id);
        }
        tag.putInt("Phase", this.entityData.get(PHASE));
        if (homePos != null) {
            tag.putDouble("HomeX", homePos.x);
            tag.putDouble("HomeY", homePos.y);
            tag.putDouble("HomeZ", homePos.z);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("CopyOf")) {
            this.entityData.set(COPY_OF, Optional.of(tag.getUUID("CopyOf")));
        }
        if (tag.contains("Phase")) {
            this.entityData.set(PHASE, Math.max(1, tag.getInt("Phase")));
        }
        if (tag.contains("HomeX")) {
            homePos = new Vec3(tag.getDouble("HomeX"), tag.getDouble("HomeY"), tag.getDouble("HomeZ"));
        }
        if (hasCustomName()) {
            bossBar.setName(getDisplayName());
        }
    }
}
