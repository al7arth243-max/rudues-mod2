package com.rudues.mod.entity;

import com.rudues.mod.registry.ModEffects;
import com.rudues.mod.registry.ModEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.Optional;
import java.util.UUID;

/**
 * The 7-block "Hero of Hell" demon. It is a render/animation avatar that glues itself to the transformed
 * player every tick (the player stays the real, controllable entity and keeps the health pool).
 */
public class DemonEntity extends Entity implements GeoEntity {
    private static final EntityDataAccessor<Optional<UUID>> OWNER =
            SynchedEntityData.defineId(DemonEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Boolean> MOVING =
            SynchedEntityData.defineId(DemonEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> INTRO =
            SynchedEntityData.defineId(DemonEntity.class, EntityDataSerializers.BOOLEAN);
    /** Hero's Rush: entity id of the victim being dragged on its back (-1 = none) and the game tick the drag began. */
    private static final EntityDataAccessor<Integer> DRAGGED =
            SynchedEntityData.defineId(DemonEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DRAG_START =
            SynchedEntityData.defineId(DemonEntity.class, EntityDataSerializers.INT);

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.infernal_titan.idle");
    private static final RawAnimation RUN = RawAnimation.begin().thenLoop("animation.infernal_titan.run");
    private static final RawAnimation JUMP = RawAnimation.begin().thenPlayAndHold("animation.infernal_titan.jump");
    private static final RawAnimation SPAWN = RawAnimation.begin().thenPlay("animation.infernal_titan.spawn");
    private static final RawAnimation BEAM = RawAnimation.begin().thenPlay("animation.infernal_titan.concentrated_beam");
    private static final RawAnimation OBLITERATOR = RawAnimation.begin().thenPlay("animation.infernal_titan.obliterator_maximum");
    private static final RawAnimation HELLFIRE = RawAnimation.begin().thenPlay("animation.infernal_titan.hellfire");
    private static final RawAnimation METEOR_RUSH = RawAnimation.begin().thenPlayAndHold("animation.infernal_titan.meteor_rush");
    private static final RawAnimation METEOR_LEAP = RawAnimation.begin().thenPlayAndHold("animation.infernal_titan.meteor_leap");
    private static final RawAnimation METEOR_SLAM = RawAnimation.begin().thenPlay("animation.infernal_titan.meteor_slam");
    private static final RawAnimation METEOR_RECOVER = RawAnimation.begin().thenPlay("animation.infernal_titan.meteor_recover");
    private static final RawAnimation TREE_RUSH = RawAnimation.begin().thenPlayAndHold("animation.infernal_titan.tree_rush");
    private static final RawAnimation TREE_COMBO = RawAnimation.begin().thenPlay("animation.infernal_titan.tree_combo");
    private static final RawAnimation TREE_MISS = RawAnimation.begin().thenPlay("animation.infernal_titan.tree_miss");
    private static final RawAnimation HR_RUSH = RawAnimation.begin().thenPlayAndHold("animation.infernal_titan.hr_rush");
    private static final RawAnimation HR_COMBO = RawAnimation.begin().thenPlay("animation.infernal_titan.hr_combo");
    private static final RawAnimation SLASH = RawAnimation.begin().thenPlay("animation.infernal_titan.slash_combo");

    /** length of the spawn/roar animation in ticks */
    private static final int INTRO_TICKS = 28;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private double lastOwnerX;
    private double lastOwnerZ;

    public DemonEntity(EntityType<? extends DemonEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public DemonEntity(Level level, Player owner, boolean intro) {
        this(ModEntities.DEMON.get(), level);
        this.entityData.set(OWNER, Optional.of(owner.getUUID()));
        this.entityData.set(INTRO, intro);
        glue(owner);
    }

    @Nullable
    public UUID getOwnerUUID() {
        return this.entityData.get(OWNER).orElse(null);
    }

    /** Server: start (entityId >= 0) or stop (-1) drawing the dragged victim lying on its back. */
    public void setDragged(int entityId, long gameTime) {
        this.entityData.set(DRAGGED, entityId);
        this.entityData.set(DRAG_START, (int) gameTime);
    }

    public int getDraggedId() {
        return this.entityData.get(DRAGGED);
    }

    public int getDragStart() {
        return this.entityData.get(DRAG_START);
    }

    @Nullable
    private Player getOwner() {
        UUID id = getOwnerUUID();
        return id == null ? null : level().getPlayerByUUID(id);
    }

    private void glue(Player owner) {
        setPos(owner.getX(), owner.getY(), owner.getZ());
        setYRot(owner.getYRot());
        setXRot(0.0F);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            return;
        }
        Player owner = getOwner();
        if (owner == null || !owner.isAlive() || !owner.hasEffect(ModEffects.HERO_OF_HELL.get())) {
            discard();
            return;
        }
        glue(owner);
        // (owner.xo is already updated by the time this runs, so compare with our own previous sample)
        boolean moving = Math.hypot(owner.getX() - lastOwnerX, owner.getZ() - lastOwnerZ) > 0.04;
        lastOwnerX = owner.getX();
        lastOwnerZ = owner.getZ();
        if (this.entityData.get(MOVING) != moving) {
            this.entityData.set(MOVING, moving);
        }
    }

    // ------------------------------------------------------------ GeckoLib
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 3, this::mainAnimation));
        controllers.add(new AnimationController<>(this, "attack", 0, state -> PlayState.STOP)
                .triggerableAnim("beam", BEAM)
                .triggerableAnim("obliterator", OBLITERATOR)
                .triggerableAnim("hellfire", HELLFIRE)
                .triggerableAnim("slash", SLASH)
                .triggerableAnim("meteor_rush", METEOR_RUSH)
                .triggerableAnim("meteor_leap", METEOR_LEAP)
                .triggerableAnim("meteor_slam", METEOR_SLAM)
                .triggerableAnim("meteor_recover", METEOR_RECOVER)
                .triggerableAnim("tree_rush", TREE_RUSH)
                .triggerableAnim("tree_combo", TREE_COMBO)
                .triggerableAnim("tree_miss", TREE_MISS)
                .triggerableAnim("hr_rush", HR_RUSH)
                .triggerableAnim("hr_combo", HR_COMBO));
    }

    private PlayState mainAnimation(AnimationState<DemonEntity> state) {
        if (this.entityData.get(INTRO) && this.tickCount < INTRO_TICKS) {
            return state.setAndContinue(SPAWN);
        }
        Player owner = getOwner();
        if (owner != null && !owner.onGround() && !owner.isInWater() && !owner.isInLava()) {
            return state.setAndContinue(JUMP);
        }
        return state.setAndContinue(isOwnerMoving() ? RUN : IDLE);
    }

    /** Client side: the owning player is right here, so just look at how far they moved this tick. */
    private boolean isOwnerMoving() {
        Player owner = getOwner();
        if (owner == null) {
            return this.entityData.get(MOVING);
        }
        return Math.hypot(owner.getX() - owner.xo, owner.getZ() - owner.zo) > 0.03;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    // ------------------------------------------------------------ boilerplate
    @Override
    protected void defineSynchedData() {
        this.entityData.define(OWNER, Optional.empty());
        this.entityData.define(MOVING, false);
        this.entityData.define(INTRO, true);
        this.entityData.define(DRAGGED, -1);
        this.entityData.define(DRAG_START, 0);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("Owner")) {
            this.entityData.set(OWNER, Optional.of(tag.getUUID("Owner")));
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        UUID id = getOwnerUUID();
        if (id != null) {
            tag.putUUID("Owner", id);
        }
    }
}
